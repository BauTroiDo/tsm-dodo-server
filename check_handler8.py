import os
for f in os.listdir('C:/Server-Game/tsm_dodo/app/src/main/kotlin/tw/tsm/app/handlers'):
    if f.endswith('.kt'):
        p = 'C:/Server-Game/tsm_dodo/app/src/main/kotlin/tw/tsm/app/handlers/' + f
        with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
            c = fp.read()
            if 'BATTLE' in c or 'mainKind = 8' in c or 'mainKind = Opcodes.BATTLE' in c:
                print('Found in handler:', f)
