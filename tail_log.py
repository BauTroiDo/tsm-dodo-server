import os, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
with open('C:/Server-Game/tsm_dodo/logs/tsm_dodo.log', 'r', encoding='utf-8', errors='replace') as f:
    lines = f.readlines()
    for l in lines[-30:]:
        print(l.strip())
