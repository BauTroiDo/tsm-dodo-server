import os
with open('C:/Server-Game/tsm_dodo/logs/tsm_dodo.2026-09-26.log', 'r', encoding='utf-8', errors='ignore') as f:
    for line in f:
        if '31' in line and ('login' in line.lower() or 'char' in line.lower() or 'role' in line.lower() or 'skill' in line.lower()):
            print(line.strip())
