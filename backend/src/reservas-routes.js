import express from 'express';
import {validarPasajeros} from './pasajeros-validacion.js';
import { exigirAutenticacion } from './autenticacion.js';
import { crearReservasRepository } from './reservas-repository.js';
import { ErrorHttp } from './errores.js';

const uuid = valor => typeof valor === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(valor);
const entero = valor => Number.isInteger(valor) && valor >= 1 && valor <= 2147483647;
const objeto = valor => valor !== null && typeof valor === 'object' && !Array.isArray(valor);
const invalido = () => new ErrorHttp(400, 'DATOS_INVALIDOS', 'Revisa los datos de la solicitud.');

// Rechaza UID, precio o cualquier campo que no forme parte del contrato de reserva.
export function validarNuevaReserva(cuerpo) {
  if (!objeto(cuerpo) || Object.keys(cuerpo).some(k => !['salidaId','asientos','solicitudId'].includes(k)) ||
      !entero(cuerpo.salidaId) || !uuid(cuerpo.solicitudId) || !Array.isArray(cuerpo.asientos) ||
      cuerpo.asientos.length < 1 || cuerpo.asientos.length > 9 ||
      !cuerpo.asientos.every(entero) || new Set(cuerpo.asientos).size !== cuerpo.asientos.length) throw invalido();
  return { salidaId: cuerpo.salidaId, asientos: [...cuerpo.asientos].sort((a,b)=>a-b), solicitudId: cuerpo.solicitudId.toLowerCase() };
}

export function registrarReservas(app, pool, opciones = {}) {
  const autenticar = exigirAutenticacion(opciones.verificarToken);
  const repository = opciones.reservasRepository ?? crearReservasRepository(pool);
  const json = express.json({ limit: '8kb', strict: true });

  // La consulta revela disponibilidad de asientos, sin datos de otros pasajeros.
  app.get('/api/v1/salidas/:id/asientos', autenticar, async (req,res) => {
    if (!/^[1-9]\d{0,9}$/.test(req.params.id) || !entero(Number(req.params.id))) throw invalido();
    res.json({ datos: await repository.asientos(Number(req.params.id)) });
  });
  app.post('/api/v1/reservas', autenticar, json, async (req,res) => {
    if (!req.is('application/json')) throw new ErrorHttp(415,'JSON_REQUERIDO','Envia la solicitud en formato JSON.');
    const datos = validarNuevaReserva(req.body);
    // Devuelve 200 tanto al crear como al recuperar un reintento idempotente.
    res.json({ datos: await repository.crear(req.usuarioUid, datos) });
  });
  // El formulario completo se guarda con PUT y no amplia el tiempo de reserva.
  app.put('/api/v1/reservas/:id/pasajeros',autenticar,json,async(req,res)=>{
    if(!req.is('application/json'))throw new ErrorHttp(415,'JSON_REQUERIDO','Envia la solicitud en formato JSON.');
    if(!uuid(req.params.id))throw invalido();
    res.json({datos:await repository.guardarPasajeros(req.usuarioUid,req.params.id,validarPasajeros(req.body))});
  });
  app.get('/api/v1/reservas/:id/resumen',autenticar,async(req,res)=>{
    if(!uuid(req.params.id))throw invalido();
    res.json({datos:await repository.resumen(req.usuarioUid,req.params.id)});
  });
  app.get('/api/v1/reservas/:id', autenticar, async (req,res) => {
    if (!uuid(req.params.id)) throw invalido();
    res.json({ datos: await repository.obtener(req.usuarioUid,req.params.id) });
  });
  app.post('/api/v1/reservas/:id/cancelacion', autenticar, json, async (req,res) => {
    const tieneCuerpo = req.headers['transfer-encoding'] !== undefined || Number(req.headers['content-length'] || 0) > 0;
    if (tieneCuerpo && !req.is('application/json')) throw new ErrorHttp(415,'JSON_REQUERIDO','Envia la solicitud en formato JSON.');
    if (!uuid(req.params.id) || (req.body !== undefined && (!objeto(req.body) || Object.keys(req.body).length !== 0))) throw invalido();
    res.json({ datos: await repository.cancelar(req.usuarioUid,req.params.id) });
  });
}
