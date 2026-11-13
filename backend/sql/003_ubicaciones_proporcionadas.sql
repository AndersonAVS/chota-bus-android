-- Ubicaciones proporcionadas por el usuario el 2026-11-13.
-- Los puntos son centros de celdas Plus Code, no una comprobacion oficial del acceso.
-- Mantiene nombres DEMO y no modifica rutas, horarios, tarifas ni capacidades ficticias.
-- Lajas no se actualiza: el usuario indico que su codigo se copio por error.
BEGIN;

-- Actualiza solo los tres registros identificados en esta base local.
-- Si cambia un id/nombre/ciudad o existe otra coordenada, revierte toda la operacion.
DO $ubicaciones$
DECLARE
    filas_actualizadas integer;
BEGIN
    UPDATE terminales AS t
    SET latitud = u.latitud,
        longitud = u.longitud,
        direccion = CASE
            WHEN t.direccion = 'DEMO - Direccion pendiente de validacion'
            THEN 'Referencia de ubicacion: Plus Code ' || u.plus_code || '. Direccion postal pendiente.'
            ELSE t.direccion
        END
    FROM (VALUES
            (3, 'DEMO - Terminal Chota', 'Chota', -6.563063::numeric, -78.646188::numeric, '6753C9P3+QG'),
            (2, 'DEMO - Terminal Cajamarca', 'Cajamarca', -7.151438::numeric, -78.523063::numeric, '6743RFXG+CQ'),
            (1, 'DEMO - Terminal Jaén', 'Jaén', -5.719063::numeric, -78.797688::numeric, '676376J2+9W')
    ) AS u(id, nombre, ciudad, latitud, longitud, plus_code)
    WHERE t.id = u.id AND t.nombre = u.nombre AND t.ciudad = u.ciudad
      AND ((t.latitud IS NULL AND t.longitud IS NULL)
        OR (t.latitud = u.latitud AND t.longitud = u.longitud));

    GET DIAGNOSTICS filas_actualizadas = ROW_COUNT;
    IF filas_actualizadas <> 3 THEN
        RAISE EXCEPTION 'Se esperaban 3 terminales sin otra ubicacion. Revisar ids y datos antes de reintentar.';
    END IF;
END;
$ubicaciones$;

COMMIT;

-- Verifica las ubicaciones finales y que Lajas siga pendiente.
SELECT id, nombre, ciudad, direccion, latitud, longitud FROM terminales ORDER BY id;
