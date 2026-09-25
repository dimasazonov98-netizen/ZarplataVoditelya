const RUB = n => new Intl.NumberFormat('ru-RU').format(Math.round(Number(n)||0)) + ' ₽';
const KEY_TRIPS='driver_salary_trips_v2';
const OLD_KEY='driver_salary_trips_v1';
const KEY_RATES='driver_salary_rates_v2_3';
const OLD_RATES='driver_salary_rates_v1';
const defaultRates={"2-3":5200,"3-4":5500,"4-5":5800,noLoader:1500,balloon:200,rack:150,over180:{"2-3":1500,"3-4":1700,"4-5":1700},over400:{"2-3":2200,"3-4":2500,"4-5":2500},secondTrip:4000};
const $=id=>document.getElementById(id);
const nativeAvailable=()=>typeof window.AndroidData!=='undefined';
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
if(!localStorage.getItem(KEY_RATES)){
  let prev={};
  try{
    prev=JSON.parse(
      localStorage.getItem('driver_salary_rates_v2_1') ||
      localStorage.getItem('driver_salary_rates_v2') ||
      localStorage.getItem(OLD_RATES) || '{}'
    )
  }catch(e){}
  localStorage.setItem(KEY_RATES,JSON.stringify({
    ...prev,
    "2-3":5200,"3-4":5500,"4-5":5800,
    noLoader:1500,balloon:200,rack:150,
    over180:{"2-3":1500,"3-4":1700,"4-5":1700},
    over400:{"2-3":2200,"3-4":2500,"4-5":2500},
    secondTrip:4000
  }));
}

const loadTrips=()=>JSON.parse(localStorage.getItem(KEY_TRIPS)||'[]');
const saveTrips=a=>{localStorage.setItem(KEY_TRIPS,JSON.stringify(a));persistNativeBackup();};
const loadRates=()=>({...defaultRates,...JSON.parse(localStorage.getItem(KEY_RATES)||'{}')});
const saveRatesObj=r=>{localStorage.setItem(KEY_RATES,JSON.stringify(r));persistNativeBackup();};

function todayLocal(){const d=new Date(),z=d.getTimezoneOffset()*60000;return new Date(d-z).toISOString().slice(0,10)}
$('date').value=todayLocal();
const val=id=>Number($(id).value)||0;

function showPage(id){
 document.querySelectorAll('.page').forEach(p=>p.classList.remove('active'));
 $(id).classList.add('active');
 document.querySelectorAll('.navbtn').forEach(b=>b.classList.toggle('active',b.dataset.page===id));
 document.getElementById('fab').style.display=id==='add'?'none':'block';
 if(id==='home') renderHome();
 if(id==='history') {fillMonths();renderHistory();}
 if(id==='calendarPage') renderCalendar();
 if(id==='stats') renderStats();
 window.scrollTo({top:0,behavior:'smooth'});
}
window.showPage=showPage;
document.querySelectorAll('.navbtn').forEach(b=>b.onclick=()=>showPage(b.dataset.page));
$('fab').onclick=()=>showPage('add');

function calculate(){
 const r=loadRates(),w=$('weight').value,m=val('mileage'),b=val('balloons'),s=val('racks'),manual=val('manualExtra');
 let total=Number(r[w])||0; const parts=[`База ${w} т: ${RUB(r[w])}`];
 if($('noLoader').checked){total+=r.noLoader;parts.push(`Без грузчика: +${RUB(r.noLoader)}`)}
 if(b){const x=b*r.balloon;total+=x;parts.push(`Баллоны ×${b}: +${RUB(x)}`)}
 if(s){const x=s*r.rack;total+=x;parts.push(`Стойки ×${s}: +${RUB(x)}`)}
 if(m>=400 && r.over400?.[w]){const x=Number(r.over400[w])||0;total+=x;parts.push(`Пробег 400+: +${RUB(x)}`)}
 else if(m>=180 && r.over180?.[w]){const x=Number(r.over180[w])||0;total+=x;parts.push(`Пробег 180+: +${RUB(x)}`)}
 if($('secondTrip').checked){total+=r.secondTrip;parts.push(`Второй рейс: +${RUB(r.secondTrip)}`)}
 if(manual){total+=manual;parts.push(`Ручная доплата: ${manual>=0?'+':''}${RUB(manual)}`)}
 $('calcTotal').textContent=RUB(total);$('breakdown').innerHTML=parts.join('<br>');return total
}
['weight','mileage','noLoader','secondTrip','balloons','racks','manualExtra'].forEach(id=>{
 $(id).addEventListener('input',calculate);$(id).addEventListener('change',calculate)
});

function resetForm(){
 $('date').value=todayLocal();$('weight').value='2-3';$('mileage').value='';$('noLoader').checked=false;$('secondTrip').checked=false;
 $('balloons').value=0;$('racks').value=0;$('manualExtra').value=0;$('comment').value='';calculate()
}
$('saveTrip').onclick=()=>{
 const trip={id:Date.now(),date:$('date').value||todayLocal(),weight:$('weight').value,mileage:val('mileage'),noLoader:$('noLoader').checked,
 secondTrip:$('secondTrip').checked,balloons:val('balloons'),racks:val('racks'),manualExtra:val('manualExtra'),
 comment:$('comment').value.trim(),total:calculate(),ratesSnapshot:loadRates()};
 const a=loadTrips();a.push(trip);saveTrips(a);resetForm();renderHome();fillMonths();renderCalendar();renderStats();showPage('home');
 setTimeout(()=>alert('Смена сохранена'),100)
};

function monthKey(d){return String(d).slice(0,7)}
function currentMonthKey(){return todayLocal().slice(0,7)}

function groupByDate(trips){
 const m={};trips.forEach(t=>{m[t.date]=(m[t.date]||0)+(Number(t.total)||0)});return m
}

function renderHome(){
 const all=loadTrips(), cur=all.filter(t=>monthKey(t.date)===currentMonthKey()), today=all.filter(t=>t.date===todayLocal());
 const sum=cur.reduce((a,t)=>a+(+t.total||0),0), todaySum=today.reduce((a,t)=>a+(+t.total||0),0);
 $('monthTotal').textContent=RUB(sum);$('todayTotal').textContent=RUB(todaySum);$('monthCount').textContent=cur.length;$('avgShift').textContent=RUB(cur.length?sum/cur.length:0);
 const rec=[...all].sort((a,b)=>String(b.date).localeCompare(String(a.date))||b.id-a.id).slice(0,4);
 $('recentList').innerHTML=rec.length?rec.map(t=>`<div class="list-item"><div><div class="list-title">${new Date(t.date+'T12:00:00').toLocaleDateString('ru-RU')} · ${t.weight} т</div><div class="list-meta">${t.mileage?`${t.mileage} км · `:''}${t.noLoader?'без грузчика · ':''}${t.comment||'без комментария'}</div></div><div class="amount">${RUB(t.total)}</div></div>`).join(''):'<div class="empty">Пока нет сохранённых смен</div>';
 drawChart($('miniChart'),dailySeries(7),'7 дней')
}

function dailySeries(days){
 const all=loadTrips(), map=groupByDate(all), out=[];const d=new Date();
 for(let i=days-1;i>=0;i--){const x=new Date(d);x.setDate(d.getDate()-i);const z=new Date(x-x.getTimezoneOffset()*60000).toISOString().slice(0,10);out.push({date:z,value:map[z]||0})}
 return out
}

function drawChart(canvas,data,title){
 const rect=canvas.getBoundingClientRect(),dpr=window.devicePixelRatio||1;canvas.width=Math.max(300,rect.width*dpr);canvas.height=Math.max(160,rect.height*dpr);
 const ctx=canvas.getContext('2d');ctx.setTransform(dpr,0,0,dpr,0,0);const w=rect.width,h=rect.height;ctx.clearRect(0,0,w,h);
 const pad={l:14,r:8,t:15,b:28}, iw=w-pad.l-pad.r,ih=h-pad.t-pad.b,max=Math.max(...data.map(x=>x.value),1);
 ctx.strokeStyle='#24334c';ctx.lineWidth=1;
 for(let i=0;i<4;i++){const y=pad.t+ih*i/3;ctx.beginPath();ctx.moveTo(pad.l,y);ctx.lineTo(w-pad.r,y);ctx.stroke()}
 const bw=iw/data.length*.58;
 data.forEach((x,i)=>{const cx=pad.l+iw*(i+.5)/data.length,bh=(x.value/max)*ih,y=pad.t+ih-bh;ctx.fillStyle='#22c55e';ctx.beginPath();roundRect(ctx,cx-bw/2,y,bw,bh,6);ctx.fill();ctx.fillStyle='#91a0b5';ctx.font='10px system-ui';ctx.textAlign='center';ctx.fillText(new Date(x.date+'T12:00').toLocaleDateString('ru-RU',{day:'2-digit',month:'2-digit'}),cx,h-8)})
}
function roundRect(ctx,x,y,w,h,r){r=Math.min(r,w/2,h/2);ctx.moveTo(x+r,y);ctx.arcTo(x+w,y,x+w,y+h,r);ctx.arcTo(x+w,y+h,x,y+h,r);ctx.arcTo(x,y+h,x,y,r);ctx.arcTo(x,y,x+w,y,r)}

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
 if(q)a=a.filter(t=>`${t.date} ${t.comment||''} ${t.weight}`.toLowerCase().includes(q));
 $('historyList').innerHTML=a.length?a.map(t=>`<div class="list-item"><div style="flex:1"><div class="list-title">${new Date(t.date+'T12:00:00').toLocaleDateString('ru-RU')} · ${t.weight} т</div><div class="list-meta">${t.mileage?`${t.mileage} км · `:''}${t.noLoader?'без грузчика · ':''}${t.secondTrip?'второй рейс · ':''}${t.comment||'без комментария'}</div><div style="margin-top:7px"><button class="danger" onclick="deleteTrip(${t.id})">Удалить</button></div></div><div class="amount">${RUB(t.total)}</div></div>`).join(''):'<div class="empty">Ничего не найдено</div>'
}
$('historyMonth').onchange=renderHistory;$('historySearch').oninput=renderHistory;
window.deleteTrip=id=>{if(!confirm('Удалить эту смену?'))return;saveTrips(loadTrips().filter(t=>t.id!==id));renderHome();fillMonths();renderHistory();renderCalendar();renderStats()};

let statsDays=7;
function renderStats(){
 const data=dailySeries(statsDays);drawChart($('statsChart'),data,`${statsDays} дней`);
 const all=loadTrips();const by=groupByDate(all);const best=Math.max(0,...Object.values(by));$('bestDay').textContent=RUB(best);
 $('noLoaderCount').textContent=all.filter(t=>t.noLoader).length;$('allCount').textContent=all.length;
 const miles=all.filter(t=>+t.mileage>0);$('avgMileage').textContent=(miles.length?Math.round(miles.reduce((a,t)=>a+(+t.mileage||0),0)/miles.length):0)+' км'
}
document.querySelectorAll('[data-period]').forEach(b=>b.onclick=()=>{document.querySelectorAll('[data-period]').forEach(x=>x.classList.remove('active'));b.classList.add('active');statsDays=+b.dataset.period;renderStats()});

function populateRates(){
 const r=loadRates();$('rate23').value=r['2-3'];$('rate34').value=r['3-4'];$('rate45').value=r['4-5'];$('rateNoLoader').value=r.noLoader;$('rateBalloon').value=r.balloon;$('rateRack').value=r.rack;$('rate180_23').value=r.over180['2-3'];$('rate400_23').value=r.over400['2-3'];$('rate180_34').value=r.over180['3-4'];$('rate400_34').value=r.over400['3-4'];$('rate180_45').value=r.over180['4-5'];$('rate400_45').value=r.over400['4-5'];$('rateSecond').value=r.secondTrip
}
$('saveRates').onclick=()=>{saveRatesObj({"2-3":val('rate23'),"3-4":val('rate34'),"4-5":val('rate45'),noLoader:val('rateNoLoader'),balloon:val('rateBalloon'),rack:val('rateRack'),over180:{"2-3":val('rate180_23'),"3-4":val('rate180_34'),"4-5":val('rate180_45')},over400:{"2-3":val('rate400_23'),"3-4":val('rate400_34'),"4-5":val('rate400_45')},secondTrip:val('rateSecond')});calculate();alert('Тарифы сохранены')};

$('exportCsv').onclick=()=>{
 const m=$('historyMonth').value,rows=loadTrips().filter(t=>monthKey(t.date)===m),head=['Дата','Вес','Пробег','Без грузчика','Второй рейс','Баллоны','Стойки','Доплата','Комментарий','Итого'];
 const csv=[head,...rows.map(t=>[t.date,t.weight,t.mileage,t.noLoader?'Да':'Нет',t.secondTrip?'Да':'Нет',t.balloons,t.racks,t.manualExtra,t.comment,t.total])].map(r=>r.map(x=>`"${String(x??'').replaceAll('"','""')}"`).join(';')).join('\n');
 const blob=new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=`зарплата_${m}.csv`;a.click();URL.revokeObjectURL(a.href)
};
$('backup').onclick=()=>{const d={version:2,rates:loadRates(),trips:loadTrips()},blob=new Blob([JSON.stringify(d,null,2)],{type:'application/json'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='зарплата_водителя_v2_backup.json';a.click();URL.revokeObjectURL(a.href)};
$('restoreBtn').onclick=()=>$('restoreFile').click();
$('restoreFile').onchange=async e=>{const f=e.target.files[0];if(!f)return;try{const d=JSON.parse(await f.text());if(d.rates)saveRatesObj(d.rates);if(Array.isArray(d.trips))saveTrips(d.trips);populateRates();renderHome();fillMonths();renderCalendar();renderStats();alert('Данные восстановлены')}catch{alert('Не удалось прочитать файл')}e.target.value=''};

window.addEventListener('resize',()=>{if($('home').classList.contains('active'))renderHome();if($('stats').classList.contains('active'))renderStats()});
populateRates();calculate();renderHome();fillMonths();renderCalendar();renderStats();setTimeout(persistNativeBackup,500);

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
function extractUnitCount(t, nounPattern){
  let m=t.match(new RegExp('(\\d+|[а-я]+(?:\\s+[а-я]+){0,2})\\s+(?:'+nounPattern+')'));
  if(m){const n=spokenNumber(m[1]);if(n!==null)return n}
  m=t.match(new RegExp('(?:'+nounPattern+')\\s+(\\d+|[а-я]+(?:\\s+[а-я]+){0,2})'));
  if(m){const n=spokenNumber(m[1]);if(n!==null)return n}
  return null;
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
  if(!nativeAvailable() || typeof AndroidData.startVoiceInput!=='function'){
    showVoiceToast('Голосовой ввод','Эта функция доступна в Android-приложении.');
    return;
  }
  try{
    if(typeof AndroidData.isVoiceAvailable==='function' && !AndroidData.isVoiceAvailable()){
      showVoiceToast('Голосовой ввод','На телефоне не найден системный сервис распознавания речи.');
      return;
    }
    setVoiceActive(true);
    showVoiceToast('Говорите…','Например: «до трёх тонн, пробег 240, без грузчика, второй рейс».',0);
    AndroidData.startVoiceInput();
  }catch(e){
    setVoiceActive(false);
    showVoiceToast('Голосовой ввод','Не удалось запустить микрофон.');
  }
}
function parseVoiceShift(raw){
  const t=voiceNormalize(raw);
  if(!t) return {changed:false,save:false,summary:'Пустая фраза'};

  if(/^(отмена|отмени|не надо|закрой)$/.test(t)){
    resetForm(); showPage('home');
    return {changed:false,cancel:true,save:false,summary:'Ввод отменён'};
  }

  const saveRequested=/\b(сохрани|сохранить|запиши|записать)\b/.test(t);
  const hasDetails=/(пробег|километр|тонн|грузчик|рейс|баллон|стойк|доплат|комментар)/.test(t);
  if(saveRequested && !hasDetails){
    if($('add').classList.contains('active')){
      $('saveTrip').click();
      return {changed:false,save:true,summary:'Смена сохранена'};
    }
  }

  showPage('add');
  let changed=false;

  if(/\bпозавчера\b/.test(t)){$('date').value=shiftDate(-2);changed=true}
  else if(/\bвчера\b/.test(t)){$('date').value=shiftDate(-1);changed=true}
  else if(/\bзавтра\b/.test(t)){$('date').value=shiftDate(1);changed=true}
  else if(/\bсегодня\b/.test(t)){$('date').value=todayLocal();changed=true}

  if(/(?:до\s*(?:3|трех)|до\s+трех)\s*(?:т|тонн|тонны)?\b/.test(t)){
    $('weight').value='2-3'; changed=true;
  } else if(/(?:3\s*[- ]\s*4|от\s+трех\s+до\s+четырех|три\s+четыре)\s*(?:т|тонн|тонны)?\b/.test(t)){
    $('weight').value='3-4'; changed=true;
  } else if(/(?:4\s*[- ]\s*5|от\s+четырех\s+до\s+пяти|четыре\s+пять)\s*(?:т|тонн|тонны)?\b/.test(t)){
    $('weight').value='4-5'; changed=true;
  }

  let mm=t.match(/(?:пробег|километраж|проехал(?:а)?)\s+(.+?)(?=\s+(?:без\s+грузчика|с\s+грузчиком|второй\s+рейс|2(?:-?й)?\s+рейс|баллон\w*|стойк\w*|доплат\w*|комментар\w*)|$)/);
  let mileage=mm?spokenNumber(mm[1]):null;
  if(mileage===null){
    mm=t.match(/(\d{2,4})\s*(?:км|километр\w*)\b/);
    mileage=mm?Number(mm[1]):null;
  }
  if(mileage!==null && mileage>=0){$('mileage').value=mileage;changed=true}

  if(/\bбез\s+грузчика\b/.test(t)){$('noLoader').checked=true;changed=true}
  else if(/\bс\s+грузчиком\b/.test(t)){$('noLoader').checked=false;changed=true}

  if(/\b(?:второй|2(?:-?й)?|два)\s+рейс/.test(t)){$('secondTrip').checked=true;changed=true}
  else if(/\bодин\s+рейс\b/.test(t)){$('secondTrip').checked=false;changed=true}

  const balloons=extractUnitCount(t,'баллон(?:а|ов|ы)?');
  if(balloons!==null){$('balloons').value=balloons;changed=true}
  const racks=extractUnitCount(t,'стойк(?:а|и|у|ой)?|стоек');
  if(racks!==null){$('racks').value=racks;changed=true}

  const extraMatch=t.match(/(?:доплата|доплату|доплатить)\s+([^,.;]{1,30}?)(?=\s+(?:комментар|баллон|стойк|рейс|без\s+грузчика)|$)/);
  if(extraMatch){
    const extra=spokenNumber(extraMatch[1]);
    if(extra!==null){$('manualExtra').value=extra;changed=true}
  }

  const commentMatch=raw.match(/(?:комментарий|заметка)\s+(.+)$/i);
  if(commentMatch){$('comment').value=commentMatch[1].trim();changed=true}

  const total=calculate();
  if(saveRequested && changed){
    setTimeout(()=>$('saveTrip').click(),250);
    return {changed:true,save:true,summary:'Распознано. Смена будет сохранена. Итого '+RUB(total)};
  }

  const parts=[];
  parts.push($('weight').options[$('weight').selectedIndex].text);
  if(val('mileage'))parts.push(val('mileage')+' км');
  if($('noLoader').checked)parts.push('без грузчика');
  if($('secondTrip').checked)parts.push('второй рейс');
  if(val('balloons'))parts.push('баллоны: '+val('balloons'));
  if(val('racks'))parts.push('стойки: '+val('racks'));
  return {changed,save:false,summary:(changed?parts.join(' · ')+' · Итого '+RUB(total):'Не удалось найти параметры смены')};
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
  showVoiceToast(r.save?'Готово':'Распознано','«'+text+'»\n'+r.summary,r.save?3000:6500);
};
window.onVoiceError=msg=>{
  setVoiceActive(false);
  showVoiceToast('Голосовой ввод',msg,4500);
};

if($('voiceFab')) $('voiceFab').onclick=startVoiceEntry;
if($('voiceFormBtn')) $('voiceFormBtn').onclick=startVoiceEntry;
