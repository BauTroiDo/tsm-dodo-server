
import lz4_packed, hashlib

base = 'C:/Server-Game/tsm_dodo/cdn/mirror/tsr/2.4.76/ResourcesPacked/CompreseData'
for name in ['Eve.emg', 'ground.mmg', 'Talk_C.dat']:
    raw_path = f'{base}/{name}'
    lz4_path = f'{base}/{name}.lz4'
    raw_data = open(raw_path, 'rb').read()
    raw_md5 = hashlib.md5(raw_data).hexdigest().upper()
    try:
        dec_data = lz4_packed.decompress(open(lz4_path, 'rb').read())
        dec_md5 = hashlib.md5(dec_data).hexdigest().upper()
        match = (raw_md5 == dec_md5)
        print(f'{name}: raw_len={len(raw_data)} dec_len={len(dec_data)} md5_match={match}')
    except Exception as e:
        print(f'{name}: DECOMPRESS FAILED -> {e}')
