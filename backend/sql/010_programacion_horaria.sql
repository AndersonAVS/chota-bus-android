-- Horarios indicados por el usuario: 12 rutas, de 06:00 a 18:00 cada hora exacta.
-- Tarifa, duracion, vehiculos y capacidad siguen siendo DEMO.
BEGIN;
CREATE FUNCTION programar_salidas_demo(p_desde DATE DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date,
 p_dias INTEGER DEFAULT 30) RETURNS INTEGER LANGUAGE plpgsql AS $f$
DECLARE v_nuevas INTEGER;v_ids INTEGER[];v_terminales INTEGER;
BEGIN
 IF p_desde IS NULL OR p_dias IS NULL OR p_dias NOT BETWEEN 1 AND 31 THEN RAISE EXCEPTION 'PROGRAMACION_INVALIDA'; END IF;
 -- Serializa la programacion automatica entre procesos; nunca reabre salidas canceladas.
 PERFORM pg_advisory_xact_lock(hashtextextended('chota_bus_programacion_horaria_v1',0));
 SELECT count(*) INTO v_terminales FROM terminales
  WHERE activo AND (nombre,ciudad) IN (('DEMO - Terminal Chota','Chota'),('DEMO - Terminal Cajamarca','Cajamarca'),
   ('DEMO - Terminal Jaén','Jaén'),('DEMO - Terminal Lajas','Lajas'));
 IF v_terminales<>4 THEN RAISE EXCEPTION 'REVISAR_TERMINALES_DEMO'; END IF;
 INSERT INTO rutas(terminal_origen_id,terminal_destino_id)
 SELECT o.id,d.id FROM terminales o CROSS JOIN terminales d
 WHERE o.id<>d.id AND o.activo AND d.activo
  AND o.nombre IN ('DEMO - Terminal Chota','DEMO - Terminal Cajamarca','DEMO - Terminal Jaén','DEMO - Terminal Lajas')
  AND d.nombre IN ('DEMO - Terminal Chota','DEMO - Terminal Cajamarca','DEMO - Terminal Jaén','DEMO - Terminal Lajas')
 ON CONFLICT(terminal_origen_id,terminal_destino_id) DO NOTHING;

 -- Inserta solo los horarios que faltan. Los registros anteriores conservan sus IDs y datos.
 INSERT INTO salidas(ruta_id,fecha_hora_salida,fecha_hora_llegada,precio,capacidad)
 SELECT r.id,p.inicio,p.inicio+INTERVAL '3 hours',30.00,15
 FROM rutas r JOIN terminales o ON o.id=r.terminal_origen_id JOIN terminales d ON d.id=r.terminal_destino_id
 CROSS JOIN generate_series(0,p_dias-1) dia CROSS JOIN generate_series(6,18) hora
 CROSS JOIN LATERAL (SELECT ((p_desde+dia)+make_time(hora,0,0)) AT TIME ZONE 'America/Lima' AS inicio) p
 WHERE r.activo AND o.activo AND d.activo
  AND o.nombre IN ('DEMO - Terminal Chota','DEMO - Terminal Cajamarca','DEMO - Terminal Jaén','DEMO - Terminal Lajas')
  AND d.nombre IN ('DEMO - Terminal Chota','DEMO - Terminal Cajamarca','DEMO - Terminal Jaén','DEMO - Terminal Lajas')
  AND NOT EXISTS(SELECT 1 FROM salidas s WHERE s.ruta_id=r.id AND s.fecha_hora_salida=p.inicio);
 GET DIAGNOSTICS v_nuevas=ROW_COUNT;
 SELECT array_agg(s.id) INTO v_ids FROM salidas s JOIN rutas r ON r.id=s.ruta_id
 JOIN terminales o ON o.id=r.terminal_origen_id JOIN terminales d ON d.id=r.terminal_destino_id
 WHERE s.vehiculo_id IS NULL AND s.estado='PROGRAMADA'
  AND s.fecha_hora_salida>=p_desde::timestamp AT TIME ZONE 'America/Lima'
  AND s.fecha_hora_salida<(p_desde+p_dias)::timestamp AT TIME ZONE 'America/Lima'
  AND o.nombre IN ('DEMO - Terminal Chota','DEMO - Terminal Cajamarca','DEMO - Terminal Jaén','DEMO - Terminal Lajas')
  AND d.nombre IN ('DEMO - Terminal Chota','DEMO - Terminal Cajamarca','DEMO - Terminal Jaén','DEMO - Terminal Lajas');
 -- Completa inventario solo para salidas sin vehiculo; no cambia asientos ya reservados.
 INSERT INTO vehiculos(placa,nombre) SELECT 'DEMO-S'||id,'Vehiculo DEMO de salida '||id FROM salidas WHERE id=ANY(v_ids)
 ON CONFLICT(placa) DO NOTHING;
 UPDATE salidas s SET vehiculo_id=v.id FROM vehiculos v WHERE s.id=ANY(v_ids) AND v.placa='DEMO-S'||s.id;
 INSERT INTO asientos(vehiculo_id,numero,fila,columna)
 SELECT s.vehiculo_id,n,((n-1)/3)+1,((n-1)%3)+1 FROM salidas s CROSS JOIN LATERAL generate_series(1,s.capacidad) n WHERE s.id=ANY(v_ids)
 ON CONFLICT DO NOTHING;
 INSERT INTO salida_asientos(salida_id,vehiculo_id,numero)
 SELECT s.id,s.vehiculo_id,a.numero FROM salidas s JOIN asientos a ON a.vehiculo_id=s.vehiculo_id WHERE s.id=ANY(v_ids) AND a.activo
 ON CONFLICT DO NOTHING;
 RETURN v_nuevas;
END;
$f$;
REVOKE ALL ON FUNCTION programar_salidas_demo(DATE,INTEGER) FROM PUBLIC;
COMMIT;
