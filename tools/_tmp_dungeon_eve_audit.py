# -*- coding: utf-8 -*-
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = (ROOT / "gamedata" / "Eve.emg").read_bytes()
TARGET_SCENE = 62001
TARGET_MISSIONS = {12322, 12324, 12326, 12328, 12336}

class R:
    def __init__(self, b, o=0):
        self.b=b; self.o=o
    def u8(self):
        v=self.b[self.o]; self.o+=1; return v
    def u16(self):
        v=struct.unpack_from("<H", self.b, self.o)[0]; self.o+=2; return v
    def i32(self):
        v=struct.unpack_from("<i", self.b, self.o)[0]; self.o+=4; return v
    def skip(self,n): self.o+=n
    def bytes(self,n):
        v=self.b[self.o:self.o+n]; self.o+=n; return v

r=R(DATA)
scene_count=r.u16()
entries={}
for _ in range(scene_count):
    name_len=r.u8()
    name=r.bytes(23)
    pos=r.i32(); size=r.i32()
    try:
        sid=int(name[:name_len].decode("ascii").replace(".eve",""))
    except Exception:
        continue
    if pos>0 and size>0:
        entries[sid]=(pos,size)

def parse_scene(rr, sid):
    n=rr.i32()
    for _ in range(n):
        rr.skip(4)
        ec=rr.u16(); rr.skip(ec)
        sk=rr.u8(); rr.skip(sk)
        mn=rr.u8(); rr.skip((mn+1)*8)
        rr.skip(4+2+1+16+8+8+6+16+16+1+2+1)
    n=rr.u16(); rr.skip(13*n)
    n=rr.u16()
    for _ in range(n):
        rr.skip(2); ec=rr.u16(); rr.skip(ec); rr.skip(16+1+4+1)
    n=rr.u16()
    for _ in range(n):
        rr.skip(2); ec=rr.u16(); rr.skip(ec); rr.skip(17)
    n=rr.u16()
    for _ in range(n):
        rr.skip(4); sc=rr.u8(); rr.skip(3); rr.skip(5*sc)
    n=rr.u16(); rr.skip(39*n)
    n=rr.u16()
    for _ in range(n):
        rr.skip(6); mn=rr.u8(); rr.skip(mn*2+1)

    event_count=rr.u16()
    all_mission_rows=[]
    focused=[]
    for _ in range(event_count):
        eve_no=rr.u16()
        rr.skip(9)
        rr.skip(4)
        cc=rr.u8()
        conds=[]
        hit=False
        for ci in range(cc):
            cond_no=rr.u8()
            cls=rr.u8()
            param=rr.u16()
            pstyle=rr.u8()
            ops=rr.u8()
            value=rr.i32()
            rr.skip(8)
            to_result=rr.u8()
            and_num=rr.u8()
            sub_item=rr.u16()
            rc=rr.u8()
            results=[]
            if cls==2:
                all_mission_rows.append(("cond",eve_no,cond_no,cls,param,pstyle,ops,value,to_result,and_num,sub_item))
            if cls==2 and param in TARGET_MISSIONS:
                hit=True
            for _ in range(rc):
                group_no=rr.u16()
                result_no=rr.u8()
                rtype=rr.u8()
                rcls=rr.u8()
                rparam=rr.u16()
                rpstyle=rr.u8()
                rvalue=rr.i32()
                mean_no=rr.u16()
                row=(result_no,rtype,rcls,rparam,rpstyle,rvalue,mean_no,group_no)
                results.append(row)
                if rcls==2:
                    all_mission_rows.append(("result",eve_no)+row)
                if rcls==2 and rparam in TARGET_MISSIONS:
                    hit=True
            conds.append((cond_no,cls,param,pstyle,ops,value,to_result,and_num,sub_item,results))
        if hit:
            focused.append((eve_no,conds))

    print("SCENE",sid,"EVENTS",event_count)
    print("=== FOCUSED TARGET MISSIONS ===")
    for eve_no,conds in focused:
        print("## eve",eve_no)
        for row in conds:
            cond_no,cls,param,pstyle,ops,value,to_result,and_num,sub_item,results=row
            print(" cond",cond_no,"cls",cls,"param",param,"pStyle",pstyle,"ops",ops,"val",value,"to",to_result,"and",and_num,"sub",sub_item)
            for x in results:
                print("  result",*x)
    print("=== ALL CLASS2 MISSION ROWS IN SCENE ===")
    for row in all_mission_rows:
        print(*row)

pos,size=entries[TARGET_SCENE]
parse_scene(R(DATA,pos+103),TARGET_SCENE)
