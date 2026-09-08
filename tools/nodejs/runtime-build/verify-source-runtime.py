#!/usr/bin/env python3
"""Manual ELF/embedding validation for a source-built libnode and its Android bridge."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--library', type=Path, required=True)
    parser.add_argument('--abi', choices=['arm64-v8a', 'armeabi-v7a', 'x86_64'], required=True)
    parser.add_argument('--llvm-bin', type=Path, required=True)
    parser.add_argument('--bridge', type=Path, help='Also validate direct imports and the adapter export map')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]

    def llvm(name, *arguments):
        executable = args.llvm_bin / name
        if not executable.exists():
            executable = executable.with_suffix('.exe')
        return subprocess.check_output([str(executable), *map(str, arguments)], text=True, encoding='utf-8')

    def symbols(library, defined=True):
        text = llvm('llvm-nm', '-D', '--defined-only' if defined else '--undefined-only', library)
        return {line.split()[-1].split('@')[0] for line in text.splitlines() if line.split()}

    data = args.library.read_bytes()
    if data[:4] != b'\x7fELF' or data[5] != 1:
        raise ValueError('Expected a materialized little-endian ELF library')
    expected_class, expected_machine = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40), 'x86_64': (2, 62)}[args.abi]
    if data[4] != expected_class or struct.unpack_from('<H', data, 18)[0] != expected_machine:
        raise ValueError('ELF architecture does not match ' + args.abi)
    program_headers = llvm('llvm-readelf', '-lW', args.library)
    alignments = [int(line.split()[-1], 16) for line in program_headers.splitlines() if line.strip().startswith('LOAD ')]
    if not alignments or any(value < 16384 or value % 16384 for value in alignments):
        raise ValueError('Every LOAD segment must support 16 KB pages: ' + str(alignments))
    notes = llvm('llvm-readelf', '-n', args.library)
    build_id = re.search(r'Build ID: ([0-9a-f]+)', notes)
    if not build_id:
        raise ValueError('ELF Build ID is missing')
    exported = symbols(args.library)
    source = (root / 'app/src/main/cpp/node_bridge_sources.cpp').read_text(encoding='utf-8')
    required = dict(re.findall(r'const char\* const (k\w*Symbol)\s*=\s*"([^"]+)";', source))
    # Disposal has a documented fallback to Isolate::Dispose; all startup/execution symbols are mandatory.
    required.pop('kMultiIsolatePlatformDisposeIsolateSymbol', None)
    missing = {name: symbol for name, symbol in required.items() if symbol not in exported}
    if missing:
        raise ValueError('Missing dynamic embedding symbols: ' + json.dumps(missing))
    adapter_exports = []
    direct_imports = []
    if args.bridge:
        bridge_exports = symbols(args.bridge)
        export_map = (root / 'tools/nodejs/runtime-build/libnode.exports.map').read_text(encoding='utf-8')
        global_part = export_map.split('global:', 1)[1].split('local:', 1)[0]
        adapter_exports = re.findall(r'\b(autojs_\w+)\s*;', global_part)
        if not adapter_exports or not set(adapter_exports) <= bridge_exports:
            raise ValueError('The bridge does not export the adapter ABI declared in libnode.exports.map')
        direct_imports = sorted(symbol for symbol in symbols(args.bridge, False)
                                if symbol.startswith(('_ZN2v8', '_ZNK2v8', '_ZN4node', '_ZNK4node', 'uv_')))
        absent = sorted(set(direct_imports) - exported)
        if absent:
            raise ValueError('New libnode cannot resolve bridge imports: ' + ', '.join(absent))
    dynamic = llvm('llvm-readelf', '-dW', args.library)
    report = {
        'abi': args.abi,
        'size': len(data),
        'sha256': hashlib.sha256(data).hexdigest(),
        'buildId': build_id.group(1),
        'loadAlignments': alignments,
        'neededLibraries': re.findall(r'\(NEEDED\).*?\[([^]]+)\]', dynamic),
        'soname': (re.findall(r'\(SONAME\).*?\[([^]]+)\]', dynamic) or [None])[0],
        'embeddingSymbolCount': len(required),
        'directBridgeImportCount': len(direct_imports),
        'adapterExports': adapter_exports,
        'deviceValidation': 'required separately before promotion',
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
