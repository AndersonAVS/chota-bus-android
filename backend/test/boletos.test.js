import {test} from 'node:test';
import assert from 'node:assert/strict';
import {crearApp} from '../src/app.js';
const id='00000000-0000-4000-8000-000000000001';
async function pedir(ruta,autorizado=true){
 let llamadas=0,propietario;
 const obtener=async uid=>{llamadas++;propietario=uid;return{id,modo:'PRUEBA'};};
 const app=crearApp({},{verificarToken:async()=>({uid:'uid-verificado'}),
  boletosRepository:{listar:async uid=>({boletos:[await obtener(uid)],siguiente:null}),obtener,deReserva:async uid=>[await obtener(uid)]}});
 const server=app.listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));
 try{const r=await fetch('http://127.0.0.1:'+server.address().port+ruta,{headers:autorizado?{authorization:'Bearer prueba.usuario.firma'}:{}});
 return{estado:r.status,cache:r.headers.get('cache-control'),body:await r.json(),llamadas,propietario};}
 finally{await new Promise(r=>{server.close(r);server.closeAllConnections();});}
}
for(const ruta of ['/api/v1/boletos/'+id,'/api/v1/reservas/'+id+'/boletos']){
 test(ruta+' exige sesion antes de consultar',async()=>{const r=await pedir(ruta,false);assert.equal(r.estado,401);assert.equal(r.llamadas,0);});
 test(ruta+' usa UID verificado y evita cachear',async()=>{const r=await pedir(ruta+'?uid=intruso');assert.equal(r.estado,200);assert.equal(r.propietario,'uid-verificado');assert.equal(r.cache,'no-store');});
}
for(const ruta of ['/api/v1/boletos/invalido','/api/v1/reservas/invalido/boletos'])
 test(ruta+' rechaza identificadores invalidos antes de consultar',async()=>{const r=await pedir(ruta);assert.equal(r.estado,400);assert.equal(r.llamadas,0);});

test('lista general exige sesion y usa el UID del token',async()=>{
 assert.equal((await pedir('/api/v1/boletos',false)).estado,401);
 const r=await pedir('/api/v1/boletos');assert.equal(r.estado,200);assert.equal(r.propietario,'uid-verificado');assert.equal(r.cache,'no-store');assert.equal(r.body.siguiente,null);
});
for(const consulta of ['uid=otro','limite=0','limite=51','limite=2&limite=3','despues=invalido','despues='+id+'&despues='+id])
 test('lista rechaza '+consulta,async()=>{const r=await pedir('/api/v1/boletos?'+consulta);assert.equal(r.estado,400);assert.equal(r.llamadas,0);});
