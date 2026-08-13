#!/usr/bin/env python3
"""Generate versioned Caelum DirectX/DXGI raw API catalog schemas.

This tool is intentionally outside the normal Gradle build.  It consumes pinned
Windows SDK / DirectX-Headers inputs, asks clang for the authoritative C ABI AST,
and emits deterministic JSON resources.  It can also drive jextract with a
dependency-closure filter for maintainers regenerating raw Java FFM sources.

Requirements: Python 3.11+, clang 20+ targeting x86_64-pc-windows-msvc.
The jextract subcommand additionally requires an OpenJDK jextract distribution.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Iterator, Sequence


SCHEMA_VERSION = 1
TARGET_TRIPLE = "x86_64-pc-windows-msvc"
SDK_VERSION = "10.0.22621.0"
DIRECTX_HEADERS_VERSION = "1.619.5"
DIRECTX_HEADERS_COMMIT = "ee479f0bd5f7b884f202bcf0c3f076cc050dd256"


@dataclass(frozen=True)
class HeaderSpec:
    role: str
    name: str


@dataclass(frozen=True)
class Profile:
    api: str
    artifact: str
    output_name: str
    headers: tuple[HeaderSpec, ...]
    dlls: tuple[str, ...]
    symbol_prefixes: tuple[str, ...]
    exact_symbols: tuple[str, ...] = ()
    symbol_dlls: tuple[tuple[str, str], ...] = ()


PROFILES: OrderedDict[str, Profile] = OrderedDict(
    (
        ("d3d9", Profile(
            "d3d9", "directx", f"d3d9-sdk-{SDK_VERSION}.json",
            (
                HeaderSpec("windows-sdk-shared", "d3d9.h"),
                HeaderSpec("windows-sdk-shared", "d3d9types.h"),
                HeaderSpec("windows-sdk-shared", "d3d9caps.h"),
                HeaderSpec("windows-sdk-um", "d3d9on12.h"),
            ),
            ("d3d9.dll",), ("D3D", "IDirect3D"),
            ("Direct3DCreate9", "Direct3DCreate9Ex", "Direct3DCreate9On12", "Direct3DCreate9On12Ex"),
        )),
        ("d3d10", Profile(
            "d3d10", "directx", f"d3d10-sdk-{SDK_VERSION}.json",
            tuple(HeaderSpec("windows-sdk-um", x) for x in (
                "d3d10_1.h", "d3d10.h", "d3d10misc.h", "d3d10sdklayers.h",
                "d3d10shader.h", "d3d10_1shader.h", "d3d10effect.h",
            )),
            ("d3d10.dll", "d3d10_1.dll"), ("D3D10", "ID3D10"),
            symbol_dlls=(
                ("D3D10CreateDevice1", "d3d10_1.dll"),
                ("D3D10CreateDeviceAndSwapChain1", "d3d10_1.dll"),
            ),
        )),
        ("d3d11", Profile(
            "d3d11", "directx", f"d3d11-sdk-{SDK_VERSION}.json",
            tuple(HeaderSpec("windows-sdk-um", x) for x in (
                "d3d11.h", "d3d11_1.h", "d3d11_2.h", "d3d11_3.h", "d3d11_4.h",
                "d3d11on12.h", "d3d11sdklayers.h", "d3d11shader.h", "d3d11shadertracing.h",
            )),
            ("d3d11.dll", "d3dcompiler_47.dll"), ("D3D11", "ID3D11"),
            ("D3DDisassemble11Trace",),
        )),
        ("d3dcommon-compiler", Profile(
            "d3dcommon-compiler", "directx", f"d3dcommon-compiler-sdk-{SDK_VERSION}.json",
            (HeaderSpec("windows-sdk-um", "d3dcommon.h"), HeaderSpec("windows-sdk-um", "d3dcompiler.h")),
            ("d3dcompiler_47.dll",), ("D3D", "ID3D"),
        )),
        ("d3d12", Profile(
            "d3d12", "directx", f"d3d12-headers-{DIRECTX_HEADERS_VERSION}.json",
            tuple(HeaderSpec("directx-headers", x) for x in (
                "d3d12.h", "d3d12compatibility.h", "d3d12compiler.h", "d3d12sdklayers.h",
                "d3d12shader.h", "d3d12video.h", "D3D12Events.h", "d3dcommon.h", "d3dshadercacheregistration.h",
            )),
            ("d3d12.dll", "D3D12StateObjectCompiler.dll"),
            ("D3D12", "ID3D12", "D3D_SHADER_CACHE", "ID3DShaderCache", "EventD3D12", "Direct3D12", "MSG_Map_D3D12"),
            symbol_dlls=(
                ("D3D12CompilerCreateFactory", "D3D12StateObjectCompiler.dll"),
                ("D3D12CompilerSerializeVersionedRootSignature", "D3D12StateObjectCompiler.dll"),
            ),
        )),
        ("dxgi", Profile(
            "dxgi", "dxgi", f"dxgi-sdk-{SDK_VERSION}.json",
            (
                *(HeaderSpec("windows-sdk-shared", x) for x in (
                    "dxgi.h", "dxgi1_2.h", "dxgi1_3.h", "dxgi1_4.h", "dxgi1_5.h", "dxgi1_6.h",
                    "dxgicommon.h", "dxgiformat.h", "dxgitype.h",
                )),
                HeaderSpec("windows-sdk-um", "dxgidebug.h"),
                HeaderSpec("windows-sdk-um", "DXGIMessages.h"),
            ),
            ("dxgi.dll", "dxgidebug.dll"), ("DXGI", "IDXGI"),
            ("CreateDXGIFactory", "CreateDXGIFactory1", "CreateDXGIFactory2", "DXGIGetDebugInterface1"),
            (("DXGIGetDebugInterface", "dxgidebug.dll"),),
        )),
    )
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def run(command: Sequence[str], *, input_text: str | None = None, cwd: Path | None = None,
        check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        list(command), input=input_text, text=True, encoding="utf-8", errors="replace",
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=cwd,
    )
    if check and result.returncode:
        rendered = subprocess.list2cmdline(list(command))
        raise RuntimeError(f"command failed ({result.returncode}): {rendered}\n{result.stderr[-12000:]}")
    return result


def java_argfile_quote(value: str) -> str:
    """Quote one token for the Java launcher @argfile grammar."""
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'


def header_path(spec: HeaderSpec, roots: dict[str, Path]) -> Path:
    root = roots[spec.role]
    candidate = root / spec.name
    if not candidate.is_file():
        raise FileNotFoundError(f"missing {spec.role} header: {candidate}")
    return candidate.resolve()


def source_loc(node: dict[str, Any]) -> tuple[str | None, int | None]:
    def unwrap(loc: dict[str, Any] | None) -> dict[str, Any]:
        if not loc:
            return {}
        return loc.get("expansionLoc") or loc.get("spellingLoc") or loc

    loc = unwrap(node.get("loc"))
    if not loc.get("file"):
        begin = unwrap((node.get("range") or {}).get("begin"))
        if begin.get("file"):
            loc = begin
    return loc.get("file"), loc.get("line")


def split_top_level(text: str, separator: str = ",") -> list[str]:
    result: list[str] = []
    depth = 0
    start = 0
    for index, char in enumerate(text):
        if char in "([<{":
            depth += 1
        elif char in ")]>":
            depth = max(0, depth - 1)
        elif char == separator and depth == 0:
            result.append(text[start:index].strip())
            start = index + 1
    tail = text[start:].strip()
    if tail:
        result.append(tail)
    return result


def parse_function_pointer(signature: str) -> tuple[str | None, list[str]]:
    marker = "(*)"
    normalized = signature.replace("(*)(", "(*) (")
    index = normalized.find(marker)
    if index < 0:
        return None, []
    return_type = normalized[:index].strip()
    open_paren = normalized.find("(", index + len(marker))
    if open_paren < 0:
        return return_type, []
    depth = 0
    close_paren = -1
    for i in range(open_paren, len(normalized)):
        if normalized[i] == "(":
            depth += 1
        elif normalized[i] == ")":
            depth -= 1
            if depth == 0:
                close_paren = i
                break
    if close_paren < 0:
        return return_type, []
    body = normalized[open_paren + 1:close_paren].strip()
    if not body or body == "void":
        return return_type, []
    return return_type, split_top_level(body)


def first_constant_value(node: dict[str, Any]) -> str | None:
    if "value" in node and node.get("kind") in {
        "ConstantExpr", "IntegerLiteral", "FloatingLiteral", "CharacterLiteral", "StringLiteral",
    }:
        return str(node["value"])
    for child in node.get("inner") or ():
        value = first_constant_value(child)
        if value is not None:
            return value
    return None


def guid_from_var(node: dict[str, Any]) -> str | None:
    values: list[int] = []

    def visit(current: dict[str, Any]) -> None:
        if current.get("kind") == "IntegerLiteral" and "value" in current:
            values.append(int(current["value"]))
        for child in current.get("inner") or ():
            visit(child)

    visit(node)
    if len(values) < 11:
        return None
    d1, d2, d3, *d4 = values[:11]
    return f"{d1 & 0xffffffff:08x}-{d2 & 0xffff:04x}-{d3 & 0xffff:04x}-{d4[0] & 0xff:02x}{d4[1] & 0xff:02x}-" + "".join(
        f"{x & 0xff:02x}" for x in d4[2:8]
    )


def declaration_header(node: dict[str, Any], owned: dict[str, Path]) -> tuple[str, int] | None:
    file, line = source_loc(node)
    if not file:
        return None
    try:
        resolved = Path(file).resolve()
    except OSError:
        return None
    key = os.path.normcase(str(resolved))
    if key not in owned:
        return None
    return owned[key].name, int(line or 0)


def iter_json_documents(text: str) -> Iterator[dict[str, Any]]:
    decoder = json.JSONDecoder()
    index = 0
    length = len(text)
    while index < length:
        while index < length and text[index].isspace():
            index += 1
        if index >= length:
            break
        value, index = decoder.raw_decode(text, index)
        if isinstance(value, dict):
            yield value


def dedupe(items: Iterable[dict[str, Any]], key_fields: Sequence[str]) -> list[dict[str, Any]]:
    by_key: dict[tuple[Any, ...], dict[str, Any]] = {}
    for item in items:
        key = tuple(item.get(field) for field in key_fields)
        old = by_key.get(key)
        if old is None or len(json.dumps(item, sort_keys=True)) > len(json.dumps(old, sort_keys=True)):
            by_key[key] = item
    return sorted(by_key.values(), key=lambda item: tuple(str(item.get(field) or "") for field in key_fields))


def record_from_node(node: dict[str, Any], header: str, line: int, fallback_name: str | None = None,
                     layout_owner: str | None = None) -> dict[str, Any]:
    fields: list[dict[str, Any]] = []
    anonymous_index = 0
    pending_anonymous: list[dict[str, Any]] = []
    owner = layout_owner or node.get("name") or fallback_name
    for child in node.get("inner") or ():
        if child.get("kind") == "RecordDecl" and child.get("completeDefinition"):
            anonymous_index += 1
            nested_line = source_loc(child)[1] or line
            nested_name = child.get("name") or f"{fallback_name or node.get('name') or '<anonymous>'}::$anonymous{anonymous_index}"
            pending_anonymous.append(record_from_node(child, header, int(nested_line), nested_name, owner))
        elif child.get("kind") == "FieldDecl":
            field: dict[str, Any] = {
                "name": child.get("name"),
                "type": (child.get("type") or {}).get("qualType"),
                "canonicalType": (child.get("type") or {}).get("desugaredQualType"),
                "offsetBits": None,
                "bitWidth": int(first_constant_value(child) or 0) if child.get("isBitfield") else None,
            }
            if pending_anonymous:
                field_type = " ".join(filter(None, (field.get("type"), field.get("canonicalType"))))
                matched_index = next(
                    (index for index, nested in enumerate(pending_anonymous)
                     if (nested.get("name") and not nested.get("anonymous") and nested["name"] in field_type)
                     or (nested.get("anonymous") and re.search(rf":{nested.get('_layoutLine')}:\d+\)", field_type))),
                    None,
                )
                if matched_index is not None:
                    nested = pending_anonymous.pop(matched_index)
                    if nested.get("anonymous"):
                        field["anonymousRecord"] = nested
                    else:
                        # Persist named nested records as ordinary declarations
                        # so a field signature can resolve them by name.
                        field["_namedRecordDefinition"] = nested
            fields.append(field)
    name = node.get("name") or fallback_name
    return {
        "name": name, "header": header, "sourceLine": line,
        "kind": node.get("tagUsed", "struct"), "size": None, "align": None, "fields": fields,
        "anonymous": not bool(node.get("name")),
        "_layoutOwner": layout_owner or owner, "_layoutLine": line,
    }


def count_record_fields(records: Sequence[dict[str, Any]]) -> tuple[int, int]:
    fields = 0
    bitfields = 0

    def visit(record: dict[str, Any]) -> None:
        nonlocal fields, bitfields
        for field in record.get("fields") or ():
            fields += 1
            if field.get("bitWidth") is not None:
                bitfields += 1
            nested = field.get("anonymousRecord")
            if nested:
                visit(nested)

    for record in records:
        visit(record)
    return fields, bitfields


def promote_named_record_definitions(records: list[dict[str, Any]]) -> None:
    promoted: list[dict[str, Any]] = []

    def visit(record: dict[str, Any]) -> None:
        for field in record.get("fields") or ():
            named = field.pop("_namedRecordDefinition", None)
            if named:
                promoted.append(copy.deepcopy(named))
                visit(named)
            anonymous = field.get("anonymousRecord")
            if anonymous:
                visit(anonymous)

    for record in records:
        visit(record)
    records.extend(promoted)


def count_resolved_offsets(records: Sequence[dict[str, Any]]) -> int:
    resolved = 0

    def visit(record: dict[str, Any]) -> None:
        nonlocal resolved
        for field in record.get("fields") or ():
            if field.get("offsetBits") is not None:
                resolved += 1
            nested = field.get("anonymousRecord")
            if nested:
                visit(nested)

    for record in records:
        visit(record)
    return resolved


def collect_candidates(paths: Sequence[Path], profile: Profile) -> dict[str, set[str]]:
    result = {kind: set() for kind in ("interfaces", "records", "enums", "typedefs", "functions", "constants")}
    name_re = re.compile(r"\b(?:D3D\w*|DXGI\w*|PFN_\w*|ID(?:irect3D|3D|XGI)\w*)\b")
    function_re = re.compile(r"\b(?:WINAPI|STDAPICALLTYPE|APIENTRY|__stdcall)\s+([A-Za-z_]\w*)\s*\(|\b((?:Direct3D|D3D|CreateDXGI|DXGIGet)\w*)\s*\(")
    define_re = re.compile(r"^\s*#\s*define\s+([A-Za-z_]\w*)\b", re.MULTILINE)
    for path in paths:
        text = path.read_text(encoding="utf-8", errors="replace")
        for name in name_re.findall(text):
            if name.startswith(("IDirect3D", "ID3D", "IDXGI")):
                result["interfaces"].add(name)
            else:
                result["records"].add(name)
                result["enums"].add(name)
                result["typedefs"].add(name)
        for match in function_re.finditer(text):
            result["functions"].add(match.group(1) or match.group(2))
        for name in define_re.findall(text):
            if name.startswith(profile.symbol_prefixes):
                result["constants"].add(name)
    result["functions"].update(profile.exact_symbols)
    return result


def public_declaration_names(paths: Sequence[Path], profile: Profile) -> set[str]:
    names: set[str] = set()
    token = re.compile(r"\b(?:D3D\w*|DXGI\w*|PFN_\w*|ID(?:irect3D|3D|XGI)\w*)\b")
    for path in paths:
        names.update(token.findall(path.read_text(encoding="utf-8", errors="replace")))
    names.update(profile.exact_symbols)
    return names


def clang_base_command(clang: Path, include_dirs: Sequence[Path]) -> list[str]:
    command = [
        str(clang), "-x", "c", "-fms-extensions", "-fms-compatibility",
        "-Wno-nonportable-include-path", "-target", TARGET_TRIPLE,
    ]
    for directory in include_dirs:
        command.extend(("-isystem", str(directory)))
    return command


def translation_unit(paths: Sequence[Path]) -> str:
    return "\n".join((
        "#define CINTERFACE 1",
        "#define COBJMACROS 1",
        "#include <initguid.h>",
        *(f'#include "{path.as_posix()}"' for path in paths),
        "",
    ))


def ast_for_filter(clang: Path, include_dirs: Sequence[Path], unit: str, name_filter: str) -> list[dict[str, Any]]:
    command = clang_base_command(clang, include_dirs) + [
        "-Xclang", "-ast-dump=json", "-Xclang", f"-ast-dump-filter={name_filter}", "-fsyntax-only", "-",
    ]
    result = run(command, input_text=unit)
    return list(iter_json_documents(result.stdout))


SIMPLE_LAYOUT = re.compile(
    r"Type:\s+(struct|union)\s+([^\r\n]+)\s+"
    r"Layout:\s+<ASTRecordLayout\s+Size:(\d+)\s+Alignment:(\d+)\s+FieldOffsets:\s*\[([^\]]*)\]>",
    re.MULTILINE,
)


def clang_record_layouts(clang: Path, include_dirs: Sequence[Path], unit: str) -> dict[tuple[str, str], dict[str, Any]]:
    command = clang_base_command(clang, include_dirs) + [
        "-Xclang", "-fdump-record-layouts-simple", "-Xclang", "-fdump-record-layouts-complete",
        "-fsyntax-only", "-",
    ]
    result = run(command, input_text=unit)
    # Depending on clang build, record-layout diagnostics are emitted to stdout
    # or stderr. Parse both without depending on that implementation detail.
    text = result.stdout + "\n" + result.stderr
    layouts: dict[tuple[str, str], dict[str, Any]] = {}
    for match in SIMPLE_LAYOUT.finditer(text):
        kind, raw_name, size_bits, align_bits, offsets = match.groups()
        name = raw_name.strip()
        values = [int(value.strip()) for value in offsets.split(",") if value.strip()]
        layout = {
            "size": int(size_bits) // 8,
            "align": int(align_bits) // 8,
            "fieldOffsets": values,
        }
        if "::" not in name and not name.startswith("(unnamed"):
            layouts[(kind, name)] = layout
        anonymous = re.match(r"^([^:]+)::\((?:unnamed|anonymous) at .*:(\d+):\d+\)$", name, re.DOTALL)
        if anonymous:
            layouts[(kind, f"@{anonymous.group(1)}:{anonymous.group(2)}")] = layout
    return layouts


def apply_record_layout(record: dict[str, Any], layouts: dict[tuple[str, str], dict[str, Any]]) -> None:
    name = record.get("name")
    kind = record.get("kind", "struct")
    layout = layouts.get((kind, name)) if name else None
    if record.get("anonymous"):
        layout = layouts.get((kind, f"@{record.get('_layoutOwner')}:{record.get('_layoutLine')}")) or layout
    if layout:
        record["size"] = layout["size"]
        record["align"] = layout["align"]
        offsets = layout["fieldOffsets"]
        for index, field in enumerate(record.get("fields") or ()):
            if index < len(offsets):
                field["offsetBits"] = offsets[index]
    for field in record.get("fields") or ():
        nested = field.get("anonymousRecord")
        if nested:
            apply_record_layout(nested, layouts)
    record.pop("_layoutOwner", None)
    record.pop("_layoutLine", None)


def extract_profile(profile: Profile, roots: dict[str, Path], clang: Path) -> dict[str, Any]:
    paths = [header_path(spec, roots) for spec in profile.headers]
    owned = {os.path.normcase(str(path.resolve())): path for path in paths}
    unit = translation_unit(paths)
    include_dirs = list(OrderedDict.fromkeys((roots["windows-sdk-um"], roots["windows-sdk-shared"], roots["windows-sdk-ucrt"], roots["directx-headers"])))
    candidates = collect_candidates(paths, profile)
    public_names = public_declaration_names(paths, profile)

    nodes: list[dict[str, Any]] = []
    filters = list(profile.symbol_prefixes)
    # A few DirectX-owned interfaces use the shared ID3D prefix (without the
    # version number), e.g. ID3DDeviceContextState and shader-cache contracts.
    if "D3D" in profile.symbol_prefixes or any(prefix.startswith("D3D") for prefix in profile.symbol_prefixes):
        filters.append("ID3D")
    # Exact exported names not covered by a broad prefix are cheap filtered AST passes.
    filters.extend(name for name in profile.exact_symbols if not name.startswith(profile.symbol_prefixes))
    # Callback typedefs can be referenced by selected vtable methods while
    # having neutral names (for example PFN_DESTRUCTION_CALLBACK in
    # d3dcommon.h). Query their owned AST declarations explicitly instead of
    # guessing that an unresolved PFN_* name is pointer-sized.
    filters.extend(name for name in public_names if name.startswith("PFN_"))
    for name_filter in OrderedDict.fromkeys(filters):
        nodes.extend(ast_for_filter(clang, include_dirs, unit, name_filter))

    # We need IID initializer values even where the public header uses EXTERN_C.
    iid_nodes: list[dict[str, Any]] = []
    for prefix in ("IID_ID",):
        iid_nodes.extend(ast_for_filter(clang, include_dirs, unit, prefix))
    nodes.extend(iid_nodes)
    layouts = clang_record_layouts(clang, include_dirs, unit)

    interfaces: list[dict[str, Any]] = []
    records: list[dict[str, Any]] = []
    enums: list[dict[str, Any]] = []
    typedefs: list[dict[str, Any]] = []
    functions: list[dict[str, Any]] = []
    variables: dict[str, str] = {}

    for node in nodes:
        kind = node.get("kind")
        owned_loc = declaration_header(node, owned)
        name = node.get("name") or ""
        if kind == "VarDecl" and name.startswith("IID_"):
            value = guid_from_var(node)
            if value:
                variables[name] = value
        if not owned_loc:
            continue
        header, line = owned_loc
        if kind == "RecordDecl" and node.get("completeDefinition"):
            records.append(record_from_node(node, header, line))
        elif kind == "EnumDecl" and name:
            entries = []
            implicit_value = -1
            for child in node.get("inner") or ():
                if child.get("kind") != "EnumConstantDecl":
                    continue
                raw = first_constant_value(child)
                if raw is None:
                    implicit_value += 1
                    raw = str(implicit_value)
                else:
                    try:
                        implicit_value = int(raw, 0)
                    except ValueError:
                        pass
                entries.append({"name": child.get("name"), "value": raw})
            enums.append({
                "name": name, "header": header, "sourceLine": line,
                "underlyingType": (node.get("fixedUnderlyingType") or {}).get("qualType", "int"),
                "entries": entries,
            })
        elif kind == "TypedefDecl" and name and name in public_names:
            typedefs.append({
                "name": name, "header": header, "sourceLine": line,
                "type": (node.get("type") or {}).get("qualType"),
                "canonicalType": (node.get("type") or {}).get("desugaredQualType"),
            })
        elif kind == "FunctionDecl" and name and name in public_names and (name.startswith(profile.symbol_prefixes) or name in profile.exact_symbols):
            signature = (node.get("type") or {}).get("qualType")
            params = []
            for index, child in enumerate(node.get("inner") or ()):
                if child.get("kind") == "ParmVarDecl":
                    params.append({
                        "name": child.get("name") or f"arg{index}",
                        "type": (child.get("type") or {}).get("qualType"),
                    })
            return_type = signature.split("(", 1)[0].strip() if signature else None
            functions.append({
                "name": name, "header": header, "sourceLine": line, "returnType": return_type,
                "params": params, "type": signature, "dll": dll_for_function(name, profile),
            })

    # Vtable structs are the canonical C ABI and already contain inherited slots.
    for record in records:
        name = record.get("name") or ""
        if not name.endswith("Vtbl"):
            continue
        interface_name = name[:-4]
        if not interface_name.startswith(("IDirect3D", "ID3D", "IDXGI")):
            continue
        methods = []
        for slot, field in enumerate(record["fields"]):
            signature = field.get("type") or ""
            return_type, param_types = parse_function_pointer(signature)
            methods.append({
                "slot": slot, "name": field.get("name"), "returnType": return_type,
                "params": [{"name": f"arg{i}", "type": value} for i, value in enumerate(param_types)],
                "type": signature,
            })
        interfaces.append({
            "name": interface_name, "header": record["header"], "sourceLine": record["sourceLine"],
            "iid": variables.get("IID_" + interface_name), "parent": None, "methods": methods,
        })

    parents, source_iids = scan_interface_metadata(paths)
    for interface in interfaces:
        interface["parent"] = parents.get(interface["name"])
        interface["iid"] = interface["iid"] or source_iids.get(interface["name"])

    constants = extract_macros(profile, paths, clang, include_dirs, unit, owned)
    records = [item for item in records if item.get("name") and not str(item["name"]).endswith("Vtbl")]
    promote_named_record_definitions(records)
    for record in records:
        apply_record_layout(record, layouts)

    interfaces = dedupe(interfaces, ("name",))
    records = dedupe(records, ("name", "header", "sourceLine"))
    enums = dedupe(enums, ("name", "header"))
    typedefs = dedupe(typedefs, ("name", "header"))
    functions = dedupe(functions, ("name", "type"))
    constants = dedupe(constants, ("name",))

    header_manifest = []
    for spec, path in zip(profile.headers, paths):
        origin = (f"Microsoft DirectX-Headers {DIRECTX_HEADERS_VERSION} commit {DIRECTX_HEADERS_COMMIT}"
                  if spec.role == "directx-headers" else f"Microsoft Windows SDK {SDK_VERSION}")
        header_manifest.append({
            "name": path.name, "pathRole": spec.role, "sha256": sha256_file(path), "origin": origin,
        })

    missing_iids = sorted(item["name"] for item in interfaces if not item.get("iid") and item["name"] != "IUnknown")
    header_interfaces = sorted(scan_vtable_interface_names(paths))
    schema_interfaces = sorted(item["name"] for item in interfaces)
    missing_vtables = sorted(set(header_interfaces) - set(schema_interfaces))
    unexpected_vtables = sorted(set(schema_interfaces) - set(header_interfaces))
    record_fields, bitfields = count_record_fields(records)
    layouts_resolved = sum(1 for record in records if record.get("size") is not None and record.get("align") is not None)
    field_offsets_resolved = count_resolved_offsets(records)
    declarations = {
        "interfaces": interfaces, "enums": enums, "records": records, "typedefs": typedefs,
        "functions": functions, "constants": constants,
    }
    stats = {key: len(value) for key, value in declarations.items()}
    stats.update({
        "interfaceMethods": sum(len(item["methods"]) for item in interfaces),
        "enumEntries": sum(len(item["entries"]) for item in enums),
        "recordFields": record_fields,
        "bitfields": bitfields,
        "recordLayoutsResolved": layouts_resolved,
        "fieldOffsetsResolved": field_offsets_resolved,
        "recordLayoutsUnresolved": len(records) - layouts_resolved,
        "fieldOffsetsUnresolved": record_fields - field_offsets_resolved,
        "missingInterfaceIids": len(missing_iids),
        "headerVtables": len(header_interfaces),
        "missingVtables": len(missing_vtables),
        "unexpectedVtables": len(unexpected_vtables),
    })
    schema = {
        "schemaVersion": SCHEMA_VERSION,
        "api": profile.api,
        "namespace": "net.echonolix.caelum." + ("dxgi.api" if profile.artifact == "dxgi" else "directx.api"),
        "target": {"triple": TARGET_TRIPLE, "pointerBits": 64, "abi": "Windows x64 / LLP64"},
        "sourceSet": {
            "headers": header_manifest,
            "defines": ["CINTERFACE=1", "COBJMACROS=1", "INITGUID=1"],
            "includeRoles": ["windows-sdk-um", "windows-sdk-shared", "windows-sdk-ucrt", "directx-headers"],
            "clang": {"version": clang_version(clang), "target": TARGET_TRIPLE},
            "directXHeaders": ({"version": DIRECTX_HEADERS_VERSION, "commit": DIRECTX_HEADERS_COMMIT}
                               if any(spec.role == "directx-headers" for spec in profile.headers) else None),
        },
        "reviewedExclusions": reviewed_exclusions(profile, missing_iids, bitfields),
        "coverageAudit": {
            "headerVtableInterfaces": header_interfaces,
            "schemaInterfaces": schema_interfaces,
            "missingVtables": missing_vtables,
            "unexpectedVtables": unexpected_vtables,
            "unionCoverage": union_coverage(profile),
        },
        "declarations": declarations,
        "statistics": stats,
    }
    return canonicalize_paths(schema, paths)


def canonicalize_paths(value: Any, paths: Sequence[Path]) -> Any:
    """Remove machine-specific header roots from every persisted string."""
    replacements: list[tuple[str, str]] = []
    for path in paths:
        absolute = str(path.resolve())
        replacements.extend(((absolute, f"<{path.name}>"), (absolute.replace("\\", "/"), f"<{path.name}>")))
    # Also normalize any transitive SDK/DirectX header location carried in an
    # anonymous C type spelling to only its stable basename and line/column.
    anonymous_location = re.compile(r"(?:[A-Za-z]:)?[^()]*[\\/]([^\\/:()]+\.h):(\d+):(\d+)")

    def visit(item: Any) -> Any:
        if isinstance(item, str):
            result = item
            for source, target in replacements:
                result = result.replace(source, target)
            result = anonymous_location.sub(lambda match: f"<{match.group(1)}>:{match.group(2)}:{match.group(3)}", result)
            return result
        if isinstance(item, list):
            return [visit(child) for child in item]
        if isinstance(item, dict):
            return {key: visit(child) for key, child in item.items()}
        return item

    return visit(value)


def scan_interface_metadata(paths: Sequence[Path]) -> tuple[dict[str, str], dict[str, str]]:
    parents: dict[str, str] = {}
    iids: dict[str, str] = {}
    declare = re.compile(r"DECLARE_INTERFACE_\(\s*(\w+)\s*,\s*(\w+)\s*\)")
    midl = re.compile(r'MIDL_INTERFACE\("([0-9A-Fa-f-]{36})"\)\s*(\w+)\s*:\s*public\s+(\w+)', re.DOTALL)
    guid = re.compile(
        r"DEFINE_GUID\s*\(\s*IID_(\w+)\s*,\s*(0x[0-9A-Fa-f]+|\d+)\s*,\s*(0x[0-9A-Fa-f]+|\d+)\s*,\s*"
        r"(0x[0-9A-Fa-f]+|\d+)\s*,\s*(0x[0-9A-Fa-f]+|\d+)\s*,\s*(0x[0-9A-Fa-f]+|\d+)\s*,\s*"
        r"(0x[0-9A-Fa-f]+|\d+)\s*,\s*(0x[0-9A-Fa-f]+|\d+)\s*,\s*(0x[0-9A-Fa-f]+|\d+)\s*,\s*"
        r"(0x[0-9A-Fa-f]+|\d+)\s*,\s*(0x[0-9A-Fa-f]+|\d+)\s*,\s*(0x[0-9A-Fa-f]+|\d+)\s*\)", re.DOTALL,
    )
    for path in paths:
        text = path.read_text(encoding="utf-8", errors="replace")
        for match in declare.finditer(text):
            parents[match.group(1)] = match.group(2)
        for match in midl.finditer(text):
            iids[match.group(2)] = match.group(1).lower()
            parents[match.group(2)] = match.group(3)
        for match in guid.finditer(text):
            values = [int(value, 0) for value in match.groups()[1:]]
            d1, d2, d3, *d4 = values
            iids[match.group(1)] = f"{d1:08x}-{d2:04x}-{d3:04x}-{d4[0]:02x}{d4[1]:02x}-" + "".join(f"{x:02x}" for x in d4[2:])
    return parents, iids


def scan_vtable_interface_names(paths: Sequence[Path]) -> set[str]:
    names: set[str] = set()
    pattern = re.compile(r"\b(?:struct\s+)?((?:IDirect3D|ID3D|IDXGI)\w*)Vtbl\b")
    declared = re.compile(r"DECLARE_INTERFACE_\(\s*((?:IDirect3D|ID3D|IDXGI)\w+)\s*,")
    declared_root = re.compile(r"DECLARE_INTERFACE\(\s*((?:IDirect3D|ID3D|IDXGI)\w+)\s*\)")
    for path in paths:
        text = path.read_text(encoding="utf-8", errors="replace")
        names.update(pattern.findall(text))
        names.update(declared.findall(text))
        names.update(declared_root.findall(text))
    return names


def dll_for_function(name: str, profile: Profile) -> str:
    for symbol, dll in profile.symbol_dlls:
        if name == symbol:
            return dll
    if name == "D3DDisassemble11Trace":
        return "d3dcompiler_47.dll"
    return profile.dlls[0]


def extract_macros(profile: Profile, paths: Sequence[Path], clang: Path, include_dirs: Sequence[Path], unit: str,
                   owned: dict[str, Path]) -> list[dict[str, Any]]:
    command = clang_base_command(clang, include_dirs) + ["-dM", "-E", "-"]
    output = run(command, input_text=unit).stdout
    source_locations: dict[str, tuple[str, int]] = {}
    for path in paths:
        for line_number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            match = re.match(r"\s*#\s*define\s+([A-Za-z_]\w*)\b", line)
            if match:
                source_locations.setdefault(match.group(1), (path.name, line_number))
    constants = []
    for line in output.splitlines():
        match = re.match(r"#define\s+([A-Za-z_]\w*)\s*(.*)$", line)
        if not match:
            continue
        name, value = match.groups()
        if name not in source_locations or "(" in name or not name.startswith(profile.symbol_prefixes):
            continue
        header, line_number = source_locations[name]
        constants.append({
            "name": name, "header": header, "sourceLine": line_number,
            "type": "macro", "value": value.strip(), "valueText": value.strip(),
        })
    return constants


def reviewed_exclusions(profile: Profile, missing_iids: Sequence[str], bitfields: int) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = [
        {
            "pattern": "transitive Win32/OLE declarations outside the listed target headers",
            "reason": "The catalog is the DirectX/DXGI public header surface, not a replacement for the Windows SDK.",
            "impact": "Referenced external ABI types remain present in signature strings and require the platform layer.",
        },
        {
            "pattern": "function-like preprocessor macros",
            "reason": "They are source helpers rather than addressable ABI declarations; object-like public macros are retained.",
            "impact": "No exported function, COM slot, enum entry, record, typedef, or object-like constant is removed.",
        },
        {
            "pattern": "C++ convenience helpers and inline methods",
            "reason": "CINTERFACE is the stable binary ABI used by the FFM binding.",
            "impact": "The complete flattened C vtable remains represented.",
        },
    ]
    if profile.api == "d3d12":
        result.append({
            "pattern": "D3D12Events.h EXTERN_C __declspec(selectany) const data definitions",
            "reason": (
                "The 1 provider GUID and 146 EVENT_DESCRIPTOR objects are header-local weak/selectany COMDAT "
                "definitions with initializers, not addressable exports of d3d12.dll or D3D12Core.dll."
            ),
            "impact": (
                "They are not listed in declarations.constants. Consumers needing ETW descriptors must define or "
                "materialize them from the values in the pinned D3D12Events.h; its object-like public macros remain cataloged."
            ),
            "symbols": [
                "Direct3D12EtwProviderGuid", "EventD3D12RenameObject", "EventD3D12CreateDevice",
                "D3D12SupplementalContiguous", "EventD3D12CreateStateObject",
            ],
            "gate": "PE export audit found zero matching exports in x86/x64 d3d12.dll and D3D12Core.dll",
        })
    if bitfields:
        result.append({
            "pattern": "jextract typed accessors for bitfields",
            "reason": "OpenJDK jextract reports bitfields as unsupported.",
            "impact": f"Schema preserves {bitfields} bitfield declarations and widths; generated raw Java must not claim typed bitfield completeness.",
            "gate": "manual carrier-word accessor or verified generated wrapper required before exposing each affected typed record",
        })
    if missing_iids:
        result.append({
            "pattern": "interfaces without an IID initializer in the selected public C headers",
            "reason": "Some helper/forward declarations do not publish an IID in this source set.",
            "impact": "Interface vtables are retained; QueryInterface-by-IID is unavailable until an authoritative IID source is added.",
            "symbols": list(missing_iids),
        })
    if profile.api == "d3d12":
        result.append({
            "pattern": "D3DX12 C++ helper headers",
            "reason": "D3DX12 is a convenience layer, not the D3D12 core COM/C ABI requested by this catalog.",
            "impact": "D3D12 core, debug, video, compatibility, compiler and shader-cache interfaces remain selected.",
        })
    return result


def union_coverage(profile: Profile) -> dict[str, Any]:
    if profile.api != "d3d12":
        return {
            "components": [profile.api],
            "note": "This profile is self-audited against every C vtable defined by its selected headers.",
        }
    return {
        "components": ["d3d12", "d3dcommon-compiler", "dxgi"],
        "note": (
            "The D3D12 raw surface is intentionally split by artifact ownership: D3D12 core/debug/video/compiler/"
            "shader-cache declarations are here; reusable Windows-SDK d3dcommon+d3dcompiler declarations are in "
            "the d3dcommon-compiler schema; DXGI factory/adapter/swap-chain declarations are in caelum-dxgi. "
            "Counts must be compared as this union, not against a monolithic transitive header AST."
        ),
        "excludedFromUnion": ["D3DX12 C++ helper headers", "DirectML", "DXCore"],
    }


def clang_version(clang: Path) -> str:
    first_line = run((str(clang), "--version")).stdout.splitlines()[0]
    return first_line.removeprefix("clang version ").strip()


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n"
    path.write_text(text, encoding="utf-8", newline="\n")


def write_artifact_index(root: Path, profiles: Sequence[Profile]) -> None:
    entries: list[tuple[Profile, Path, dict[str, Any]]] = []
    for profile in profiles:
        path = root / profile.output_name
        if path.is_file():
            entries.append((profile, path, json.loads(path.read_text(encoding="utf-8"))))
    if not entries:
        return
    index = {
        "schemaVersion": SCHEMA_VERSION,
        "schemas": OrderedDict((profile.api, path.name) for profile, path, _ in entries),
        "hashes": {profile.api: sha256_file(path) for profile, path, _ in entries},
        "statistics": {profile.api: schema["statistics"] for profile, _, schema in entries},
    }
    write_json(root / "index.json", index)


def generate_all(args: argparse.Namespace) -> None:
    sdk_include = args.windows_sdk_include.resolve()
    roots = {
        "windows-sdk-um": sdk_include / "um",
        "windows-sdk-shared": sdk_include / "shared",
        "windows-sdk-ucrt": sdk_include / "ucrt",
        "directx-headers": args.directx_headers.resolve() / "include" / "directx",
    }
    selected = list(PROFILES) if args.profile == "all" else [args.profile]
    output_roots = {
        "directx": args.repo_root / "directx" / "src" / "main" / "resources" / "net" / "echonolix" / "caelum" / "directx" / "api",
        "dxgi": args.repo_root / "dxgi" / "src" / "main" / "resources" / "net" / "echonolix" / "caelum" / "dxgi" / "api",
    }
    for key in selected:
        profile = PROFILES[key]
        print(f"extracting {key} ...", flush=True)
        schema = extract_profile(profile, roots, args.clang.resolve())
        destination = output_roots[profile.artifact] / profile.output_name
        write_json(destination, schema)
        print(f"wrote {destination} {schema['statistics']}", flush=True)
    for artifact, root in output_roots.items():
        write_artifact_index(root, [profile for profile in PROFILES.values() if profile.artifact == artifact])


INCLUDE_LINE = re.compile(r"^(--include-(?:function|constant|struct|typedef|union|var))\s+(\S+)\s+#\s+header:\s+(.+?)\s*$")
DEPENDENCY_ERROR = re.compile(r"depends on declaration ([A-Za-z_]\w*) which has been excluded|depends on ([A-Za-z_]\w*) which has been excluded")


def parse_dump_includes(path: Path) -> dict[str, tuple[str, str]]:
    symbols: dict[str, tuple[str, str]] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = INCLUDE_LINE.match(line)
        if match:
            option, name, header = match.groups()
            symbols.setdefault(name, (option, header.strip()))
    return symbols


def generate_jextract(args: argparse.Namespace) -> None:
    profile = PROFILES[args.profile]
    sdk_include = args.windows_sdk_include.resolve()
    roots = {
        "windows-sdk-um": sdk_include / "um",
        "windows-sdk-shared": sdk_include / "shared",
        "windows-sdk-ucrt": sdk_include / "ucrt",
        "directx-headers": args.directx_headers.resolve() / "include" / "directx",
    }
    paths = [header_path(spec, roots) for spec in profile.headers]
    owned = {os.path.normcase(str(path)): path for path in paths}
    jextract = args.jextract_root.resolve() / "bin" / ("jextract.bat" if os.name == "nt" else "jextract")
    if not jextract.is_file():
        raise FileNotFoundError(jextract)
    include_dirs = [roots["windows-sdk-um"], roots["windows-sdk-shared"], roots["windows-sdk-ucrt"], roots["directx-headers"]]
    with tempfile.TemporaryDirectory(prefix="caelum-jextract-") as temp_name:
        temp = Path(temp_name)
        wrapper = temp / f"{profile.api}.h"
        wrapper.write_text(translation_unit(paths), encoding="utf-8", newline="\n")
        dumped = temp / "all-includes.txt"
        base = [str(jextract), "--dump-includes", str(dumped)]
        for directory in include_dirs:
            base.extend(("-I", str(directory)))
        base.append(str(wrapper))
        dump_result = run(base, check=False)
        if not dumped.is_file():
            raise RuntimeError(f"jextract did not create dump file:\n{dump_result.stderr[-12000:]}")
        all_symbols = parse_dump_includes(dumped)
        selected: OrderedDict[str, tuple[str, str]] = OrderedDict()
        for name, entry in sorted(all_symbols.items()):
            header = Path(entry[1])
            if os.path.normcase(str(header.resolve())) in owned:
                selected[name] = entry
        if not selected:
            raise RuntimeError("no target-header symbols found in jextract --dump-includes output")

        attempt = 0
        while True:
            attempt += 1
            tool_args = ["--output", str(args.output_root.resolve()), "--target-package", args.target_package,
                         "--header-class-name", args.header_class_name]
            for directory in include_dirs:
                tool_args.extend(("-I", str(directory)))
            tool_args.extend(("-D", "CINTERFACE=1", "-D", "COBJMACROS=1"))
            for name, (option, _) in selected.items():
                tool_args.extend((option, name))
            tool_args.append(str(wrapper))
            # Windows CreateProcess limits command lines to ~32K. Invoke the
            # bundled Java runtime directly and let its @argfile parser carry
            # the (often thousands of symbols) jextract tool arguments.
            java_args = temp / f"jextract-{attempt}.args"
            java_args.write_text("\n".join(java_argfile_quote(value) for value in tool_args) + "\n", encoding="utf-8", newline="\n")
            java = args.jextract_root.resolve() / "runtime" / "bin" / ("java.exe" if os.name == "nt" else "java")
            command = [str(java), "-m", "org.openjdk.jextract/org.openjdk.jextract.JextractTool", "@" + str(java_args)]
            result = run(command, check=False)
            if result.returncode == 0:
                closure = args.output_root.resolve() / f"{profile.api}-jextract-closure.json"
                write_json(closure, {
                    "profile": profile.api, "attempts": attempt, "symbols": [
                        {"name": name, "option": option, "header": Path(header).name,
                         "dependency": os.path.normcase(str(Path(header).resolve())) not in owned}
                        for name, (option, header) in selected.items()
                    ],
                })
                print(f"jextract completed with {len(selected)} symbols; closure: {closure}")
                return
            dependencies = {next(value for value in match.groups() if value) for match in DEPENDENCY_ERROR.finditer(result.stderr + result.stdout)}
            added = []
            for name in sorted(dependencies):
                if name in all_symbols and name not in selected:
                    selected[name] = all_symbols[name]
                    added.append(name)
            if not added:
                raise RuntimeError(f"jextract failed without resolvable excluded dependencies:\n{result.stderr[-12000:]}")
            print(f"jextract dependency closure attempt {attempt}: added {', '.join(added)}", flush=True)


def parser() -> argparse.ArgumentParser:
    root = Path(__file__).resolve().parents[2]
    default_sdk = Path(os.environ.get("WindowsSdkDir", r"C:\Program Files (x86)\Windows Kits\10")) / "Include" / SDK_VERSION
    command = argparse.ArgumentParser(description=__doc__)
    sub = command.add_subparsers(dest="command", required=True)
    generate = sub.add_parser("schema", help="generate committed versioned JSON schemas")
    generate.add_argument("--repo-root", type=Path, default=root)
    generate.add_argument("--windows-sdk-include", type=Path, default=default_sdk)
    generate.add_argument("--directx-headers", type=Path, required=True,
                          help="DirectX-Headers 1.619.5 source root")
    generate.add_argument("--clang", type=Path, default=Path(shutil.which("clang") or "clang"))
    generate.add_argument("--profile", choices=("all", *PROFILES), default="all")
    generate.set_defaults(func=generate_all)

    jextract = sub.add_parser("jextract", help="generate filtered raw Java with dependency closure")
    jextract.add_argument("--windows-sdk-include", type=Path, default=default_sdk)
    jextract.add_argument("--directx-headers", type=Path, required=True)
    jextract.add_argument("--jextract-root", type=Path, required=True)
    jextract.add_argument("--output-root", type=Path, required=True)
    jextract.add_argument("--profile", choices=tuple(PROFILES), required=True)
    jextract.add_argument("--target-package", required=True)
    jextract.add_argument("--header-class-name", default="DirectXRaw")
    jextract.set_defaults(func=generate_jextract)
    return command


def main() -> int:
    args = parser().parse_args()
    args.func(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
