import {test} from 'node:test';
import assert from 'node:assert/strict';
import {crearApp} from '../src/app.js';
async function pedir(autorizado,habilitado,ruta='/api/v1/saldo-prueba',metodo='GET',body){
 let consultas=0;
 const pool={connect:async()=>{consultas++;throw new Error('No debe consultar SQL');}};
 const server=crearApp(pool,{verificarToken:async()=>({uid:'prueba'}),pagosPruebaHabilitados:()=>habilitado}).listen(0,'127.0.0.1');
 await new Promise(r=>server.once('listening',r));
 try{const res=await fetch('http://127.0.0.1:'+server.address().port+ruta,{method:metodo,
 headers:{...(autorizado?{authorization:'Bearer prueba.usuario.firma'}:{}),'content-type':'application/json'},
 body:body===undefined?undefined:JSON.stringify(body)});return{estado:res.status,consultas,cache:res.headers.get('cache-control')};}
 finally{await new Promise(r=>{server.close(r);server.closeAllConnections();});}
}
for(const [ruta,metodo] of [['/api/v1/saldo-prueba','GET'],['/api/v1/reservas/00000000-0000-4000-8000-000000000001/pago-prueba','POST']]){
 test(metodo+' '+ruta+' requiere sesion',async()=>assert.equal((await pedir(false,true,ruta,metodo)).estado,401));
 test(metodo+' '+ruta+' deshabilitado no accede a SQL',async()=>{
 const r=await pedir(true,false,ruta,metodo);assert.equal(r.estado,503);assert.equal(r.consultas,0);assert.equal(r.cache,'no-store');});
}
test('no acepta un UID, saldo ni importe enviados por Android',async()=>{
 const r=await pedir(true,true,'/api/v1/reservas/00000000-0000-4000-8000-000000000001/pago-prueba','POST',{uid:'otro',saldo:300,importe:1});
 assert.equal(r.estado,400);assert.equal(r.consultas,0);
});

test('simulador apagado por defecto y bloqueado en produccion aunque la bandera sea true',async()=>{
 const {modoPruebaPermitido}=await import('../src/pagos-prueba.js');
 assert.equal(modoPruebaPermitido({}),false);
 assert.equal(modoPruebaPermitido({PAGOS_PRUEBA_HABILITADOS:'false'}),false);
 assert.equal(modoPruebaPermitido({PAGOS_PRUEBA_HABILITADOS:'true',NODE_ENV:'production'}),false);
 assert.equal(modoPruebaPermitido({PAGOS_PRUEBA_HABILITADOS:'true',NODE_ENV:'development'}),true);
});
