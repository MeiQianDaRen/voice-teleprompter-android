"""Check the ARM64 OCR/speech native linkage in the final APK (stdlib only)."""
import struct
import sys
from zipfile import ZipFile


def elf_api(data):
    assert data[:6] == b'\x7fELF\x02\x01', 'Expected little-endian ELF64'
    assert struct.unpack_from('<H', data, 18)[0] == 183, 'Expected AArch64'
    table = struct.unpack_from('<Q', data, 40)[0]
    size, count = struct.unpack_from('<HH', data, 58)
    sections = [struct.unpack_from('<IIQQQQIIQQ', data, table + i * size) for i in range(count)]

    def payload(section):
        return data[section[4]:section[4] + section[5]]

    def string(strings, offset):
        return strings[offset:strings.index(b'\0', offset)].decode('utf-8')

    exports, imports, needed = set(), set(), set()
    versions, requirements = set(), {}
    for section in sections:
        if section[1] not in (6, 11, 0x6ffffffd, 0x6ffffffe):
            continue
        strings = payload(sections[section[6]])
        if section[1] in (0x6ffffffd, 0x6ffffffe):  # GNU version definitions / requirements
            offset = section[4]
            while True:
                if section[1] == 0x6ffffffd:
                    _, _, _, _, _, aux, next_entry = struct.unpack_from('<HHHHIII', data, offset)
                    name, _ = struct.unpack_from('<II', data, offset + aux)
                    versions.add(string(strings, name))
                else:
                    _, count, library, aux, next_entry = struct.unpack_from('<HHIII', data, offset)
                    required = requirements.setdefault(string(strings, library), set())
                    aux_offset = offset + aux
                    for _ in range(count):
                        _, _, _, name, next_aux = struct.unpack_from('<IHHII', data, aux_offset)
                        required.add(string(strings, name))
                        aux_offset += next_aux
                if not next_entry:
                    break
                offset += next_entry
            continue
        for offset in range(section[4], section[4] + section[5], section[9]):
            if section[1] == 6:
                tag, value = struct.unpack_from('<qQ', data, offset)
                if tag == 1:  # DT_NEEDED
                    needed.add(string(strings, value))
            else:
                name, info, visibility, index, _, _ = struct.unpack_from('<IBBHQQ', data, offset)
                if name and info >> 4 in (1, 2) and visibility & 3 in (0, 3):
                    (imports if index == 0 else exports).add(string(strings, name))
    return exports, imports, needed, versions, requirements


def check(apk_path):
    prefix = 'lib/arm64-v8a/'
    with ZipFile(apk_path) as apk:
        names = apk.namelist()
        libs = {}
        for name in ('libonnxruntime.so', 'libonnxruntime4j_jni.so', 'libsherpa-onnx-jni.so'):
            path = prefix + name
            assert names.count(path) == 1, f'Missing or duplicate {path}'
            libs[name] = elf_api(apk.read(path))
    runtime = libs['libonnxruntime.so']
    bridge = libs['libonnxruntime4j_jni.so']
    speech = libs['libsherpa-onnx-jni.so']
    assert 'OrtGetApiBase' in runtime[0], 'OCR runtime does not export OrtGetApiBase'
    assert 'OrtGetApiBase' in bridge[1], 'Unexpected OCR JNI bridge'
    assert 'libonnxruntime.so' in bridge[2], 'OCR JNI must link its matching shared runtime'
    required = bridge[4].get('libonnxruntime.so', set())
    assert required and required <= runtime[3], f'OCR runtime missing symbol versions: {required - runtime[3]}'
    assert 'libonnxruntime.so' not in speech[2], 'Speech must use the static-link ONNX Runtime build'
    assert 'OrtGetApiBase' not in speech[0], 'Speech must keep its embedded runtime private'
    print('PASS: ARM64 OCR JNI resolves OrtGetApiBase; speech runtime is isolated')


if __name__ == '__main__':
    check(sys.argv[1])
