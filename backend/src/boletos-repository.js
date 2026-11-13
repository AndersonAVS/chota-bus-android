import {ErrorHttp} from './errores.js';

// Devuelve la copia emitida y filtra siempre por el UID verificado en Firebase.
const CAMPOS=`b.id,b.codigo,b.reserva_id,b.numero_asiento AS asiento,b.importe_unitario::text,
 b.moneda,b.modo,b.estado,b.emitido_en,b.datos_recuperados,b.pasajero,b.viaje,
 'BOLETO DE PRUEBA - SIN VALOR FISCAL' AS aviso`;
export function crearBoletosRepository(pool){
 return {
  // El cursor es un boleto propio: la fecha se toma de SQL para conservar sus microsegundos.
  async listar(uid,despues=null,limite=20){
   const resultado=await pool.query(`WITH propios AS (
    SELECT b.* FROM boletos_prueba b JOIN reservas r ON r.id=b.reserva_id WHERE r.usuario_uid=$1
   ), cursor AS (SELECT id,emitido_en FROM propios WHERE id=$2::uuid)
   SELECT ($2::uuid IS NULL OR EXISTS(SELECT 1 FROM cursor)) AS cursor_valido,
    COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.emitido_en DESC,t.id DESC) FROM (
     SELECT ${CAMPOS} FROM propios b
     WHERE $2::uuid IS NULL OR (b.emitido_en,b.id)<(SELECT emitido_en,id FROM cursor)
     ORDER BY b.emitido_en DESC,b.id DESC LIMIT $3
    ) t),'[]'::jsonb) AS boletos`,[uid,despues,limite+1]);
   const fila=resultado.rows[0];
   if(!fila.cursor_valido)throw new ErrorHttp(404,'CURSOR_NO_ENCONTRADO','No se encontro la pagina solicitada. Actualiza la lista.');
   const boletos=fila.boletos.slice(0,limite);
   return {boletos,siguiente:fila.boletos.length>limite?boletos.at(-1).id:null};
  },
  async deReserva(uid,id){
   // Una sola sentencia evita mezclar propietario o estado entre consultas.
   const r=await pool.query(`SELECT r.id,
    COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.asiento) FROM
     (SELECT ${CAMPOS} FROM boletos_prueba b WHERE b.reserva_id=r.id) t),'[]'::jsonb) AS boletos
    FROM reservas r WHERE r.id=$1::uuid AND r.usuario_uid=$2`,[id,uid]);
   if(!r.rows[0])throw new ErrorHttp(404,'RESERVA_NO_ENCONTRADA','No se encontro la reserva.');
   return r.rows[0].boletos;
  },
  async obtener(uid,id){
   const r=await pool.query(`SELECT ${CAMPOS} FROM boletos_prueba b JOIN reservas r ON r.id=b.reserva_id
    WHERE b.id=$1::uuid AND r.usuario_uid=$2`,[id,uid]);
   if(!r.rows[0])throw new ErrorHttp(404,'BOLETO_NO_ENCONTRADO','No se encontro el boleto.');
   return r.rows[0];
  }
 };
}
