# -*- coding: utf-8 -*-
"""Eve.emg 全量分析：條件/結果使用統計 vs 伺服器支援面（鏡像 EveDataLoader.kt 解析）"""
import struct
import io
from pathlib import Path
from collections import Counter, defaultdict

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "gamedata" / "Eve.emg"
OUT = ROOT / "_eve_report.txt"

data = open(PATH, "rb").read()


class R:
    def __init__(self, buf, off=0):
        self.b = buf
        self.o = off

    def u8(self):
        v = self.b[self.o]; self.o += 1; return v

    def u16(self):
        v = struct.unpack_from("<H", self.b, self.o)[0]; self.o += 2; return v

    def i32(self):
        v = struct.unpack_from("<i", self.b, self.o)[0]; self.o += 4; return v

    def f64(self):
        v = struct.unpack_from("<d", self.b, self.o)[0]; self.o += 8; return v

    def skip(self, n):
        self.o += n

    def bytes(self, n):
        v = self.b[self.o:self.o + n]; self.o += n; return v


r = R(data)
scene_count = r.u16()
entries = []
for _ in range(scene_count):
    name_len = r.u8()
    name = r.bytes(23)
    pos = r.i32()
    size = r.i32()
    try:
        sid = int(name[:name_len].decode("ascii").replace(".eve", ""))
    except Exception:
        continue
    if pos > 0 and size > 0:
        entries.append((sid, pos, size))

cond_class = Counter()                  # conditionClass → count
cond_detail = Counter()                 # (class, param, pStyle, ops) → count
cond_scenes = defaultdict(set)          # class → scenes
cond7_param = Counter()                 # conditionClass=7 的 param → count
res_combo = Counter()                   # (type, class, pStyle) → count
res_scenes = defaultdict(set)           # (type, class, pStyle) → scenes
res_type = Counter()
res_class = Counter()
cls7_results = defaultdict(list)        # pStyle → [(scene, eveNo, evtName, type, param, value)]
unsup_cond_examples = defaultdict(list) # class → [(scene, eveNo, evtName, param, pStyle, ops, value)]
parse_fail = 0
scene_ok = 0
total_events = 0
total_conditions = 0
total_results = 0


def parse_scene(rr, sid):
    global total_events, total_conditions, total_results
    # [1] NpcData
    n = rr.i32()
    if n < 0 or n > 10000:
        raise ValueError("npc count %d" % n)
    for _ in range(n):
        rr.skip(4)                       # id npcId
        ec = rr.u16()
        rr.skip(ec)                      # events 1B each
        sk = rr.u8()
        rr.skip(sk)
        mn = rr.u8()
        rr.skip((mn + 1) * 8)
        rr.skip(4)                       # motionType back cycle dir
        rr.skip(2)                       # suspendMS
        rr.skip(1)                       # speedLv
        rr.skip(16 + 8 + 8)              # roleGrid moveOffset position
        rr.skip(6)                       # roleStatus
        rr.skip(16 + 16)                 # inner outer
        rr.skip(1 + 2 + 1)               # traceSpeedLv traceRadius close
    # [2] Goods
    n = rr.u16()
    rr.skip(13 * n)
    # [3] Door
    n = rr.u16()
    for _ in range(n):
        rr.skip(2)
        ec = rr.u16()
        rr.skip(ec)
        rr.skip(16 + 1 + 4 + 1)
    # [4] Mine
    n = rr.u16()
    for _ in range(n):
        rr.skip(2)
        ec = rr.u16()
        rr.skip(ec)
        rr.skip(17)
    # [5] Surface
    n = rr.u16()
    for _ in range(n):
        rr.skip(4)
        sc = rr.u8()
        rr.skip(3)
        rr.skip(5 * sc)
    # [6] SceneInfo
    n = rr.u16()
    rr.skip(39 * n)
    # [7] Group
    n = rr.u16()
    for _ in range(n):
        rr.skip(6)
        mn = rr.u8()
        rr.skip(mn * 2 + 1)
    # [8] NpcEventData
    n = rr.u16()
    for _ in range(n):
        eve_no = rr.u16()
        name = rr.bytes(9)
        try:
            evt_name = name.split(b"\x00")[0].decode("big5", errors="replace")
        except Exception:
            evt_name = "?"
        rr.skip(4)                       # whenHappen
        cc = rr.u8()
        total_events += 1
        for _ in range(cc):
            rr.skip(1)                   # conditionNo
            cls = rr.u8()
            param = rr.u16()
            pstyle = rr.u8()
            ops = rr.u8()
            value = rr.i32()
            rr.skip(8)                   # ctime
            rr.skip(2)                   # toResult andNum
            rr.skip(2)                   # subItem
            total_conditions += 1
            cond_class[cls] += 1
            cond_detail[(cls, param, pstyle, ops)] += 1
            cond_scenes[cls].add(sid)
            if cls == 7:
                cond7_param[param] += 1
            if cls not in COND_SUPPORTED and len(unsup_cond_examples[cls]) < 8:
                unsup_cond_examples[cls].append((sid, eve_no, evt_name, param, pstyle, ops, value))
            rc = rr.u8()
            for _ in range(rc):
                rr.skip(2)               # groupNo
                rr.skip(1)               # resultNo
                rtype = rr.u8()
                rcls = rr.u8()
                rparam = rr.u16()
                rpstyle = rr.u8()
                rvalue = rr.i32()
                rr.skip(2)               # meanNo
                total_results += 1
                res_type[rtype] += 1
                res_class[rcls] += 1
                res_combo[(rtype, rcls, rpstyle)] += 1
                key = (rtype, rcls, rpstyle)
                if len(res_scenes[key]) < 6:
                    res_scenes[key].add(sid)
                if rcls == 7 and len(cls7_results[rpstyle]) < 12:
                    cls7_results[rpstyle].append((sid, eve_no, evt_name, rtype, rparam, rvalue))


COND_SUPPORTED = {0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 14, 15, 18}
COND7_PARAM_SUPPORTED = {0, 2, 3, 5, 6, 16}
RES_CLASS_SUPPORTED = {0, 1, 2, 3, 4, 5, 7, 8, 10}
CLS7_PSTYLE_MAPPED = {1, 2, 3, 4, 5, 6, 10, 12, 13, 15, 21, 27, 29, 30, 32, 35, 39}

for sid, pos, size in entries:
    try:
        rr = R(data, pos + 103)
        parse_scene(rr, sid)
        scene_ok += 1
    except Exception:
        parse_fail += 1

out = io.open(OUT, "w", encoding="utf-8")
w = out.write
w(u"=== Eve.emg 全量分析 ===\n")
w(u"場景目錄 %d 筆，解析成功 %d，失敗 %d\n" % (len(entries), scene_ok, parse_fail))
w(u"事件 %d / 條件 %d / 結果 %d\n\n" % (total_events, total_conditions, total_results))

w(u"--- conditionClass 分佈（*=伺服器未支援） ---\n")
for cls, cnt in sorted(cond_class.items()):
    mark = "" if cls in COND_SUPPORTED else " *未支援*"
    w(u"class=%-3d count=%-6d scenes=%d%s\n" % (cls, cnt, len(cond_scenes[cls]), mark))

w(u"\n--- 未支援 conditionClass 範例 ---\n")
for cls in sorted(unsup_cond_examples):
    for (sid, eve_no, name, param, pstyle, ops, value) in unsup_cond_examples[cls]:
        w(u"class=%d scene=%d eve=%d name=%s param=%d pStyle=%d ops=%d val=%d\n"
          % (cls, sid, eve_no, name, param, pstyle, ops, value))

w(u"\n--- conditionClass=7 param 分佈（*=未支援） ---\n")
for p, cnt in sorted(cond7_param.items()):
    mark = "" if p in COND7_PARAM_SUPPORTED else " *未支援*"
    w(u"param=%-3d count=%d%s\n" % (p, cnt, mark))

w(u"\n--- conditionClass 細項 Top 60（class,param,pStyle,ops） ---\n")
for (k, cnt) in cond_detail.most_common(60):
    w(u"class=%d param=%d pStyle=%d ops=%d count=%d\n" % (k[0], k[1], k[2], k[3], cnt))

w(u"\n--- resultType 分佈 ---\n")
for t, cnt in sorted(res_type.items()):
    w(u"type=%-3d count=%d\n" % (t, cnt))

w(u"\n--- resultClass 分佈（*=伺服器未支援） ---\n")
for c, cnt in sorted(res_class.items()):
    mark = "" if c in RES_CLASS_SUPPORTED else " *未支援*"
    w(u"class=%-3d count=%d%s\n" % (c, cnt, mark))

w(u"\n--- (type,class,pStyle) 組合 Top 80 ---\n")
for (k, cnt) in res_combo.most_common(80):
    scenes = ",".join(str(s) for s in sorted(res_scenes[k])[:5])
    w(u"type=%d cls=%d pStyle=%d count=%-5d scenes=[%s]\n" % (k[0], k[1], k[2], cnt, scenes))

w(u"\n--- resultClass=7 各 pStyle（*=未映射） ---\n")
for ps in sorted(cls7_results):
    mark = "" if ps in CLS7_PSTYLE_MAPPED else " *未映射*"
    total = sum(cnt for (k, cnt) in res_combo.items() if k[1] == 7 and k[2] == ps)
    w(u"pStyle=%-3d total=%d%s\n" % (ps, total, mark))
    for (sid, eve_no, name, rtype, rparam, rvalue) in cls7_results[ps][:8]:
        w(u"    scene=%d eve=%d name=%s type=%d param=%d val=%d\n"
          % (sid, eve_no, name, rtype, rparam, rvalue))

out.close()
print("done")
