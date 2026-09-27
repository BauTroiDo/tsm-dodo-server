
import os
for root, dirs, files in os.walk(r'C:/Server-Game/tsm_dodo'):
    for f in files:
        if f.endswith('.kt'):
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
                for i, l in enumerate(fp):
                    if 'fun sendSetAttribute' in l or 'fun PacketWriter.writeSetAttribute' in l:
                        print(f'{f}:{i+1}: {l.strip()[:100]}')
