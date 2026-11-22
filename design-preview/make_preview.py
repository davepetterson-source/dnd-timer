import re, sys, math
import os
HERE=os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0,HERE)
import gen
html = gen.main_html()
def items(labels, pos, pitch, big, fit):
    R = pitch/0.41888; out=[]
    for i,l in enumerate(labels):
        d=i-pos; a=d*24; rad=math.radians(a)
        if abs(a)>=85: continue
        fs=big-min(abs(d),2)*(5.5 if big>22 else 2.5)
        if fit: fs=min(fs, fit/(len(l)*0.6))
        out.append(dict(label=l,aria=l,tf='translateY(%.1fpx) perspective(300px) rotateX(%.1fdeg)'%(R*math.sin(rad),-a),op='%.2f'%(max(0,math.cos(rad))**1.3),fs='%.1f'%fs,fw='700' if abs(d)<.5 else '500',col='#ffffff' if abs(d)<.5 else '#9fabd8',pick=''))
    return out
DUR=['1min','5min','10min','15min','30min','45min','1hr','1hr 15min','1hr 30min','2hrs','3hrs']
ST=['22:00','22:15','22:30','23:00','23:30','00:00','00:30','01:00']
active = len(sys.argv)>1
ctx = dict(dim='', notActive=not active, isActive=active, showDrum=not active, clockDisp='flex' if active else 'none', clock='01:29:54', caption='of 1hr 30min', clockSize='40',
  startedFixed=active, notStarted=not active, showWheel=not active, startedTime='23:30', startedDay='Today', endTime='01:00', endDay='Tomorrow',
  goCls='end' if active else 'start', isIdle=not active, showStop=active, goLabel='END SLEEP TIMER' if active else 'SCHEDULE SLEEP TIMER',
  dndCls='sel', silentCls='', locked='', isDnd='true', isSilent='false',
  pMed_name='Meditation', pMed_dur='15m', pMed_isSilent=True, pMed_isDnd=False, pMed_hasMusic=True,
  pKip_name='Kip', pKip_dur='1h', pKip_isSilent=False, pKip_isDnd=True, pKip_hasMusic=False,
  pSleep_name='Sleep', pSleep_dur='8h', pSleep_isSilent=True, pSleep_isDnd=False, pSleep_hasMusic=False, pSet_name='Setup',
  durItems=items(DUR,8,44,27,170), stItems=items(ST,4,30,19,0),
  repAria='Repeats Mon to Fri', repDots=[dict(l=l, bd='#b3a8ff' if i<5 else 'rgba(130,150,230,.35)', bg='#6a5cf5' if i<5 else 'rgba(16,26,70,.5)', col='#ffffff' if i<5 else '#7f8bc0') for i,l in enumerate('MTWTFSS')],
  segs=[dict(fill='rgb(%d,%d,255)'%(60+int(130*i/59),200-int(120*i/59)) if i<45 else '#26306a', tf='rotate(%d 137 137)'%(i*6), glow='drop-shadow(0 0 3px rgba(110,140,255,.9))' if i<45 else 'none') for i in range(60)])
def render(t, c):
    def forrep(m):
        lst=c[m.group(1)]; var=m.group(2); body=m.group(3); out=''
        for it in lst:
            out+=re.sub(r'\{\{'+var+r'\.(\w+)\}\}', lambda mm: str(it[mm.group(1)]), body)
        return out
    t=re.sub(r'<sc-for list="\{\{(\w+)\}\}" as="(\w+)"[^>]*>(.*?)</sc-for>', forrep, t, flags=re.S)
    while '<sc-if' in t:
        t=re.sub(r'<sc-if value="\{\{(\w+)\}\}"[^>]*>((?:(?!<sc-if).)*?)</sc-if>', lambda m: m.group(2) if c.get(m.group(1)) else '', t, flags=re.S)
    t=re.sub(r'\{\{\s*(\w+)\s*\}\}', lambda m: str(c.get(m.group(1),'')), t)
    t=re.sub(r' on\w+="[^"]*"','',t)
    return t
css=open(os.path.join(HERE,'preview.css')).read()
page='<!doctype html><html><head><link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet"><style>'+css+gen.CSS+'</style></head><body style="margin:0;background:#000"><div style="position:relative;width:390px;height:740px;overflow:hidden;font-family:Inter,Arial,sans-serif;color:#eef2ff;background:linear-gradient(180deg,#232a6a,#0e1540 30%,#090f2c 60%)">'+'<div style="position:absolute;top:0;left:0;width:390px;height:520px;overflow:hidden"><img src="file://'+os.path.join(HERE,'..','app','src','main','res','drawable-nodpi','bg_moon.jpg')+'" style="position:absolute;top:-348px;left:-127px;width:567px;height:850px"><div style="position:absolute;inset:0;background:linear-gradient(180deg, rgba(9,15,44,.10) 0%, rgba(9,15,44,.40) 45%, rgba(9,15,44,.85) 78%, rgb(9,15,44) 96%)"></div></div>'+render(html,ctx)+'</div></body></html>'
open(os.path.join(HERE,'preview.html'),'w').write(page)
