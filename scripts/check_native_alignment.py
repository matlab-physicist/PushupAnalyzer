#!/usr/bin/env python3
"""Verify every bundled ARM64/x86_64 ELF LOAD segment supports 16 KB pages."""
import struct
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as apk:
    libraries = [n for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so')]
    assert libraries, 'No native libraries found'
    assert any(n.startswith('lib/arm64-v8a/') for n in libraries), 'Missing ARM64 libraries'
    for name in libraries:
        elf = apk.read(name)
        assert elf[:6] == b'\x7fELF\x02\x01', f'{name}: expected little-endian ELF64'
        phoff = struct.unpack_from('<Q', elf, 32)[0]
        phentsize, phnum = struct.unpack_from('<HH', elf, 54)
        loads = 0
        for i in range(phnum):
            entry = struct.unpack_from('<IIQQQQQQ', elf, phoff+i*phentsize)
            kind, _, offset, address, _, _, _, alignment = entry
            if kind == 1:
                loads += 1
                assert alignment >= 16384, f'{name}: LOAD alignment {alignment} < 16384'
                assert (address-offset) % 16384 == 0, f'{name}: unaligned LOAD offset'
        assert loads, f'{name}: missing LOAD segments'
        print(f'PASS {name}: {loads} LOAD segments aligned for 16 KB pages')
