
import os
for root, dirs, files in os.walk(r'C:/Server-Game/tsm_dodo'):
    for f in files:
        if 'login' in f.lower() and f.endswith(('.kt', '.java')):
            print(os.path.join(root, f))
