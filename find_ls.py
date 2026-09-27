
p = r'C:/Server-Game/tsm_dodo/gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/LoginService.kt'
lines = open(p, 'r', encoding='utf-8', errors='ignore').read().splitlines()
for i in range(190, min(len(lines), 240)):
    print(str(i+1) + ': ' + lines[i].encode('ascii', errors='backslashreplace').decode())
