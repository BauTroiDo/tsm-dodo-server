"""Prepare Android/Resources for the legacy client, preserving decoded asset bytes.
Usage: python prepare_unity_cdn_20260925.py [Android/Resources directory]
Backs up every changed file; does not change BasePackage or ResourcesPacked.
"""
from pathlib import Path
import sys,json,shutil,datetime
import lz4_packed
from unity_integrity import unity_crc
root=Path(sys.argv[1] if len(sys.argv)>1 else 'cdn/mirror/tsr/2.4.76/Android/Resources').resolve()
mf=root/'ResourceList.dat';j=json.loads(mf.read_text(encoding='utf-8-sig'));plan=[]
# Validate all inputs before making changes.
for name,a in j['bundles'].items():
 f=root/(name+'.unity3d')
 if f.resolve().parent!=root:raise ValueError('Invalid bundle name')
 b=f.read_bytes();raw=b if b.startswith(b'UnityFS\0') else lz4_packed.decompress(b)
 crc=unity_crc(raw)
 if b.startswith(b'UnityFS\0'):
  packed=lz4_packed.compress(raw,high_compression=False)
  assert lz4_packed.decompress(packed)==raw
  plan.append((f,packed))
 a['size']=len(raw);a['crc']=crc
encoded=json.dumps(j,ensure_ascii=False,indent=2).encode('utf-8')
if encoded!=mf.read_bytes():plan.append((mf,encoded))
if plan:
 backup=root.parents[3]/'backups'/('unity-publish-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True)
 for f,b in plan:
  shutil.copy2(f,backup/f.name);tmp=f.with_name(f.name+'.publish-tmp');tmp.write_bytes(b);tmp.replace(f)
 print('Backup:',backup)
print('Validated bundles:',len(j['bundles']),'Changed files:',len(plan))
