import{test}from'node:test';
import assert from'node:assert/strict';
import{validarPasajeros}from'../src/pasajeros-validacion.js';
import{crearApp}from'../src/app.js';
const id='00000000-0000-4000-8000-000000000001';
const p={asiento:1,nombreCompleto:' Pasajero de prueba ',dni:'00000001',celular:'900000001'};
test('normaliza nombre y conserva ceros iniciales del DNI',()=>{
  const datos=validarPasajeros({pasajeros:[p]});
  assert.equal(datos[0].nombreCompleto,'Pasajero de prueba');assert.equal(datos[0].dni,'00000001');
});
for(const[nombre,cuerpo]of[
  ['lista vacia',{pasajeros:[]}],['campo UID',{pasajeros:[p],uid:'otra-persona'}],
  ['nombre vacio',{pasajeros:[{...p,nombreCompleto:' '}]}],['DNI numerico',{pasajeros:[{...p,dni:12345678}]}],
  ['celular corto',{pasajeros:[{...p,celular:'123'}]}],['precio en pasajero',{pasajeros:[{...p,precio:1}]}],
  ['asiento duplicado',{pasajeros:[p,{...p,dni:'00000002'}]}],
  ['DNI duplicado',{pasajeros:[p,{...p,asiento:2}]}]]){
  test('rechaza '+nombre,()=>assert.throws(()=>validarPasajeros(cuerpo),e=>e.estado===400));
}
async function pedir(ruta,metodo,autorizado){
  let propietario;
  const app=crearApp({},{verificarToken:async()=>({uid:'uid-token'}),reservasRepository:{
    guardarPasajeros:async(uid)=>{propietario=uid;return{datos_completos:true};},
    resumen:async(uid)=>{propietario=uid;return{datos_completos:true};}
  }});
  const server=app.listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));
  try{
    const respuesta=await fetch('http://127.0.0.1:'+server.address().port+ruta,{method:metodo,
      headers:{...(autorizado?{authorization:'Bearer prueba.usuario.firma'}:{}),'content-type':'application/json'},
      body:metodo==='PUT'?JSON.stringify({pasajeros:[p]}):undefined});
    return{estado:respuesta.status,propietario,cache:respuesta.headers.get('cache-control')};
  }finally{await new Promise(r=>{server.close(r);server.closeAllConnections();});}
}
for(const[accion,metodo]of[['pasajeros','PUT'],['resumen','GET']]){
  test(accion+' exige sesion',async()=>assert.equal((await pedir('/api/v1/reservas/'+id+'/'+accion,metodo,false)).estado,401));
  test(accion+' usa UID del token y no cachea datos',async()=>{
    const r=await pedir('/api/v1/reservas/'+id+'/'+accion,metodo,true);
    assert.equal(r.estado,200);assert.equal(r.propietario,'uid-token');assert.equal(r.cache,'no-store');
  });
}
