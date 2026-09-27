# -*- coding: utf-8 -*-
"""聚焦分析：未映射 (type=0/7, cls=7) pStyle 與未支援 conditionClass/resultClass 的完整事件上下文"""
import struct
import io
from pathlib import Path
from collections import defaultdict

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "gamedata" / "Eve.emg"
OUT = ROOT / "_eve_focus.txt"

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

# 關注集合
UNMAPPED_CLS7_PSTYLE = {7, 8, 9, 11, 14, 16, 17, 20, 22, 33, 34, 36, 38}
UNSUP_RES_CLASS = {11, 12, 13, 14, 15}

hits = []           # (sid, event_dict) — 含未映射 cls7 pStyle（type 0/7）的事件
res_cls_hits = defaultdict(list)   # resultClass → [(sid, eveNo, type, param, pStyle, value)]
cond13_detail = defaultdict(int)   # (param,pStyle,ops,value) for class13
cond13_scenes = set()


def parse_scene(rr, sid):
    n = rr.i32()
    if n < 0 or n > 10000:
        raise ValueError
    for _ in range(n):
        rr.skip(4)
        ec = rr.u16(); rr.skip(ec)
        sk = rr.u8(); rr.skip(sk)
        mn = rr.u8(); rr.skip((mn + 1) * 8)
        rr.skip(4 + 2 + 1 + 16 + 8 + 8 + 6 + 16 + 16 + 1 + 2 + 1)
    n = rr.u16(); rr.skip(13 * n)
    n = rr.u16()
    for _ in range(n):
        rr.skip(2)
        ec = rr.u16(); rr.skip(ec)
        rr.skip(16 + 1 + 4 + 1)
    n = rr.u16()
    for _ in range(n):
        rr.skip(2)
        ec = rr.u16(); rr.skip(ec)
        rr.skip(17)
    n = rr.u16()
    for _ in range(n):
        rr.skip(4)
        sc = rr.u8(); rr.skip(3); rr.skip(5 * sc)
    n = rr.u16(); rr.skip(39 * n)
    n = rr.u16()
    for _ in range(n):
        rr.skip(6)
        mn = rr.u8(); rr.skip(mn * 2 + 1)
    n = rr.u16()
    for _ in range(n):
        eve_no = rr.u16()
        rr.skip(9)
        rr.skip(4)
        cc = rr.u8()
        conds = []
        interesting = False
        for ci in range(cc):
            cond_no = rr.u8()
            cls = rr.u8()
            param = rr.u16()
            pstyle = rr.u8()
            ops = rr.u8()
            value = rr.i32()
            rr.skip(8)
            to_result = rr.u8()
            and_num = rr.u8()
            sub_item = rr.u16()
            rc = rr.u8()
            results = []
            for _ in range(rc):
                group_no = rr.u16()
                result_no = rr.u8()
                rtype = rr.u8()
                rcls = rr.u8()
                rparam = rr.u16()
                rpstyle = rr.u8()
                rvalue = rr.i32()
                mean_no = rr.u16()
                results.append((result_no, rtype, rcls, rparam, rpstyle, rvalue, mean_no, group_no))
                if rcls == 7 and rtype in (0, 7) and rpstyle in UNMAPPED_CLS7_PSTYLE:
                    interesting = True
                if rcls in UNSUP_RES_CLASS and rtype in (0, 7):
                    res_cls_hits[rcls].append((sid, eve_no, rtype, rparam, rpstyle, rvalue))
            conds.append((cond_no, cls, param, pstyle, ops, value, to_result, and_num, sub_item, results))
            if cls == 13:
                cond13_detail[(param, pstyle, ops, value)] += 1
                cond13_scenes.add(sid)
        if interesting:
            hits.append((sid, eve_no, conds))


for sid, pos, size in entries:
    try:
        parse_scene(R(data, pos + 103), sid)
    except Exception:
        pass

out = io.open(OUT, "w", encoding="utf-8")
w = out.write

w(u"=== 未映射 (type=0/7, cls=7) pStyle 事件完整上下文（前 40 件） ===\n")
for (sid, eve_no, conds) in hits[:40]:
    w(u"\n## scene=%d eve=%d\n" % (sid, eve_no))
    for (cond_no, cls, param, pstyle, ops, value, to_result, and_num, sub_item, results) in conds:
        w(u"  cond#%d class=%d param=%d pStyle=%d ops=%d val=%d toResult=%d andNum=%d subItem=%d\n"
          % (cond_no, cls, param, pstyle, ops, value, to_result, and_num, sub_item))
        for (rn, rt, rc_, rp, rps, rv, mn, gn) in results:
            w(u"    result#%d type=%d cls=%d param=%d pStyle=%d val=%d meanNo=%d group=%d\n"
              % (rn, rt, rc_, rp, rps, rv, mn, gn))

w(u"\n=== conditionClass=13 細項分佈 ===\n")
w(u"scenes: %s\n" % sorted(cond13_scenes))
for k, cnt in sorted(cond13_detail.items()):
    w(u"param=%d pStyle=%d ops=%d val=%d count=%d\n" % (k[0], k[1], k[2], k[3], cnt))

w(u"\n=== 未支援 resultClass（type=0/7 才需處理）出現位置 ===\n")
for rc_ in sorted(res_cls_hits):
    lst = res_cls_hits[rc_]
    w(u"resultClass=%d 共 %d 筆\n" % (rc_, len(lst)))
    for (sid, eve_no, rt, rp, rps, rv) in lst[:15]:
        w(u"    scene=%d eve=%d type=%d param=%d pStyle=%d val=%d\n" % (sid, eve_no, rt, rp, rps, rv))

out.close()
print("hits=%d" % len(hits))
