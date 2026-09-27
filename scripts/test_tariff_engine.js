const assert=require('assert');
const engine=require('../app/src/main/assets/tariff-engine.js');

const legacy={
  '2-3':5200,'3-4':5500,'4-5':5800,
  noLoader:1500,balloon:200,rack:150,secondTrip:4000,
  over180:{'2-3':1500,'3-4':1700,'4-5':1700},
  over400:{'2-3':2200,'3-4':2500,'4-5':2500}
};
const migrated=engine.normalizeConfig(legacy);
assert.equal(migrated.bases.length,3);
assert.equal(migrated.extras.length,10);

let r=engine.compute(migrated,{
  baseId:'2-3',mileage:240,
  values:{noLoader:true,secondTrip:true,balloon:2,rack:0},
  manualExtra:0
});
assert.equal(r.total,12600,'legacy test phrase must remain 12600');

r=engine.compute(migrated,{
  baseId:'4-5',mileage:410,
  values:{noLoader:false,secondTrip:false,balloon:0,rack:3},
  manualExtra:300
});
assert.equal(r.total,9050,'400+ legacy case must remain 9050');

const custom=engine.normalizeConfig({
  schema:2,
  bases:[{id:'night',name:'Ночная смена',rate:7000}],
  extras:[
    {id:'helper',name:'Без помощника',kind:'toggle',rate:1200},
    {id:'stop',name:'Точка',kind:'quantity',rate:350},
    {id:'far100',name:'100+ км',kind:'mileage',rate:900,threshold:100,baseId:''},
    {id:'far300',name:'300+ км',kind:'mileage',rate:1800,threshold:300,baseId:''}
  ]
});
r=engine.compute(custom,{baseId:'night',mileage:350,values:{helper:true,stop:3},manualExtra:-200});
assert.equal(r.total,10850,'custom calculation failed');
assert.equal(r.applied.filter(x=>x.kind==='mileage').length,1,'only highest mileage rule must apply');
assert.equal(r.applied.find(x=>x.kind==='mileage').id,'far300');

const noBase=engine.compute({schema:2,bases:[],extras:[]},{baseId:'',mileage:0,values:{},manualExtra:500});
assert.equal(noBase.total,500,'no-base mode must work');

const deleted=engine.normalizeConfig({schema:2,bases:[{id:'x',name:'X',rate:1000}],extras:[]});
r=engine.compute(deleted,{baseId:'x',mileage:999,values:{noLoader:true},manualExtra:0});
assert.equal(r.total,1000,'deleted extras must not affect total');

console.log('Tariff engine tests passed');
