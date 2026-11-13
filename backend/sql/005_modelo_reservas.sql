-- Sprint 4: ejecutar una sola vez sobre las tablas terminales, rutas y salidas.
-- Toda la migracion es atomica; no elimina ni reemplaza datos existentes.
BEGIN;

-- Identifica el vehiculo y su distribucion fisica de asientos.
CREATE TABLE vehiculos (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    placa VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(120) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (length(trim(placa)) > 0), CHECK (length(trim(nombre)) > 0)
);
CREATE TABLE asientos (
    vehiculo_id INTEGER NOT NULL REFERENCES vehiculos(id),
    numero INTEGER NOT NULL CHECK (numero > 0),
    fila INTEGER NOT NULL CHECK (fila > 0),
    columna INTEGER NOT NULL CHECK (columna BETWEEN 1 AND 6),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (vehiculo_id, numero),
    UNIQUE (vehiculo_id, fila, columna)
);

-- Relaciona una salida con su vehiculo y con los asientos que puede ofrecer.
ALTER TABLE salidas ADD COLUMN vehiculo_id INTEGER REFERENCES vehiculos(id);
ALTER TABLE salidas ADD CONSTRAINT salida_vehiculo_unico UNIQUE (id, vehiculo_id);
CREATE TABLE salida_asientos (
    salida_id INTEGER NOT NULL,
    vehiculo_id INTEGER NOT NULL,
    numero INTEGER NOT NULL,
    habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (salida_id, numero),
    FOREIGN KEY (salida_id, vehiculo_id) REFERENCES salidas(id, vehiculo_id),
    FOREIGN KEY (vehiculo_id, numero) REFERENCES asientos(vehiculo_id, numero)
);

-- Guarda la reserva, su propietario Firebase y el precio calculado por el servidor.
CREATE TABLE reservas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    salida_id INTEGER NOT NULL REFERENCES salidas(id),
    usuario_uid VARCHAR(128) NOT NULL CHECK (length(trim(usuario_uid)) > 0),
    solicitud_id UUID NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'CANCELADA', 'EXPIRADA', 'CONFIRMADA')),
    cantidad INTEGER NOT NULL CHECK (cantidad BETWEEN 1 AND 9),
    precio_unitario NUMERIC(10,2) NOT NULL CHECK (precio_unitario > 0),
    total NUMERIC(12,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    vence_en TIMESTAMPTZ NOT NULL,
    CHECK (vence_en > creado_en),
    UNIQUE (id, salida_id),
    UNIQUE (usuario_uid, solicitud_id)
);
CREATE TABLE reserva_asientos (
    reserva_id UUID NOT NULL,
    salida_id INTEGER NOT NULL,
    numero INTEGER NOT NULL,
    liberado BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (reserva_id, numero),
    FOREIGN KEY (reserva_id, salida_id) REFERENCES reservas(id, salida_id),
    FOREIGN KEY (salida_id, numero) REFERENCES salida_asientos(salida_id, numero)
);

-- La restriccion impide dos ocupaciones activas del mismo asiento en la misma salida.
CREATE UNIQUE INDEX asiento_una_reserva_activa
    ON reserva_asientos(salida_id, numero) WHERE liberado = FALSE;
CREATE INDEX reservas_pendientes_vencimiento ON reservas(salida_id, vence_en)
    WHERE estado = 'PENDIENTE';
CREATE INDEX reservas_usuario_fecha ON reservas(usuario_uid, creado_en DESC);

-- Calcula la disponibilidad, sin mostrar UID ni informacion privada de la reserva.
CREATE VIEW disponibilidad_asientos AS
SELECT sa.salida_id, sa.numero, a.fila, a.columna,
    (sa.habilitado AND a.activo AND v.activo AND r.activo AND o.activo AND d.activo
     AND s.estado = 'PROGRAMADA' AND s.fecha_hora_salida > statement_timestamp()
     AND NOT EXISTS (
        SELECT 1 FROM reserva_asientos ra JOIN reservas re ON re.id = ra.reserva_id
        WHERE ra.salida_id = sa.salida_id AND ra.numero = sa.numero AND NOT ra.liberado
          AND (re.estado = 'CONFIRMADA' OR
              (re.estado = 'PENDIENTE' AND re.vence_en > statement_timestamp()))
     )) AS disponible
FROM salida_asientos sa
JOIN asientos a ON a.vehiculo_id = sa.vehiculo_id AND a.numero = sa.numero
JOIN vehiculos v ON v.id = sa.vehiculo_id
JOIN salidas s ON s.id = sa.salida_id
JOIN rutas r ON r.id = s.ruta_id
JOIN terminales o ON o.id = r.terminal_origen_id
JOIN terminales d ON d.id = r.terminal_destino_id;

-- Libera reservas vencidas bajo el mismo bloqueo de salida usado al reservar.
CREATE FUNCTION liberar_reservas_vencidas(p_salida_id INTEGER) RETURNS INTEGER
LANGUAGE plpgsql AS $funcion$
DECLARE v_liberados INTEGER;
BEGIN
    PERFORM id FROM salidas WHERE id = p_salida_id FOR UPDATE;
    WITH vencidas AS (
        UPDATE reservas SET estado = 'EXPIRADA'
        WHERE salida_id = p_salida_id AND estado = 'PENDIENTE' AND vence_en <= clock_timestamp()
        RETURNING id
    )
    UPDATE reserva_asientos SET liberado = TRUE
    WHERE reserva_id IN (SELECT id FROM vencidas) AND NOT liberado;
    GET DIAGNOSTICS v_liberados = ROW_COUNT;
    RETURN v_liberados;
END;
$funcion$;

-- Reserva todos los asientos solicitados o ninguno; no recibe precio del cliente.
-- p_usuario_uid debe provenir de un token Firebase verificado en la futura API.
CREATE FUNCTION reservar_asientos(p_salida_id INTEGER, p_usuario_uid TEXT,
    p_numeros INTEGER[], p_solicitud_id UUID) RETURNS reservas
LANGUAGE plpgsql AS $funcion$
DECLARE
    v_salida salidas%ROWTYPE;
    v_reserva reservas%ROWTYPE;
    v_numeros INTEGER[];
    v_existentes INTEGER[];
    v_disponibles INTEGER;
    v_ahora TIMESTAMPTZ;
BEGIN
    IF p_usuario_uid IS NULL OR length(trim(p_usuario_uid)) NOT BETWEEN 1 AND 128
       OR p_solicitud_id IS NULL OR p_numeros IS NULL
       OR cardinality(p_numeros) NOT BETWEEN 1 AND 9 OR array_ndims(p_numeros) <> 1
       OR EXISTS (SELECT 1 FROM unnest(p_numeros) n WHERE n IS NULL OR n <= 0)
       OR (SELECT count(DISTINCT n) FROM unnest(p_numeros) n) <> cardinality(p_numeros) THEN
        RAISE EXCEPTION 'DATOS_RESERVA_INVALIDOS';
    END IF;
    SELECT array_agg(n ORDER BY n) INTO v_numeros FROM unnest(p_numeros) n;

    -- Serializa reintentos de la misma solicitud y luego bloquea una sola salida.
    PERFORM pg_advisory_xact_lock(hashtextextended(p_usuario_uid || ':' || p_solicitud_id::text, 0));
    SELECT * INTO v_salida FROM salidas WHERE id = p_salida_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'SALIDA_NO_ENCONTRADA'; END IF;
    PERFORM liberar_reservas_vencidas(p_salida_id);

    SELECT * INTO v_reserva FROM reservas
    WHERE usuario_uid = p_usuario_uid AND solicitud_id = p_solicitud_id;
    IF FOUND THEN
        SELECT array_agg(numero ORDER BY numero) INTO v_existentes
        FROM reserva_asientos WHERE reserva_id = v_reserva.id;
        IF v_reserva.salida_id <> p_salida_id OR v_existentes <> v_numeros THEN
            RAISE EXCEPTION 'SOLICITUD_REUTILIZADA_CON_OTROS_DATOS';
        END IF;
        RETURN v_reserva;
    END IF;

    v_ahora := clock_timestamp();
    IF v_salida.estado <> 'PROGRAMADA' OR v_salida.fecha_hora_salida <= v_ahora
       OR v_salida.vehiculo_id IS NULL THEN RAISE EXCEPTION 'SALIDA_NO_DISPONIBLE'; END IF;
    SELECT count(*) INTO v_disponibles FROM disponibilidad_asientos
    WHERE salida_id = p_salida_id AND numero = ANY(v_numeros) AND disponible;
    IF v_disponibles <> cardinality(v_numeros) THEN RAISE EXCEPTION 'ASIENTO_NO_DISPONIBLE'; END IF;

    INSERT INTO reservas(salida_id, usuario_uid, solicitud_id, cantidad, precio_unitario, creado_en, vence_en)
    VALUES (p_salida_id, p_usuario_uid, p_solicitud_id, cardinality(v_numeros), v_salida.precio,
        v_ahora, v_ahora + INTERVAL '10 minutes') RETURNING * INTO v_reserva;
    INSERT INTO reserva_asientos(reserva_id, salida_id, numero)
    SELECT v_reserva.id, p_salida_id, n FROM unnest(v_numeros) n;
    RETURN v_reserva;
END;
$funcion$;

-- Solo permite al propietario cancelar una reserva pendiente; conserva el historial.
CREATE FUNCTION cancelar_reserva(p_reserva_id UUID, p_usuario_uid TEXT) RETURNS reservas
LANGUAGE plpgsql AS $funcion$
DECLARE v_reserva reservas%ROWTYPE; v_salida_id INTEGER;
BEGIN
    SELECT salida_id INTO v_salida_id FROM reservas WHERE id = p_reserva_id AND usuario_uid = p_usuario_uid;
    IF NOT FOUND THEN RAISE EXCEPTION 'RESERVA_NO_ENCONTRADA'; END IF;
    PERFORM id FROM salidas WHERE id = v_salida_id FOR UPDATE;
    PERFORM liberar_reservas_vencidas(v_salida_id);
    SELECT * INTO v_reserva FROM reservas WHERE id = p_reserva_id AND usuario_uid = p_usuario_uid FOR UPDATE;
    IF v_reserva.estado = 'CONFIRMADA' THEN RAISE EXCEPTION 'RESERVA_NO_CANCELABLE'; END IF;
    IF v_reserva.estado <> 'PENDIENTE' THEN RETURN v_reserva; END IF;
    UPDATE reservas SET estado = 'CANCELADA' WHERE id = p_reserva_id RETURNING * INTO v_reserva;
    UPDATE reserva_asientos SET liberado = TRUE WHERE reserva_id = p_reserva_id;
    RETURN v_reserva;
END;
$funcion$;

-- Las funciones de escritura quedan restringidas al propietario del esquema.
REVOKE ALL ON FUNCTION liberar_reservas_vencidas(INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION reservar_asientos(INTEGER, TEXT, INTEGER[], UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION cancelar_reserva(UUID, TEXT) FROM PUBLIC;
COMMIT;
