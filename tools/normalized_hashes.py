import pathlib,hashlib,sys
sys.path.insert(0,'.')
import lz4_packed
root=pathlib.Path('cdn/mirror/tsr/2.4.76/ResourcesPacked')
out=pathlib.Path('tools/normalized-packed-hashes.tsv')
groups={'Jma','ETCJmg','ETCPmg','Ensign','Sty','Unity'}
with out.open('w',encoding='utf-8') as f:
    for g in sorted(groups):
        d=root/g
        if not d.exists(): continue
        for p in sorted(d.rglob('*.lz4')):
            raw=lz4_packed.decompress(p.read_bytes())
            f.write(f'{p.relative_to(root).as_posix()[:-4]}\t{len(raw)}\t{hashlib.sha256(raw).hexdigest().upper()}\n')
print(out, out.stat().st_size)
