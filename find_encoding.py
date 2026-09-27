
import os
for root, dirs, files in os.walk(r'C:/Server-Game/tsm_dodo'):
    for f in files:
        if f.endswith(('.kt', '.java', '.py', '.txt')):
            p = os.path.join(root, f)
            try:
                content = open(p, 'r', encoding='utf-8', errors='ignore').read()
                if 'writeBig5WithByteLen' in content:
                    print(f'Found in {p}')
            except:
                pass
