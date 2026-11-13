-- Esquema inicial ya creado manualmente en el primer bloque del Sprint 3.
-- Este archivo permite reconstruir una base VACIA. No ejecutarlo sobre la base actual.
BEGIN;
CREATE TABLE terminales (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL, ciudad VARCHAR(80) NOT NULL, direccion VARCHAR(250) NOT NULL,
    latitud NUMERIC(9,6), longitud NUMERIC(9,6), activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT coordenadas_validas CHECK (
        (latitud IS NULL AND longitud IS NULL) OR
        (latitud IS NOT NULL AND longitud IS NOT NULL AND latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180))
);
CREATE TABLE rutas (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    terminal_origen_id INTEGER NOT NULL REFERENCES terminales(id),
    terminal_destino_id INTEGER NOT NULL REFERENCES terminales(id), activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT terminales_distintos CHECK (terminal_origen_id <> terminal_destino_id),
    CONSTRAINT ruta_unica UNIQUE (terminal_origen_id,terminal_destino_id)
);
CREATE TABLE salidas (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ruta_id INTEGER NOT NULL REFERENCES rutas(id),
    fecha_hora_salida TIMESTAMPTZ NOT NULL, fecha_hora_llegada TIMESTAMPTZ NOT NULL,
    precio NUMERIC(10,2) NOT NULL CHECK (precio > 0), capacidad INTEGER NOT NULL CHECK (capacidad > 0),
    estado VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADA',
    CONSTRAINT horario_valido CHECK (fecha_hora_llegada > fecha_hora_salida),
    CONSTRAINT estado_valido CHECK (estado IN ('PROGRAMADA','CANCELADA','FINALIZADA'))
);
CREATE INDEX idx_salidas_ruta_fecha ON salidas(ruta_id,fecha_hora_salida);
COMMIT;
