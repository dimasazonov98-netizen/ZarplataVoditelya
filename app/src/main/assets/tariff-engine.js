(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  if(root)root.TariffEngine=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  const clone=x=>JSON.parse(JSON.stringify(x));
  const num=(v,d=0)=>Number.isFinite(Number(v))?Number(v):d;
  const cleanName=(v,fallback)=>String(v==null?'':v).trim().slice(0,80)||fallback;
  const safeId=(v,prefix,index)=>{
    const raw=String(v||'').trim().replace(/[^A-Za-z0-9_-]/g,'').slice(0,50);
    return raw||prefix+'_'+index;
  };

  function defaultConfig(){
    return {
      schema:2,
      bases:[
        {id:'2-3',name:'до 3 т',rate:5200},
        {id:'3-4',name:'3–4 т',rate:5500},
        {id:'4-5',name:'4–5 т',rate:5800}
      ],
      extras:[
        {id:'noLoader',name:'Без грузчика',kind:'toggle',rate:1500},
        {id:'balloon',name:'Баллоны',kind:'quantity',rate:200},
        {id:'rack',name:'Стойки',kind:'quantity',rate:150},
        {id:'secondTrip',name:'Второй рейс',kind:'toggle',rate:4000},
        {id:'m180_23',name:'Пробег 180+ · до 3 т',kind:'mileage',rate:1500,threshold:180,baseId:'2-3'},
        {id:'m400_23',name:'Пробег 400+ · до 3 т',kind:'mileage',rate:2200,threshold:400,baseId:'2-3'},
        {id:'m180_34',name:'Пробег 180+ · 3–4 т',kind:'mileage',rate:1700,threshold:180,baseId:'3-4'},
        {id:'m400_34',name:'Пробег 400+ · 3–4 т',kind:'mileage',rate:2500,threshold:400,baseId:'3-4'},
        {id:'m180_45',name:'Пробег 180+ · 4–5 т',kind:'mileage',rate:1700,threshold:180,baseId:'4-5'},
        {id:'m400_45',name:'Пробег 400+ · 4–5 т',kind:'mileage',rate:2500,threshold:400,baseId:'4-5'}
      ]
    };
  }

  function migrateLegacy(raw){
    const r=raw&&typeof raw==='object'?raw:{};
    const d=defaultConfig();
    d.bases.forEach(b=>{if(r[b.id]!=null)b.rate=num(r[b.id],b.rate)});
    const map={noLoader:'noLoader',balloon:'balloon',rack:'rack',secondTrip:'secondTrip'};
    d.extras.forEach(e=>{
      if(map[e.id]&&r[map[e.id]]!=null)e.rate=num(r[map[e.id]],e.rate);
      if(e.kind==='mileage'){
        const source=e.threshold>=400?r.over400:r.over180;
        if(source&&source[e.baseId]!=null)e.rate=num(source[e.baseId],e.rate);
      }
    });
    return d;
  }

  function normalizeConfig(raw){
    if(!raw||typeof raw!=='object')return defaultConfig();
    if(!Array.isArray(raw.bases)||!Array.isArray(raw.extras))return migrateLegacy(raw);
    const bases=raw.bases.slice(0,50).map((b,i)=>({
      id:safeId(b&&b.id,'base',i),
      name:cleanName(b&&b.name,'Тариф '+(i+1)),
      rate:Math.max(0,num(b&&b.rate,0))
    }));
    const baseIds=new Set(bases.map(b=>b.id));
    const kinds=new Set(['toggle','quantity','mileage']);
    const extras=raw.extras.slice(0,100).map((e,i)=>{
      const kind=kinds.has(e&&e.kind)?e.kind:'toggle';
      const x={
        id:safeId(e&&e.id,'extra',i),
        name:cleanName(e&&e.name,'Доплата '+(i+1)),
        kind,
        rate:num(e&&e.rate,0)
      };
      if(kind==='mileage'){
        x.threshold=Math.max(0,num(e&&e.threshold,0));
        x.baseId=e&&baseIds.has(e.baseId)?e.baseId:'';
      }
      return x;
    });
    return {schema:2,bases,extras};
  }

  function compute(config,input){
    const cfg=normalizeConfig(config),i=input||{};
    const base=cfg.bases.find(b=>b.id===i.baseId)||null;
    const values=i.values&&typeof i.values==='object'?i.values:{};
    const mileage=Math.max(0,num(i.mileage,0));
    const manualExtra=num(i.manualExtra,0);
    let total=base?base.rate:0;
    const applied=[];
    const parts=[];
    if(base)parts.push({kind:'base',name:'База · '+base.name,amount:base.rate,id:base.id});

    cfg.extras.filter(e=>e.kind!=='mileage').forEach(e=>{
      if(e.kind==='toggle'&&values[e.id]){
        total+=e.rate;applied.push({id:e.id,name:e.name,kind:e.kind,value:true,amount:e.rate});
        parts.push({kind:e.kind,name:e.name,amount:e.rate,id:e.id});
      }
      if(e.kind==='quantity'){
        const q=Math.max(0,num(values[e.id],0));
        if(q>0){
          const amount=q*e.rate;total+=amount;
          applied.push({id:e.id,name:e.name,kind:e.kind,value:q,amount});
          parts.push({kind:e.kind,name:e.name+' ×'+q,amount,id:e.id});
        }
      }
    });

    const mileageMatches=cfg.extras
      .filter(e=>e.kind==='mileage'&&mileage>=e.threshold&&(!e.baseId||e.baseId===(base&&base.id)))
      .sort((a,b)=>b.threshold-a.threshold||b.rate-a.rate);
    if(mileageMatches.length){
      const e=mileageMatches[0];
      total+=e.rate;
      applied.push({id:e.id,name:e.name,kind:e.kind,value:mileage,amount:e.rate,threshold:e.threshold});
      parts.push({kind:e.kind,name:e.name,amount:e.rate,id:e.id});
    }

    if(manualExtra){
      total+=manualExtra;
      parts.push({kind:'manual',name:'Ручная доплата',amount:manualExtra,id:'manual'});
    }
    return {total,base,applied,parts,mileage,manualExtra};
  }

  return {defaultConfig,normalizeConfig,migrateLegacy,compute,clone};
});
