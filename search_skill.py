import os, re
search_dir = 'C:/Server-Game/tsm_dodo'
matches = []
for root, dirs, files in os.walk(search_dir):
    for f in files:
        if f.endswith(('.kt', '.java')):
            p = os.path.join(root, f)
            try:
                with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
                    content = fp.read()
                    if 'skillPoint' in content or 'skill_point' in content or 'SKILL_POINT' in content:
                        print('Found in:', p)
            except Exception as e:
                pass
