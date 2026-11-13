import { ErrorHttp, traducirErrorReserva } from './errores.js';

// Proyecta solo los campos que necesita Android y muestra EXPIRADA aunque falte limpieza.
const SQL_RESERVA = `
  SELECT r.id, r.salida_id,
    CASE WHEN r.estado='PENDIENTE' AND r.vence_en<=statement_timestamp()
         THEN 'EXPIRADA' ELSE r.estado END AS estado,
    r.cantidad, r.precio_unitario::text, r.total::text, 'PEN' AS moneda,
    r.creado_en, r.vence_en, statement_timestamp() AS hora_servidor,
    ARRAY(SELECT numero FROM reserva_asientos WHERE reserva_id=r.id ORDER BY numero) AS asientos
  FROM reservas r WHERE r.id=$1::uuid AND r.usuario_uid=$2::text
`;

export function crearReservasRepository(pool) {
  async function obtenerCon(cliente, id, uid) {
    const resultado = await cliente.query(SQL_RESERVA, [id, uid]);
    if (!resultado.rows[0]) throw new ErrorHttp(404, 'RESERVA_NO_ENCONTRADA', 'No se encontro la reserva.');
    return resultado.rows[0];
  }

  // El resumen se obtiene en una sola consulta y solo para el propietario verificado.
  async function resumenCon(cliente,id,uid) {
    const resultado=await cliente.query(`
      WITH reserva AS (${SQL_RESERVA})
      SELECT jsonb_build_object(
        'reserva',to_jsonb(re),
        'viaje',jsonb_build_object('fecha_hora_salida',s.fecha_hora_salida,'fecha_hora_llegada',s.fecha_hora_llegada,
          'embarque',jsonb_build_object('id',o.id,'nombre',o.nombre,'ciudad',o.ciudad,'direccion',o.direccion,'latitud',o.latitud,'longitud',o.longitud),
          'llegada',jsonb_build_object('id',d.id,'nombre',d.nombre,'ciudad',d.ciudad,'direccion',d.direccion,'latitud',d.latitud,'longitud',d.longitud)),
        'pasajeros',p.datos,'datos_completos',p.cantidad=re.cantidad,
        'puede_continuar',re.estado='PENDIENTE' AND s.estado='PROGRAMADA' AND s.fecha_hora_salida>statement_timestamp()
          AND r.activo AND o.activo AND d.activo AND COALESCE(v.activo,FALSE)
          AND p.cantidad=re.cantidad AND ocupados.cantidad=re.cantidad
      ) AS resumen
      FROM reserva re JOIN salidas s ON s.id=re.salida_id JOIN rutas r ON r.id=s.ruta_id
      JOIN terminales o ON o.id=r.terminal_origen_id JOIN terminales d ON d.id=r.terminal_destino_id
      LEFT JOIN vehiculos v ON v.id=s.vehiculo_id
      CROSS JOIN LATERAL (
        SELECT count(*)::integer AS cantidad,
          COALESCE(jsonb_agg(jsonb_build_object('asiento',numero_asiento,'nombreCompleto',nombre_completo,
            'dni',dni,'celular',celular) ORDER BY numero_asiento),'[]'::jsonb) AS datos
        FROM pasajeros_reserva WHERE reserva_id=re.id
      ) p
      CROSS JOIN LATERAL (
        SELECT count(*)::integer AS cantidad FROM reserva_asientos ra
        JOIN salida_asientos sa ON sa.salida_id=ra.salida_id AND sa.numero=ra.numero
        JOIN asientos a ON a.vehiculo_id=sa.vehiculo_id AND a.numero=sa.numero
        WHERE ra.reserva_id=re.id AND NOT ra.liberado AND sa.habilitado AND a.activo
      ) ocupados
    `,[id,uid]);
    if(!resultado.rows[0])throw new ErrorHttp(404,'RESERVA_NO_ENCONTRADA','No se encontro la reserva.');
    return resultado.rows[0].resumen;
  }

  // La escritura y su lectura usan la misma conexion y se confirman juntas.
  async function transaccion(operacion) {
    const cliente = await pool.connect();
    try {
      await cliente.query('BEGIN');
      await cliente.query("SET LOCAL lock_timeout = '5s'");
      const resultado = await operacion(cliente);
      await cliente.query('COMMIT');
      return resultado;
    } catch (error) {
      await cliente.query('ROLLBACK').catch(() => {});
      throw traducirErrorReserva(error);
    } finally { cliente.release(); }
  }

  return {
    async asientos(salidaId) {
      const resultado = await pool.query(`
        SELECT s.id AS salida_id, s.estado, s.fecha_hora_salida, s.precio::text AS precio_unitario,
          'PEN' AS moneda, statement_timestamp() AS hora_servidor,
          count(da.numero)::integer AS asientos_totales,
          count(da.numero) FILTER (WHERE da.disponible)::integer AS asientos_libres,
          COALESCE(jsonb_agg(jsonb_build_object('numero',da.numero,'fila',da.fila,
            'columna',da.columna,'disponible',da.disponible) ORDER BY da.numero)
            FILTER (WHERE da.numero IS NOT NULL),'[]'::jsonb) AS asientos
        FROM salidas s LEFT JOIN disponibilidad_asientos da ON da.salida_id=s.id
        WHERE s.id=$1 GROUP BY s.id
      `,[salidaId]);
      if (!resultado.rows[0]) throw new ErrorHttp(404,'SALIDA_NO_ENCONTRADA','No se encontro la salida.');
      return resultado.rows[0];
    },
    crear(uid, datos) {
      return transaccion(async cliente => {
        const resultado = await cliente.query(
          'SELECT id FROM reservar_asientos($1::integer,$2::text,$3::integer[],$4::uuid)',
          [datos.salidaId, uid, datos.asientos, datos.solicitudId]);
        return obtenerCon(cliente, resultado.rows[0].id, uid);
      });
    },
    obtener(uid, id) { return obtenerCon(pool, id, uid); },
    resumen(uid,id) { return resumenCon(pool,id,uid); },
    guardarPasajeros(uid,id,pasajeros) {
      return transaccion(async cliente=>{
        await cliente.query('SELECT guardar_pasajeros_reserva($1::uuid,$2::text,$3::jsonb)',[id,uid,JSON.stringify(pasajeros)]);
        return resumenCon(cliente,id,uid);
      });
    },
    cancelar(uid, id) {
      return transaccion(async cliente => {
        await cliente.query('SELECT id FROM cancelar_reserva($1::uuid,$2::text)',[id,uid]);
        return obtenerCon(cliente, id, uid);
      });
    },
  };
}
