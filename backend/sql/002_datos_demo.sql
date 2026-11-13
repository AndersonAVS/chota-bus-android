-- DATOS FICTICIOS para desarrollo local: no representan tarifas ni horarios oficiales.
-- Ejecutar en chota_bus desde pgAdmin. Puede repetirse secuencialmente sin duplicar.
BEGIN;
SET LOCAL TIME ZONE 'America/Lima';

-- Crea terminales identificados como DEMO sin inventar coordenadas de la empresa.
INSERT INTO terminales (nombre, ciudad, direccion)
SELECT 'DEMO - Terminal ' || ciudad, ciudad, 'DEMO - Direccion pendiente de validacion'
FROM (VALUES ('Chota'), ('Cajamarca'), ('Jaén'), ('Lajas')) AS ciudades(ciudad)
WHERE NOT EXISTS (
  SELECT 1 FROM terminales t WHERE t.nombre = 'DEMO - Terminal ' || ciudades.ciudad
);

-- Conecta la sede DEMO de Chota con las otras tres sedes solo para las pruebas.
INSERT INTO rutas (terminal_origen_id, terminal_destino_id)
SELECT o.id, d.id FROM terminales o CROSS JOIN terminales d
WHERE o.nombre = 'DEMO - Terminal Chota'
  AND d.nombre IN ('DEMO - Terminal Cajamarca', 'DEMO - Terminal Jaén', 'DEMO - Terminal Lajas')
ON CONFLICT (terminal_origen_id, terminal_destino_id) DO NOTHING;

-- Programa dos salidas para manana con precios, duracion y capacidad ficticios.
INSERT INTO salidas (ruta_id, fecha_hora_salida, fecha_hora_llegada, precio, capacidad)
SELECT r.id, programacion.inicio, programacion.inicio + INTERVAL '3 hours', 30.00, 15
FROM rutas r
JOIN terminales o ON o.id = r.terminal_origen_id
JOIN terminales d ON d.id = r.terminal_destino_id
CROSS JOIN (
  SELECT ((CURRENT_DATE + 1) + hora) AT TIME ZONE 'America/Lima' AS inicio
  FROM (VALUES (TIME '08:00'), (TIME '14:00')) AS horas(hora)
) AS programacion
WHERE o.nombre = 'DEMO - Terminal Chota'
  AND d.nombre IN ('DEMO - Terminal Cajamarca', 'DEMO - Terminal Jaén', 'DEMO - Terminal Lajas')
  AND NOT EXISTS (
    SELECT 1 FROM salidas s WHERE s.ruta_id = r.id AND s.fecha_hora_salida = programacion.inicio
  );
COMMIT;

-- La fecha devuelta se debe utilizar en la URL de busqueda.
SELECT ((CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date + 1)::text AS fecha_de_prueba;
