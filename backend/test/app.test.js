import { test } from 'node:test';
import assert from 'node:assert/strict';
import { crearApp } from '../src/app.js';

// Verifica el contrato HTTP con una base simulada, sin acceder a datos personales.
async function consultar(db, ruta) {
  const servidor = crearApp(db).listen(0, '127.0.0.1');
  await new Promise(resolve => servidor.once('listening', resolve));
  try {
    const respuesta = await fetch(`http://127.0.0.1:${servidor.address().port}${ruta}`);
    return { estado: respuesta.status, cuerpo: await respuesta.json() };
  } finally {
    await new Promise((resolve, reject) => {
      servidor.close(error => error ? reject(error) : resolve());
      servidor.closeAllConnections();
    });
  }
}

test('salud confirma la conexion solo si responde PostgreSQL', async () => {
  let consulta;
  const respuesta = await consultar({ query: async sql => { consulta = sql; } }, '/api/v1/salud');
  assert.equal(consulta, 'SELECT 1');
  assert.equal(respuesta.estado, 200);
  assert.equal(respuesta.cuerpo.baseDeDatos, 'conectada');
});
test('terminales vacios devuelve lista vacia', async () => {
  const respuesta = await consultar({ query: async () => ({ rows: [] }) }, '/api/v1/terminales');
  assert.deepEqual(respuesta, { estado: 200, cuerpo: { datos: [] } });
});
test('terminales conserva el contrato de datos', async () => {
  const terminal = { id: 1, nombre: 'Terminal de prueba', ciudad: 'Chota', direccion: 'Ejemplo', latitud: null, longitud: null };
  const respuesta = await consultar({ query: async () => ({ rows: [terminal] }) }, '/api/v1/terminales');
  assert.deepEqual(respuesta.cuerpo.datos, [terminal]);
});
test('fallo SQL no expone informacion interna', async () => {
  const respuesta = await consultar({ query: async () => { throw new Error('dato-interno'); } }, '/api/v1/salud');
  assert.equal(respuesta.estado, 503);
  assert.equal(JSON.stringify(respuesta.cuerpo).includes('dato-interno'), false);
});
test('ruta inexistente devuelve 404', async () => {
  const respuesta = await consultar({}, '/no-existe');
  assert.equal(respuesta.estado, 404);
});

// Comprueba filtros invalidos, parametros SQL y resultados de la busqueda.
const filtrosValidos = '/api/v1/salidas?origen=Chota&destino=Cajamarca&fecha=2026-12-20&pasajeros=1';
test('busqueda transmite los filtros separados de la consulta SQL', async () => {
  let parametros;
  const respuesta = await consultar({ query: async (_sql, valores) => { parametros = valores; return { rows: [] }; } }, filtrosValidos);
  assert.equal(respuesta.estado, 200);
  assert.deepEqual(parametros, ['Chota', 'Cajamarca', '2026-12-20', 1]);
  assert.deepEqual(respuesta.cuerpo.datos, []);
  assert.equal(respuesta.cuerpo.disponibilidadConfirmada, false);
});
for (const [nombre, ruta] of [
  ['sin filtros', '/api/v1/salidas'],
  ['mismo origen y destino', filtrosValidos.replace('destino=Cajamarca', 'destino=chota')],
  ['fecha inexistente', filtrosValidos.replace('2026-12-20', '2026-02-30')],
  ['cero pasajeros', filtrosValidos.replace('pasajeros=1', 'pasajeros=0')],
  ['pasajeros decimales', filtrosValidos.replace('pasajeros=1', 'pasajeros=1.5')],
  ['filtro repetido', filtrosValidos + '&origen=Lajas'],
  ['fecha con texto SQL', filtrosValidos.replace('2026-12-20', '2026-12-20%27')],
]) {
  test(`busqueda rechaza ${nombre} sin consultar SQL`, async () => {
    let consultado = false;
    const respuesta = await consultar({ query: async () => { consultado = true; return { rows: [] }; } }, ruta);
    assert.equal(respuesta.estado, 400);
    assert.equal(consultado, false);
  });
}
test('busqueda devuelve terminales de embarque y llegada', async () => {
  const salida = { id: 1, precio: '30.00', embarque: { ciudad: 'Chota' }, llegada: { ciudad: 'Cajamarca' } };
  const respuesta = await consultar({ query: async () => ({ rows: [salida] }) }, filtrosValidos);
  assert.deepEqual(respuesta.cuerpo.datos, [salida]);
});
test('busqueda informa cuando hay mas de cien resultados', async () => {
  const respuesta = await consultar({ query: async () => ({ rows: Array.from({ length: 101 }, (_, id) => ({ id })) }) }, filtrosValidos);
  assert.equal(respuesta.cuerpo.datos.length, 100);
  assert.equal(respuesta.cuerpo.hayMas, true);
});
test('busqueda maneja un fallo de PostgreSQL', async () => {
  const respuesta = await consultar({ query: async () => { throw new Error('interno'); } }, filtrosValidos);
  assert.equal(respuesta.estado, 503);
});
