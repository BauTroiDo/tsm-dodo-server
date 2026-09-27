
import os, hashlib

for d in ['CompreseData', 'CompreseData.bak']:
    p = 'C:/Server-Game/tsm_dodo/cdn/mirror/tsr/2.4.76/ResourcesPacked/' + d
    if os.path.exists(p):
        print('=== ' + d + ' ===')
        for f in os.listdir(p):
            fp = os.path.join(p, f)
            size = os.path.getsize(fp)
            with open(fp, 'rb') as fl:
                data = fl.read()
                head = data[:16].hex()
                md5 = hashlib.md5(data).hexdigest().upper()
            print(f'{f:25} {size:10} bytes md5={md5} head={head}')
