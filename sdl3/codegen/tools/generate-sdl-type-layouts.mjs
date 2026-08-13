#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const toolDir = dirname(fileURLToPath(import.meta.url));
const moduleDir = resolve(toolDir, "../..");
const repositoryDir = resolve(moduleDir, "..");
const headerDir = resolve(moduleDir, "include");
const layoutSource = resolve(
  moduleDir,
  "codegen/src/main/kotlin/net/echonolix/caelum/sdl3/codegen/SDLTypeLayouts.kt",
);

const currentSource = readFileSync(layoutSource, "utf8");
const expectedRecords = [
  ...currentSource.matchAll(/(?:record\(\s*"|name\s*=\s*")(SDL_[^"]+)"/g),
]
  .map((match) => match[1])
  .filter((name, index, names) => names.indexOf(name) === index)
  .sort();

if (expectedRecords.length !== 122) {
  throw new Error(`Expected 122 records in the checked-in snapshot, found ${expectedRecords.length}`);
}

const layoutDump = execFileSync(
  "clang",
  [
    "-target", "x86_64-pc-windows-msvc",
    `-I${headerDir}`,
    "-include", "SDL3/SDL.h",
    "-Xclang", "-fdump-record-layouts-complete",
    "-Xclang", "-fdump-record-layouts-simple",
    "-fsyntax-only", "-x", "c", "NUL",
  ],
  { cwd: repositoryDir, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 },
);

const layouts = parseSimpleLayouts(layoutDump);
const ast = JSON.parse(execFileSync(
  "clang",
  [
    "-target", "x86_64-pc-windows-msvc",
    `-I${headerDir}`,
    "-include", "SDL3/SDL.h",
    "-Xclang", "-ast-dump=json",
    "-fsyntax-only", "-x", "c", "NUL",
  ],
  { cwd: repositoryDir, encoding: "utf8", maxBuffer: 512 * 1024 * 1024 },
));
const declarations = collectRecordDeclarations(ast);
const records = expectedRecords.map((name) => buildRecord(name, layouts, declarations));

writeFileSync(layoutSource, renderKotlin(records));

const storageFields = records.flatMap((record) => record.fields.filter((field) => field.carrier === "STORAGE"));
const partialRecords = records.filter((record) => record.fields.some((field) => field.carrier === "STORAGE"));
const storageOnlyRecords = records.filter(
  (record) => record.fields.length === 1 && record.fields[0].carrier === "STORAGE",
);
console.log(
  `Generated ${records.length} records, ${records.reduce((sum, record) => sum + record.fields.length, 0)} fields, ` +
    `${partialRecords.length} partial records, ${storageOnlyRecords.length} storage-only records, ` +
    `${storageFields.length} unsupported fields.`,
);

function parseSimpleLayouts(output) {
  const result = new Map();
  const pattern = /Type: (struct|union) (SDL_[^\r\n]+)\s+\r?\n\s*Layout: <ASTRecordLayout\s+Size:(\d+)\s+Alignment:(\d+)\s+FieldOffsets: \[([^\]]*)\]>/g;
  for (const match of output.matchAll(pattern)) {
    const name = match[2].trim();
    if (!/^SDL_[A-Za-z0-9_]+$/.test(name)) continue;
    result.set(name, {
      kind: match[1] === "union" ? "UNION" : "STRUCT",
      size: Number(match[3]) / 8,
      alignment: Number(match[4]) / 8,
      offsets: match[5].trim() === ""
        ? []
        : match[5].split(",").map((offset) => Number(offset.trim()) / 8),
    });
  }
  return result;
}

function collectRecordDeclarations(root) {
  const result = new Map();
  walk(root, (node) => {
    if (
      node.kind === "RecordDecl" &&
      node.completeDefinition === true &&
      /^SDL_[A-Za-z0-9_]+$/.test(node.name ?? "")
    ) {
      const fields = (node.inner ?? []).filter((child) => child.kind === "FieldDecl");
      const existing = result.get(node.name);
      if (fields.length > (existing?.fields.length ?? -1)) {
        result.set(node.name, { tag: node.tagUsed, fields });
      }
    }
  });
  return result;
}

function walk(node, visit) {
  if (node == null || typeof node !== "object") return;
  visit(node);
  for (const child of node.inner ?? []) walk(child, visit);
}

function buildRecord(name, layouts, declarations) {
  const layout = layouts.get(name);
  const declaration = declarations.get(name);
  if (layout == null) throw new Error(`Missing Clang layout for ${name}`);
  if (declaration == null) throw new Error(`Missing Clang declaration for ${name}`);
  if (layout.offsets.length !== declaration.fields.length) {
    throw new Error(
      `${name}: ${layout.offsets.length} offsets but ${declaration.fields.length} direct fields`,
    );
  }

  const fields = declaration.fields.map((field, index) => {
    const offset = layout.offsets[index];
    const nextOffset = layout.kind === "UNION"
      ? layout.size
      : (layout.offsets[index + 1] ?? layout.size);
    return classifyField(name, field, offset, nextOffset - offset, layouts);
  });
  return { name, ...layout, fields };
}

function classifyField(recordName, field, offset, availableSize, layouts) {
  const nativeType = normalizeSnapshotText(field.type?.qualType ?? "unknown");
  const canonicalType = normalizeSnapshotText(field.type?.desugaredQualType ?? nativeType);
  const name = field.name ?? `anonymous_${offset}`;
  const array = canonicalType.match(/^(.*)\[(\d+)]$/);
  const elementType = (array?.[1] ?? canonicalType).trim();
  const elementCount = Number(array?.[2] ?? 1);
  const pointer = elementType.includes("(*)") || /\(\s*\*[^)]*\)\s*\(/.test(elementType)
    ? { function: true, pointee: null }
    : parsePointer(elementType);

  let classified;
  if (field.isBitfield === true) {
    classified = unsupported("bitfield");
  } else if (pointer != null) {
    classified = {
      carrier: "POINTER",
      pointerType: pointer.function ? null : normalizePointee(pointer.pointee),
      unsupportedReason: pointer.function ? "function pointer exposed as an untyped native pointer" : null,
    };
  } else {
    const recordType = parseRecordType(elementType);
    if (recordType != null && layouts.has(recordType)) {
      classified = { carrier: "RECORD", recordType };
    } else {
      classified = classifyScalar(elementType) ?? unsupported(`unsupported C type: ${nativeType}`);
    }
  }

  const elementSize = carrierSize(classified, layouts);
  const exactSize = elementSize == null ? availableSize : elementSize * elementCount;
  if (exactSize > availableSize) {
    throw new Error(
      `${recordName}.${name}: classified size ${exactSize} exceeds available ${availableSize} (${nativeType})`,
    );
  }
  return {
    name,
    offset,
    size: exactSize,
    elementCount,
    nativeType,
    ...classified,
  };
}

function parsePointer(type) {
  const normalized = type.replace(/\s+/g, " ").trim();
  const match = normalized.match(/^(.*?)\s*\*\s*(?:const\s*)?$/);
  if (match == null) return null;
  const pointee = match[1].replace(/^const\s+/, "").trim();
  const nested = parsePointer(pointee);
  return {
    function: false,
    pointee: nested == null ? pointee : `NPointer<${normalizePointerType(nested.pointee)}>`,
  };
}

function normalizePointee(type) {
  return type.startsWith("NPointer<")
    ? type
    : normalizePointerType(type);
}

function normalizePointerType(type) {
  return type.replace(/^(struct|union|enum)\s+/, "").replace(/\s+const$/, "").trim();
}

function parseRecordType(type) {
  const match = type.match(/^(?:struct|union)\s+(SDL_[A-Za-z0-9_]+)$/);
  return match?.[1] ?? (/^SDL_[A-Za-z0-9_]+$/.test(type) ? type : null);
}

function classifyScalar(type) {
  const normalized = type.replace(/^(?:enum\s+)?SDL_[A-Za-z0-9_]+$/, (value) =>
    value.startsWith("enum ") ? "int" : value,
  );
  if (/^(?:_Bool|bool)$/.test(normalized)) return { carrier: "BOOL" };
  if (/^(?:char|signed char|Sint8|int8_t)$/.test(normalized)) return { carrier: "INT8" };
  if (/^(?:unsigned char|Uint8|uint8_t)$/.test(normalized)) return { carrier: "UINT8" };
  if (/^(?:short|short int|signed short|signed short int|Sint16|int16_t)$/.test(normalized)) return { carrier: "INT16" };
  if (/^(?:unsigned short|unsigned short int|Uint16|uint16_t|wchar_t)$/.test(normalized)) return { carrier: "UINT16" };
  if (/^(?:int|signed int|long|long int|Sint32|int32_t)$/.test(normalized)) return { carrier: "INT32" };
  if (/^(?:unsigned|unsigned int|unsigned long|unsigned long int|Uint32|uint32_t)$/.test(normalized)) return { carrier: "UINT32" };
  if (/^(?:long long|long long int|signed long long|signed long long int|Sint64|int64_t|intptr_t|ptrdiff_t)$/.test(normalized)) return { carrier: "INT64" };
  if (/^(?:unsigned long long|unsigned long long int|Uint64|uint64_t|size_t|uintptr_t)$/.test(normalized)) return { carrier: "UINT64" };
  if (normalized === "float") return { carrier: "FLOAT" };
  if (normalized === "double") return { carrier: "DOUBLE" };
  if (/^SDL_[A-Za-z0-9_]+$/.test(normalized)) return { carrier: "INT32" };
  return null;
}

function carrierSize(field, layouts) {
  switch (field.carrier) {
    case "BOOL":
    case "INT8":
    case "UINT8": return 1;
    case "INT16":
    case "UINT16": return 2;
    case "INT32":
    case "UINT32":
    case "FLOAT": return 4;
    case "INT64":
    case "UINT64":
    case "DOUBLE":
    case "POINTER": return 8;
    case "RECORD": return layouts.get(field.recordType)?.size;
    default: return null;
  }
}

function unsupported(reason) {
  return { carrier: "STORAGE", unsupportedReason: reason };
}

function normalizeSnapshotText(value) {
  return value.replace(
    /(?:[A-Za-z]:)?[^()]*?[\\/]sdl3[\\/]include[\\/]SDL3[\\/]([^():]+\.h:\d+:\d+)/g,
    "SDL3/$1",
  );
}

function renderKotlin(records) {
  const lines = [];
  lines.push("package net.echonolix.caelum.sdl3.codegen", "");
  lines.push("internal enum class SDLRecordKind {", "    STRUCT,", "    UNION,", "}", "");
  lines.push("internal enum class SDLFieldCarrier {");
  for (const carrier of ["BOOL", "INT8", "UINT8", "INT16", "UINT16", "INT32", "UINT32", "INT64", "UINT64", "FLOAT", "DOUBLE", "POINTER", "RECORD", "STORAGE"]) {
    lines.push(`    ${carrier},`);
  }
  lines.push("}", "");
  lines.push(
    "internal data class SDLFieldLayout(",
    "    val name: String,",
    "    val offset: Long,",
    "    val size: Long,",
    "    val carrier: SDLFieldCarrier,",
    "    val elementCount: Long = 1,",
    "    val nativeType: String,",
    "    val recordType: String? = null,",
    "    val pointerType: String? = null,",
    "    val unsupportedReason: String? = null,",
    ")",
    "",
    "internal data class SDLRecordLayout(",
    "    val name: String,",
    "    val kind: SDLRecordKind,",
    "    val size: Long,",
    "    val alignment: Long,",
    "    val fields: List<SDLFieldLayout>,",
    ")",
    "",
    "/**",
    " * Public SDL 3.4.14 record ABI captured with Clang 22 targeting",
    " * `x86_64-pc-windows-msvc`. Regenerate with",
    " * `node sdl3/codegen/tools/generate-sdl-type-layouts.mjs` after updating SDL.",
    " */",
    "internal object SDLWindowsX64Layouts {",
    "    val records: List<SDLRecordLayout> = listOf(",
  );
  for (const record of records) renderRecord(lines, record);
  lines.push(
    "    )",
    "",
    "    val byName: Map<String, SDLRecordLayout> = records.associateBy(SDLRecordLayout::name)",
    "}",
    "",
  );
  return lines.join("\n");
}

function renderRecord(lines, record) {
  lines.push(
    "        SDLRecordLayout(",
    `            name = ${quote(record.name)},`,
    `            kind = SDLRecordKind.${record.kind},`,
    `            size = ${record.size}L,`,
    `            alignment = ${record.alignment}L,`,
    "            fields = listOf(",
  );
  for (const field of record.fields) {
    const extras = [];
    if (field.elementCount !== 1) extras.push(`elementCount = ${field.elementCount}L`);
    extras.push(`nativeType = ${quote(field.nativeType)}`);
    if (field.recordType != null) extras.push(`recordType = ${quote(field.recordType)}`);
    if (field.pointerType != null) extras.push(`pointerType = ${quote(field.pointerType)}`);
    if (field.unsupportedReason != null) extras.push(`unsupportedReason = ${quote(field.unsupportedReason)}`);
    lines.push(
      `                SDLFieldLayout(${quote(field.name)}, ${field.offset}L, ${field.size}L, ` +
        `SDLFieldCarrier.${field.carrier}, ${extras.join(", ")}),`,
    );
  }
  lines.push("            ),", "        ),");
}

function quote(value) {
  return JSON.stringify(value).replace(/\$/g, "\\$");
}
