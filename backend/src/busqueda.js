// Valida los filtros antes de consultar PostgreSQL; las fechas se interpretan en Peru.
export function validarBusqueda(query) {
  const texto = (valor, maximo) => typeof valor === 'string' && valor.trim().length > 0 && valor.trim().length <= maximo;
  if (!texto(query.origen, 80) || !texto(query.destino, 80)) {
    return { error: 'Selecciona un origen y un destino validos.' };
  }
  const origen = query.origen.trim();
  const destino = query.destino.trim();
  if (origen.toLocaleLowerCase('es') === destino.toLocaleLowerCase('es')) {
    return { error: 'El origen y el destino deben ser distintos.' };
  }
  if (typeof query.fecha !== 'string' || !/^20\d{2}-\d{2}-\d{2}$/.test(query.fecha)) {
    return { error: 'La fecha debe tener el formato AAAA-MM-DD (anos 2000 a 2099).' };
  }
  const fecha = new Date(`${query.fecha}T00:00:00Z`);
  if (Number.isNaN(fecha.getTime()) || fecha.toISOString().slice(0, 10) !== query.fecha) {
    return { error: 'La fecha indicada no existe.' };
  }
  if (typeof query.pasajeros !== 'string' || !/^[1-9]$/.test(query.pasajeros)) {
    return { error: 'Selecciona entre 1 y 9 pasajeros.' };
  }
  return { filtros: { origen, destino, fecha: query.fecha, pasajeros: Number(query.pasajeros) } };
}

// Consulta salidas futuras con parametros separados del SQL y horarios de America/Lima.
export const SQL_SALIDAS = `
  SELECT s.id, s.ruta_id, s.fecha_hora_salida, s.fecha_hora_llegada,
         s.precio::text AS precio, 'PEN' AS moneda, s.capacidad, libres.asientos_libres,
         json_build_object('id', o.id, 'nombre', o.nombre, 'ciudad', o.ciudad,
           'direccion', o.direccion, 'latitud', o.latitud::double precision,
           'longitud', o.longitud::double precision) AS embarque,
         json_build_object('id', d.id, 'nombre', d.nombre, 'ciudad', d.ciudad,
           'direccion', d.direccion, 'latitud', d.latitud::double precision,
           'longitud', d.longitud::double precision) AS llegada
  FROM salidas s
  JOIN rutas r ON r.id = s.ruta_id
  JOIN terminales o ON o.id = r.terminal_origen_id
  JOIN terminales d ON d.id = r.terminal_destino_id
  JOIN LATERAL (SELECT count(*)::integer AS asientos_libres FROM disponibilidad_asientos da
    WHERE da.salida_id=s.id AND da.disponible) libres ON TRUE
  WHERE lower(o.ciudad) = lower($1) AND lower(d.ciudad) = lower($2)
    AND o.activo AND d.activo AND r.activo AND s.estado = 'PROGRAMADA'
    AND s.fecha_hora_salida >= ($3::date::timestamp AT TIME ZONE 'America/Lima')
    AND s.fecha_hora_salida < (($3::date + 1)::timestamp AT TIME ZONE 'America/Lima')
    AND s.fecha_hora_salida > CURRENT_TIMESTAMP AND libres.asientos_libres >= $4
  ORDER BY s.fecha_hora_salida, s.id
  LIMIT 101
`;

export function registrarBusqueda(app, db) {
  app.get('/api/v1/salidas', async (req, res) => {
    const validacion = validarBusqueda(req.query);
    if (validacion.error) {
      return res.status(400).json({ error: { codigo: 'FILTROS_INVALIDOS', mensaje: validacion.error } });
    }
    const { origen, destino, fecha, pasajeros } = validacion.filtros;
    const resultado = await db.query(SQL_SALIDAS, [origen, destino, fecha, pasajeros]);
    res.json({
      datos: resultado.rows.slice(0, 100),
      filtros: validacion.filtros,
      hayMas: resultado.rows.length > 100,
      disponibilidadConfirmada: false,
    });
  });
}
