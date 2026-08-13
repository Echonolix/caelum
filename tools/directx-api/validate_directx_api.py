#!/usr/bin/env python3
"""Fail-fast validation for committed DirectX/DXGI API schema resources."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any, Iterable


# Persisted source locations must use stable ``<header.h>:line:column`` tokens,
# never a workstation filesystem path.  Cover Windows drive/UNC spellings and
# the conventional POSIX user/temp roots used by CI and developer machines.
FORBIDDEN_PATH = re.compile(
    r"(?i)(?:(?<![A-Za-z0-9])[A-Z]:[\\/]|(?<![\\A-Za-z0-9_.-])\\\\[^\\/\s]+[\\/][^\\/\s]+|"
    r"(?<![:A-Za-z0-9])/(?:home|Users|private|tmp|var/tmp|workspace|builds|mnt/[A-Z])(?:/|$)|"
    r"Program Files|AppData|\\Temp\\)"
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def strings(value: Any) -> Iterable[str]:
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from strings(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from strings(item)


def nested_records(record: dict[str, Any]) -> Iterable[dict[str, Any]]:
    yield record
    for field in record.get("fields") or ():
        nested = field.get("anonymousRecord")
        if nested:
            assert isinstance(nested, dict), "anonymousRecord must be an object"
            yield from nested_records(nested)


def validate_record_tree(path: Path, record: dict[str, Any], owner: str) -> tuple[int, int, int]:
    """Validate every nested record, not only the known regression fixtures."""
    fields = record.get("fields")
    assert isinstance(fields, list), f"{path}: {owner} fields must be an array"
    size = record.get("size")
    align = record.get("align")
    assert isinstance(size, int) and size >= 0, f"{path}: unresolved/invalid record size: {owner}"
    assert isinstance(align, int) and align > 0, f"{path}: unresolved/invalid record align: {owner}"
    assert size % align == 0, f"{path}: record size is not alignment-multiple: {owner}"
    nested_count = 1
    field_count = 0
    bitfield_count = 0
    nested_names: set[str] = set()
    for index, field in enumerate(fields):
        assert isinstance(field, dict), f"{path}: {owner}.fields[{index}] must be an object"
        offset = field.get("offsetBits")
        assert isinstance(offset, int) and 0 <= offset <= size * 8, f"{path}: invalid field offset: {owner}.fields[{index}]"
        bit_width = field.get("bitWidth")
        if bit_width is not None:
            assert isinstance(bit_width, int) and bit_width > 0, f"{path}: invalid bitfield width: {owner}.fields[{index}]"
            assert offset + bit_width <= size * 8, f"{path}: bitfield exceeds record size: {owner}.fields[{index}]"
            bitfield_count += 1
        nested = field.get("anonymousRecord")
        if nested is not None:
            assert isinstance(nested, dict), f"{path}: {owner}.fields[{index}].anonymousRecord must be an object"
            nested_name = nested.get("name")
            assert isinstance(nested_name, str) and nested_name, f"{path}: anonymous record lacks a stable synthetic name: {owner}.fields[{index}]"
            assert nested_name not in nested_names, f"{path}: duplicate nested record name {nested_name} in {owner}"
            nested_names.add(nested_name)
            nested_size = nested.get("size")
            assert isinstance(nested_size, int) and offset + nested_size * 8 <= size * 8, f"{path}: nested record exceeds owner: {nested_name}"
            records, nested_fields, nested_bitfields = validate_record_tree(path, nested, nested_name)
            nested_count += records
            field_count += nested_fields
            bitfield_count += nested_bitfields
        field_count += 1
    return nested_count, field_count, bitfield_count


def validate_schema(path: Path) -> dict[str, Any]:
    schema = json.loads(path.read_text(encoding="utf-8"))
    leaked = next((value for value in strings(schema) if FORBIDDEN_PATH.search(value)), None)
    assert leaked is None, f"{path}: machine-specific absolute path leak: {leaked}"
    assert schema["schemaVersion"] == 1, f"{path}: unsupported schema version"
    declarations = schema["declarations"]
    stats = schema["statistics"]
    for kind in ("interfaces", "enums", "records", "typedefs", "functions", "constants"):
        assert stats[kind] == len(declarations[kind]), f"{path}: {kind} statistics mismatch"
    interfaces = declarations["interfaces"]
    assert len({value["name"] for value in interfaces}) == len(interfaces), f"{path}: duplicate interfaces"
    assert stats["headerVtables"] == len(interfaces), f"{path}: header/schema interface count mismatch"
    assert stats["missingVtables"] == 0 and stats["unexpectedVtables"] == 0, f"{path}: vtable coverage gap"
    for interface in interfaces:
        methods = interface["methods"]
        assert methods, f"{path}: empty vtable {interface['name']}"
        assert [method["slot"] for method in methods] == list(range(len(methods))), f"{path}: non-contiguous slots {interface['name']}"
        for method in methods:
            assert method["name"] and method["type"], f"{path}: incomplete method descriptor {interface['name']}"
            assert method["params"] and method["params"][0]["type"].endswith("*"), f"{path}: method omits COM This {interface['name']}.{method['name']}"
    for kind in ("enums", "records", "typedefs", "functions", "constants"):
        names = [declaration["name"] for declaration in declarations[kind]]
        assert len(names) == len(set(names)), f"{path}: duplicate {kind} declaration name"
    record_totals = [validate_record_tree(path, record, record["name"]) for record in declarations["records"]]
    all_fields = sum(total[1] for total in record_totals)
    all_bitfields = sum(total[2] for total in record_totals)
    assert stats["recordFields"] == all_fields, f"{path}: recursive record-field statistics mismatch"
    assert stats["bitfields"] == all_bitfields, f"{path}: recursive bitfield statistics mismatch"
    assert stats["recordLayoutsResolved"] == len(declarations["records"]), f"{path}: resolved layout stats mismatch"
    assert stats["fieldOffsetsResolved"] == all_fields, f"{path}: resolved offset stats mismatch"
    assert stats["recordLayoutsUnresolved"] == 0 and stats["fieldOffsetsUnresolved"] == 0, f"{path}: unresolved layout stats"
    assert schema["coverageAudit"]["missingVtables"] == [] and schema["coverageAudit"]["unexpectedVtables"] == [], f"{path}: coverage audit gap"
    assert all(header["sha256"] and len(header["sha256"]) == 64 for header in schema["sourceSet"]["headers"]), f"{path}: missing header hash"
    return schema


def validate_index(root: Path) -> None:
    index_path = root / "index.json"
    index = json.loads(index_path.read_text(encoding="utf-8"))
    assert index["schemaVersion"] == 1
    for api, name in index["schemas"].items():
        path = root / name
        assert path.is_file(), f"{index_path}: missing {name}"
        assert index["hashes"][api] == sha256(path), f"{index_path}: stale hash for {api}"
        schema = validate_schema(path)
        assert schema["api"] == api, f"{path}: api/index mismatch"
        assert index["statistics"][api] == schema["statistics"], f"{index_path}: stale statistics for {api}"


def validate_symbol_libraries(repo_root: Path) -> None:
    directx_root = repo_root / "directx/src/main/resources/net/echonolix/caelum/directx/api"
    dxgi_root = repo_root / "dxgi/src/main/resources/net/echonolix/caelum/dxgi/api"

    def functions(path: Path) -> dict[str, str | None]:
        declarations = json.loads(path.read_text(encoding="utf-8"))["declarations"]["functions"]
        return {declaration["name"]: declaration.get("dll") for declaration in declarations}

    d3d10 = functions(directx_root / "d3d10-sdk-10.0.22621.0.json")
    d3d12 = functions(directx_root / "d3d12-headers-1.619.5.json")
    dxgi = functions(dxgi_root / "dxgi-sdk-10.0.22621.0.json")
    expected = {
        "D3D10CreateDevice1": (d3d10, "d3d10_1.dll"),
        "D3D10CreateDeviceAndSwapChain1": (d3d10, "d3d10_1.dll"),
        "D3D12CompilerCreateFactory": (d3d12, "D3D12StateObjectCompiler.dll"),
        "D3D12CompilerSerializeVersionedRootSignature": (d3d12, "D3D12StateObjectCompiler.dll"),
        "DXGIGetDebugInterface": (dxgi, "dxgidebug.dll"),
        "DXGIGetDebugInterface1": (dxgi, "dxgi.dll"),
    }
    for symbol, (catalog, dll) in expected.items():
        assert catalog.get(symbol) == dll, f"{symbol}: expected {dll}, found {catalog.get(symbol)}"


def validate_required_signature_types(repo_root: Path) -> None:
    """Guard owned named by-value types that previously fell outside profile prefixes."""
    path = repo_root / "directx/src/main/resources/net/echonolix/caelum/directx/api/d3d12-headers-1.619.5.json"
    schema = json.loads(path.read_text(encoding="utf-8"))
    declarations = schema["declarations"]
    enum_names = {declaration["name"] for declaration in declarations["enums"]}
    typedef_names = {declaration["name"] for declaration in declarations["typedefs"]}
    required_named_types = {"D3D_SHADER_CACHE_TARGET_FLAGS", "D3D_SHADER_CACHE_APP_REGISTRATION_SCOPE"}
    missing = required_named_types - (enum_names | typedef_names)
    assert not missing, f"{path}: unresolved by-value shader-cache signature types: {sorted(missing)}"

    callback = "PFN_DESTRUCTION_CALLBACK"
    assert callback in typedef_names, f"{path}: missing owned callback typedef {callback}"
    callback_declarations = [declaration for declaration in declarations["typedefs"] if declaration["name"] == callback]
    assert len(callback_declarations) == 1, f"{path}: expected one {callback} typedef"
    callback_type = callback_declarations[0].get("type") or ""
    assert "void (*)(void *)" in callback_type and "stdcall" in callback_type, f"{path}: malformed {callback}: {callback_type}"
    callback_uses = [
        (interface["name"], method["name"])
        for interface in declarations["interfaces"]
        for method in interface["methods"]
        if callback in (method.get("type") or "")
        or any(callback in (parameter.get("type") or "") for parameter in method.get("params", ()))
    ]
    expected_callback_use = ("ID3DDestructionNotifier", "RegisterDestructionCallback")
    assert expected_callback_use in callback_uses, f"{path}: {callback} is absent from {expected_callback_use}"


def validate_nested_record_closure(repo_root: Path) -> None:
    directx = repo_root / "directx/src/main/resources/net/echonolix/caelum/directx/api"
    d3d12 = json.loads((directx / "d3d12-headers-1.619.5.json").read_text(encoding="utf-8"))
    indirect = next(record for record in d3d12["declarations"]["records"] if record["name"] == "D3D12_INDIRECT_ARGUMENT_DESC")
    union = next(field["anonymousRecord"] for field in indirect["fields"] if field.get("anonymousRecord"))
    expected = {"VertexBuffer", "Constant", "ConstantBufferView", "ShaderResourceView", "UnorderedAccessView", "IncrementingConstant"}
    actual = {field["name"] for field in union["fields"] if isinstance(field.get("anonymousRecord"), dict)}
    assert actual == expected, f"D3D12_INDIRECT_ARGUMENT_DESC: nested record closure mismatch: {sorted(actual)}"

    d3d11 = json.loads((directx / "d3d11-sdk-10.0.22621.0.json").read_text(encoding="utf-8"))
    midl = [record for record in d3d11["declarations"]["records"] if record["name"] == "__MIDL___MIDL_itf_d3d11_0000_0034_0001"]
    assert len(midl) == 1 and [field["bitWidth"] for field in midl[0]["fields"]] == [1, 1, 30], "missing named nested MIDL bitfield record"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    roots = (
        args.repo_root / "directx/src/main/resources/net/echonolix/caelum/directx/api",
        args.repo_root / "dxgi/src/main/resources/net/echonolix/caelum/dxgi/api",
    )
    for root in roots:
        validate_index(root)
        print(f"validated {root}")
    validate_symbol_libraries(args.repo_root)
    print("validated native symbol DLL ownership")
    validate_required_signature_types(args.repo_root)
    print("validated required by-value signature types")
    validate_nested_record_closure(args.repo_root)
    print("validated nested record closure")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
