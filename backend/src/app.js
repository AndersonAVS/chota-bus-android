import express from 'express';
import {registrarHistorial} from './historial.js';
import {registrarBoletos} from './boletos-routes.js';
import {registrarPagosPrueba} from './pagos-prueba.js';
import { registrarBusqueda } from './busqueda.js';
import { registrarReservas } from './reservas-routes.js';
import { ErrorHttp } from './errores.js';

// Mantiene el catalogo publico y protege las operaciones de reservas con Firebase.
export function crearApp(db, opciones = {}) {
  const app = express();
  app.disable('x-powered-by');

  // Comprueba que el servidor pueda comunicarse con la base de datos.
  app.get('/api/v1/salud', async (_req, res) => {
    await db.query('SELECT 1');
    res.json({ estado: 'ok', baseDeDatos: 'conectada' });
  });

  // Entrega los terminales activos, incluyendo su ubicacion cuando existe.
  app.get('/api/v1/terminales', async (_req, res) => {
    const resultado = await db.query(`
      SELECT id, nombre, ciudad, direccion,
             latitud::double precision AS latitud,
             longitud::double precision AS longitud
      FROM terminales WHERE activo = TRUE
      ORDER BY ciudad, nombre
    `);
    res.json({ datos: resultado.rows });
  });

  registrarHistorial(app,db,opciones);
  registrarBoletos(app,db,opciones);
  registrarBusqueda(app, db);
  registrarPagosPrueba(app,db,opciones);
  registrarReservas(app, db, opciones);

  // Responde de forma uniforme sin exponer credenciales ni errores SQL al cliente.
  app.use((_req, res) => {
    res.status(404).json({ error: { codigo: 'NO_ENCONTRADO', mensaje: 'El recurso solicitado no existe.' } });
  });
  app.use((error, _req, res, _next) => {
    if (error instanceof ErrorHttp) {
      return res.status(error.estado).json({ error: { codigo: error.codigo, mensaje: error.message } });
    }
    if (['entity.parse.failed','entity.too.large','encoding.unsupported','charset.unsupported'].includes(error.type)) {
      const estado = error.type === 'entity.too.large' ? 413 : error.type === 'entity.parse.failed' ? 400 : 415;
      return res.status(estado).json({ error: { codigo: 'CUERPO_INVALIDO', mensaje: 'El contenido de la solicitud no es valido.' } });
    }
    const codigo = typeof error.code === 'string' && /^[A-Z0-9_]+$/.test(error.code)
      ? error.code : 'CONSULTA_FALLIDA';
    console.error('Fallo de consulta:', codigo);
    res.status(503).json({ error: {
      codigo: 'SERVICIO_NO_DISPONIBLE',
      mensaje: 'No se pudo consultar la base de datos. Intenta nuevamente.',
    } });
  });
  return app;
}
