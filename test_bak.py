
import os, hashlib, lz4_packed

base_bak = 'C:/Server-Game/tsm_dodo/cdn/mirror/tsr/2.4.76/ResourcesPacked/CompreseData.bak'
for f in os.listdir(base_bak):
    if f.endswith('.lz4'):
        fp = os.path.join(base_bak, f)
        data = open(fp, 'rb').read()
        try:
            decomp = lz4_packed.decompress(data)
            dmd5 = hashlib.md5(decomp).hexdigest().upper()
            print(f'BAK {f}: decompressed size={len(decomp)}, md5={dmd5}')
        except Exception as e:
            print(f'BAK {f}: FAILED -> {e}')

# check if any .bak files in ResourcesPacked
rp = 'C:/Server-Game/tsm_dodo/cdn/mirror/tsr/2.4.76/ResourcesPacked'
for item in os.listdir(rp):
    if 'Comprese' in item:
        print('RP item:', item)
