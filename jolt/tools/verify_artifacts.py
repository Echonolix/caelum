"""Check portable/source jars and, optionally, native export tables. Does not execute Jolt."""

import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import zipfile


TARGETS = {
    "windows-x86_64": "caelum_jolt.dll",
    "linux-x86_64": "libcaelum_jolt.so",
    "macos-aarch64": "libcaelum_jolt.dylib",
}


def check_jar(path, required):
    with zipfile.ZipFile(path) as archive:
        missing = set(required) - set(archive.namelist())
        if missing:
            raise SystemExit(f"{path}: missing entries: {sorted(missing)}")
        for entry in required:
            if not archive.read(entry):
                raise SystemExit(f"{path}: empty entry: {entry}")
        license_text = archive.read("META-INF/licenses/JoltPhysics-LICENSE").decode("utf-8")
        if "Permission is hereby granted" not in license_text or "Jorrit Rouwe" not in license_text:
            raise SystemExit(f"{path}: missing upstream MIT notice")
    print(f"PASS {path}: sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--sources", required=True, type=Path)
    parser.add_argument("--native-dir", type=Path)
    parser.add_argument("--llvm-readobj", default="llvm-readobj")
    args = parser.parse_args()
    module = Path(__file__).resolve().parents[1]
    header = (module / "include/caelum_jolt.h").read_text(encoding="utf-8")
    expected = set(re.findall(r"CJ_API[^;]+?\b(cj_\w+)\s*\(", header))
    common = ["LICENSE", "META-INF/licenses/JoltPhysics-LICENSE"]
    check_jar(args.jar, common + [
        "META-INF/licenses/UPSTREAM.md", "META-INF/caelum/jolt-build.properties",
        "net/echonolix/caelum/jolt/JoltRuntime.class",
        "net/echonolix/caelum/jolt/structs/JoltBodyCreateInfo.class",
        *[f"META-INF/caelum/native/{target}/{name}" for target, name in TARGETS.items()],
    ])
    check_jar(args.sources, common + [
        "include/caelum_jolt.h", "native/caelum_jolt.cpp", "native/caelum_jolt_abi.cpp",
        "UPSTREAM.md", "SURFACE.md", "native-sources.txt", "build.gradle.kts",
    ])
    with zipfile.ZipFile(args.sources) as sources:
        if sources.read("include/caelum_jolt.h") != (module / "include/caelum_jolt.h").read_bytes():
            raise SystemExit("Source jar header differs from the audited checkout")
    if args.native_dir:
        with zipfile.ZipFile(args.jar) as archive:
            for target, filename in TARGETS.items():
                library = args.native_dir / target / filename
                binary = library.read_bytes()
                if binary != archive.read(f"META-INF/caelum/native/{target}/{filename}"):
                    raise SystemExit(f"{target}: audited binary differs from portable jar")
                options = {
                    "windows-x86_64": ["--coff-exports", "--coff-imports"],
                    "linux-x86_64": ["--dyn-symbols", "--needed-libs"],
                    "macos-aarch64": ["--symbols", "--macho-version-min"],
                }[target]
                report = subprocess.check_output([args.llvm_readobj, *options, str(library)], text=True)
                blocks = re.findall(r"(?:Export|Symbol)\s*\{(.*?)\}", report, re.S)
                exports = set()
                for block in blocks:
                    if re.search(r"(?:Section|Type):\s+Undefined", block):
                        continue
                    match = re.search(r"Name:\s+_?(cj_\w+)\b", block)
                    if match:
                        exports.add(match[1])
                if exports != expected:
                    raise SystemExit(f"{target}: missing={sorted(expected - exports)}, unexpected cj exports={sorted(exports - expected)}")
                print(f"PASS {target}: {len(exports)} bridge exports; sha256={hashlib.sha256(binary).hexdigest()}")
                print(report)
                if target == "macos-aarch64":
                    print(f"REQUIRED ON macOS: otool -L {library} (runtime dependency audit)")
    print("Package checks passed. Target runtime and dependency acceptance are separate gates.")


if __name__ == "__main__":
    main()
