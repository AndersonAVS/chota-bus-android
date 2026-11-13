-- Distribucion FICTICIA para pruebas, no corresponde a la flota oficial de Chota Bus.
-- Asigna un vehiculo DEMO distinto a cada salida DEMO existente sin vehiculo.
BEGIN;
DO $demo$
DECLARE s RECORD; v_id INTEGER; v_asientos INTEGER;
BEGIN
    FOR s IN
        SELECT sa.id, sa.capacidad FROM salidas sa
        JOIN rutas r ON r.id = sa.ruta_id
        JOIN terminales o ON o.id = r.terminal_origen_id
        JOIN terminales d ON d.id = r.terminal_destino_id
        WHERE sa.vehiculo_id IS NULL AND sa.estado = 'PROGRAMADA'
          AND o.nombre LIKE 'DEMO - Terminal %' AND d.nombre LIKE 'DEMO - Terminal %'
        ORDER BY sa.id FOR UPDATE OF sa
    LOOP
        INSERT INTO vehiculos(placa, nombre)
        VALUES ('DEMO-S' || s.id, 'Vehiculo DEMO de salida ' || s.id)
        ON CONFLICT (placa) DO UPDATE SET nombre = vehiculos.nombre RETURNING id INTO v_id;
        INSERT INTO asientos(vehiculo_id, numero, fila, columna)
        SELECT v_id, n, ((n - 1) / 3) + 1, ((n - 1) % 3) + 1
        FROM generate_series(1, s.capacidad) n ON CONFLICT DO NOTHING;
        SELECT count(*) INTO v_asientos FROM asientos WHERE vehiculo_id = v_id AND activo;
        IF v_asientos <> s.capacidad THEN RAISE EXCEPTION 'Distribucion DEMO incompatible; revisar vehiculo.'; END IF;
        UPDATE salidas SET vehiculo_id = v_id WHERE id = s.id;
        INSERT INTO salida_asientos(salida_id, vehiculo_id, numero)
        SELECT s.id, v_id, numero FROM asientos WHERE vehiculo_id = v_id AND activo;
    END LOOP;
END;
$demo$;
COMMIT;

-- Muestra disponibilidad real calculada sobre el inventario DEMO.
SELECT salida_id, count(*) AS asientos_totales,
       count(*) FILTER (WHERE disponible) AS asientos_libres
FROM disponibilidad_asientos GROUP BY salida_id ORDER BY salida_id;
