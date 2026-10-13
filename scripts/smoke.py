"""Start the built JAR on an available local port and check HTTP behavior."""
import json, socket, subprocess, time, urllib.error, urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1]
with socket.socket() as sock:
    sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
log=(root/'target/smoke-server.log').open('w')
p=subprocess.Popen(['java','-jar',str(root/'target/payment-event-lab-0.0.1-SNAPSHOT.jar'),f'--server.port={port}'],stdout=log,stderr=subprocess.STDOUT)
def req(path,body=None,key=None):
    headers={'Content-Type':'application/json'}
    if key:headers['Idempotency-Key']=key
    r=urllib.request.Request(f'http://127.0.0.1:{port}'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    try:
        with urllib.request.urlopen(r,timeout=2) as x:return x.status,json.load(x)
    except urllib.error.HTTPError as x:return x.code,json.load(x)
try:
    for i in range(100):
        try:
            assert req('/api/health')[0]==200;break
        except Exception:
            if p.poll() is not None:raise RuntimeError('Server exited; inspect target/smoke-server.log')
            time.sleep(.1)
    else:raise RuntimeError('Server did not become healthy')
    body={'accountId':'demo-1','amountMinor':100000,'currency':'EUR','country':'DE'}
    checks=[]
    def check(name,actual,expected):
        assert actual==expected,(name,actual,expected);checks.append(name)
    status,result=req('/api/events',body,'demo-1');check('create',status,201)
    check('read ID',req('/api/events/'+result['event']['id'])[0],200)
    check('retry',req('/api/events',body,'demo-1')[0],200)
    check('conflict',req('/api/events',dict(body,amountMinor=500),'demo-1')[0],409)
    for name,invalid in [('negative',dict(body,amountMinor=-1)),('currency',dict(body,currency='USD')),('missing',{}),('unknown field',dict(body,unexpected=True))]:
        check(name,req('/api/events',invalid,'bad-'+name.replace(' ','_'))[0],400)
    check('missing key',req('/api/events',body)[0],400)
    check('invalid UUID',req('/api/events/not-a-uuid')[0],400)
    check('not found',req('/api/events/00000000-0000-0000-0000-000000000000')[0],404)
    check('list size',len(req('/api/events')[1]),1)
    for i in range(100):
        audits=req('/api/audit')[1]
        if audits:break
        time.sleep(.01)
    check('async audit',len(audits),1);check('risk decision',audits[0]['decision'],'REVIEW')
    print(json.dumps({'passed':len(checks),'checks':checks},indent=2))
finally:
    p.terminate();p.wait(timeout=10);log.close()
