import pg from 'pg';

// Reutiliza conexiones y limita el tiempo de espera de PostgreSQL.
export function crearPool() {
  if (!process.env.PGPASSWORD || process.env.PGPASSWORD === 'REEMPLAZAR_CON_TU_CLAVE_LOCAL') {
    throw new Error('Configura PGPASSWORD en backend/.env antes de iniciar.');
  }
  return new pg.Pool({
    max: 5,
    connectionTimeoutMillis: 5000,
    idleTimeoutMillis: 30000,
    statement_timeout: 10000,
    options: '-c timezone=America/Lima',
  });
}
