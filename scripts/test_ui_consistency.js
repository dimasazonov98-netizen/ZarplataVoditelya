const fs=require('fs');
const assert=require('assert');

const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const js=fs.readFileSync('app/src/main/assets/app.js','utf8');

const ids=new Set([...html.matchAll(/id="([^"]+)"/g)].map(m=>m[1]));
const dynamicIds=new Set(['noLoader','secondTrip','balloons','racks']);
const refs=[...js.matchAll(/\$\('([^']+)'\)/g)].map(m=>m[1]);
const missing=[...new Set(refs.filter(id=>!ids.has(id)&&!dynamicIds.has(id)))];
assert.deepStrictEqual(missing,[], 'Missing static DOM ids: '+missing.join(', '));

const enginePos=html.indexOf('<script src="tariff-engine.js"></script>');
const appPos=html.indexOf('<script src="app.js"></script>');
assert(enginePos>=0&&appPos>enginePos,'tariff-engine.js must load before app.js');

for(const oldId of ['rate23','rate34','rate45','rateNoLoader','rateBalloon','rateRack','rateSecond']){
  assert(!js.includes("$('"+oldId+"')"),'legacy fixed rate field is still referenced: '+oldId);
}

assert(html.includes('id="baseRatesList"'));
assert(html.includes('id="extraRatesList"'));
assert(html.includes('id="dynamicExtras"'));
assert(js.includes('TariffEngine.compute'));
assert(js.includes('TariffEngine.normalizeConfig'));

console.log('UI consistency checks passed');
