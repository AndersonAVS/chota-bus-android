import {test} from 'node:test';
import assert from 'node:assert/strict';
import {crearApp} from '../src/app.js';
async function pedir(consulta='',sesion=true){
 let uid,consultas=0;
 const server=crearApp({},{verificarToken:async()=>({uid:'usuario-verificado'}),historialRepository:{listar:async propietario=>{
  consultas++;uid=propietario;return{registros:[],siguiente:null};
 }}}).listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));
 try{const r=await fetch('http://127.0.0.1:'+server.address().port+'/api/v1/historial'+consulta,{headers:sesion?{authorization:'Bearer prueba.usuario.firma'}:{}});
 return{estado:r.status,body:await r.json(),cache:r.headers.get('cache-control'),uid,consultas};}
 finally{await new Promise(r=>{server.close(r);server.closeAllConnections();});}
}
test('historial exige sesion antes de consultar',async()=>{const r=await pedir('',false);assert.equal(r.estado,401);assert.equal(r.consultas,0);});
test('historial usa propietario verificado y no se cachea',async()=>{const r=await pedir();assert.equal(r.estado,200);assert.equal(r.uid,'usuario-verificado');assert.equal(r.cache,'no-store');assert.deepEqual(r.body,{datos:[],siguiente:null});});
for(const consulta of ['?uid=intruso','?limite=0','?limite=51','?limite=1&limite=2','?despues=invalido','?despues=00000000-0000-4000-8000-000000000001&despues=00000000-0000-4000-8000-000000000002'])
 test('historial rechaza '+consulta,async()=>{const r=await pedir(consulta);assert.equal(r.estado,400);assert.equal(r.consultas,0);});
