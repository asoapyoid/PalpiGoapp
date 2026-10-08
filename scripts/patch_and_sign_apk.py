import os
import sys
import struct
import zlib
import hashlib
import zipfile

def write_uleb128(val):
    res = bytearray()
    while True:
        b = val & 0x7f
        val >>= 7
        if val != 0:
            b |= 0x80
        res.append(b)
        if val == 0:
            break
    return bytes(res)

def patch_axml(manifest_bytes, new_version_code, new_version_name):
    axml = bytearray(manifest_bytes)
    chunk_type, chunk_size = struct.unpack('<II', axml[8:16])
    assert chunk_type == 0x001c0001, "Invalid AXML string pool chunk"
    stringCount, styleCount, flags, stringsStart, stylesStart = struct.unpack('<IIIII', axml[16:36])
    offsets = list(struct.unpack(f'<{stringCount}I', axml[36:36+stringCount*4]))
    strings_base = 8 + stringsStart

    orig_strings = []
    for i in range(stringCount):
        s_ptr = strings_base + offsets[i]
        s_len = struct.unpack('<H', axml[s_ptr:s_ptr+2])[0]
        s_bytes = axml[s_ptr+2:s_ptr+2+s_len*2]
        orig_strings.append(s_bytes.decode('utf-16le'))

    print(f"[AXML] Original string 31 was: '{orig_strings[31]}'")
    orig_strings[31] = new_version_name
    print(f"[AXML] Replaced string 31 with: '{new_version_name}'")

    new_str_data = bytearray()
    new_offsets = []
    for s in orig_strings:
        new_offsets.append(len(new_str_data))
        encoded = s.encode('utf-16le')
        new_str_data.extend(struct.pack('<H', len(s)))
        new_str_data.extend(encoded)
        new_str_data.extend(b'\x00\x00')

    # 4-byte align the string pool data
    while len(new_str_data) % 4 != 0:
        new_str_data.append(0)

    styles_data = axml[8 + stylesStart : 8 + chunk_size] if styleCount > 0 else b''
    new_strings_start = 28 + stringCount * 4 + styleCount * 4
    # Note: chunk_size INCLUDES the 8-byte chunk header (chunk_type + chunk_size)
    # The header is 8 bytes, pool_header is 20 bytes -> 28 bytes total before offsets.
    # Therefore, new_chunk_size = new_strings_start + len(new_str_data) + len(styles_data)!
    new_chunk_size = new_strings_start + len(new_str_data) + len(styles_data)

    new_pool_header = struct.pack('<IIIII', stringCount, styleCount, flags, new_strings_start, new_strings_start + len(new_str_data) if styleCount > 0 else 0)
    new_offsets_bytes = struct.pack(f'<{stringCount}I', *new_offsets)

    rebuilt_pool = struct.pack('<II', chunk_type, new_chunk_size) + new_pool_header + new_offsets_bytes + new_str_data + styles_data
    assert len(rebuilt_pool) == new_chunk_size, f"Pool length mismatch: {len(rebuilt_pool)} != {new_chunk_size}"

    rest = axml[8 + chunk_size :]
    rebuilt_axml = bytearray(axml[:8] + rebuilt_pool + rest)
    struct.pack_into('<I', rebuilt_axml, 4, len(rebuilt_axml))

    # Verify chunk structure and find StartElement <manifest> to update versionCode
    idx = 8 + new_chunk_size
    found_code = False
    manifest_checked = False
    while idx < len(rebuilt_axml) - 8:
        tag, size = struct.unpack('<II', rebuilt_axml[idx:idx+8])
        if tag == 0x00100102: # RES_XML_START_ELEMENT_TYPE
            attr_start, attr_size, attr_count = struct.unpack('<HHH', rebuilt_axml[idx+24:idx+30])
            aptr = idx + 16 + attr_start
            for a in range(attr_count):
                ans, aname, araw, asize_attr, atype, adata = struct.unpack('<IIIHHI', rebuilt_axml[aptr:aptr+20])
                if aname == 0:  # versionCode
                    print(f"[AXML] Found versionCode attribute: old={adata}, setting to {new_version_code}")
                    struct.pack_into('<I', rebuilt_axml, aptr+16, new_version_code)
                    found_code = True
                aptr += 20
            manifest_checked = True
            break
        idx += size

    assert manifest_checked and found_code, "Failed to find versionCode in AXML manifest"

    # FULL VALIDATION of every chunk in rebuilt_axml
    v_idx = 8
    chunk_count = 0
    while v_idx < len(rebuilt_axml):
        c_type, c_sz = struct.unpack('<II', rebuilt_axml[v_idx:v_idx+8])
        assert c_sz > 0, f"Invalid chunk size 0 at offset {v_idx}"
        v_idx += c_sz
        chunk_count += 1
    assert v_idx == len(rebuilt_axml), f"Manifest length mismatch: walked {v_idx} bytes, total {len(rebuilt_axml)}"
    print(f"[AXML] Validated all {chunk_count} AXML chunks cleanly! Output size={len(rebuilt_axml)}")

    return bytes(rebuilt_axml)

def patch_dex(dex_bytes, new_version_code, new_version_name):
    dex = bytearray(dex_bytes)

    # 1. Update static_values in MainActivity class_def
    class_defs_size, class_defs_off = struct.unpack('<II', dex[96:104])
    found_main = False
    for i in range(class_defs_size):
        class_idx, access_flags, superclass_idx, interfaces_off, source_file_idx, annotations_off, class_data_off, static_values_off = struct.unpack('<IIIIIIII', dex[class_defs_off + i*32 : class_defs_off + (i+1)*32])
        if class_idx == 230:  # MainActivity
            print(f"[DEX] MainActivity static_values before: {list(dex[static_values_off:static_values_off+4])}")
            dex[static_values_off + 2] = new_version_code
            print(f"[DEX] MainActivity static_values after: {list(dex[static_values_off:static_values_off+4])}")
            found_main = True

    assert found_main, "Failed to find MainActivity in DEX class_defs"

    # 2. String replacements
    replacements = {
        272: new_version_name,
        2537: f"PalpiGO v{new_version_name}",
        4846: f"v{new_version_name} \u2022 CUSTOMIZABLE HUD",
        5128: f"\u2713 AUTO-SCAN VERIFIED: You are on the LATEST version!\nInstalled: v{new_version_name} (Build {new_version_code} = Remote Build ",
        5129: f"\u2713 Auto-Scan Complete: You are already on the latest version (v{new_version_name})!",
        5280: f"\U0001F50D Auto-Scanning OTA relays for latest version (Installed: v{new_version_name} \u2022 Build {new_version_code})...",
    }

    string_ids_size, string_ids_off = struct.unpack('<II', dex[56:64])
    for s_idx, new_str in replacements.items():
        encoded = new_str.encode('utf-8')
        new_data = write_uleb128(len(new_str)) + encoded + b'\x00'
        new_off = len(dex)
        dex.extend(new_data)
        struct.pack_into('<I', dex, string_ids_off + s_idx * 4, new_off)
        print(f"[DEX] Replaced string {s_idx} with '{new_str}' @ offset {new_off}")

    while len(dex) % 4 != 0:
        dex.append(0)

    new_file_size = len(dex)
    struct.pack_into('<I', dex, 32, new_file_size)
    data_size, data_off = struct.unpack('<II', dex[104:112])
    struct.pack_into('<I', dex, 104, new_file_size - data_off)

    # Recalculate signature and checksum
    sig = hashlib.sha1(dex[32:]).digest()
    dex[12:32] = sig
    chk = zlib.adler32(dex[12:]) & 0xffffffff
    struct.pack_into('<I', dex, 8, chk)

    print(f"[DEX] Patched classes.dex: new size={len(dex)}, checksum={hex(chk)}")
    return bytes(dex)

def build_unsigned_apk(base_apk_path, out_unsigned_apk_path, new_version_code, new_version_name):
    with zipfile.ZipFile(base_apk_path, 'r') as in_zip:
        manifest_raw = in_zip.read('AndroidManifest.xml')
        dex_raw = in_zip.read('classes.dex')

        patched_manifest = patch_axml(manifest_raw, new_version_code, new_version_name)
        patched_dex = patch_dex(dex_raw, new_version_code, new_version_name)

        with zipfile.ZipFile(out_unsigned_apk_path, 'w', compression=zipfile.ZIP_DEFLATED) as out_zip:
            for item in in_zip.infolist():
                if item.filename.startswith('META-INF/'):
                    continue  # Strip old signatures so apk_sign_ts can sign cleanly with release key
                if item.filename == 'AndroidManifest.xml':
                    out_zip.writestr(item, patched_manifest)
                elif item.filename == 'classes.dex':
                    out_zip.writestr(item, patched_dex)
                else:
                    data = in_zip.read(item.filename)
                    out_zip.writestr(item, data)

    print(f"[APK] Built clean unsigned APK at {out_unsigned_apk_path}")

if __name__ == '__main__':
    vcode = int(sys.argv[1]) if len(sys.argv) > 1 else 3
    vname = sys.argv[2] if len(sys.argv) > 2 else "1.2.2026"
    base_apk = "public/PalpiGO-v1.0.0.apk"
    out_apk = "/tmp/palpigo-unsigned.apk"
    build_unsigned_apk(base_apk, out_apk, vcode, vname)
