import os
search_dir = 'C:/Server-Game/tsm_dodo'
for root, dirs, files in os.walk(search_dir):
    for f in files:
        if f.endswith(('.kt', '.java')):
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
                content = fp.read()
                if '13' in content and ('subKind' in content or 'register' in content or 'Handler' in p):
                    for line in content.splitlines():
                        if '13' in line and ('subKind' in line or 'register' in line or 'Skill' in line):
                            print(p, ':', line.strip())
