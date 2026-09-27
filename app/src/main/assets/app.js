const RUB = n => new Intl.NumberFormat('ru-RU').format(Math.round(Number(n)||0)) + ' ₽';
const KEY_TRIPS='driver_salary_trips_v2';
const OLD_KEY='driver_salary_trips_v1';
const OLD_ANDROID_KEY='zarplata_android_v1';
const KEY_RATES='driver_salary_rates_v2_3';
const OLD_RATES='driver_salary_rates_v1';
const defaultRates=TariffEngine.defaultConfig();
const $=id=>document.getElementById(id);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const nativeAvailable=()=>typeof window.AndroidData!=='undefined';
const ANALYTICS_CONSENT_KEY='truk_wallet_analytics_consent_v1';
function analyticsAllowed(){return localStorage.getItem(ANALYTICS_CONSENT_KEY)==='yes'}
function analyticsConfigured(){
  if(!nativeAvailable()||typeof AndroidData.analyticsConfigured!=='function')return false;
  try{return !!AndroidData.analyticsConfigured()}catch(e){return false}
}
function trackUsage(eventName,properties={}){
  if(!analyticsAllowed()||!nativeAvailable()||typeof AndroidData.trackEvent!=='function')return;
  try{AndroidData.trackEvent(eventName,JSON.stringify(properties||{}))}catch(e){}
}
function refreshAnalyticsUi(){
  const opt=$('analyticsOptIn'),status=$('analyticsStatus');
  const allowed=analyticsAllowed(),configured=analyticsConfigured();
  if(opt)opt.checked=allowed;
  if(status){
    status.textContent=!allowed?'Статус: выключена':configured?'Статус: активна':'Статус: готова, требуется подключение сервиса аналитики';
  }
}
function setAnalyticsConsent(enabled){
  localStorage.setItem(ANALYTICS_CONSENT_KEY,enabled?'yes':'no');
  const modal=$('analyticsConsentModal');if(modal)modal.classList.remove('show');
  refreshAnalyticsUi();
  if(enabled)trackUsage('analytics_consent_granted',{source:'app'});
}
function initAnalyticsUi(){
  const saved=localStorage.getItem(ANALYTICS_CONSENT_KEY);
  const opt=$('analyticsOptIn'),allow=$('analyticsAllow'),decline=$('analyticsDecline'),modal=$('analyticsConsentModal'),test=$('analyticsTest');
  if(opt)opt.onchange=()=>{setAnalyticsConsent(opt.checked);trackUsage('analytics_setting_changed',{enabled:opt.checked})};
  if(allow)allow.onclick=()=>setAnalyticsConsent(true);
  if(decline)decline.onclick=()=>setAnalyticsConsent(false);
  if(test)test.onclick=()=>{
    const status=$('analyticsStatus');
    if(!analyticsAllowed()){
      if(status)status.textContent='Статус: сначала включите аналитику';
      return
    }
    if(!analyticsConfigured()||!nativeAvailable()||typeof AndroidData.sendAnalyticsTest!=='function'){
      if(status)status.textContent='Статус: AppMetrica не настроена';
      return
    }
    try{
      const result=AndroidData.sendAnalyticsTest();
      if(status)status.textContent=result&&String(result).startsWith('sent:')
        ?'Статус: тест отправлен · SDK '+String(result).slice(5)
        :'Статус: ошибка отправки теста';
    }catch(e){
      if(status)status.textContent='Статус: ошибка отправки теста'
    }
  };
  refreshAnalyticsUi();
  if(saved===null&&modal)modal.classList.add('show');
  if(saved==='yes'){
    trackUsage('app_open',{launch:true});
    setTimeout(()=>trackUsage('app_active',{after_seconds:3}),3000);
  }
}

function importBackupState(raw){
  try{
    if(!raw) return false;
    const d=JSON.parse(raw);
    if(Array.isArray(d.trips)) localStorage.setItem(KEY_TRIPS,JSON.stringify(d.trips));
    if(d.rates) localStorage.setItem(KEY_RATES,JSON.stringify(d.rates));
    return Array.isArray(d.trips) || !!d.rates;
  }catch(e){ return false; }
}
function restorePersistentData(){
  const hasTrips=!!localStorage.getItem(KEY_TRIPS);
  const hasRates=!!localStorage.getItem(KEY_RATES);
  if(hasTrips && hasRates) return;
  if(!nativeAvailable()) return;
  let restored=false;
  try{ restored=importBackupState(AndroidData.getState()); }catch(e){}
  if(!restored){
    try{ restored=importBackupState(AndroidData.getExternalBackup()); }catch(e){}
  }
}
restorePersistentData();

function persistNativeBackup(){
  if(!nativeAvailable()) return;
  try{
    const state={
      version:4,
      savedAt:new Date().toISOString(),
      rates:JSON.parse(localStorage.getItem(KEY_RATES)||'{}'),
      trips:JSON.parse(localStorage.getItem(KEY_TRIPS)||'[]')
    };
    AndroidData.saveState(JSON.stringify(state));
  }catch(e){}
}


if(!localStorage.getItem(KEY_TRIPS) && localStorage.getItem(OLD_KEY)) localStorage.setItem(KEY_TRIPS,localStorage.getItem(OLD_KEY));
if(!localStorage.getItem(KEY_TRIPS) && localStorage.getItem(OLD_ANDROID_KEY)){
  try{
    const oldAndroid=JSON.parse(localStorage.getItem(OLD_ANDROID_KEY)||'[]');
    const source=Array.isArray(oldAndroid)?oldAndroid:(Array.isArray(oldAndroid.trips)?oldAndroid.trips:[]);
    const migratedAndroidTrips=source.map((t,i)=>({
      ...t,
      id:Number(t.id)||Date.now()+i,
      date:t.date||todayLocal(),
      weight:['2-3','3-4','4-5'].includes(t.weight)?t.weight:'2-3',
      mileage:Number(t.mileage)||0,
      noLoader:!!t.noLoader,
      secondTrip:!!t.secondTrip,
      balloons:Number(t.balloons)||0,
      racks:Number(t.racks)||0,
      manualExtra:Number(t.manualExtra??t.extra)||0,
      comment:String(t.comment||''),
      total:Number(t.total)||0
    }));
    if(migratedAndroidTrips.length) localStorage.setItem(KEY_TRIPS,JSON.stringify(migratedAndroidTrips));
  }catch(e){}
}
if(!localStorage.getItem(KEY_RATES)){
  let prev={};
  try{
    prev=JSON.parse(
      localStorage.getItem('driver_salary_rates_v2_1') ||
      localStorage.getItem('driver_salary_rates_v2') ||
      localStorage.getItem(OLD_RATES) || '{}'
    )
  }catch(e){}
  localStorage.setItem(KEY_RATES,JSON.stringify(TariffEngine.normalizeConfig(prev)));
}else{
  try{
    const normalized=TariffEngine.normalizeConfig(JSON.parse(localStorage.getItem(KEY_RATES)||'{}'));
    localStorage.setItem(KEY_RATES,JSON.stringify(normalized));
  }catch(e){
    localStorage.setItem(KEY_RATES,JSON.stringify(TariffEngine.defaultConfig()));
  }
}

const loadTrips=()=>{
  try{const x=JSON.parse(localStorage.getItem(KEY_TRIPS)||'[]');return Array.isArray(x)?x:[]}catch(e){return []}
};
const saveTrips=a=>{localStorage.setItem(KEY_TRIPS,JSON.stringify(Array.isArray(a)?a:[]));persistNativeBackup();};
const loadRates=()=>{
  try{return TariffEngine.normalizeConfig(JSON.parse(localStorage.getItem(KEY_RATES)||'{}'))}
  catch(e){return TariffEngine.defaultConfig()}
};
const saveRatesObj=r=>{localStorage.setItem(KEY_RATES,JSON.stringify(TariffEngine.normalizeConfig(r)));persistNativeBackup();};

function todayLocal(){const d=new Date(),z=d.getTimezoneOffset()*60000;return new Date(d-z).toISOString().slice(0,10)}
$('date').value=todayLocal();
const val=id=>{const el=$(id);return el?(Number(el.value)||0):0};

function showPage(id){
 document.querySelectorAll('.page').forEach(p=>p.classList.remove('active'));
 $(id).classList.add('active');
 document.querySelectorAll('.navbtn').forEach(b=>b.classList.toggle('active',b.dataset.page===id));
 document.getElementById('fab').style.display=id==='add'?'none':'block';
 if(id==='home') renderHome();
 if(id==='history') {fillMonths();renderHistory();}
 if(id==='calendarPage') renderCalendar();
 if(id==='stats') renderStats();
 if(id==='settings') populateRates();
 trackUsage('screen_view',{screen:id});
 window.scrollTo({top:0,behavior:'smooth'});
}
window.showPage=showPage;
document.querySelectorAll('.navbtn').forEach(b=>b.onclick=()=>showPage(b.dataset.page));
$('fab').onclick=()=>showPage('add');

function extraInputId(extra){
 if(extra.id==='noLoader')return 'noLoader';
 if(extra.id==='secondTrip')return 'secondTrip';
 if(extra.id==='balloon')return 'balloons';
 if(extra.id==='rack')return 'racks';
 return 'extra_'+extra.id
}
function renderShiftTariffs(){
 const cfg=loadRates(),select=$('weight'),box=$('dynamicExtras');
 const current=select?select.value:'';
 if(select){
   if(cfg.bases.length){
     select.innerHTML=cfg.bases.map(b=>`<option value="${esc(b.id)}">${esc(b.name)} · ${RUB(b.rate)}</option>`).join('');
     select.value=cfg.bases.some(b=>b.id===current)?current:cfg.bases[0].id;
   }else{
     select.innerHTML='<option value="">Без базового тарифа</option>';
     select.value='';
   }
 }
 if(!box)return;
 const controls=cfg.extras.filter(e=>e.kind!=='mileage').map(e=>{
   const id=extraInputId(e);
   if(e.kind==='quantity'){
     return `<div class="dynamic-extra"><div class="dynamic-extra-qty"><div><b>${esc(e.name)}</b><small>${RUB(e.rate)} за единицу</small></div><div><label>Количество</label><input id="${esc(id)}" data-extra-id="${esc(e.id)}" type="number" min="0" step="1" value="0" inputmode="numeric"></div></div></div>`;
   }
   return `<div class="dynamic-extra"><div class="dynamic-extra-row"><div><b>${esc(e.name)}</b><small>Доплата ${e.rate>=0?'+':''}${RUB(e.rate)}</small></div><label class="switch"><input id="${esc(id)}" data-extra-id="${esc(e.id)}" type="checkbox"><span class="slider"></span></label></div></div>`;
 }).join('');
 const mileage=cfg.extras.filter(e=>e.kind==='mileage');
 const mileageHtml=mileage.length?`<div class="auto-extra"><b>Автоматически по пробегу</b><br>${mileage.map(e=>{
   const base=e.baseId?(cfg.bases.find(b=>b.id===e.baseId)?.name||'выбранный тариф'):'любой тариф';
   return `${esc(e.name)}: от ${Math.round(e.threshold)} км · ${base} · ${e.rate>=0?'+':''}${RUB(e.rate)}`;
 }).join('<br>')}</div>`:'';
 box.innerHTML=controls+ mileageHtml;
 box.querySelectorAll('input[data-extra-id]').forEach(el=>{
   el.addEventListener('input',calculate);el.addEventListener('change',calculate)
 });
}
function readShiftValues(){
 const cfg=loadRates(),values={};
 cfg.extras.filter(e=>e.kind!=='mileage').forEach(e=>{
   const el=$(extraInputId(e));
   if(!el)return;
   values[e.id]=e.kind==='toggle'?!!el.checked:(Number(el.value)||0)
 });
 return values
}
function getShiftCalculation(){
 return TariffEngine.compute(loadRates(),{
   baseId:$('weight')?$('weight').value:'',
   mileage:val('mileage'),
   values:readShiftValues(),
   manualExtra:val('manualExtra')
 })
}
function calculate(){
 const result=getShiftCalculation();
 $('calcTotal').textContent=RUB(result.total);
 $('breakdown').innerHTML=result.parts.length?result.parts.map(p=>{
   const sign=p.kind==='base'?'':(p.amount>=0?'+':'');
   return `${esc(p.name)}: ${sign}${RUB(p.amount)}`
 }).join('<br>'):'Тарифы не выбраны';
 return result.total
}
['weight','mileage','manualExtra'].forEach(id=>{
 const el=$(id);if(el){el.addEventListener('input',calculate);el.addEventListener('change',calculate)}
});

function resetForm(){
 $('date').value=todayLocal();
 $('mileage').value='';
 $('manualExtra').value=0;
 $('comment').value='';
 renderShiftTariffs();
 const cfg=loadRates();if($('weight'))$('weight').value=cfg.bases[0]?.id||'';
 calculate()
}
$('saveTrip').onclick=()=>{
 const result=getShiftCalculation(),values=readShiftValues(),base=result.base;
 const trip={
   id:Date.now(),date:$('date').value||todayLocal(),
   baseId:base?.id||'',baseLabel:base?.name||'Без базового тарифа',weight:base?.id||'',
   mileage:val('mileage'),extras:values,extraSummary:result.applied,
   noLoader:!!values.noLoader,secondTrip:!!values.secondTrip,
   balloons:Number(values.balloon)||0,racks:Number(values.rack)||0,
   manualExtra:val('manualExtra'),comment:$('comment').value.trim(),
   total:result.total,ratesSnapshot:loadRates()
 };
 const trips=loadTrips();trips.push(trip);saveTrips(trips);
 trackUsage('shift_saved',{method:'manual_or_voice',has_extras:result.applied.length>0});
 resetForm();renderHome();fillMonths();renderCalendar();renderStats();showPage('home');
 setTimeout(()=>alert('Смена сохранена'),100)
};

function monthKey(d){return String(d).slice(0,7)}
function currentMonthKey(){return todayLocal().slice(0,7)}

function groupByDate(trips){
 const m={};trips.forEach(t=>{m[t.date]=(m[t.date]||0)+(Number(t.total)||0)});return m
}

function tripBaseLabel(t){
 if(t&&t.baseLabel)return String(t.baseLabel);
 const legacy={'2-3':'до 3 т','3-4':'3–4 т','4-5':'4–5 т'};
 return legacy[t&&t.weight]||String((t&&t.weight)||'Без базового тарифа')
}
function tripExtrasList(t){
 if(Array.isArray(t&&t.extraSummary))return t.extraSummary.filter(x=>x&&x.name).map(x=>String(x.name));
 const out=[];
 if(t&&t.noLoader)out.push('Без грузчика');
 if(t&&t.secondTrip)out.push('Второй рейс');
 if(Number(t&&t.balloons)>0)out.push('Баллоны ×'+Number(t.balloons));
 if(Number(t&&t.racks)>0)out.push('Стойки ×'+Number(t.racks));
 return out
}
function tripHasExtras(t){
 return tripExtrasList(t).length>0||Number(t&&t.manualExtra)!==0
}

function renderHome(){
 const all=loadTrips(),cur=all.filter(t=>monthKey(t.date)===currentMonthKey()),today=all.filter(t=>t.date===todayLocal());
 const sum=cur.reduce((a,t)=>a+(+t.total||0),0),todaySum=today.reduce((a,t)=>a+(+t.total||0),0);
 $('monthTotal').textContent=RUB(sum);$('todayTotal').textContent=RUB(todaySum);$('monthCount').textContent=cur.length;$('avgShift').textContent=RUB(cur.length?sum/cur.length:0);
 const rec=[...all].sort((a,b)=>String(b.date).localeCompare(String(a.date))||b.id-a.id).slice(0,4);
 $('recentList').innerHTML=rec.length?rec.map(t=>{
   const extras=tripExtrasList(t),meta=[t.mileage?Number(t.mileage)+' км':'',...extras,esc(t.comment||'без комментария')].filter(Boolean).join(' · ');
   return `<div class="list-item"><div><div class="list-title">${new Date(t.date+'T12:00:00').toLocaleDateString('ru-RU')} · ${esc(tripBaseLabel(t))}</div><div class="list-meta">${meta}</div></div><div class="amount">${RUB(t.total)}</div></div>`
 }).join(''):'<div class="empty">Пока нет сохранённых смен</div>';
 drawChart($('miniChart'),dailySeries(7),'7 дней')
}

function localDateKey(d){
 const y=d.getFullYear(),m=String(d.getMonth()+1).padStart(2,'0'),day=String(d.getDate()).padStart(2,'0');
 return `${y}-${m}-${day}`
}
function dailySeries(days,endOffsetDays=0){
 const all=loadTrips(),map=groupByDate(all),out=[];
 const end=new Date();end.setHours(12,0,0,0);end.setDate(end.getDate()-Math.max(0,endOffsetDays));
 for(let i=days-1;i>=0;i--){
   const x=new Date(end);x.setDate(end.getDate()-i);
   const z=localDateKey(x);
   out.push({date:z,value:map[z]||0})
 }
 return out.sort((a,b)=>a.date.localeCompare(b.date))
}

function drawChart(canvas,data,title){
 if(!canvas||typeof canvas.getContext!=='function')return;
 const rect=canvas.getBoundingClientRect(),w=Math.floor(rect.width),h=Math.floor(rect.height);
 if(w<40||h<40)return;
 const dpr=window.devicePixelRatio||1;
 canvas.width=Math.max(1,Math.floor(w*dpr));canvas.height=Math.max(1,Math.floor(h*dpr));
 const ctx=canvas.getContext('2d');if(!ctx)return;
 ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,w,h);
 const pad={l:18,r:12,t:15,b:30},iw=Math.max(1,w-pad.l-pad.r),ih=Math.max(1,h-pad.t-pad.b),max=Math.max(...data.map(x=>Number(x.value)||0),1);
 ctx.strokeStyle='#e1e8e4';ctx.lineWidth=1;
 for(let i=0;i<4;i++){const y=pad.t+ih*i/3;ctx.beginPath();ctx.moveTo(pad.l,y);ctx.lineTo(w-pad.r,y);ctx.stroke()}
 const bw=Math.max(4,Math.min(24,iw/Math.max(data.length,1)*.58));
 data.forEach((x,i)=>{
   const cx=pad.l+iw*(i+.5)/Math.max(data.length,1),bh=Math.max(0,((Number(x.value)||0)/max)*ih),y=pad.t+ih-bh;
   if(bh>0){ctx.fillStyle='#22c55e';ctx.beginPath();roundRect(ctx,cx-bw/2,y,bw,bh,Math.min(6,bw/2));ctx.fill()}
   ctx.fillStyle='#7a8880';ctx.font='10px system-ui';ctx.textAlign='center';
   ctx.fillText(new Date(x.date+'T12:00').toLocaleDateString('ru-RU',{day:'2-digit',month:'2-digit'}),cx,h-8)
 })
}
function roundRect(ctx,x,y,w,h,r){
 if(w<=0||h<=0)return;
 r=Math.max(0,Math.min(r,w/2,h/2));
 ctx.moveTo(x+r,y);ctx.arcTo(x+w,y,x+w,y+h,r);ctx.arcTo(x+w,y+h,x,y+h,r);ctx.arcTo(x,y+h,x,y,r);ctx.arcTo(x,y,x+w,y,r)
}
let calendarDate=new Date();calendarDate.setDate(1);
function renderCalendar(){
 const y=calendarDate.getFullYear(),m=calendarDate.getMonth(),key=`${y}-${String(m+1).padStart(2,'0')}`;
 $('calendarTitle').textContent=new Intl.DateTimeFormat('ru-RU',{month:'long',year:'numeric'}).format(calendarDate);
 const trips=loadTrips().filter(t=>monthKey(t.date)===key),map=groupByDate(trips),sum=trips.reduce((a,t)=>a+(+t.total||0),0);
 $('calendarMonthTotal').textContent=RUB(sum);$('calendarCount').textContent=trips.length;$('calendarAvg').textContent=RUB(trips.length?sum/trips.length:0);
 const grid=$('calendarGrid');const dows=['Пн','Вт','Ср','Чт','Пт','Сб','Вс'];let html=dows.map(x=>`<div class="dow">${x}</div>`).join('');
 const first=new Date(y,m,1),last=new Date(y,m+1,0),start=(first.getDay()+6)%7,days=last.getDate(),prevLast=new Date(y,m,0).getDate();
 for(let i=0;i<42;i++){
  let day,cls='',dateStr='';
  if(i<start){day=prevLast-start+i+1;cls='muted'}
  else if(i>=start+days){day=i-start-days+1;cls='muted'}
  else{day=i-start+1;dateStr=`${y}-${String(m+1).padStart(2,'0')}-${String(day).padStart(2,'0')}`;if(dateStr===todayLocal())cls+=' today';if(map[dateStr])cls+=' has'}
  html+=`<div class="day ${cls}"><div class="day-num">${day}</div>${dateStr&&map[dateStr]?`<div class="day-money">${Math.round(map[dateStr]/1000)}к</div><div class="dot"></div>`:''}</div>`
 }
 grid.innerHTML=html
}

$('prevMonth').onclick=()=>{calendarDate.setMonth(calendarDate.getMonth()-1);renderCalendar()};
$('nextMonth').onclick=()=>{calendarDate.setMonth(calendarDate.getMonth()+1);renderCalendar()};

function monthLabel(k){const [y,m]=k.split('-').map(Number);return new Intl.DateTimeFormat('ru-RU',{month:'long',year:'numeric'}).format(new Date(y,m-1,1))}
function fillMonths(){
 const s=$('historyMonth'),cur=s.value;let months=[...new Set(loadTrips().map(t=>monthKey(t.date)))].sort().reverse();if(!months.includes(currentMonthKey()))months.unshift(currentMonthKey());
 s.innerHTML=months.map(m=>`<option value="${m}">${monthLabel(m)}</option>`).join('');if(months.includes(cur))s.value=cur
}
function renderHistory(){
 const mk=$('historyMonth').value||currentMonthKey(),q=($('historySearch').value||'').toLowerCase().trim();
 let a=loadTrips().filter(t=>monthKey(t.date)===mk).sort((x,y)=>String(y.date).localeCompare(String(x.date))||y.id-x.id);
 if(q)a=a.filter(t=>`${t.date} ${t.comment||''} ${tripBaseLabel(t)} ${tripExtrasList(t).join(' ')}`.toLowerCase().includes(q));
 $('historyList').innerHTML=a.length?a.map(t=>{
   const extras=tripExtrasList(t),meta=[t.mileage?Number(t.mileage)+' км':'',...extras,esc(t.comment||'без комментария')].filter(Boolean).join(' · ');
   return `<div class="list-item"><div style="flex:1"><div class="list-title">${new Date(t.date+'T12:00:00').toLocaleDateString('ru-RU')} · ${esc(tripBaseLabel(t))}</div><div class="list-meta">${meta}</div><div style="margin-top:7px"><button class="danger" onclick="deleteTrip(${t.id})">Удалить</button></div></div><div class="amount">${RUB(t.total)}</div></div>`
 }).join(''):'<div class="empty">Ничего не найдено</div>'
}
$('historyMonth').onchange=renderHistory;$('historySearch').oninput=renderHistory;
window.deleteTrip=id=>{if(!confirm('Удалить эту смену?'))return;saveTrips(loadTrips().filter(t=>t.id!==id));trackUsage('shift_deleted');renderHome();fillMonths();renderHistory();renderCalendar();renderStats()};

let statsDays=7;
let statsOffsetDays=0;
function formatStatsRange(data){
 if(!data.length)return '';
 const opts={day:'2-digit',month:'short'};
 const a=new Date(data[0].date+'T12:00').toLocaleDateString('ru-RU',opts);
 const b=new Date(data[data.length-1].date+'T12:00').toLocaleDateString('ru-RU',opts);
 return a+' — '+b
}
function renderStats(){
 const data=dailySeries(statsDays,statsOffsetDays);
 const range=$('statsRange');if(range)range.textContent=formatStatsRange(data);
 const next=$('statsNext');if(next)next.disabled=statsOffsetDays===0;

 if($('stats').classList.contains('active')){
   const scroll=$('statsScroll'),inner=$('statsChartInner'),canvas=$('statsChart');
   if(scroll&&inner&&canvas){
     const viewport=Math.max(280,scroll.clientWidth||280);
     const chartWidth=statsDays>=30?Math.max(viewport,data.length*44):viewport;
     inner.style.width=chartWidth+'px';
     canvas.style.width='100%';
     drawChart(canvas,data,`${statsDays} дней`);
     requestAnimationFrame(()=>{scroll.scrollLeft=scroll.scrollWidth-scroll.clientWidth})
   }
 }

 const start=data[0]?.date||'',end=data[data.length-1]?.date||'';
 const periodTrips=loadTrips().filter(t=>t.date>=start&&t.date<=end);
 const by=groupByDate(periodTrips),best=Math.max(0,...Object.values(by));
 $('bestDay').textContent=RUB(best);
 $('noLoaderCount').textContent=periodTrips.filter(tripHasExtras).length;
 $('allCount').textContent=periodTrips.length;
 const miles=periodTrips.filter(t=>+t.mileage>0);
 $('avgMileage').textContent=(miles.length?Math.round(miles.reduce((a,t)=>a+(+t.mileage||0),0)/miles.length):0)+' км'
}
document.querySelectorAll('[data-period]').forEach(b=>b.onclick=()=>{
 document.querySelectorAll('[data-period]').forEach(x=>x.classList.remove('active'));
 b.classList.add('active');
 statsDays=+b.dataset.period;
 statsOffsetDays=0;
 trackUsage('stats_period_selected',{days:statsDays});
 renderStats()
});
$('statsPrev').onclick=()=>{statsOffsetDays+=statsDays;trackUsage('stats_period_navigated',{direction:'previous',days:statsDays});renderStats()};
$('statsNext').onclick=()=>{statsOffsetDays=Math.max(0,statsOffsetDays-statsDays);trackUsage('stats_period_navigated',{direction:'next',days:statsDays});renderStats()};

let rateEditorConfig=TariffEngine.clone(loadRates());
function newTariffId(prefix){
 return prefix+'_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,7)
}
function renderRateEditor(){
 const baseBox=$('baseRatesList'),extraBox=$('extraRatesList');
 if(baseBox){
   baseBox.innerHTML=rateEditorConfig.bases.length?rateEditorConfig.bases.map((b,i)=>`
     <div class="tariff-editor">
       <div class="tariff-editor-top">
         <div><label>Название</label><input data-base-name="${i}" maxlength="80" value="${esc(b.name)}" placeholder="Например, до 3 т"></div>
         <div><label>Сумма, ₽</label><input data-base-rate="${i}" type="number" min="0" step="1" value="${Number(b.rate)||0}"></div>
         <button class="tariff-remove" data-remove-base="${i}" aria-label="Удалить">×</button>
       </div>
     </div>`).join(''):'<div class="empty-editor">Базовых тарифов нет. Смена будет считаться только по доплатам.</div>';
 }
 if(extraBox){
   const baseOptions='<option value="">Любой базовый тариф</option>'+rateEditorConfig.bases.map(b=>`<option value="${esc(b.id)}">${esc(b.name)}</option>`).join('');
   extraBox.innerHTML=rateEditorConfig.extras.length?rateEditorConfig.extras.map((e,i)=>`
     <div class="tariff-editor">
       <div class="tariff-editor-top">
         <div><label>Название</label><input data-extra-name="${i}" maxlength="80" value="${esc(e.name)}" placeholder="Например, Без грузчика"></div>
         <div><label>Сумма, ₽</label><input data-extra-rate="${i}" type="number" step="1" value="${Number(e.rate)||0}"></div>
         <button class="tariff-remove" data-remove-extra="${i}" aria-label="Удалить">×</button>
       </div>
       <div class="tariff-editor-grid">
         <div><label>Как считать</label>
           <select data-extra-kind="${i}">
             <option value="toggle" ${e.kind==='toggle'?'selected':''}>Флажок · разовая доплата</option>
             <option value="quantity" ${e.kind==='quantity'?'selected':''}>Количество × тариф</option>
             <option value="mileage" ${e.kind==='mileage'?'selected':''}>Автоматически по пробегу</option>
           </select>
         </div>
         ${e.kind==='mileage'?`<div><label>От какого пробега, км</label><input data-extra-threshold="${i}" type="number" min="0" step="1" value="${Number(e.threshold)||0}"></div>
         <div><label>Для какого тарифа</label><select data-extra-base="${i}">${baseOptions}</select></div>`:''}
       </div>
       <div class="tariff-kind-hint">${e.kind==='toggle'?'В смене появится переключатель.':e.kind==='quantity'?'В смене появится поле количества.':'Доплата включится сама при достижении пробега.'}</div>
     </div>`).join(''):'<div class="empty-editor">Доплат пока нет. Добавьте только те, которыми пользуетесь.</div>';
   rateEditorConfig.extras.forEach((e,i)=>{
     if(e.kind==='mileage'){
       const el=extraBox.querySelector(`[data-extra-base="${i}"]`);
       if(el)el.value=e.baseId||''
     }
   })
 }

 document.querySelectorAll('[data-base-name]').forEach(el=>el.oninput=()=>{const i=+el.dataset.baseName;if(rateEditorConfig.bases[i])rateEditorConfig.bases[i].name=el.value});
 document.querySelectorAll('[data-base-rate]').forEach(el=>el.oninput=()=>{const i=+el.dataset.baseRate;if(rateEditorConfig.bases[i])rateEditorConfig.bases[i].rate=Math.max(0,Number(el.value)||0)});
 document.querySelectorAll('[data-remove-base]').forEach(el=>el.onclick=()=>{
   const i=+el.dataset.removeBase,removed=rateEditorConfig.bases[i];if(!removed)return;
   rateEditorConfig.bases.splice(i,1);
   rateEditorConfig.extras.forEach(x=>{if(x.baseId===removed.id)x.baseId=''});
   renderRateEditor()
 });
 document.querySelectorAll('[data-extra-name]').forEach(el=>el.oninput=()=>{const i=+el.dataset.extraName;if(rateEditorConfig.extras[i])rateEditorConfig.extras[i].name=el.value});
 document.querySelectorAll('[data-extra-rate]').forEach(el=>el.oninput=()=>{const i=+el.dataset.extraRate;if(rateEditorConfig.extras[i])rateEditorConfig.extras[i].rate=Number(el.value)||0});
 document.querySelectorAll('[data-extra-kind]').forEach(el=>el.onchange=()=>{
   const i=+el.dataset.extraKind,e=rateEditorConfig.extras[i];if(!e)return;
   e.kind=el.value;
   if(e.kind==='mileage'){e.threshold=Number(e.threshold)||180;e.baseId=e.baseId||''}else{delete e.threshold;delete e.baseId}
   renderRateEditor()
 });
 document.querySelectorAll('[data-extra-threshold]').forEach(el=>el.oninput=()=>{const i=+el.dataset.extraThreshold;if(rateEditorConfig.extras[i])rateEditorConfig.extras[i].threshold=Math.max(0,Number(el.value)||0)});
 document.querySelectorAll('[data-extra-base]').forEach(el=>el.onchange=()=>{const i=+el.dataset.extraBase;if(rateEditorConfig.extras[i])rateEditorConfig.extras[i].baseId=el.value});
 document.querySelectorAll('[data-remove-extra]').forEach(el=>el.onclick=()=>{const i=+el.dataset.removeExtra;if(rateEditorConfig.extras[i])rateEditorConfig.extras.splice(i,1);renderRateEditor()});
}
function populateRates(){
 rateEditorConfig=TariffEngine.clone(loadRates());
 renderRateEditor()
}
$('addBaseRate').onclick=()=>{
 rateEditorConfig.bases.push({id:newTariffId('base'),name:'Новый тариф',rate:0});
 renderRateEditor()
};
$('addExtraRate').onclick=()=>{
 rateEditorConfig.extras.push({id:newTariffId('extra'),name:'Новая доплата',kind:'toggle',rate:0});
 renderRateEditor()
};
$('saveRates').onclick=()=>{
 rateEditorConfig=TariffEngine.normalizeConfig(rateEditorConfig);
 saveRatesObj(rateEditorConfig);
 renderShiftTariffs();
 calculate();
 populateRates();
 trackUsage('rates_saved',{base_count:rateEditorConfig.bases.length,extra_count:rateEditorConfig.extras.length});
 alert('Тарифы сохранены')
};

function browserSaveText(filename,mime,content){
 const blob=new Blob([content],{type:mime}),a=document.createElement('a');
 a.href=URL.createObjectURL(blob);a.download=filename;a.click();
 setTimeout(()=>URL.revokeObjectURL(a.href),1000)
}
function saveTextFile(filename,mime,content){
 if(nativeAvailable() && typeof AndroidData.saveTextFile==='function'){
   try{
     const result=AndroidData.saveTextFile(filename,mime,content);
     if(result==='ok')return true;
   }catch(e){}
 }
 browserSaveText(filename,mime,content);return false
}
$('exportCsv').onclick=()=>{
 const m=$('historyMonth').value,rows=loadTrips().filter(t=>monthKey(t.date)===m),head=['Дата','Вес','Пробег','Без грузчика','Второй рейс','Баллоны','Стойки','Доплата','Комментарий','Итого'];
 const csv=[head,...rows.map(t=>[t.date,t.weight,t.mileage,t.noLoader?'Да':'Нет',t.secondTrip?'Да':'Нет',t.balloons,t.racks,t.manualExtra,t.comment,t.total])].map(r=>r.map(x=>`"${String(x??'').replaceAll('"','""')}"`).join(';')).join('\n');
 saveTextFile(`зарплата_${m}.csv`,'text/csv;charset=utf-8','\ufeff'+csv);trackUsage('csv_exported')
};
$('backup').onclick=()=>{
 const d={version:5,savedAt:new Date().toISOString(),rates:loadRates(),trips:loadTrips()};
 saveTextFile('зарплата_водителя_backup.json','application/json',JSON.stringify(d,null,2));trackUsage('backup_exported')
};
$('restoreBtn').onclick=()=>$('restoreFile').click();
$('restoreFile').onchange=async e=>{const f=e.target.files[0];if(!f)return;try{const d=JSON.parse(await f.text());if(d.rates)saveRatesObj(d.rates);if(Array.isArray(d.trips))saveTrips(d.trips);populateRates();renderHome();fillMonths();renderCalendar();renderStats();trackUsage('backup_restored');alert('Данные восстановлены')}catch{alert('Не удалось прочитать файл')}e.target.value=''};

window.addEventListener('resize',()=>{if($('home').classList.contains('active'))renderHome();if($('stats').classList.contains('active'))renderStats()});
populateRates();calculate();renderHome();fillMonths();renderCalendar();renderStats();initAnalyticsUi();setTimeout(persistNativeBackup,500);

/* Voice input 1.3.0 */
const RU_NUMBERS={
  'ноль':0,'нуль':0,'один':1,'одна':1,'одно':1,'первый':1,
  'два':2,'две':2,'второй':2,'три':3,'третий':3,'четыре':4,'четвертый':4,
  'пять':5,'шесть':6,'семь':7,'восемь':8,'девять':9,'десять':10,
  'одиннадцать':11,'двенадцать':12,'тринадцать':13,'четырнадцать':14,'пятнадцать':15,
  'шестнадцать':16,'семнадцать':17,'восемнадцать':18,'девятнадцать':19,
  'двадцать':20,'тридцать':30,'сорок':40,'пятьдесят':50,'шестьдесят':60,
  'семьдесят':70,'восемьдесят':80,'девяносто':90,
  'сто':100,'двести':200,'триста':300,'четыреста':400,'пятьсот':500,
  'шестьсот':600,'семьсот':700,'восемьсот':800,'девятьсот':900
};

function voiceNormalize(s){
  return String(s||'').toLowerCase().replace(/ё/g,'е').replace(/[–—]/g,'-').replace(/\s+/g,' ').trim();
}
function spokenNumber(chunk){
  const s=voiceNormalize(chunk);
  const digit=s.match(/\d+/);
  if(digit) return Number(digit[0]);
  let total=0,current=0,found=false;
  for(const token of s.replace(/-/g,' ').split(/\s+/)){
    if(token==='тысяча'||token==='тысячи'||token==='тысяч'){
      total+=(current||1)*1000; current=0; found=true;
    } else if(Object.prototype.hasOwnProperty.call(RU_NUMBERS,token)){
      current+=RU_NUMBERS[token]; found=true;
    }
  }
  return found?total+current:null;
}
function shiftDate(days){
  const d=new Date(); d.setDate(d.getDate()+days);
  const z=d.getTimezoneOffset()*60000;
  return new Date(d-z).toISOString().slice(0,10);
}
const COUNT_WORD='(?:\\d+|ноль|нуль|один|одна|одно|два|две|три|четыре|пять|шесть|семь|восемь|девять|десять|одиннадцать|двенадцать|тринадцать|четырнадцать|пятнадцать|шестнадцать|семнадцать|восемнадцать|девятнадцать|двадцать)';
function extractUnitCount(t,nounPattern){
 const before=new RegExp('(?:^|\\s)('+COUNT_WORD+')\\s+(?:'+nounPattern+')(?:\\s|$|[,.])');
 let m=t.match(before);
 if(m){const n=spokenNumber(m[1]);if(n!==null)return n}
 const after=new RegExp('(?:^|\\s)(?:'+nounPattern+')\\s+('+COUNT_WORD+')(?:\\s|$|[,.])');
 m=t.match(after);
 if(m){const n=spokenNumber(m[1]);if(n!==null)return n}
 return null
}
function showVoiceToast(title,text,timeout=4500){
  const box=$('voiceToast'),t=$('voiceToastTitle'),p=$('voiceToastText');
  if(!box||!t||!p)return;
  t.textContent=title;p.textContent=text;box.classList.add('show');
  clearTimeout(showVoiceToast.timer);
  if(timeout) showVoiceToast.timer=setTimeout(()=>box.classList.remove('show'),timeout);
}
function setVoiceActive(active){
  const b=$('voiceFab'); if(b)b.classList.toggle('listening',!!active);
  const f=$('voiceFormBtn'); if(f)f.textContent=active?'🎙 Слушаю…':'🎙 Заполнить смену голосом';
}
function startVoiceEntry(){
  showPage('add');
  try{
    setVoiceActive(true);
    trackUsage('voice_input_started');
    showVoiceToast('Говорите…','Открываю системный голосовой ввод Android…',0);
    window.location.href='driverapp://voice';
  }catch(e){
    setVoiceActive(false);
    showVoiceToast('Голосовой ввод','Не удалось открыть системный голосовой ввод.');
  }
}
function parseVoiceShift(raw){
  const t=voiceNormalize(raw);
  if(!t)return {changed:false,save:false,summary:'Пустая фраза'};

  if(/^(отмена|отмени|не надо|закрой)$/.test(t)){
    resetForm();showPage('home');
    return {changed:false,cancel:true,save:false,summary:'Ввод отменён'}
  }

  const saveRequested=/(?:^|\s)(сохрани|сохранить|запиши|записать)(?:\s|$|[,.])/.test(t);
  const hasDetails=/(пробег|километр|тонн|грузчик|рейс|баллон|стойк|доплат|комментар|заметка)/.test(t);

  if(saveRequested&&!hasDetails&&$('add').classList.contains('active')){
    $('saveTrip').click();
    return {changed:false,save:true,summary:'Смена сохранена'}
  }

  showPage('add');
  let changed=false;

  if(t.includes('позавчера')){$('date').value=shiftDate(-2);changed=true}
  else if(t.includes('вчера')){$('date').value=shiftDate(-1);changed=true}
  else if(t.includes('завтра')){$('date').value=shiftDate(1);changed=true}
  else if(t.includes('сегодня')){$('date').value=todayLocal();changed=true}

  if(/(?:^|\s)(?:4\s*[- ]\s*5|от\s+четырех\s+до\s+пяти|четыре\s+пять)(?:\s*(?:т|тонн|тонны))?(?:\s|$|[,.])/.test(t)){
    $('weight').value='4-5';changed=true
  }else if(/(?:^|\s)(?:3\s*[- ]\s*4|от\s+трех\s+до\s+четырех|три\s+четыре)(?:\s*(?:т|тонн|тонны))?(?:\s|$|[,.])/.test(t)){
    $('weight').value='3-4';changed=true
  }else if(/(?:^|\s)(?:до\s*(?:3|трех)|до\s+трех)(?:\s*(?:т|тонн|тонны))?(?:\s|$|[,.])/.test(t)){
    $('weight').value='2-3';changed=true
  }

  let mm=t.match(/(?:пробег|километраж|проехал(?:а)?)\s+(.+?)(?=\s+(?:без\s+грузчика|с\s+грузчиком|второй\s+рейс|2(?:-?й)?\s+рейс|баллон\w*|стойк\w*|доплат\w*|комментар\w*|заметка)|$)/);
  let mileage=mm?spokenNumber(mm[1]):null;
  if(mileage===null){
    mm=t.match(/(?:^|\s)(\d{2,4})\s*(?:км|километр\w*)(?:\s|$|[,.])/);
    mileage=mm?Number(mm[1]):null
  }
  if(mileage!==null&&mileage>=0){$('mileage').value=mileage;changed=true}

  if(t.includes('без грузчика')){$('noLoader').checked=true;changed=true}
  else if(t.includes('с грузчиком')){$('noLoader').checked=false;changed=true}

  if(/(?:^|\s)(?:второй|2(?:-?й)?|два)\s+рейс(?:а)?(?:\s|$|[,.])/.test(t)){$('secondTrip').checked=true;changed=true}
  else if(/(?:^|\s)один\s+рейс(?:\s|$|[,.])/.test(t)){$('secondTrip').checked=false;changed=true}

  const balloons=extractUnitCount(t,'баллон(?:а|ов|ы)?');
  if(balloons!==null){$('balloons').value=balloons;changed=true}
  const racks=extractUnitCount(t,'(?:стойк(?:а|и|у|ой)?|стоек)');
  if(racks!==null){$('racks').value=racks;changed=true}

  const extraMatch=t.match(/(?:доплата|доплату|доплатить)\s+(.+?)(?=\s+(?:комментар|заметка|баллон|стойк|рейс|без\s+грузчика|с\s+грузчиком)|$)/);
  if(extraMatch){
    const extra=spokenNumber(extraMatch[1]);
    if(extra!==null){$('manualExtra').value=extra;changed=true}
  }

  const commentMatch=String(raw).match(/(?:комментарий|заметка)\s+(.+)$/i);
  if(commentMatch){$('comment').value=commentMatch[1].trim();changed=true}

  const total=calculate();
  if(saveRequested&&changed){
    setTimeout(()=>$('saveTrip').click(),250);
    return {changed:true,save:true,summary:'Распознано. Смена будет сохранена. Итого '+RUB(total)}
  }

  const parts=[$('weight').options[$('weight').selectedIndex].text];
  if(val('mileage'))parts.push(val('mileage')+' км');
  if($('noLoader').checked)parts.push('без грузчика');
  if($('secondTrip').checked)parts.push('второй рейс');
  if(val('balloons'))parts.push('баллоны: '+val('balloons'));
  if(val('racks'))parts.push('стойки: '+val('racks'));
  return {changed,save:false,summary:changed?parts.join(' · ')+' · Итого '+RUB(total):'Не удалось найти параметры смены'}
}

window.onVoiceState=state=>{
  if(state==='listening'||state==='speaking'){
    setVoiceActive(true);
    showVoiceToast('Говорите…','Назовите параметры смены.',0);
  } else if(state==='processing'){
    showVoiceToast('Распознаю…','Секунду, разбираю фразу.',0);
  }
};
window.onVoicePartial=text=>{
  setVoiceActive(true);
  showVoiceToast('Слышу…',text,0);
};
window.onVoiceResult=text=>{
  setVoiceActive(false);
  const r=parseVoiceShift(text);
  trackUsage('voice_input_result',{recognized:!!r.changed||!!r.save,saved:!!r.save});
  showVoiceToast(r.save?'Готово':'Распознано','«'+text+'»\n'+r.summary,r.save?3000:6500);
};
window.onVoiceError=msg=>{
  setVoiceActive(false);
  trackUsage('voice_input_error');
  showVoiceToast('Голосовой ввод',msg,4500);
};

if($('voiceFab')) $('voiceFab').onclick=startVoiceEntry;
if($('voiceFormBtn')) $('voiceFormBtn').onclick=startVoiceEntry;
