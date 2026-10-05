#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Real HTTP/MySQL acceptance with synthetic TEST data; private credentials never printed."""
from pathlib import Path
import argparse,concurrent.futures,http.cookiejar,json,os,secrets,urllib.request,urllib.error,uuid
ROOT=Path(__file__).resolve().parents[1];STATE=ROOT/'output/qa-state.json';BASE=os.environ.get('TEST_URL','http://127.0.0.1:8126').rstrip('/');checks=0

def check(ok,message):
    """Verify invariant without emitting a credential or response body."""
    global checks
    checks+=1
    if not ok:raise AssertionError(message)

def key():return str(uuid.uuid4())

class Client:
    """Isolated cookie session with real CSRF on writes."""
    def __init__(self,name,password):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.request('/auth/csrf');self.profile=self.request('/auth/login','POST',{'username':name,'password':password})
    def request(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf and hasattr(self,'csrf'):headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(BASE+'/api'+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        check(actual in status if isinstance(status,tuple) else actual==status,f'{method} {path}: expected {status}, got {actual}, code={value.get("code") if isinstance(value,dict) else None}')
        if code:check(value.get('code')==code,path+': wrong error')
        return value

def command(who,case,action,extra=None,status=200,code=None,body=None):
    data=body or {'requestKey':key(),'version':admin.request(f'/shipments/{case}')['shipment']['version'],'note':'TEST isolation evidence checked'}
    data.update(extra or {});return who.request(f'/shipments/{case}/commands/{action}','POST',data,status,code)

def capture(state):
    paths=['/admin/users','/admin/roles','/admin/departments','/admin/permissions','/admin/menus','/admin/settings','/admin/dictionaries','/options','/dashboard','/shipments','/agreements']
    paths += [f'/shipments/{i}' for i in state['caseIds']]+[f'/agreements/{i}' for i in state['agreementIds']]
    return {p:admin.request(p) for p in paths}

parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--allow-test-writes',action='store_true');parser.add_argument('--verify',action='store_true');parser.add_argument('--capture',action='store_true');args=parser.parse_args()
env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'));admin=Client('admin',env['ADMIN_PASSWORD'])
if args.verify or args.capture:
    state=json.loads(STATE.read_text());current=capture(state)
    if args.capture:state['caseIds']=[s['id'] for s in admin.request('/shipments?size=100')['items']];state['agreementIds']=[a['id'] for a in admin.request('/agreements?size=100')['items']];current=capture(state);state['responses']=current;STATE.write_text(json.dumps(state,ensure_ascii=False));print(json.dumps({'mode':'capture','responses':len(current),'result':'PASS'}));raise SystemExit
    for p,expected in state['responses'].items():check(current[p]==expected,'Persistence mismatch: '+p)
    for name,username in state['users'].items():check(Client(username,state['password']).request('/auth/me')['username']==username,'Actor missing: '+name)
    print(json.dumps({'mode':'persistence','assertions':checks,'responsesMatched':len(current),'result':'PASS'}));raise SystemExit
if not args.allow_test_writes:raise SystemExit('Use --allow-test-writes only with a disposable isolated database.')
if STATE.exists():raise SystemExit('QA state exists; use --verify or a new isolated test database.')
suffix=secrets.token_hex(4);password='Aa9'+secrets.token_urlsafe(24)
roles={r['name']:r['id'] for r in admin.request('/admin/roles')}
dep=admin.request('/admin/departments','POST',{'name':'TEST 箱务团队 '+suffix})['id'];outside=admin.request('/admin/departments','POST',{'name':'TEST 外部团队 '+suffix})['id']
customer=admin.request('/customers','POST',{'name':'TEST 进口货主 '+suffix,'departmentId':dep,'enabled':True})['id'];otherCustomer=admin.request('/customers','POST',{'name':'TEST 另一货主 '+suffix,'departmentId':dep,'enabled':True})['id']
selfRole=admin.request('/admin/roles','POST',{'name':'TEST 本人箱务 '+suffix,'scope':'SELF','permissions':['shipment.read','shipment.write','agreement.read','dashboard','export']})['id'];users={};clients={};userIds={}
for name,role,department,binding in [('ops',roles['箱务运营'],dep,None),('review',roles['独立复核'],dep,None),('finance',roles['财务'],dep,None),('finance2',roles['财务'],dep,None),('customer',roles['货主客户'],dep,customer),('other',roles['货主客户'],dep,otherCustomer),('outside',roles['箱务运营'],outside,None),('bound',roles['管理员'],dep,otherCustomer),('self',selfRole,dep,None)]:
    username='test-'+name+'-'+suffix;users[name]=username;userIds[name]=admin.request('/admin/users','POST',{'username':username,'displayName':'TEST '+name,'password':password,'roleId':role,'departmentId':department,'customerId':binding,'enabled':True})['id'];clients[name]=Client(username,password)
ops=clients['ops'];review=clients['review'];finance=clients['finance'];finance2=clients['finance2'];owner=clients['customer'];cases=[];agreements=[]

def newAgreement(mode='SEPARATE',calendar='CALENDAR',holidays='',freeCombined=5):
    """Create synthetic contract then independently approve it."""
    value={'requestKey':key(),'reference':'TEST-AG-'+key()[:8],'customerId':customer,'carrier':'TEST 承运方','port':'TEST 上海港','containerType':'20GP','zone':'Asia/Shanghai','mode':mode,'calendar':calendar,'includeStart':True,'includeEnd':False,'holidays':holidays,'validFrom':'2026-01-01','validTo':'2026-12-31','freeDemurrage':3,'freeDetention':2,'freeCombined':freeCombined,'tierDays':2,'demurrageFirst':'100.00','demurrageAfter':'200.00','detentionFirst':'50.00','detentionAfter':'100.00','combinedFirst':'100.00','combinedAfter':'200.00'}
    a=ops.request('/agreements','POST',value);agreements.append(a['id']);ops.request('/agreements/'+str(a['id']),'PUT',{**value,'requestKey':key(),'version':a['version']})
    a=admin.request('/agreements/'+str(a['id']));review.request(f'/agreements/{a["id"]}/commands/approve','POST',{'requestKey':key(),'version':a['version'],'note':'TEST independently verified contract'})
    ops.request('/agreements/'+str(a['id']),'PUT',{**value,'requestKey':key(),'version':a['version']+1},409,'INVALID_STATE')
    return a['id']

def newCase(agreement,arrival='2026-09-01'):
    """One container per case with real persisted facts."""
    n=len(cases)+1;value={'requestKey':key(),'reference':f'TEST-BOX-{n:03d}-{suffix}','billOfLading':f'TEST-BL-{n:03d}-{suffix}','containerNo':f'TEST{n:07d}','agreementId':agreement,'arrival':arrival};s=ops.request('/shipments','POST',value)['shipment'];cases.append(s['id']);return s['id']

def returned(case,gate='2026-09-06',back='2026-09-10'):
    command(ops,case,'submit');command(ops,case,'gate-out',{'date':gate});command(ops,case,'return',{'date':back})

def billed(case,amount='300.00'):
    command(ops,case,'calculate');command(review,case,'review');command(finance,case,'bill',{'amount':amount,'reference':'TEST-BILL-'+key()[:8]})

a=newAgreement();c=newCase(a)
ops.request('/shipments','POST',{'requestKey':key(),'reference':'TEST duplicate '+key(),'billOfLading':admin.request(f'/shipments/{c}')['shipment']['billOfLading'],'containerNo':'TEST0000001','agreementId':a,'arrival':'2026-09-01'},409,'DUPLICATE_CONTAINER_CASE')
ops.request('/shipments','POST',{'requestKey':key(),'reference':key(),'billOfLading':key(),'containerNo':'bad','agreementId':a,'arrival':'2026-09-01'},400,'INVALID_CONTAINER_NO')
for name in ['other','outside','self','bound']:
    clients[name].request(f'/shipments/{c}',status=403,code='OUT_OF_SCOPE');clients[name].request(f'/shipments/{c}/report.json',status=403,code='OUT_OF_SCOPE')
clients['bound'].request('/admin/users',status=403,code='STAFF_ONLY');clients['bound'].request('/audit',status=403,code='STAFF_ONLY');check(clients['other'].request('/dashboard')['shipments']==0,'Customer stats leak')
owner.request(f'/shipments/{c}/commands/submit','POST',{'requestKey':key(),'version':1},403,'FORBIDDEN')
ops.request(f'/shipments/{c}/commands/submit','POST',{'requestKey':key(),'version':1},403,csrf=False)
retry={'requestKey':key(),'version':1,'note':'TEST exact retry'};command(ops,c,'submit',body=retry.copy());command(ops,c,'submit',body=retry.copy());command(ops,c,'submit',body={**retry,'note':'changed'},status=409,code='REQUEST_KEY_REUSED')
command(ops,c,'return',status=409,code='MISSING_GATE_OUT');command(ops,c,'gate-out',{'date':'2026-08-31'},400,'INVALID_DATE');command(ops,c,'gate-out',{'date':'2099-01-01'},400,'INVALID_DATE');command(ops,c,'gate-out',{'date':'2026-09-06'});command(ops,c,'return',{'date':'2026-09-10'})
command(owner,c,'extend',{'phase':'DEMURRAGE','days':2});command(ops,c,'calculate',status=409,code='PENDING_EXTENSION');extension=admin.request(f'/shipments/{c}')['extensions'][0]['id'];command(review,c,'approve-extension',{'extensionId':extension});billed(c,'100.00');check(admin.request(f'/shipments/{c}')['calculation']['total']==100,'Approved extension did not change charge');command(owner,c,'confirm')
command(finance,c,'pay',{'amount':'100.01','reference':key()},409,'OVERPAYMENT');body={'requestKey':key(),'version':admin.request(f'/shipments/{c}')['shipment']['version'],'amount':'100.00','reference':'TEST-PAY-'+key()[:8],'note':'TEST offline payment'}
with concurrent.futures.ThreadPoolExecutor(2) as pool:
    f1=pool.submit(command,finance,c,'pay',None,(200,409),None,body.copy());f2=pool.submit(command,finance2,c,'pay',None,(200,409),None,{**body,'requestKey':key(),'reference':'TEST-PAY-'+key()[:8]});r1=f1.result();r2=f2.result()
check(sum('shipment' in r for r in [r1,r2])==1,'Competing payment both committed');d=admin.request(f'/shipments/{c}');check(d['paid']==100 and len(d['payments'])==1,'Duplicate payment');payment=d['payments'][0];payer=finance if payment['paidBy']==userIds['finance'] else finance2;reverser=finance2 if payer is finance else finance
command(payer,c,'reverse',{'paymentId':payment['id']},409,'INDEPENDENT_REVIEW_REQUIRED');command(reverser,c,'reverse',{'paymentId':payment['id']});check(admin.request(f'/shipments/{c}')['paid']==0,'Reversal balance');command(finance,c,'pay',{'amount':'100.00','reference':'TEST-PAY-'+key()[:8]})
b=newCase(a);returned(b);billed(b)
live=newCase(a);command(ops,live,'submit');p=owner.request(f'/shipments/{live}/preview?asOf=2026-09-10');check(not p['final'],'Preview incorrectly final');command(ops,live,'gate-out',{'date':'2026-09-06'});owner.request(f'/shipments/{live}/preview?asOf=2026-09-05',status=400,code='INVALID_DATE')
disputed=newCase(a);returned(disputed);billed(disputed);command(owner,disputed,'dispute');command(finance,disputed,'pay',{'amount':'10.00','reference':key()},409,'INVALID_STATE')
combined=newAgreement('COMBINED','WORKDAY','2026-09-07',2);work=newCase(combined,'2026-09-04');returned(work,'2026-09-08','2026-09-10');billed(work,'100.00');calc=admin.request(f'/shipments/{work}')['calculation'];check(calc['total']==100,'Combined working calendar calculation');check(calc['phases'][0]['lastFreeDate']=='2026-09-08','Working cutoff');command(owner,work,'confirm')
cancelled=newCase(a);command(ops,cancelled,'cancel');zero=newCase(a);returned(zero,'2026-09-01','2026-09-01');billed(zero,'0.00');command(owner,zero,'confirm');check(admin.request(f'/shipments/{zero}')['shipment']['status']=='PAID','Zero bill not settled')
ops.request('/shipments?page=-1',status=400,code='INVALID_PAGE');ops.request('/shipments?size=101',status=400,code='INVALID_PAGE');ops.request('/shipments?sort=sql',status=400,code='INVALID_PAGE');check(ops.request('/shipments?search=TEST-BOX-001')['total']==1,'Search');check(owner.request('/shipments?status=ACTIVE')['total']==1,'Status filter');check(owner.request('/shipments?size=2&page=0')['size']==2,'Pagination')
check(admin.request('/dashboard')['confirmed']==200,'Confirmed accounting');check(admin.request('/dashboard')['paid']==100,'Paid accounting');admin.request('/customers/'+str(customer),'DELETE',status=409,code='CONFLICT');admin.request('/customers/'+str(customer),'PUT',{'name':'TEST moved','departmentId':outside,'enabled':True},409,'IMMUTABLE_DEPARTMENT')
check('passwordHash' not in json.dumps(admin.request('/admin/users')),'Password hash leak');check('zhuatech' not in json.dumps(owner.request(f'/shipments/{c}/report.json')),'Advertising in export')
STATE.parent.mkdir(exist_ok=True);state={'password':password,'users':users,'userIds':userIds,'caseIds':cases,'agreementIds':agreements,'customerId':customer,'departmentId':dep};state['responses']=capture(state)
fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:json.dump(state,out,ensure_ascii=False)
print(json.dumps({'mode':'real-http-mysql','assertions':checks,'cases':len(cases),'agreements':len(agreements),'result':'PASS'}))
