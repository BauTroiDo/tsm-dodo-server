
import os
for root, dirs, files in os.walk(r'C:/Server-Game/tsm_dodo'):
    for f in files:
        if f.endswith(('.kt', '.java')):
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
                for i, line in enumerate(fp):
                    if 'enum class EAttribute' in line or 'SKILL_POINT' in line:
                        print(f'{f}:{i+1}: {line.strip()[:100]}')
