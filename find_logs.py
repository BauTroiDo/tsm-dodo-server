import os
for root, dirs, files in os.walk('C:/Server-Game/tsm_dodo'):
    for f in files:
        if f.endswith('.log'):
            p = os.path.join(root, f)
            print(p, os.path.getsize(p))
