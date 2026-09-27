from pathlib import Path
import struct,zlib,lzma,lz4.block

def unity_crc(data):
 pos=data.index(b'\0')+1
 version=struct.unpack_from('>I',data,pos)[0];pos+=4
 pos=data.index(b'\0',pos)+1;pos=data.index(b'\0',pos)+1
 total,cs,us,flags=struct.unpack_from('>QIII',data,pos);pos+=20
 if total!=len(data):raise ValueError('UnityFS total size mismatch')
 if version>=7:pos=(pos+15)&~15
 def decode(b,n,f):
  codec=f&63
  if codec==0:r=b
  elif codec in (2,3):r=lz4.block.decompress(b,uncompressed_size=n)
  else:raise ValueError('Unsupported UnityFS codec '+str(codec))
  if len(r)!=n:raise ValueError('UnityFS block size mismatch')
  return r
 info=decode(data[-cs:] if flags&128 else data[pos:pos+cs],us,flags)
 if not flags&128:pos+=cs
 if flags&512:pos=(pos+15)&~15
 count=struct.unpack_from('>I',info,16)[0];crc=0
 for i in range(count):
  u,c,f=struct.unpack_from('>IIH',info,20+10*i);raw=decode(data[pos:pos+c],u,f);pos+=c;crc=zlib.crc32(raw,crc)
 return crc
if __name__=='__main__':
 import json,lz4_packed
 root=Path('cdn/mirror/tsr/2.4.76/Android/Resources');j=json.loads((root/'ResourceList.dat').read_text(encoding='utf-8-sig'));issues=[]
 for name,a in j['bundles'].items():
  try:
   b=(root/(name+'.unity3d')).read_bytes();raw=lz4_packed.decompress(b);crc=unity_crc(raw)
   if crc!=a['crc'] or len(raw)!=a['size']:issues.append(dict(name=name,crc=crc,expected_crc=a['crc'],size=len(raw),expected_size=a['size']))
  except Exception as e:issues.append(dict(name=name,error=str(e)))
 Path('unity-integrity-audit.json').write_text(json.dumps(issues,indent=2));print(json.dumps(issues))
