import {crearApp} from '../src/app.js';
import {SQL_SALIDAS} from '../src/busqueda.js';
import {crearHistorialRepository} from '../src/historial.js';
import {crearBoletosRepository} from '../src/boletos-repository.js';
import {crearReservasRepository} from '../src/reservas-repository.js';
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import pg from 'pg';

// Cada ejecucion usa un esquema temporal aislado; no crea reservas en public.
const esquema = 'pruebas_reservas_' + randomUUID().replaceAll('-', '');
if (!/^pruebas_reservas_[a-f0-9]{32}$/.test(esquema)) throw new Error('Esquema de prueba invalido.');
const pool = new pg.Pool({ max: 5, connectionTimeoutMillis: 5000, statement_timeout: 10000,
  options: `-c search_path=${esquema},public -c timezone=America/Lima` });

before(async () => {
  await pool.query(`CREATE SCHEMA "${esquema}"`);
  try {
    await pool.query(await readFile(new URL('../sql/001_esquema_inicial.sql', import.meta.url), 'utf8'));
    await pool.query(await readFile(new URL('../sql/005_modelo_reservas.sql', import.meta.url), 'utf8'));
    await pool.query(await readFile(new URL('../sql/007_pasajeros_reserva.sql', import.meta.url), 'utf8'));
    await pool.query(await readFile(new URL('../sql/008_saldo_pruebas.sql', import.meta.url), 'utf8'));
    await pool.query(await readFile(new URL('../sql/009_boletos_prueba.sql', import.meta.url), 'utf8'));
    await pool.query(await readFile(new URL('../sql/010_programacion_horaria.sql', import.meta.url), 'utf8'));
  } catch (error) {
    await pool.query('ROLLBACK');
    throw error;
  }
});
after(async () => {
  try { await pool.query(`DROP SCHEMA IF EXISTS "${esquema}" CASCADE`); }
  finally { await pool.end(); }
});

async function salida(conInventario = true) {
  const o = (await pool.query("INSERT INTO terminales(nombre,ciudad,direccion) VALUES('Terminal de prueba','Prueba','Direccion de prueba') RETURNING id")).rows[0].id;
  const d = (await pool.query("INSERT INTO terminales(nombre,ciudad,direccion) VALUES('Terminal de prueba','Prueba','Direccion de prueba') RETURNING id")).rows[0].id;
  const r = (await pool.query('INSERT INTO rutas(terminal_origen_id, terminal_destino_id) VALUES($1,$2) RETURNING id', [o,d])).rows[0].id;
  const s = (await pool.query(`INSERT INTO salidas(ruta_id,fecha_hora_salida,fecha_hora_llegada,precio,capacidad)
    VALUES($1,clock_timestamp()+INTERVAL '2 days',clock_timestamp()+INTERVAL '2 days 3 hours',30,3) RETURNING id`, [r])).rows[0].id;
  if (conInventario) {
    const v = (await pool.query('INSERT INTO vehiculos(placa,nombre) VALUES($1,$2) RETURNING id', ['PRUEBA-'+s,'Vehiculo de prueba'])).rows[0].id;
    await pool.query('INSERT INTO asientos(vehiculo_id,numero,fila,columna) SELECT $1,n,1,n FROM generate_series(1,3) n', [v]);
    await pool.query('UPDATE salidas SET vehiculo_id=$1 WHERE id=$2', [v,s]);
    await pool.query('INSERT INTO salida_asientos(salida_id,vehiculo_id,numero) SELECT $1,$2,n FROM generate_series(1,3) n',[s,v]);
  }
  return s;
}
async function reservar(s, numeros, uid = 'usuario-prueba', solicitud = randomUUID()) {
  return (await pool.query('SELECT * FROM reservar_asientos($1::integer,$2::text,$3::integer[],$4::uuid)',[s,uid,numeros,solicitud])).rows[0];
}
async function libres(s) {
  return Number((await pool.query('SELECT count(*) FROM disponibilidad_asientos WHERE salida_id=$1 AND disponible',[s])).rows[0].count);
}

test('los scripts DEMO crean seis inventarios de quince asientos sin duplicarse', async () => {
  await pool.query(await readFile(new URL('../sql/002_datos_demo.sql', import.meta.url), 'utf8'));
  const seed=await readFile(new URL('../sql/006_vehiculos_asientos_demo.sql', import.meta.url),'utf8');
  await pool.query(seed);
  await pool.query(seed);
  const inventarios=(await pool.query('SELECT salida_id,count(*) AS total FROM disponibilidad_asientos GROUP BY salida_id')).rows;
  assert.equal(inventarios.length,6);
  assert.ok(inventarios.every(i=>Number(i.total)===15));
});
test('inventario nuevo contiene tres asientos disponibles', async () => {
  assert.equal(await libres(await salida()),3);
});
test('reserva varios asientos y calcula importe con el precio SQL', async () => {
  const s = await salida();
  const r = await reservar(s,[1,2]);
  assert.equal(r.cantidad,2);
  assert.equal(r.precio_unitario,'30.00');
  assert.equal(r.total,'60.00');
  assert.equal(r.estado,'PENDIENTE');
  assert.equal(r.vence_en - r.creado_en, 600000);
  assert.equal(await libres(s),1);
});
test('dos usuarios simultaneos no reservan el mismo asiento', async () => {
  const s = await salida();
  const resultados = await Promise.allSettled([reservar(s,[1],'usuario-a'),reservar(s,[1],'usuario-b')]);
  assert.equal(resultados.filter(r=>r.status==='fulfilled').length,1);
  assert.match(resultados.find(r=>r.status==='rejected').reason.message,/ASIENTO_NO_DISPONIBLE/);
  assert.equal(await libres(s),2);
});
test('el mismo numero puede reservarse en salidas diferentes', async () => {
  const a = await salida(), b = await salida();
  const resultados = await Promise.all([reservar(a,[1]),reservar(b,[1])]);
  assert.equal(resultados.length,2);
});
test('reintento con la misma solicitud devuelve la misma reserva', async () => {
  const s = await salida(), clave = randomUUID();
  const a = await reservar(s,[2,1],'usuario',clave);
  const b = await reservar(s,[1,2],'usuario',clave);
  assert.equal(a.id,b.id);
  assert.equal(await libres(s),1);
});
test('la misma solicitud simultanea tampoco duplica reservas', async () => {
  const s=await salida(), clave=randomUUID();
  const [a,b]=await Promise.all([reservar(s,[1],'usuario',clave),reservar(s,[1],'usuario',clave)]);
  assert.equal(a.id,b.id);
});
test('rechaza reutilizar la solicitud con otros asientos', async () => {
  const s = await salida(), clave = randomUUID();
  await reservar(s,[1],'usuario',clave);
  await assert.rejects(()=>reservar(s,[2],'usuario',clave),/SOLICITUD_REUTILIZADA_CON_OTROS_DATOS/);
  assert.equal(await libres(s),2);
});
test('si un asiento esta ocupado no reserva parcialmente los demas', async () => {
  const s=await salida();
  await reservar(s,[1],'usuario-a');
  await assert.rejects(()=>reservar(s,[1,2],'usuario-b'),/ASIENTO_NO_DISPONIBLE/);
  assert.equal(await libres(s),2);
  assert.equal((await reservar(s,[2],'usuario-c')).cantidad,1);
});
test('la reserva vencida deja de ocupar y permite reservar otra vez', async () => {
  const s=await salida(), r=await reservar(s,[1],'usuario-a');
  await pool.query(`UPDATE reservas SET creado_en=clock_timestamp()-INTERVAL '20 minutes',
    vence_en=clock_timestamp()-INTERVAL '1 minute' WHERE id=$1`,[r.id]);
  assert.equal(await libres(s),3);
  await reservar(s,[1],'usuario-b');
  const anterior=(await pool.query('SELECT estado FROM reservas WHERE id=$1',[r.id])).rows[0];
  assert.equal(anterior.estado,'EXPIRADA');
  assert.equal(await libres(s),2);
});
test('solo el propietario puede cancelar y la cancelacion libera el asiento', async () => {
  const s=await salida(), r=await reservar(s,[1],'propietario');
  await assert.rejects(()=>pool.query('SELECT * FROM cancelar_reserva($1,$2)',[r.id,'otra-persona']),/RESERVA_NO_ENCONTRADA/);
  assert.equal(await libres(s),2);
  for (let i=0;i<2;i++) {
    const cancelada=(await pool.query('SELECT * FROM cancelar_reserva($1,$2)',[r.id,'propietario'])).rows[0];
    assert.equal(cancelada.estado,'CANCELADA');
  }
  assert.equal(await libres(s),3);
});
test('no cancela una reserva ya confirmada', async () => {
  const s=await salida(), r=await reservar(s,[1]);
  await crearReservasRepository(pool).guardarPasajeros('usuario-prueba',r.id,[pasajero(1)]);
  await pagar(r.id,'usuario-prueba');
  await assert.rejects(()=>pool.query('SELECT * FROM cancelar_reserva($1,$2)',[r.id,'usuario-prueba']),/RESERVA_NO_CANCELABLE/);
  assert.equal(await libres(s),2);
});
test('rechaza listas vacias, repetidas o con asientos inexistentes', async () => {
  const s=await salida();
  for(const numeros of [[],[1,1],[0],[null]]) {
    await assert.rejects(()=>reservar(s,numeros),/DATOS_RESERVA_INVALIDOS/);
  }
  await assert.rejects(()=>reservar(s,[99]),/ASIENTO_NO_DISPONIBLE/);
  assert.equal(await libres(s),3);
});
test('rechaza mas de nueve pasajeros', async () => {
  await assert.rejects(async ()=>reservar(await salida(),[1,2,3,4,5,6,7,8,9,10]),/DATOS_RESERVA_INVALIDOS/);
});
test('no reserva salidas pasadas o canceladas', async () => {
  const a=await salida(),b=await salida();
  await pool.query("UPDATE salidas SET estado='CANCELADA' WHERE id=$1",[a]);
  await pool.query("UPDATE salidas SET fecha_hora_salida=clock_timestamp()-INTERVAL '1 hour' WHERE id=$1",[b]);
  await assert.rejects(()=>reservar(a,[1]),/SALIDA_NO_DISPONIBLE/);
  await assert.rejects(()=>reservar(b,[1]),/SALIDA_NO_DISPONIBLE/);
});
test('no ofrece salidas sin vehiculo ni inventario', async () => {
  const s=await salida(false);
  assert.equal(await libres(s),0);
  await assert.rejects(()=>reservar(s,[1]),/SALIDA_NO_DISPONIBLE/);
});
test('indice unico protege incluso ante una insercion directa duplicada', async () => {
  const s=await salida();
  await reservar(s,[1],'a');
  const b=await reservar(s,[2],'b');
  await assert.rejects(()=>pool.query('INSERT INTO reserva_asientos(reserva_id,salida_id,numero) VALUES($1,$2,1)',[b.id,s]),
    e=>e.code==='23505');
  assert.equal(await libres(s),1);
});
test('no permite cambiar el vehiculo mientras existen asientos asignados a la salida', async () => {
  const s=await salida();
  const v=(await pool.query("INSERT INTO vehiculos(placa,nombre) VALUES('OTRO-TEST','Otro') RETURNING id")).rows[0].id;
  await assert.rejects(()=>pool.query('UPDATE salidas SET vehiculo_id=$1 WHERE id=$2',[v,s]),e=>e.code==='23503');
});


// La identidad se simula solo en este servidor de pruebas; SQL y HTTP son reales.
async function conApi(operacion) {
  const server=crearApp(pool,{pagosPruebaHabilitados:()=>true,verificarToken:async token=>({uid:token==='prueba.usuario_a.firma'?'usuario-a':'usuario-b'})}).listen(0,'127.0.0.1');
  await new Promise(resolve=>server.once('listening',resolve));
  const pedir=async(ruta,metodo='GET',body,usuario='a')=>{
    const r=await fetch('http://127.0.0.1:'+server.address().port+ruta,{method:metodo,
      headers:{authorization:'Bearer prueba.usuario_'+usuario+'.firma',...(body===undefined?{}:{'content-type':'application/json'})},
      body:body===undefined?undefined:JSON.stringify(body)});
    return {estado:r.status,cuerpo:await r.json()};
  };
  try{return await operacion(pedir);}finally{await new Promise(resolve=>{server.close(resolve);server.closeAllConnections();});}
}
test('HTTP y SQL permiten reservar, consultar y cancelar solo al propietario',async()=>{
  const s=await salida();
  await conApi(async pedir=>{
    assert.equal((await pedir('/api/v1/salidas/'+s+'/asientos')).cuerpo.datos.asientos_libres,3);
    const datos={salidaId:s,asientos:[1,2],solicitudId:randomUUID()};
    const creada=await pedir('/api/v1/reservas','POST',datos);
    assert.equal(creada.estado,200);
    assert.equal(creada.cuerpo.datos.total,'60.00');
    assert.equal('usuario_uid' in creada.cuerpo.datos,false);
    const id=creada.cuerpo.datos.id;
    assert.equal((await pedir('/api/v1/reservas','POST',datos)).cuerpo.datos.id,id);
    assert.equal((await pedir('/api/v1/reservas/'+id,'GET',undefined,'b')).estado,404);
    assert.equal((await pedir('/api/v1/reservas/'+id+'/cancelacion','POST',undefined,'b')).estado,404);
    assert.equal((await pedir('/api/v1/salidas/'+s+'/asientos')).cuerpo.datos.asientos_libres,1);
    assert.equal((await pedir('/api/v1/reservas/'+id+'/cancelacion','POST')).cuerpo.datos.estado,'CANCELADA');
    assert.equal((await pedir('/api/v1/salidas/'+s+'/asientos')).cuerpo.datos.asientos_libres,3);
  });
});
test('dos solicitudes HTTP simultaneas reciben exito y conflicto para el mismo asiento',async()=>{
  const s=await salida();
  await conApi(async pedir=>{
    const resultados=await Promise.all([
      pedir('/api/v1/reservas','POST',{salidaId:s,asientos:[1],solicitudId:randomUUID()},'a'),
      pedir('/api/v1/reservas','POST',{salidaId:s,asientos:[1],solicitudId:randomUUID()},'b')]);
    assert.deepEqual(resultados.map(r=>r.estado).sort(),[200,409]);
  });
});
test('consultar reserva vencida informa EXPIRADA sin exigir una escritura de limpieza',async()=>{
  const s=await salida();
  const repo=crearReservasRepository(pool);
  const r=await repo.crear('usuario-a',{salidaId:s,asientos:[1],solicitudId:randomUUID()});
  await pool.query("UPDATE reservas SET creado_en=clock_timestamp()-INTERVAL '20 minutes',vence_en=clock_timestamp()-INTERVAL '1 minute' WHERE id=$1",[r.id]);
  await conApi(async pedir=>{
    assert.equal((await pedir('/api/v1/reservas/'+r.id)).cuerpo.datos.estado,'EXPIRADA');
    assert.equal((await pedir('/api/v1/salidas/'+s+'/asientos')).cuerpo.datos.asientos_libres,3);
  });
});


const pasajero=(asiento,dni='00000001')=>({asiento,nombreCompleto:'PASAJERO DE PRUEBA',dni,celular:'900000001'});
test('guarda un pasajero por asiento sin cambiar precio o vencimiento',async()=>{
  const s=await salida(),repo=crearReservasRepository(pool);
  const r=await repo.crear('usuario-a',{salidaId:s,asientos:[1,2],solicitudId:randomUUID()});
  const resumen=await repo.guardarPasajeros('usuario-a',r.id,[pasajero(1),pasajero(2,'00000002')]);
  assert.equal(resumen.datos_completos,true);assert.equal(resumen.puede_continuar,true);
  assert.equal(resumen.reserva.total,'60.00');
  assert.equal(new Date(resumen.reserva.vence_en).getTime(),r.vence_en.getTime());
  assert.deepEqual(resumen.pasajeros.map(p=>p.asiento),[1,2]);
  assert.equal('usuario_uid' in resumen.reserva,false);
});
test('PUT repetido no duplica pasajeros y permite intercambiar DNI entre asientos',async()=>{
  const s=await salida(),repo=crearReservasRepository(pool);
  const r=await repo.crear('usuario-a',{salidaId:s,asientos:[1,2],solicitudId:randomUUID()});
  for(let i=0;i<2;i++)await repo.guardarPasajeros('usuario-a',r.id,[pasajero(1),pasajero(2,'00000002')]);
  const resumen=await repo.guardarPasajeros('usuario-a',r.id,[pasajero(1,'00000002'),pasajero(2)]);
  assert.equal(resumen.pasajeros.length,2);assert.equal(resumen.pasajeros[0].dni,'00000002');
});
test('un formulario incompleto no borra los datos guardados',async()=>{
  const s=await salida(),repo=crearReservasRepository(pool);
  const r=await repo.crear('usuario-a',{salidaId:s,asientos:[1,2],solicitudId:randomUUID()});
  await repo.guardarPasajeros('usuario-a',r.id,[pasajero(1),pasajero(2,'00000002')]);
  await assert.rejects(()=>repo.guardarPasajeros('usuario-a',r.id,[pasajero(1)]),e=>e.estado===400);
  assert.equal((await repo.resumen('usuario-a',r.id)).pasajeros.length,2);
});
test('no permite nombres vacios ni asientos fuera de la reserva',async()=>{
  const s=await salida(),repo=crearReservasRepository(pool);
  const r=await repo.crear('usuario-a',{salidaId:s,asientos:[1],solicitudId:randomUUID()});
  await assert.rejects(()=>repo.guardarPasajeros('usuario-a',r.id,[{...pasajero(1),nombreCompleto:'  '}]),e=>e.estado===400);
  await assert.rejects(()=>repo.guardarPasajeros('usuario-a',r.id,[pasajero(2)]),e=>e.estado===400);
  assert.equal((await repo.resumen('usuario-a',r.id)).datos_completos,false);
});
test('rechaza DNI repetido dentro de la misma reserva',async()=>{
  const s=await salida(),repo=crearReservasRepository(pool);
  const r=await repo.crear('usuario-a',{salidaId:s,asientos:[1,2],solicitudId:randomUUID()});
  await assert.rejects(()=>repo.guardarPasajeros('usuario-a',r.id,[pasajero(1),pasajero(2)]),e=>e.estado===400);
});
test('resumen y guardado de pasajeros rechazan al usuario ajeno',async()=>{
  const s=await salida(),repo=crearReservasRepository(pool);
  const r=await repo.crear('usuario-a',{salidaId:s,asientos:[1],solicitudId:randomUUID()});
  await assert.rejects(()=>repo.resumen('usuario-b',r.id),e=>e.estado===404);
  await assert.rejects(()=>repo.guardarPasajeros('usuario-b',r.id,[pasajero(1)]),e=>e.estado===404);
});
test('reserva vencida o cancelada no admite pasajeros y bloquea continuar',async()=>{
  const s=await salida(),repo=crearReservasRepository(pool);
  const r=await repo.crear('usuario-a',{salidaId:s,asientos:[1],solicitudId:randomUUID()});
  await pool.query("UPDATE reservas SET creado_en=clock_timestamp()-INTERVAL '20 minutes',vence_en=clock_timestamp()-INTERVAL '1 minute' WHERE id=$1",[r.id]);
  await assert.rejects(()=>repo.guardarPasajeros('usuario-a',r.id,[pasajero(1)]),e=>e.estado===409);
  assert.equal((await repo.resumen('usuario-a',r.id)).puede_continuar,false);
  const otra=await repo.crear('usuario-a',{salidaId:s,asientos:[2],solicitudId:randomUUID()});
  await repo.cancelar('usuario-a',otra.id);
  await assert.rejects(()=>repo.guardarPasajeros('usuario-a',otra.id,[pasajero(2)]),e=>e.estado===409);
});
test('cancelacion concurrente no deja la reserva apta para continuar',async()=>{
  const s=await salida(),repo=crearReservasRepository(pool);
  const r=await repo.crear('usuario-a',{salidaId:s,asientos:[1],solicitudId:randomUUID()});
  await Promise.allSettled([repo.guardarPasajeros('usuario-a',r.id,[pasajero(1)]),repo.cancelar('usuario-a',r.id)]);
  const resumen=await repo.resumen('usuario-a',r.id);
  assert.equal(resumen.reserva.estado,'CANCELADA');assert.equal(resumen.puede_continuar,false);
});
test('HTTP permite guardar y leer resumen sin exponerlo a otra cuenta',async()=>{
  const s=await salida();
  await conApi(async pedir=>{
    const r=(await pedir('/api/v1/reservas','POST',{salidaId:s,asientos:[1],solicitudId:randomUUID()})).cuerpo.datos;
    const guardado=await pedir('/api/v1/reservas/'+r.id+'/pasajeros','PUT',{pasajeros:[pasajero(1)]});
    assert.equal(guardado.estado,200);assert.equal(guardado.cuerpo.datos.datos_completos,true);
    assert.equal((await pedir('/api/v1/reservas/'+r.id+'/resumen')).cuerpo.datos.reserva.total,'30.00');
    assert.equal((await pedir('/api/v1/reservas/'+r.id+'/resumen','GET',undefined,'b')).estado,404);
  });
});

// Estas compras solo afectan el esquema temporal de pruebas, nunca el saldo de una persona.
async function listaParaPago(uid,precio=30){
 const s=await salida();await pool.query('UPDATE salidas SET precio=$1 WHERE id=$2',[precio,s]);
 const repo=crearReservasRepository(pool);
 const r=await repo.crear(uid,{salidaId:s,asientos:[1],solicitudId:randomUUID()});
 await repo.guardarPasajeros(uid,r.id,[pasajero(1)]);return r;
}
const pagar=(id,uid)=>pool.query('SELECT pagar_reserva_prueba($1,$2) AS id',[id,uid]);
test('saldo inicial se asigna una vez, no se reinicia tras una compra y es privado',async()=>{
 await conApi(async pedir=>{
  const antes=(await pedir('/api/v1/saldo-prueba')).cuerpo.datos.saldo;
  const r=await listaParaPago('usuario-a');
  const pago=await pedir('/api/v1/reservas/'+r.id+'/pago-prueba','POST');
  assert.equal(pago.estado,200);assert.equal(pago.cuerpo.datos.modo,'PRUEBA');
  assert.equal(Number(pago.cuerpo.datos.saldo),Number(antes)-30);
  assert.equal((await pedir('/api/v1/saldo-prueba')).cuerpo.datos.saldo,pago.cuerpo.datos.saldo);
  assert.equal((await pedir('/api/v1/reservas/'+r.id+'/pago-prueba','POST',undefined,'b')).estado,404);
  assert.equal((await pedir('/api/v1/reservas/'+r.id+'/pago-prueba','POST',{importe:1})).estado,400);
 });
});
test('dos pagos simultaneos de una reserva descuentan una sola vez',async()=>{
 const uid='pago-repetido',r=await listaParaPago(uid);
 const [a,b]=await Promise.all([pagar(r.id,uid),pagar(r.id,uid)]);
 assert.equal(a.rows[0].id,b.rows[0].id);
 assert.equal((await pool.query('SELECT saldo::text FROM saldos_prueba WHERE usuario_uid=$1',[uid])).rows[0].saldo,'270.00');
 assert.equal((await crearReservasRepository(pool).obtener(uid,r.id)).estado,'CONFIRMADA');
 assert.equal(await libres(r.salida_id),2);
 await assert.rejects(()=>crearReservasRepository(pool).cancelar(uid,r.id),e=>e.estado===409);
});
test('dos reservas simultaneas comparten saldo y nunca lo dejan negativo',async()=>{
 const uid='pago-concurrente',a=await listaParaPago(uid,200),b=await listaParaPago(uid,200);
 const resultados=await Promise.allSettled([pagar(a.id,uid),pagar(b.id,uid)]);
 assert.equal(resultados.filter(x=>x.status==='fulfilled').length,1);
 assert.equal(resultados.find(x=>x.status==='rejected').reason.message,'SALDO_INSUFICIENTE');
 assert.equal((await pool.query('SELECT saldo::text FROM saldos_prueba WHERE usuario_uid=$1',[uid])).rows[0].saldo,'100.00');
 assert.equal((await pool.query('SELECT count(*)::integer n FROM pagos_prueba WHERE usuario_uid=$1',[uid])).rows[0].n,1);
});
test('saldo insuficiente devuelve 422 y no confirma ni descuenta',async()=>{
 const r=await listaParaPago('usuario-a',301);
 await conApi(async pedir=>{
  const antes=(await pedir('/api/v1/saldo-prueba')).cuerpo.datos.saldo;
  assert.equal((await pedir('/api/v1/reservas/'+r.id+'/pago-prueba','POST')).estado,422);
  assert.equal((await pedir('/api/v1/saldo-prueba')).cuerpo.datos.saldo,antes);
  assert.equal((await pedir('/api/v1/reservas/'+r.id)).cuerpo.datos.estado,'PENDIENTE');
 });
});
test('reserva vencida, cancelada o sin pasajeros no permite pago',async()=>{
 const uid='pago-no-vigente',a=await listaParaPago(uid),b=await listaParaPago(uid);
 await pool.query("UPDATE reservas SET creado_en=clock_timestamp()-INTERVAL '20 minutes',vence_en=clock_timestamp()-INTERVAL '1 minute' WHERE id=$1",[a.id]);
 await crearReservasRepository(pool).cancelar(uid,b.id);
 await assert.rejects(()=>pagar(a.id,uid),/RESERVA_NO_VIGENTE/);
 await assert.rejects(()=>pagar(b.id,uid),/RESERVA_NO_VIGENTE/);
 const c=await reservar(await salida(),[1],uid);
 await assert.rejects(()=>pagar(c.id,uid),/PASAJEROS_INCOMPLETOS/);
 assert.equal((await pool.query('SELECT count(*)::integer n FROM pagos_prueba WHERE usuario_uid=$1',[uid])).rows[0].n,0);
});
test('pago y cancelacion concurrentes mantienen saldo y asientos coherentes',async()=>{
 const uid='pago-vs-cancelar',r=await listaParaPago(uid),repo=crearReservasRepository(pool);
 await Promise.allSettled([pagar(r.id,uid),repo.cancelar(uid,r.id)]);
 const estado=(await repo.obtener(uid,r.id)).estado;
 const n=(await pool.query('SELECT count(*)::integer n FROM pagos_prueba WHERE reserva_id=$1',[r.id])).rows[0].n;
 assert.ok(['CONFIRMADA','CANCELADA'].includes(estado));assert.equal(n,estado==='CONFIRMADA'?1:0);
 assert.equal(await libres(r.salida_id),estado==='CONFIRMADA'?2:3);
});


test('emite un boleto por pasajero con datos del viaje y sumatoria del pago',async()=>{
 const uid='boletos-varios',s=await salida(),repo=crearReservasRepository(pool);
 const r=await repo.crear(uid,{salidaId:s,asientos:[1,2],solicitudId:randomUUID()});
 await repo.guardarPasajeros(uid,r.id,[pasajero(1),pasajero(2,'00000002')]);
 await pagar(r.id,uid);
 const boletos=await crearBoletosRepository(pool).deReserva(uid,r.id);
 assert.equal(boletos.length,2);assert.deepEqual(boletos.map(b=>b.asiento),[1,2]);
 assert.equal(new Set(boletos.map(b=>b.codigo)).size,2);
 assert.equal(boletos.reduce((s,b)=>s+Number(b.importe_unitario),0),Number(r.total));
 for(const b of boletos){
  assert.match(b.codigo,/^CBP-[A-F0-9]{32}$/);assert.equal(b.modo,'PRUEBA');assert.equal(b.estado,'EMITIDO');
  assert.equal(b.datos_recuperados,false);assert.equal(b.moneda,'PEN');assert.equal(b.viaje.salida_id,s);
  assert.ok(b.viaje.fecha_hora_salida);assert.ok(b.viaje.embarque.direccion);assert.ok(b.viaje.llegada.direccion);
  assert.equal(b.pasajero.dni,b.asiento===1?'00000001':'00000002');
  assert.equal('usuario_uid' in b,false);assert.equal('celular' in b.pasajero,false);
 }
});
test('reintentos simultaneos conservan exactamente el mismo boleto y cargo',async()=>{
 const uid='boleto-idempotente',r=await listaParaPago(uid);
 await Promise.all([pagar(r.id,uid),pagar(r.id,uid)]);
 const repo=crearBoletosRepository(pool),antes=await repo.deReserva(uid,r.id);
 await pagar(r.id,uid);await pool.query('SELECT emitir_boletos_prueba($1)',[r.id]);
 assert.deepEqual(await repo.deReserva(uid,r.id),antes);
 assert.equal((await pool.query('SELECT saldo::text FROM saldos_prueba WHERE usuario_uid=$1',[uid])).rows[0].saldo,'270.00');
});
test('la copia del boleto no cambia si se modifica el catalogo o el pasajero despues',async()=>{
 const uid='boleto-copia',r=await listaParaPago(uid);await pagar(r.id,uid);
 const repo=crearBoletosRepository(pool),[antes]=await repo.deReserva(uid,r.id);
 await pool.query("UPDATE terminales SET direccion='Direccion posterior' WHERE id=$1",[antes.viaje.embarque.id]);
 await pool.query("UPDATE salidas SET precio=99,fecha_hora_salida=fecha_hora_salida+INTERVAL '1 minute' WHERE id=$1",[r.salida_id]);
 await pool.query("UPDATE pasajeros_reserva SET nombre_completo='NOMBRE POSTERIOR' WHERE reserva_id=$1",[r.id]);
 const despues=await repo.obtener(uid,antes.id);
 assert.deepEqual(despues.pasajero,antes.pasajero);assert.deepEqual(despues.viaje,antes.viaje);
 assert.equal(despues.importe_unitario,'30.00');
});
test('reserva temporal, vencida o cancelada no emite boletos',async()=>{
 const uid='boleto-no-pagado',r=await listaParaPago(uid),repo=crearBoletosRepository(pool);
 assert.deepEqual(await repo.deReserva(uid,r.id),[]);
 await assert.rejects(()=>pool.query('SELECT emitir_boletos_prueba($1)',[r.id]),/COMPRA_NO_CONFIRMADA/);
 await crearReservasRepository(pool).cancelar(uid,r.id);assert.deepEqual(await repo.deReserva(uid,r.id),[]);
 const e=await listaParaPago(uid);
 await pool.query("UPDATE reservas SET creado_en=clock_timestamp()-INTERVAL '20 minutes',vence_en=clock_timestamp()-INTERVAL '1 minute' WHERE id=$1",[e.id]);
 await assert.rejects(()=>pagar(e.id,uid),/RESERVA_NO_VIGENTE/);assert.deepEqual(await repo.deReserva(uid,e.id),[]);
});
test('saldo insuficiente no genera boletos',async()=>{
 const uid='boleto-sin-saldo',r=await listaParaPago(uid,301);
 await assert.rejects(()=>pagar(r.id,uid),/SALDO_INSUFICIENTE/);
 assert.deepEqual(await crearBoletosRepository(pool).deReserva(uid,r.id),[]);
});
test('fallar la emision revierte saldo, pago, boletos y confirmacion juntos',async()=>{
 const uid='boleto-fallo-atomico',r=await listaParaPago(uid);
 await pool.query('INSERT INTO saldos_prueba(usuario_uid) VALUES($1)',[uid]);
 // Esta inyeccion de fallo existe solo en el esquema temporal y afecta nuestra reserva sintetica.
 await pool.query(`CREATE FUNCTION fallo_boleto_test() RETURNS TRIGGER LANGUAGE plpgsql AS $f$
 BEGIN IF NEW.reserva_id='${r.id}'::uuid THEN RAISE EXCEPTION 'FALLO_EMISION_TEST'; END IF; RETURN NEW; END;$f$;
 CREATE TRIGGER fallo_boleto_test BEFORE INSERT ON boletos_prueba FOR EACH ROW EXECUTE FUNCTION fallo_boleto_test()`);
 try{await assert.rejects(()=>pagar(r.id,uid),/FALLO_EMISION_TEST/);}
 finally{await pool.query('DROP TRIGGER fallo_boleto_test ON boletos_prueba; DROP FUNCTION fallo_boleto_test()');}
 assert.equal((await pool.query('SELECT saldo::text FROM saldos_prueba WHERE usuario_uid=$1',[uid])).rows[0].saldo,'300.00');
 assert.equal((await crearReservasRepository(pool).obtener(uid,r.id)).estado,'PENDIENTE');
 assert.equal((await pool.query('SELECT count(*)::integer n FROM pagos_prueba WHERE reserva_id=$1',[r.id])).rows[0].n,0);
 assert.deepEqual(await crearBoletosRepository(pool).deReserva(uid,r.id),[]);
 await pagar(r.id,uid);assert.equal((await crearBoletosRepository(pool).deReserva(uid,r.id)).length,1);
});
test('recupera boletos de compras anteriores sin repetir cargos y marca datos recuperados',async()=>{
 const uid='boleto-anterior',r=await listaParaPago(uid);
 // Representa una compra hecha antes de instalar la migracion 009, solo en el esquema aislado.
 await pool.query('ALTER TABLE reservas DISABLE TRIGGER reserva_confirmada_emite_boletos');
 try{await pagar(r.id,uid);}finally{await pool.query('ALTER TABLE reservas ENABLE TRIGGER reserva_confirmada_emite_boletos');}
 await pool.query('SELECT emitir_boletos_prueba($1,TRUE)',[r.id]);
 const [b]=await crearBoletosRepository(pool).deReserva(uid,r.id);assert.equal(b.datos_recuperados,true);
 assert.equal((await pool.query('SELECT saldo::text FROM saldos_prueba WHERE usuario_uid=$1',[uid])).rows[0].saldo,'270.00');
});
test('HTTP devuelve boletos al pagar y los protege ante otra cuenta',async()=>{
 const r=await listaParaPago('usuario-a');
 await conApi(async pedir=>{
  const pago=await pedir('/api/v1/reservas/'+r.id+'/pago-prueba','POST');
  assert.equal(pago.estado,200);const [b]=pago.cuerpo.datos.boletos;assert.equal(b.modo,'PRUEBA');
  const lista=await pedir('/api/v1/reservas/'+r.id+'/boletos');assert.equal(lista.estado,200);
  assert.equal(lista.cuerpo.datos[0].id,b.id);
  const detalle=await pedir('/api/v1/boletos/'+b.id);assert.equal(detalle.estado,200);assert.equal(detalle.cuerpo.datos.aviso,'BOLETO DE PRUEBA - SIN VALOR FISCAL');
  for(const ruta of ['/api/v1/boletos/'+b.id,'/api/v1/reservas/'+r.id+'/boletos'])
   assert.equal((await pedir(ruta,'GET',undefined,'b')).estado,404);
  assert.equal((await pedir('/api/v1/boletos/'+randomUUID())).estado,404);
  const repetido=await pedir('/api/v1/reservas/'+r.id+'/pago-prueba','POST');assert.deepEqual(repetido.cuerpo.datos.boletos,pago.cuerpo.datos.boletos);
 });
});

test('no permite confirmar una reserva sin pago consistente',async()=>{
 const r=await listaParaPago('boleto-confirmacion-invalida');
 await assert.rejects(()=>pool.query("UPDATE reservas SET estado='CONFIRMADA' WHERE id=$1",[r.id]),/PAGO_INCONSISTENTE/);
 assert.equal((await crearReservasRepository(pool).obtener('boleto-confirmacion-invalida',r.id)).estado,'PENDIENTE');
});

test('lista propia pagina sin duplicados incluso con fechas de emision iguales',async()=>{
 const uid='lista-paginada',repo=crearReservasRepository(pool),s=await salida();
 const r=await repo.crear(uid,{salidaId:s,asientos:[1,2,3],solicitudId:randomUUID()});
 await repo.guardarPasajeros(uid,r.id,[pasajero(1),pasajero(2,'00000002'),pasajero(3,'00000003')]);await pagar(r.id,uid);
 const otro=await listaParaPago(uid);await pagar(otro.id,uid);
 await pool.query("UPDATE boletos_prueba SET emitido_en='2026-11-13T12:00:00.123456-05:00' WHERE reserva_id=ANY($1::uuid[])",[[r.id,otro.id]]);
 const boletosRepo=crearBoletosRepository(pool),a=await boletosRepo.listar(uid,null,2),b=await boletosRepo.listar(uid,a.siguiente,2);
 assert.equal(a.boletos.length,2);assert.equal(b.boletos.length,2);assert.equal(b.siguiente,null);
 const ids=[...a.boletos,...b.boletos].map(b=>b.id);assert.equal(new Set(ids).size,4);assert.deepEqual(ids,[...ids].sort().reverse());
 assert.deepEqual((await boletosRepo.listar('sin-compras')).boletos,[]);
 await assert.rejects(()=>boletosRepo.listar('cuenta-ajena',a.siguiente),e=>e.estado===404);
});
test('HTTP lista solo boletos de la sesion y conserva paginacion',async()=>{
 await conApi(async pedir=>{
  const lista=await pedir('/api/v1/boletos?limite=1');assert.equal(lista.estado,200);assert.equal(lista.cuerpo.datos.length,1);assert.ok(lista.cuerpo.siguiente);
  const pagina=await pedir('/api/v1/boletos?limite=1&despues='+lista.cuerpo.siguiente);
  assert.equal(pagina.estado,200);assert.notEqual(pagina.cuerpo.datos[0].id,lista.cuerpo.datos[0].id);
  const ajena=await pedir('/api/v1/boletos?despues='+lista.cuerpo.siguiente,'GET',undefined,'b');assert.equal(ajena.estado,404);
  const sinCompras=await pedir('/api/v1/boletos','GET',undefined,'b');assert.deepEqual(sinCompras.cuerpo.datos,[]);assert.equal(sinCompras.cuerpo.siguiente,null);
 });
});

test('historial diferencia compra pagada, reserva pendiente, cancelada y vencida sin modificar datos',async()=>{
 const uid='historial-estados',repo=crearReservasRepository(pool),ids=[];
 for(let i=0;i<4;i++)ids.push(await listaParaPago(uid));
 await pagar(ids[0].id,uid);await repo.cancelar(uid,ids[2].id);
 await pool.query("UPDATE reservas SET creado_en=clock_timestamp()-INTERVAL '20 minutes',vence_en=clock_timestamp()-INTERVAL '1 minute' WHERE id=$1",[ids[3].id]);
 const historial=crearHistorialRepository(pool),antes=(await pool.query('SELECT saldo::text FROM saldos_prueba WHERE usuario_uid=$1',[uid])).rows[0].saldo;
 const lista=(await historial.listar(uid)).registros;
 assert.equal(lista.length,4);
 const pagada=lista.find(r=>r.id===ids[0].id);assert.equal(pagada.estado,'CONFIRMADA');assert.equal(pagada.pagada,true);assert.equal(pagada.importe_pagado,'30.00');assert.equal(pagada.boletos_emitidos,1);assert.ok(pagada.fecha_pago);
 assert.deepEqual(lista.filter(r=>!r.pagada).map(r=>r.estado).sort(),['CANCELADA','EXPIRADA','PENDIENTE']);
 assert.ok(lista.filter(r=>!r.pagada).every(r=>r.importe_pagado==='0.00'&&r.fecha_pago===null&&r.boletos_emitidos===0));
 assert.equal((await pool.query('SELECT estado FROM reservas WHERE id=$1',[ids[3].id])).rows[0].estado,'PENDIENTE');
 assert.equal((await pool.query('SELECT saldo::text FROM saldos_prueba WHERE usuario_uid=$1',[uid])).rows[0].saldo,antes);
 assert.equal('usuario_uid' in pagada,false);assert.equal('pasajeros' in pagada,false);
});
test('historial agrupa pasajeros por reserva y usa la ruta copiada del boleto pagado',async()=>{
 const uid='historial-grupo',s=await salida(),repo=crearReservasRepository(pool);
 const r=await repo.crear(uid,{salidaId:s,asientos:[1,2],solicitudId:randomUUID()});
 await repo.guardarPasajeros(uid,r.id,[pasajero(1),pasajero(2,'00000002')]);await pagar(r.id,uid);
 const historial=crearHistorialRepository(pool),[antes]=(await historial.listar(uid)).registros;
 await pool.query("UPDATE terminales SET ciudad='Ciudad editada' WHERE id=(SELECT terminal_origen_id FROM rutas WHERE id=(SELECT ruta_id FROM salidas WHERE id=$1))",[s]);
 const [despues]=(await historial.listar(uid)).registros;
 assert.equal(despues.origen,antes.origen);assert.equal(despues.importe_pagado,'60.00');assert.equal(despues.cantidad,2);assert.equal(despues.boletos_emitidos,2);assert.deepEqual(despues.asientos,[1,2]);
});
test('historial pagina con empate de fechas y rechaza cursores de otra cuenta',async()=>{
 const uid='historial-paginas';for(let i=0;i<3;i++)await listaParaPago(uid);
 await pool.query("UPDATE reservas SET creado_en='2026-11-13T12:00:00.123456-05:00',vence_en='2026-11-13T12:10:00.123456-05:00' WHERE usuario_uid=$1",[uid]);
 const repo=crearHistorialRepository(pool),a=await repo.listar(uid,null,2),b=await repo.listar(uid,a.siguiente,2);
 assert.equal(a.registros.length,2);assert.equal(b.registros.length,1);assert.equal(b.siguiente,null);
 assert.equal(new Set([...a.registros,...b.registros].map(r=>r.id)).size,3);
 await assert.rejects(()=>repo.listar('otra-cuenta',a.siguiente),e=>e.estado===404);
 assert.deepEqual(await repo.listar('historial-vacio'),{registros:[],siguiente:null});
});
test('HTTP historial no revela reservas de otra cuenta ni acepta UID en la consulta',async()=>{
 await conApi(async pedir=>{
  const r=await pedir('/api/v1/historial?limite=1');assert.equal(r.estado,200);assert.equal(r.cuerpo.datos.length,1);assert.ok(r.cuerpo.siguiente);
  assert.equal((await pedir('/api/v1/historial?despues='+r.cuerpo.siguiente,'GET',undefined,'b')).estado,404);
  assert.equal((await pedir('/api/v1/historial?uid=intruso')).estado,400);
 });
});

test('busqueda cuenta libres reales y excluye salidas sin cupo para el grupo',async()=>{
 const s=await salida(),uid='auditoria-cupos';
 await pool.query("UPDATE terminales SET ciudad='Origen cupos' WHERE id=(SELECT terminal_origen_id FROM rutas WHERE id=(SELECT ruta_id FROM salidas WHERE id=$1))",[s]);
 await pool.query("UPDATE terminales SET ciudad='Destino cupos' WHERE id=(SELECT terminal_destino_id FROM rutas WHERE id=(SELECT ruta_id FROM salidas WHERE id=$1))",[s]);
 const fecha=(await pool.query("SELECT to_char(fecha_hora_salida AT TIME ZONE 'America/Lima','YYYY-MM-DD') fecha FROM salidas WHERE id=$1",[s])).rows[0].fecha;
 const buscar=async n=>(await pool.query(SQL_SALIDAS,['Origen cupos','Destino cupos',fecha,n])).rows;
 assert.equal((await buscar(2))[0].asientos_libres,3);
 const a=await reservar(s,[1],uid),b=await reservar(s,[2],uid);
 assert.equal((await buscar(2)).length,0);assert.equal((await buscar(1))[0].asientos_libres,1);
 await pool.query("UPDATE reservas SET creado_en=clock_timestamp()-INTERVAL '20 minutes',vence_en=clock_timestamp()-INTERVAL '1 minute' WHERE id=$1",[b.id]);
 assert.equal((await buscar(2))[0].asientos_libres,2);
 await crearReservasRepository(pool).guardarPasajeros(uid,a.id,[pasajero(1)]);await pagar(a.id,uid);
 assert.equal((await buscar(2))[0].asientos_libres,2);
});

test('programacion horaria crea 12 rutas y 13 horas exactas por dia sin duplicar',async()=>{
 const fecha=(await pool.query("SELECT ((now() AT TIME ZONE 'America/Lima')::date+1)::text fecha")).rows[0].fecha;
 await pool.query('SELECT programar_salidas_demo($1,2)',[fecha]);
 const grupos=(await pool.query(`SELECT r.id,to_char(s.fecha_hora_salida AT TIME ZONE 'America/Lima','YYYY-MM-DD') fecha,
  count(*)::integer cantidad,min(extract(hour FROM s.fecha_hora_salida AT TIME ZONE 'America/Lima')) primera,
  max(extract(hour FROM s.fecha_hora_salida AT TIME ZONE 'America/Lima')) ultima,
  bool_and(extract(minute FROM s.fecha_hora_salida AT TIME ZONE 'America/Lima')=0) exactas
  FROM salidas s JOIN rutas r ON r.id=s.ruta_id JOIN terminales o ON o.id=r.terminal_origen_id JOIN terminales d ON d.id=r.terminal_destino_id
  WHERE o.nombre LIKE 'DEMO - Terminal %' AND d.nombre LIKE 'DEMO - Terminal %'
   AND s.fecha_hora_salida>=$1::date::timestamp AT TIME ZONE 'America/Lima'
   AND s.fecha_hora_salida<($1::date+2)::timestamp AT TIME ZONE 'America/Lima'
  GROUP BY r.id,fecha`,[fecha])).rows;
 assert.equal(grupos.length,24);assert.ok(grupos.every(g=>g.cantidad===13&&Number(g.primera)===6&&Number(g.ultima)===18&&g.exactas));
 const repetidas=await Promise.all([pool.query('SELECT programar_salidas_demo($1,2) n',[fecha]),pool.query('SELECT programar_salidas_demo($1,2) n',[fecha])]);
 assert.ok(repetidas.every(r=>r.rows[0].n===0));
 const inventario=(await pool.query(`SELECT count(*)::integer n FROM salida_asientos sa JOIN salidas s ON s.id=sa.salida_id
  JOIN rutas r ON r.id=s.ruta_id JOIN terminales o ON o.id=r.terminal_origen_id JOIN terminales d ON d.id=r.terminal_destino_id
  WHERE o.nombre LIKE 'DEMO - Terminal %' AND d.nombre LIKE 'DEMO - Terminal %'
   AND s.fecha_hora_salida>=$1::date::timestamp AT TIME ZONE 'America/Lima'
   AND s.fecha_hora_salida<($1::date+2)::timestamp AT TIME ZONE 'America/Lima'`,[fecha])).rows[0].n;
 assert.equal(inventario,12*13*2*15);
});
test('programacion conserva salidas pagadas y canceladas',async()=>{
 const s=(await pool.query(`SELECT s.id FROM salidas s JOIN rutas r ON r.id=s.ruta_id
  JOIN terminales o ON o.id=r.terminal_origen_id WHERE o.nombre='DEMO - Terminal Chota' AND s.fecha_hora_salida>now() ORDER BY s.fecha_hora_salida LIMIT 1`)).rows[0].id;
 const uid='horario-preservar',repo=crearReservasRepository(pool),reserva=await repo.crear(uid,{salidaId:s,asientos:[1],solicitudId:randomUUID()});
 await repo.guardarPasajeros(uid,reserva.id,[pasajero(1)]);await pagar(reserva.id,uid);
 const antes=await crearBoletosRepository(pool).deReserva(uid,reserva.id);
 const original=(await pool.query('SELECT * FROM salidas WHERE id=$1',[s])).rows[0];
 const cancelar=(await pool.query(`SELECT id FROM salidas WHERE id<>$1 AND vehiculo_id IS NOT NULL AND estado='PROGRAMADA' AND fecha_hora_salida>now() ORDER BY id LIMIT 1`,[s])).rows[0].id;
 await pool.query("UPDATE salidas SET estado='CANCELADA' WHERE id=$1",[cancelar]);
 await pool.query('SELECT programar_salidas_demo()');
 assert.deepEqual((await pool.query('SELECT * FROM salidas WHERE id=$1',[s])).rows[0],original);
 assert.deepEqual(await crearBoletosRepository(pool).deReserva(uid,reserva.id),antes);
 assert.equal((await repo.obtener(uid,reserva.id)).estado,'CONFIRMADA');
 assert.equal((await pool.query('SELECT saldo::text FROM saldos_prueba WHERE usuario_uid=$1',[uid])).rows[0].saldo,'270.00');
 assert.equal((await pool.query('SELECT estado FROM salidas WHERE id=$1',[cancelar])).rows[0].estado,'CANCELADA');
});
