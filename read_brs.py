
p = r'C:/Server-Game/tsm_dodo/app/src/main/kotlin/tw/tsm/app/response/BattleResponseSender.kt'
lines = open(p, 'r', encoding='utf-8', errors='ignore').read().splitlines()
for i in range(20, min(len(lines), 50)):
    print(str(i+1) + ': ' + lines[i].encode('ascii', errors='backslashreplace').decode())
