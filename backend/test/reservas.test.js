import {test} from 'node:test';
import assert from 'node:assert/strict';
import {crearApp} from '../src/app.js';
import {ErrorHttp} from '../src/errores.js';
import {crearVerificadorFirebase} from '../src/autenticacion.js';
import {crearReservasRepository} from '../src/reservas-repository.js';

const id='00000000-0000-4000-8000-000000000001';
const token='prueba.usuario.firma';
const cuerpo={salidaId:1,asientos:[2,1],solicitudId:id};
const identidad=async()=>({uid:'uid-del-token'});
const repoVacio=()=>({asientos:async()=>({asientos:[]}),crear:async()=>({id}),obtener:async()=>({id}),cancelar:async()=>({id})});

// Las identidades validas de estas pruebas son dobles inyectados, no tokens reales.
async function solicitar(ruta,{metodo='GET',body,auth=true,headers={},verificarToken=identidad,repo=repoVacio()}={}) {
  const server=crearApp({}, {verificarToken,reservasRepository:repo}).listen(0,'127.0.0.1');
  await new Promise(resolve=>server.once('listening',resolve));
  try {
    const cabeceras={...headers};
    if(auth) cabeceras.authorization='Bearer '+token;
    if(body!==undefined && !cabeceras['content-type']) cabeceras['content-type']='application/json';
    const r=await fetch('http://127.0.0.1:'+server.address().port+ruta,
      {method:metodo,headers:cabeceras,body:typeof body==='string'?body:body===undefined?undefined:JSON.stringify(body)});
    return {estado:r.status,cuerpo:await r.json(),cache:r.headers.get('cache-control')};
  } finally {await new Promise(resolve=>{server.close(resolve);server.closeAllConnections();});}
}

for(const [ruta,metodo] of [ ['/api/v1/salidas/1/asientos','GET'],['/api/v1/reservas','POST'],
  ['/api/v1/reservas/'+id,'GET'],['/api/v1/reservas/'+id+'/cancelacion','POST'] ]) {
  test(`rechaza ${metodo} ${ruta} sin sesion`,async()=>{
    const r=await solicitar(ruta,{metodo,auth:false});
    assert.equal(r.estado,401);
  });
}
test('token vencido no consulta el repositorio',async()=>{
  let llamado=false;
  const r=await solicitar('/api/v1/salidas/1/asientos',{
    verificarToken:async()=>{throw Object.assign(new Error('interno'),{code:'auth/id-token-expired'});},
    repo:{asientos:async()=>{llamado=true;}}
  });
  assert.equal(r.estado,401);assert.equal(llamado,false);
  assert.equal(JSON.stringify(r.cuerpo).includes('interno'),false);
});
test('fallo de verificacion temporal devuelve 503',async()=>{
  const r=await solicitar('/api/v1/salidas/1/asientos',{verificarToken:async()=>{throw new Error('red-interna');}});
  assert.equal(r.estado,503);
});
test('identidad verificada sin UID no permite acceder',async()=>{
  const r=await solicitar('/api/v1/salidas/1/asientos',{verificarToken:async()=>({})});
  assert.equal(r.estado,401);
});
test('crear reserva toma el UID solo del token y ordena asientos',async()=>{
  let recibidos;
  const r=await solicitar('/api/v1/reservas',{metodo:'POST',body:cuerpo,
    repo:{crear:async(uid,datos)=>{recibidos={uid,datos};return{id};}}});
  assert.equal(r.estado,200);
  assert.equal(recibidos.uid,'uid-del-token');
  assert.deepEqual(recibidos.datos.asientos,[1,2]);
});
for(const campo of ['uid','usuarioUid','usuario_uid','precio','total','estado']) {
  test(`rechaza campo controlado por servidor: ${campo}`,async()=>{
    const r=await solicitar('/api/v1/reservas',{metodo:'POST',body:{...cuerpo,[campo]:'manipulado'}});
    assert.equal(r.estado,400);
  });
}
for(const [nombre,datos] of [ ['asientos repetidos',{...cuerpo,asientos:[1,1]}],
  ['salida como texto',{...cuerpo,salidaId:'1'}],['UUID invalido',{...cuerpo,solicitudId:'no-uuid'}],
  ['mas de nueve asientos',{...cuerpo,asientos:[1,2,3,4,5,6,7,8,9,10]}],['lista vacia',{...cuerpo,asientos:[]}] ]) {
  test(`rechaza ${nombre}`,async()=>{
    const r=await solicitar('/api/v1/reservas',{metodo:'POST',body:datos});
    assert.equal(r.estado,400);
  });
}
test('JSON malformado devuelve 400',async()=>{
  assert.equal((await solicitar('/api/v1/reservas',{metodo:'POST',body:'{invalido'})).estado,400);
});
test('cuerpo demasiado grande devuelve 413',async()=>{
  assert.equal((await solicitar('/api/v1/reservas',{metodo:'POST',body:{dato:'x'.repeat(9000)}})).estado,413);
});
test('formato de cuerpo distinto de JSON devuelve 415',async()=>{
  assert.equal((await solicitar('/api/v1/reservas',{metodo:'POST',body:'texto',headers:{'content-type':'text/plain'}})).estado,415);
});
test('consulta de reserva pasa el propietario al repositorio',async()=>{
  let propietario;
  const r=await solicitar('/api/v1/reservas/'+id,{repo:{obtener:async(uid)=>{propietario=uid;return{id};}}});
  assert.equal(r.estado,200);assert.equal(propietario,'uid-del-token');
});
test('cancelacion pasa el propietario del token',async()=>{
  let propietario;
  const r=await solicitar('/api/v1/reservas/'+id+'/cancelacion',{metodo:'POST',repo:{cancelar:async(uid)=>{propietario=uid;return{id};}}});
  assert.equal(r.estado,200);assert.equal(propietario,'uid-del-token');
});
test('reserva ajena devuelve 404 sin informacion interna',async()=>{
  const r=await solicitar('/api/v1/reservas/'+id,{repo:{obtener:async()=>{throw new ErrorHttp(404,'RESERVA_NO_ENCONTRADA','No se encontro la reserva.');}}});
  assert.equal(r.estado,404);
});
test('asiento ocupado devuelve 409',async()=>{
  const r=await solicitar('/api/v1/reservas',{metodo:'POST',body:cuerpo,
    repo:{crear:async()=>{throw new ErrorHttp(409,'ASIENTO_NO_DISPONIBLE','Actualiza la seleccion.');}}});
  assert.equal(r.estado,409);
});
test('asientos rechaza un id de salida fuera de rango',async()=>{
  assert.equal((await solicitar('/api/v1/salidas/9999999999/asientos')).estado,400);
});
test('una transaccion fallida hace rollback y libera la conexion',async()=>{
  const consultas=[];let liberado=false;
  const cliente={query:async sql=>{consultas.push(sql);if(sql.startsWith('SELECT id'))throw Object.assign(new Error('ASIENTO_NO_DISPONIBLE'),{code:'P0001'});return{rows:[]};},release:()=>{liberado=true;}};
  const repo=crearReservasRepository({connect:async()=>cliente});
  await assert.rejects(()=>repo.crear('uid',cuerpo),e=>e.estado===409);
  assert.ok(consultas.includes('ROLLBACK'));
  assert.ok(!consultas.includes('COMMIT'));assert.equal(liberado,true);
});
test('Firebase real rechaza tokens de otro proyecto antes de consultar certificados',async()=>{
  const ahora=Math.floor(Date.now()/1000);
  const b64=valor=>Buffer.from(JSON.stringify(valor)).toString('base64url');
  const jwt=b64({alg:'RS256',kid:'prueba'})+'.'+b64({aud:'otro-proyecto',iss:'https://securetoken.google.com/otro-proyecto',sub:'usuario',iat:ahora,exp:ahora+3600})+'.ZmlybWE';
  await assert.rejects(()=>crearVerificadorFirebase('chota-bus')(jwt),e=>e.code==='auth/argument-error');
});
test('sin configuracion Firebase no admite la solicitud',async()=>{
  await assert.rejects(()=>crearVerificadorFirebase('')('token'),e=>e.estado===503);
});

test('respuestas de reservas no se guardan en cache',async()=>{
  const r=await solicitar('/api/v1/reservas/'+id);
  assert.equal(r.cache,'no-store');
});
test('cancelacion rechaza un cuerpo que no sea JSON',async()=>{
  const r=await solicitar('/api/v1/reservas/'+id+'/cancelacion',{metodo:'POST',body:'texto',headers:{'content-type':'text/plain'}});
  assert.equal(r.estado,415);
});
test('Firebase real rechaza un algoritmo diferente de RS256',async()=>{
  const ahora=Math.floor(Date.now()/1000);
  const b64=valor=>Buffer.from(JSON.stringify(valor)).toString('base64url');
  const jwt=b64({alg:'HS256',kid:'prueba'})+'.'+b64({aud:'chota-bus',iss:'https://securetoken.google.com/chota-bus',sub:'usuario',iat:ahora,exp:ahora+3600})+'.ZmlybWE';
  await assert.rejects(()=>crearVerificadorFirebase('chota-bus')(jwt),e=>e.code==='auth/argument-error');
});
