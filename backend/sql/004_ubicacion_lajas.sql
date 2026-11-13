-- Ubicacion corregida de Lajas, proporcionada por el usuario el 2026-11-13.
-- Centro de la celda Plus Code 6753C7R8+238, con redondeo a seis decimales.
-- El codigo anterior C9Q2+3M Chota se descarto por confirmacion del usuario.
BEGIN;

-- Actualiza solo la sede DEMO de Lajas inspeccionada en esta base local.
DO $lajas$
DECLARE
    filas_actualizadas integer;
BEGIN
    UPDATE terminales
    SET latitud = -6.559963,
        longitud = -78.734797,
        direccion = CASE
            WHEN direccion = 'DEMO - Direccion pendiente de validacion'
            THEN 'Referencia de ubicacion: Plus Code 6753C7R8+238. Codigo postal 06601. Direccion postal pendiente.'
            ELSE direccion
        END
    WHERE id = 4 AND nombre = 'DEMO - Terminal Lajas' AND ciudad = 'Lajas'
      AND ((latitud IS NULL AND longitud IS NULL)
        OR (latitud = -6.559963 AND longitud = -78.734797));

    GET DIAGNOSTICS filas_actualizadas = ROW_COUNT;
    IF filas_actualizadas <> 1 THEN
        RAISE EXCEPTION 'No coincide el terminal de Lajas esperado o tiene otra ubicacion. Revisar antes de reintentar.';
    END IF;
END;
$lajas$;

COMMIT;

-- Comprueba el punto guardado sin modificar rutas, horarios o precios DEMO.
SELECT id, nombre, ciudad, direccion, latitud, longitud FROM terminales WHERE id = 4;
