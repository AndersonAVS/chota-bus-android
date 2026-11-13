import {test} from 'node:test';
import assert from 'node:assert/strict';
import {crearProgramador} from '../src/programacion.js';
test('programador usa dia de Lima y renueva al cambiar de dia',async()=>{
 let instante=new Date('2026-11-14T04:59:00Z');const fechas=[];
 const pool={connect:async()=>({query:async(sql,args)=>{if(args)fechas.push(args[0]);},release(){}})};
 const programar=crearProgramador(pool,{ahora:()=>instante,entorno:{}});
 await programar();await programar();assert.deepEqual(fechas,['2026-11-13']);
 instante=new Date('2026-11-14T05:01:00Z');await programar();assert.deepEqual(fechas,['2026-11-13','2026-11-14']);
});
test('programador comparte ejecucion entre consultas simultaneas',async()=>{
 let liberar,consultas=0;const bloqueo=new Promise(r=>{liberar=r;});
 const pool={connect:async()=>({query:async(sql,args)=>{if(args){consultas++;await bloqueo;}},release(){}})};
 const programar=crearProgramador(pool,{entorno:{}});const a=programar(),b=programar();liberar();await Promise.all([a,b]);assert.equal(consultas,1);
});
test('fallo de programacion permite reintento y devuelve conexion',async()=>{
 let intentos=0,liberadas=0;
 const pool={connect:async()=>({query:async(sql,args)=>{if(args&&++intentos===1)throw new Error('Fallo simulado');},release(){liberadas++;}})};
 const programar=crearProgramador(pool,{entorno:{}});await assert.rejects(programar);await programar();assert.equal(intentos,2);assert.equal(liberadas,2);
});
test('no genera flota ficticia en produccion',async()=>{
 const programar=crearProgramador({connect(){throw new Error('No debe consultar SQL');}},{entorno:{NODE_ENV:'production'}});await programar();
});
