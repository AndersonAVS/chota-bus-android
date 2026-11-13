import express from 'express';
import {exigirAutenticacion} from './autenticacion.js';
import {ErrorHttp,traducirErrorReserva} from './errores.js';

export const modoPruebaPermitido=(entorno=process.env)=>entorno.PAGOS_PRUEBA_HABILITADOS==='true' && entorno.NODE_ENV!=='production';

// El simulador solo funciona si el servidor lo habilita expresamente fuera de produccion.
export function registrarPagosPrueba(app,pool,opciones={}){
 const auth=exigirAutenticacion(opciones.verificarToken);
 const habilitado=opciones.pagosPruebaHabilitados ?? modoPruebaPermitido;
 const exigirModo=(req,res,next)=>{
  if(!habilitado())return next(new ErrorHttp(503,'SIMULADOR_DESHABILITADO','El saldo de prueba no esta habilitado.'));
  next();
 };
 const json=express.json({limit:'1kb',strict:true});
 async function transaccion(fn){
  const c=await pool.connect();
  try{await c.query('BEGIN');await c.query("SET LOCAL lock_timeout='5s'");const dato=await fn(c);await c.query('COMMIT');return dato;}
  catch(e){await c.query('ROLLBACK').catch(()=>{});throw traducirErrorReserva(e);}
  finally{c.release();}
 }
 app.get('/api/v1/saldo-prueba',auth,exigirModo,async(req,res)=>{
  const datos=await transaccion(async c=>{
   await c.query('INSERT INTO saldos_prueba(usuario_uid) VALUES($1) ON CONFLICT DO NOTHING',[req.usuarioUid]);
   return (await c.query("SELECT saldo::text,'PEN' AS moneda,'PRUEBA' AS modo FROM saldos_prueba WHERE usuario_uid=$1",[req.usuarioUid])).rows[0];
  });
  res.json({datos});
 });
 app.post('/api/v1/reservas/:id/pago-prueba',auth,exigirModo,json,async(req,res)=>{
  const cuerpo=req.headers['transfer-encoding']!==undefined || Number(req.headers['content-length']||0)>0;
  if(cuerpo&&!req.is('application/json'))throw new ErrorHttp(415,'JSON_REQUERIDO','Envia JSON.');
  if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(req.params.id) ||
   (req.body!==undefined&&(req.body===null||Array.isArray(req.body)||typeof req.body!=='object'||Object.keys(req.body).length)))
   throw new ErrorHttp(400,'DATOS_INVALIDOS','El importe y el usuario se determinan en el servidor.');
  const datos=await transaccion(async c=>{
   const p=await c.query('SELECT pagar_reserva_prueba($1::uuid,$2::text) AS id',[req.params.id,req.usuarioUid]);
   const compra=(await c.query(`SELECT p.id,p.reserva_id,p.importe::text,s.saldo::text,'PEN' AS moneda,'PRUEBA' AS modo
    FROM pagos_prueba p JOIN saldos_prueba s USING(usuario_uid) WHERE p.id=$1 AND p.usuario_uid=$2`,[p.rows[0].id,req.usuarioUid])).rows[0];
   // La respuesta agrega los boletos emitidos sin modificar el contrato de pago existente.
   compra.boletos=(await c.query(`SELECT id,codigo,numero_asiento AS asiento,'PRUEBA' AS modo
    FROM boletos_prueba WHERE reserva_id=$1 ORDER BY numero_asiento`,[req.params.id])).rows;
   return compra;
  });
  res.json({datos});
 });
}
