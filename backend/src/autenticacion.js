import { getApps, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { ErrorHttp } from './errores.js';

// Verifica firma, emisor, audiencia y vencimiento del ID token del proyecto indicado.
// No consulta usuarios ni revocaciones: esta operacion usa certificados publicos.
export function crearVerificadorFirebase(projectId = process.env.FIREBASE_PROJECT_ID) {
  if (!projectId || !/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(projectId) || process.env.FIREBASE_AUTH_EMULATOR_HOST) {
    return async () => { throw new ErrorHttp(503, 'AUTENTICACION_NO_CONFIGURADA', 'El acceso al servicio no esta configurado.'); };
  }
  const nombre = 'chota-bus-reservas';
  const app = getApps().find(app => app.name === nombre) ?? initializeApp({ projectId }, nombre);
  if (app.options.projectId !== projectId) throw new Error('PROYECTO_FIREBASE_INCONSISTENTE');
  const auth = getAuth(app);
  return token => auth.verifyIdToken(token, false);
}

// El UID solo se toma del token verificado, nunca de los parametros del formulario.
export function exigirAutenticacion(verificarToken) {
  return async (req, res, next) => {
    res.set('Cache-Control', 'no-store');
    const cabecera = req.get('authorization');
    const coincidencia = typeof cabecera === 'string' && cabecera.length <= 8192
      ? /^Bearer ([A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)$/i.exec(cabecera) : null;
    if (!coincidencia) return next(new ErrorHttp(401, 'SESION_REQUERIDA', 'Inicia sesion para continuar.'));
    if (!verificarToken) return next(new ErrorHttp(503, 'AUTENTICACION_NO_CONFIGURADA', 'El acceso al servicio no esta configurado.'));
    try {
      const identidad = await verificarToken(coincidencia[1]);
      if (typeof identidad?.uid !== 'string' || !identidad.uid.trim() || identidad.uid.length > 128) {
        throw new ErrorHttp(401, 'SESION_INVALIDA', 'La sesion no es valida. Inicia sesion nuevamente.');
      }
      req.usuarioUid = identidad.uid;
      next();
    } catch (error) {
      if (error instanceof ErrorHttp) return next(error);
      const invalidos = ['auth/id-token-expired','auth/id-token-revoked','auth/invalid-id-token',
        'auth/argument-error','auth/user-disabled'];
      next(invalidos.includes(error.code)
        ? new ErrorHttp(401, 'SESION_INVALIDA', 'La sesion no es valida. Inicia sesion nuevamente.')
        : new ErrorHttp(503, 'AUTENTICACION_NO_DISPONIBLE', 'No se pudo verificar la sesion. Intenta nuevamente.'));
    }
  };
}
