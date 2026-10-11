#!/usr/bin/env python3
"""Restore DVT12509/10 two-phase collection→turn-in and /14 exact original quiz NPC."""
import argparse
import hashlib
import os
import struct
from datetime import datetime
from pathlib import Path
from audit_dvt_quest_navigation import read_steps
from eve_tool import EveArchive
from repair_dvt_mission_door_refs_20261011 import scan

QUEST=12509
ROD=29454
ROD_SOURCE=(49946,1910,1580,1,10)
ROD_TURNIN=(49946,1810,1600,1,9)
QUIZ=(49946,2250,220,1,11)
BEFORE_SHA={
 "87e30327e5c42ccec30f788022fc28a5f181505c03bb8102fdb3beb14a489ec0",
 "ccb3d8a119c7adc4e9503a3aaae721e0d20103c300168075610bbac698790f78",
}
def sha(data):return hashlib.sha256(data).hexdigest()
def effect(e,typ,cl,par,ps,val):
 return e["type"]==typ and e["class"]==cl and e["param"]==par and e["pstyle"]==ps and e["val"]==val
def gate(c,step):
 return c["class"]==2 and c["param"]==QUEST and c["pstyle"]==1 and c["ops"]==5 and c["val"]==step

def source_verified(eve):
 archive=EveArchive(Path(eve))
 sc=archive.parse_scene(49946)
 def find_npc(placement,npc_id,x,y,eve_no):
  items=[n for n in sc["npcs"] if (n["id"],n["npc_id"],n["x"],n["y"],n["close"]) ==
    (placement,npc_id,x,y,False) and eve_no in n["events"]]
  if len(items)!=1:raise ValueError(f"Original NPC {npc_id} placement{placement}/Eve{eve_no} missing")
  if sc["events"][eve_no]["when_happen"]!=[True,False,False,False]:
   raise ValueError("NPC event is not normal active interaction")
 find_npc(10,24005,1910,1580,19)
 find_npc(9,15422,1810,1600,20)
 find_npc(11,22092,2250,220,21)
 acquire=sc["events"][19]["conditions"]
 acquire_gate=[c for c in acquire if c["no"]==1 and gate(c,10) and c["and_num"]==2 and c["to_result"]==1]
 acquire_item=[c for c in acquire if c["no"]==2 and c["class"]==1 and c["param"]==ROD
              and c["pstyle"]==1 and c["ops"]==1 and c["val"]==10]
 if len(acquire_gate)!=1 or len(acquire_item)!=1 or not any(
    effect(x,0,1,ROD,1,1) for x in acquire_gate[0]["results"]):
  raise ValueError("Expected original <=9 rod branch granting one branch absent")
 if not any(c["no"]==3 and gate(c,10) and c["and_num"]==2 for c in acquire):
  raise ValueError("Original >=10 rod gate absent")
 if not any(c["no"]==4 and c["class"]==1 and c["param"]==ROD
           and c["pstyle"]==1 and c["ops"]==2 and c["val"]==9 for c in acquire):
  raise ValueError("Original completion inventory-guard missing")
 turnin=sc["events"][20]["conditions"]
 success=[c for c in turnin if c["no"]==10 and gate(c,10) and c["and_num"]==3]
 requires=[c for c in turnin if c["no"]==11 and c["class"]==1 and c["param"]==ROD
             and c["pstyle"]==1 and c["ops"]==2 and c["val"]==9]
 if len(success)!=1 or len(requires)!=1 or not all(
   any(effect(z,*e) for z in success[0]["results"])
   for e in ((0,1,ROD,1,-10),(0,2,QUEST,1,1),(0,2,QUEST,1,3))):
  raise ValueError("Exact 10-rod hand-in plus mission advance missing")
 quiz=sc["events"][21]["conditions"]
 quizgate=[c for c in quiz if c["no"]==3 and gate(c,14) and c["and_num"]==2]
 if len(quizgate)!=1 or not any(x["type"]==6 for x in quizgate[0]["results"]):
  raise ValueError("Original quiz choice dispatch absent")
 right=[c for c in quiz if c["class"]==10 and c["param"] in (7,8,9,10,11,12)
        and c["pstyle"] in (30,31) and
        any(effect(z,0,2,QUEST,1,1) for z in c["results"])]
 if not right or {c["param"] for c in right}!={7,8,9,10,11,12}:
  raise ValueError("Source quiz has no verified correct-answer quest progression")
 return ROD_SOURCE,ROD_TURNIN,QUIZ

def offsets(data):
 """Locate the exact packed first condition and END for 12509 steps10 & 14."""
 n=int.from_bytes(data[:4],"little");pos=4;places={}
 for _ in range(n):
  rowstart=pos
  mission=int.from_bytes(data[pos:pos+2],"little");pos+=2
  step=data[pos];pos+=1
  descsz=int.from_bytes(data[pos:pos+2],"little");pos+=2+descsz
  cond=pos;pos+=60
  guidesz=int.from_bytes(data[pos:pos+2],"little");pos+=2+guidesz
  end=pos;pos+=8+2
  if mission==QUEST and step in (10,14):
   if step in places:raise ValueError("Duplicate mission stage")
   places[step]=(cond,end)
 if pos!=len(data) or set(places)!={10,14}:raise ValueError("Invalid MarkStep framing or stage coverage")
 return places

def packed_condition():
 return struct.pack("<BHBHHHBB",3,ROD,10,*ROD_SOURCE)

def patch(path,eve,apply=False):
 path=Path(path);raw=path.read_bytes()
 source,returnnpc,quiz=source_verified(eve)
 rows={r["step"]:r for r in read_steps(path) if r["mission"]==QUEST}
 if len(rows)!=16 or set((9,10,11,13,14,15,16))-set(rows):
  raise ValueError("Unexpected Christmas stage editions")
 before=rows[10];after=rows[14]
 valid_cond=(3,ROD,10,source)
 if before["conditions"][0] not in ((0,0,0,(0,0,0,0,0)),valid_cond):
  raise ValueError("Different original first item condition")
 if any(c[0] for c in before["conditions"][1:]) or any(c[0] for c in after["conditions"]):
  raise ValueError("Unexpected additional objectives")
 if before["end"] not in ((0,0,0,0,0),returnnpc) or after["end"] not in ((0,0,0,0,0),quiz):
  raise ValueError("Unknown END on these stages")
 if rows[9]["end"]!=(49946,1770,1530,1,9) or rows[15]["end"]!=(49946,590,310,1,12):
  raise ValueError("Previous/next quest route invalid")
 places=offsets(raw)
 if places[10][1]!=scan(raw)[(QUEST,10,"END")][-1]-7 or places[14][1]!=scan(raw)[(QUEST,14,"END")][-1]-7:
  raise ValueError("Independent packed offset discovery disagrees")
 staged=bytearray(raw)
 fields=[
  (places[10][0],packed_condition()),
  (places[10][1],struct.pack("<HHHBB",*returnnpc)),
  (places[14][1],struct.pack("<HHHBB",*quiz)),
 ]
 for at,value in fields:
  old=bytes(staged[at:at+len(value)])
  if old not in (bytes(len(value)),value):raise ValueError("Field unexpectedly populated")
  staged[at:at+len(value)]=value
 if staged==raw:
  print("ALREADY_FIXED",path,sha(raw))
  return sha(raw)
 if sha(raw) not in BEFORE_SHA or len(staged)!=len(raw):
  raise ValueError("Unknown source edition or partially modified MarkStep")
 changes={i for i,(a,b) in enumerate(zip(raw,staged)) if a!=b}
 allowed={j for at,v in fields for j in range(at,at+len(v))}
 if not changes or not changes.issubset(allowed):raise ValueError("Unexpected bytes changed")
 if tuple(scan(staged)[(QUEST,10,"END")][:5])!=returnnpc or tuple(scan(staged)[(QUEST,14,"END")][:5])!=quiz:
  raise ValueError("END roundtrip failed")
 print("PATCH",path,"OLD",sha(raw),"NEW",sha(staged),"DIFF_BYTES",len(changes))
 for at,v in fields:print(" FIELD_OFFSET",at,"BYTES",len(v),"HEX",v.hex())
 if not apply:
  print("DRY_RUN_ONLY")
  return sha(staged)
 backup=path.with_name(path.name+".pre-12509-rods-quiz-"+datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
 if backup.exists():raise FileExistsError(backup)
 backup.write_bytes(raw)
 if sha(backup.read_bytes())!=sha(raw):raise IOError("Backup SHA bad")
 temp=path.with_name(path.name+".12509-rods-quiz-tmp")
 if temp.exists():raise FileExistsError(temp)
 try:
  temp.write_bytes(staged)
  if sha(temp.read_bytes())!=sha(staged):raise IOError("Temp SHA mismatch")
  os.replace(temp,path)
 finally:
  if temp.exists():temp.unlink()
 if sha(path.read_bytes())!=sha(staged):raise IOError("Final SHA mismatch")
 print("BACKUP",backup)
 return sha(staged)

if __name__=="__main__":
 cli=argparse.ArgumentParser()
 cli.add_argument("file",type=Path)
 cli.add_argument("--eve",type=Path,default=Path("gamedata/Eve.emg"))
 cli.add_argument("--apply",action="store_true")
 a=cli.parse_args()
 patch(a.file,a.eve,a.apply)
