import re,subprocess,sys
def sh(*a): return subprocess.run(a,capture_output=True,text=True,check=True).stdout
def ls(r): return sh('git','ls-tree','-r','--name-only',r).splitlines()
def show(r,p): return subprocess.run(['git','show',f'{r}:{p}'],capture_output=True).stdout.decode('utf-8','replace')
V='60a5ac406'
def norm(p):
    p=re.sub(r'\$\w+<[^>]*>','{}',p)
    p=re.sub(r'\{\*?\w*(:[^}]*)?\}','{}',p)
    p=re.sub(r'\*\w+','{}',p)
    p=re.sub(r':\w+','{}',p)
    p=re.sub(r'/+$','',p) or '/'
    return p
v=set()
for l in show(V,'conf/routes').splitlines():
    m=re.match(r'^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+(\S+)\s+(\S+)',l)
    if m and not m.group(3).startswith('controllers.Assets'): v.add((m.group(1),norm(m.group(2))))
ANN=re.compile(r'@(Get|Post|Put|Patch|Delete|Request)Mapping\s*(?:\(([^)]*)\))?')
def paths(arg):
    if not arg: return ['']
    m=re.search(r'(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")|^\s*(\{[^}]*\}|"[^"]*")',arg)
    s=(m.group(1) or m.group(2)) if m else ''
    return re.findall(r'"([^"]*)"',s) or ['']
def meth(kind,arg):
    if kind!='Request': return [kind.upper()]
    ms=re.findall(r'RequestMethod\.(\w+)',arg or '')
    return ms or ['ANY']
def collect(b):
    out=set(); anyr=set()
    for f in ls(b):
        if not (f.endswith('.kt') and f.startswith('src/main/kotlin')): continue
        t=show(b,f)
        if '@Controller' not in t and '@RestController' not in t: continue
        cm=re.search(r'\n(?:open |abstract )?class\s+\w+',t)
        cpos=cm.start() if cm else len(t)
        prefixes=['']
        for m in ANN.finditer(t[:cpos]):
            if m.group(1)=='Request': prefixes=paths(m.group(2))
        for m in ANN.finditer(t[cpos:]):
            for pp in paths(m.group(2)):
                for pr in prefixes:
                    full=norm(('/'+(pr.strip('/')+'/'+pp.strip('/')).strip('/')) if (pr or pp) else '/')
                    for me in meth(m.group(1),m.group(2)):
                        (anyr if me=='ANY' else out).add((me,full))
    return out,anyr
for b in ('next','next2'):
    s,a=collect(b)
    anyp={p for _,p in a}
    hit={x for x in v if x in s}
    weak={x for x in v if x not in s and x[1] in anyp}
    pathonly={x for x in v if x not in s and x not in weak and any(p==x[1] for _,p in s)}
    extra={x for x in s if x not in v and x[1] not in {p for _,p in v}}
    print(b,'v1.6 라우트',len(v),'| 메서드+경로 일치',len(hit),'| 메서드 미지정 일치',len(weak),'| 경로만 일치(메서드 다름)',len(pathonly),'| 없음',len(v)-len(hit)-len(weak)-len(pathonly),'| v1.6에 없는 경로',len({p for _,p in extra}))
print('--- next2 없음')
s,a=collect('next2')
for x in sorted(v):
    if x not in s: print(x)
s1,_=collect('next')
miss=[x for x in sorted(v) if x not in s1]
print('--- next 없음 예(앞 12)',miss[:12])
