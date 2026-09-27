
import os
for root, dirs, files in os.walk(r'C:/Server-Game/tsm_dodo'):
    for f in files:
        if f.endswith('.kt'):
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
                txt = fp.read()
                if 'fun sendPlayerSkillData' in txt or 'sendPlayerSkillData(' in txt:
                    lines = txt.splitlines()
                    for i, l in enumerate(lines):
                        if 'sendPlayerSkillData' in l and 'fun ' in l:
                            print(f'{f}:{i+1}')
                            for j in range(i, min(len(lines), i+30)):
                                print(f'  {lines[j]}')
