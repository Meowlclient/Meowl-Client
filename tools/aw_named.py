import sys,re,glob
cls={};meth={};fld={}
for line in open('libs/mc/mappings.tiny',encoding='utf-8'):
    p=line.rstrip('\n').split('\t')
    if p[0]=='c': cls[p[2]]=p[3]; cur=p[2]
    elif len(p)>1 and p[0]=='' and p[1]=='m': meth[p[4]]=p[5]
    elif len(p)>1 and p[0]=='' and p[1]=='f': fld[p[4]]=p[5]
def mdesc(d): return re.sub(r'L([^;]+);',lambda m:'L'+cls.get(m.group(1),m.group(1))+';',d)
out=['accessWidener\tv2\tnamed']
for f in glob.glob('recon/aw/*.accesswidener'):
    for line in open(f):
        line=line.split('#')[0].strip()
        if not line or line.startswith('accessWidener'): continue
        p=line.split()
        kind=p[0].replace('transitive-','')
        if p[1]=='class': out.append(f'{kind}\tclass\t{cls.get(p[2],p[2])}')
        elif p[1]=='method': out.append(f'{kind}\tmethod\t{cls.get(p[2],p[2])}\t{meth.get(p[3],p[3])}\t{mdesc(p[4])}')
        elif p[1]=='field': out.append(f'{kind}\tfield\t{cls.get(p[2],p[2])}\t{fld.get(p[3],p[3])}\t{mdesc(p[4])}')
open('recon/all.accesswidener','w').write('\n'.join(dict.fromkeys(out))+'\n')
print(len(out))
