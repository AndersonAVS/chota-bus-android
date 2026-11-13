-- Sprint 4: datos de un pasajero por asiento de la reserva, sin modificar el precio.
BEGIN;
CREATE TABLE pasajeros_reserva (
    reserva_id UUID NOT NULL,
    numero_asiento INTEGER NOT NULL,
    nombre_completo VARCHAR(200) NOT NULL CHECK (length(trim(nombre_completo)) BETWEEN 1 AND 200),
    dni VARCHAR(8) NOT NULL CHECK (dni ~ '^[0-9]{8}$'),
    celular VARCHAR(9) NOT NULL CHECK (celular ~ '^[0-9]{9}$'),
    registrado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (reserva_id, numero_asiento),
    UNIQUE (reserva_id, dni),
    FOREIGN KEY (reserva_id, numero_asiento) REFERENCES reserva_asientos(reserva_id, numero)
);

-- Bloquea primero la salida, igual que reservar/cancelar, y valida la titularidad.
CREATE FUNCTION guardar_pasajeros_reserva(p_reserva_id UUID, p_usuario_uid TEXT, p_pasajeros JSONB)
RETURNS UUID LANGUAGE plpgsql AS $funcion$
DECLARE
    v_salida_id INTEGER;
    v_reserva reservas%ROWTYPE;
    v_numeros INTEGER[];
    v_reservados INTEGER[];
    v_habilitados INTEGER;
BEGIN
    SELECT salida_id INTO v_salida_id FROM reservas WHERE id=p_reserva_id AND usuario_uid=p_usuario_uid;
    IF NOT FOUND THEN RAISE EXCEPTION 'RESERVA_NO_ENCONTRADA'; END IF;
    PERFORM id FROM salidas WHERE id=v_salida_id FOR UPDATE;
    PERFORM liberar_reservas_vencidas(v_salida_id);
    SELECT * INTO v_reserva FROM reservas WHERE id=p_reserva_id AND usuario_uid=p_usuario_uid FOR UPDATE;
    IF v_reserva.estado <> 'PENDIENTE' OR v_reserva.vence_en <= clock_timestamp() THEN
        RAISE EXCEPTION 'RESERVA_NO_VIGENTE';
    END IF;
    IF p_pasajeros IS NULL OR jsonb_typeof(p_pasajeros) <> 'array' THEN RAISE EXCEPTION 'PASAJEROS_INVALIDOS'; END IF;
    IF jsonb_array_length(p_pasajeros) <> v_reserva.cantidad THEN RAISE EXCEPTION 'PASAJEROS_INCOMPLETOS'; END IF;

    -- Comprueba tipos, formato y campos permitidos antes de reemplazar datos anteriores.
    IF EXISTS (SELECT 1 FROM jsonb_array_elements(p_pasajeros) e WHERE jsonb_typeof(e) <> 'object') THEN
        RAISE EXCEPTION 'PASAJEROS_INVALIDOS';
    END IF;
    IF EXISTS (
        SELECT 1 FROM jsonb_array_elements(p_pasajeros) e
        WHERE e - ARRAY['asiento','nombreCompleto','dni','celular'] <> '{}'::jsonb
          OR NOT (e ?& ARRAY['asiento','nombreCompleto','dni','celular'])
          OR jsonb_typeof(e->'asiento') <> 'number'
          OR (e->>'asiento') !~ '^[1-9][0-9]{0,9}$'
          OR jsonb_typeof(e->'nombreCompleto') <> 'string'
          OR length(trim(e->>'nombreCompleto')) NOT BETWEEN 1 AND 200
          OR jsonb_typeof(e->'dni') <> 'string' OR (e->>'dni') !~ '^[0-9]{8}$'
          OR jsonb_typeof(e->'celular') <> 'string' OR (e->>'celular') !~ '^[0-9]{9}$'
    ) THEN RAISE EXCEPTION 'PASAJEROS_INVALIDOS'; END IF;
    IF EXISTS (SELECT 1 FROM jsonb_array_elements(p_pasajeros) e WHERE (e->>'asiento')::numeric > 2147483647) THEN
        RAISE EXCEPTION 'PASAJEROS_INVALIDOS';
    END IF;
    IF (SELECT count(DISTINCT e->>'dni') FROM jsonb_array_elements(p_pasajeros) e) <> v_reserva.cantidad THEN
        RAISE EXCEPTION 'DNI_REPETIDO';
    END IF;
    SELECT array_agg((e->>'asiento')::integer ORDER BY (e->>'asiento')::integer) INTO v_numeros
    FROM jsonb_array_elements(p_pasajeros) e;
    SELECT array_agg(numero ORDER BY numero) INTO v_reservados
    FROM reserva_asientos WHERE reserva_id=p_reserva_id AND NOT liberado;
    IF v_numeros IS DISTINCT FROM v_reservados THEN RAISE EXCEPTION 'PASAJEROS_ASIENTOS_INVALIDOS'; END IF;

    SELECT count(*) INTO v_habilitados FROM reserva_asientos ra
    JOIN salida_asientos sa ON sa.salida_id=ra.salida_id AND sa.numero=ra.numero
    JOIN asientos a ON a.vehiculo_id=sa.vehiculo_id AND a.numero=sa.numero
    JOIN vehiculos v ON v.id=sa.vehiculo_id
    JOIN salidas s ON s.id=ra.salida_id JOIN rutas r ON r.id=s.ruta_id
    JOIN terminales o ON o.id=r.terminal_origen_id JOIN terminales d ON d.id=r.terminal_destino_id
    WHERE ra.reserva_id=p_reserva_id AND NOT ra.liberado AND sa.habilitado AND a.activo AND v.activo
      AND r.activo AND o.activo AND d.activo AND s.estado='PROGRAMADA' AND s.fecha_hora_salida > clock_timestamp();
    IF v_habilitados <> v_reserva.cantidad THEN RAISE EXCEPTION 'SALIDA_NO_DISPONIBLE'; END IF;

    -- Reemplaza el formulario completo atomicamente y conserva la reserva y su vencimiento.
    DELETE FROM pasajeros_reserva WHERE reserva_id=p_reserva_id;
    INSERT INTO pasajeros_reserva(reserva_id,numero_asiento,nombre_completo,dni,celular)
    SELECT p_reserva_id,(e->>'asiento')::integer,trim(e->>'nombreCompleto'),e->>'dni',e->>'celular'
    FROM jsonb_array_elements(p_pasajeros) e;
    RETURN p_reserva_id;
END;
$funcion$;
REVOKE ALL ON FUNCTION guardar_pasajeros_reserva(UUID,TEXT,JSONB) FROM PUBLIC;
COMMIT;
