import express from 'express';
import { registrarBusqueda } from './busqueda.js';

// Define las consultas publicas del catalogo sin permitir modificar los datos.
export function crearApp(db) {
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

  registrarBusqueda(app, db);

  // Responde de forma uniforme sin exponer credenciales ni errores SQL al cliente.
  app.use((_req, res) => {
    res.status(404).json({ error: { codigo: 'NO_ENCONTRADO', mensaje: 'El recurso solicitado no existe.' } });
  });
  app.use((error, _req, res, _next) => {
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
