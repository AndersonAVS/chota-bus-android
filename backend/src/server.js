import { crearPool } from './db.js';
import { crearApp } from './app.js';

// Verifica PostgreSQL antes de abrir el servidor local para las pruebas.
let pool;
try {
  const puerto = Number(process.env.PORT || 3000);
  if (!Number.isInteger(puerto) || puerto < 1 || puerto > 65535) {
    throw new Error('PORT debe ser un puerto valido.');
  }
  pool = crearPool();
  pool.on('error', () => console.error('Se interrumpio una conexion de PostgreSQL.'));
  await pool.query('SELECT 1');
  const servidor = crearApp(pool).listen(puerto, '127.0.0.1', () => {
    console.log(`API local: http://127.0.0.1:${puerto}/api/v1/salud`);
  });
  servidor.on('error', async () => {
    console.error('No se pudo abrir el puerto del servidor.');
    await pool.end();
    process.exitCode = 1;
  });

  // Cierra las conexiones al detener el servidor desde la terminal.
  const cerrar = () => servidor.close(() => {
    pool.end().catch(() => { process.exitCode = 1; });
  });
  process.once('SIGINT', cerrar);
  process.once('SIGTERM', cerrar);
} catch {
  console.error('No se pudo iniciar la API. Revisa backend/.env, el puerto y PostgreSQL.');
  if (pool) await pool.end();
  process.exitCode = 1;
}
