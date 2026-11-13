import {exigirAutenticacion} from './autenticacion.js';
import {ErrorHttp} from './errores.js';

export function crearHistorialRepository(pool){
 return {
  // Una fila por reserva: distingue el importe reservado del dinero ficticio realmente cobrado.
  async listar(uid,despues=null,limite=20){
   const resultado=await pool.query(`WITH propias AS (
    SELECT * FROM reservas WHERE usuario_uid=$1
   ), cursor AS (SELECT id,creado_en FROM propias WHERE id=$2::uuid), pagina AS (
    SELECT * FROM propias WHERE $2::uuid IS NULL OR (creado_en,id)<(SELECT creado_en,id FROM cursor)
    ORDER BY creado_en DESC,id DESC LIMIT $3
   )
   SELECT ($2::uuid IS NULL OR EXISTS(SELECT 1 FROM cursor)) AS cursor_valido,
    COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.creado_en DESC,t.id DESC) FROM (
     SELECT r.id,r.salida_id,r.creado_en,
      CASE WHEN r.estado='PENDIENTE' AND r.vence_en<=statement_timestamp() THEN 'EXPIRADA' ELSE r.estado END AS estado,
      COALESCE((b.viaje->>'fecha_hora_salida')::timestamptz,s.fecha_hora_salida) AS fecha_hora_salida,
      COALESCE(b.viaje#>>'{embarque,ciudad}',o.ciudad) AS origen,
      COALESCE(b.viaje#>>'{llegada,ciudad}',d.ciudad) AS destino,
      r.cantidad,r.total::text AS total_reserva,COALESCE(p.importe,0)::numeric(12,2)::text AS importe_pagado,
      p.id IS NOT NULL AS pagada,p.creado_en AS fecha_pago,'PEN' AS moneda,'PRUEBA' AS modo,
      ARRAY(SELECT numero FROM reserva_asientos WHERE reserva_id=r.id ORDER BY numero) AS asientos,
      (SELECT count(*)::integer FROM boletos_prueba WHERE reserva_id=r.id) AS boletos_emitidos
     FROM pagina r JOIN salidas s ON s.id=r.salida_id JOIN rutas ru ON ru.id=s.ruta_id
     JOIN terminales o ON o.id=ru.terminal_origen_id JOIN terminales d ON d.id=ru.terminal_destino_id
     LEFT JOIN pagos_prueba p ON p.reserva_id=r.id AND p.usuario_uid=r.usuario_uid
     LEFT JOIN LATERAL (SELECT viaje FROM boletos_prueba WHERE reserva_id=r.id ORDER BY numero_asiento LIMIT 1) b ON TRUE
    ) t),'[]'::jsonb) AS registros`,[uid,despues,limite+1]);
   const fila=resultado.rows[0];
   if(!fila.cursor_valido)throw new ErrorHttp(404,'CURSOR_NO_ENCONTRADO','No se encontro la pagina solicitada. Actualiza el historial.');
   const registros=fila.registros.slice(0,limite);
   return {registros,siguiente:fila.registros.length>limite?registros.at(-1).id:null};
  }
 };
}

// La consulta es de solo lectura y toma el propietario exclusivamente del token Firebase.
export function registrarHistorial(app,pool,opciones={}){
 const auth=exigirAutenticacion(opciones.verificarToken),repo=opciones.historialRepository??crearHistorialRepository(pool);
 app.get('/api/v1/historial',auth,async(req,res)=>{
  if(Object.keys(req.query).some(k=>!['despues','limite'].includes(k)))throw new ErrorHttp(400,'DATOS_INVALIDOS','Revisa los parametros.');
  const despues=req.query.despues;
  if(despues!==undefined&&(typeof despues!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(despues)))
   throw new ErrorHttp(400,'DATOS_INVALIDOS','El cursor no es valido.');
  const limite=req.query.limite===undefined?20:Number(req.query.limite);
  if(req.query.limite!==undefined&&(typeof req.query.limite!=='string'||!/^[1-9][0-9]?$/.test(req.query.limite))||!Number.isInteger(limite)||limite<1||limite>50)
   throw new ErrorHttp(400,'DATOS_INVALIDOS','El limite debe estar entre 1 y 50.');
  const pagina=await repo.listar(req.usuarioUid,despues??null,limite);
  res.json({datos:pagina.registros,siguiente:pagina.siguiente});
 });
}
