import os, sys
with open('C:/Server-Game/tsm_dodo/out.txt', 'w', encoding='utf-8') as out:
    for path in [
        'C:/Server-Game/tsm_dodo/app/src/main/kotlin/tw/tsm/app/response/LoginResponseSender.kt',
        'C:/Server-Game/tsm_dodo/gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/LoginService.kt',
        'C:/Server-Game/tsm_dodo/app/src/main/kotlin/tw/tsm/app/handlers/SkillUpgradeHandler.kt'
    ]:
        out.write('=== ' + path + ' ===
')
        with open(path, 'r', encoding='utf-8', errors='ignore') as f:
            out.write(f.read() + '
')
