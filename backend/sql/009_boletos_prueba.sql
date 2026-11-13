-- Un boleto por asiento pagado; la emision participa en la transaccion de compra.
BEGIN;
CREATE TABLE boletos_prueba (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 codigo VARCHAR(36) NOT NULL UNIQUE DEFAULT ('CBP-'||upper(replace(gen_random_uuid()::text,'-',''))),
 reserva_id UUID NOT NULL,
 numero_asiento INTEGER NOT NULL,
 pago_id UUID NOT NULL REFERENCES pagos_prueba(id),
 importe_unitario NUMERIC(10,2) NOT NULL CHECK(importe_unitario>0),
 moneda VARCHAR(3) NOT NULL DEFAULT 'PEN' CHECK(moneda='PEN'),
 modo VARCHAR(6) NOT NULL DEFAULT 'PRUEBA' CHECK(modo='PRUEBA'),
 estado VARCHAR(7) NOT NULL DEFAULT 'EMITIDO' CHECK(estado='EMITIDO'),
 emitido_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 datos_recuperados BOOLEAN NOT NULL DEFAULT FALSE,
 pasajero JSONB NOT NULL CHECK(jsonb_typeof(pasajero)='object'),
 viaje JSONB NOT NULL CHECK(jsonb_typeof(viaje)='object'),
 UNIQUE(reserva_id,numero_asiento),
 FOREIGN KEY(reserva_id,numero_asiento) REFERENCES pasajeros_reserva(reserva_id,numero_asiento)
);

-- Copia datos al emitir y nunca reemplaza un boleto existente en un reintento.
CREATE FUNCTION emitir_boletos_prueba(p_id UUID,p_recuperados BOOLEAN DEFAULT FALSE)
RETURNS INTEGER LANGUAGE plpgsql AS $f$
DECLARE v_r reservas%ROWTYPE; v_pago pagos_prueba%ROWTYPE; v_total INTEGER; v_importe NUMERIC;
BEGIN
 SELECT * INTO v_r FROM reservas WHERE id=p_id FOR UPDATE;
 IF NOT FOUND OR v_r.estado<>'CONFIRMADA' THEN RAISE EXCEPTION 'COMPRA_NO_CONFIRMADA'; END IF;
 SELECT * INTO v_pago FROM pagos_prueba WHERE reserva_id=p_id;
 IF NOT FOUND OR v_pago.usuario_uid<>v_r.usuario_uid OR v_pago.importe<>v_r.total THEN
  RAISE EXCEPTION 'PAGO_INCONSISTENTE';
 END IF;
 SELECT count(*) INTO v_total FROM pasajeros_reserva p JOIN reserva_asientos a
  ON a.reserva_id=p.reserva_id AND a.numero=p.numero_asiento WHERE p.reserva_id=p_id AND NOT a.liberado;
 IF v_total<>v_r.cantidad THEN RAISE EXCEPTION 'PASAJEROS_INCOMPLETOS'; END IF;
 INSERT INTO boletos_prueba(reserva_id,numero_asiento,pago_id,importe_unitario,datos_recuperados,pasajero,viaje)
 SELECT v_r.id,p.numero_asiento,v_pago.id,v_r.precio_unitario,p_recuperados,
  jsonb_build_object('nombreCompleto',p.nombre_completo,'dni',p.dni),
  jsonb_build_object('salida_id',s.id,'fecha_hora_salida',s.fecha_hora_salida,'fecha_hora_llegada',s.fecha_hora_llegada,
   'embarque',jsonb_build_object('id',o.id,'nombre',o.nombre,'ciudad',o.ciudad,'direccion',o.direccion,'latitud',o.latitud,'longitud',o.longitud),
   'llegada',jsonb_build_object('id',d.id,'nombre',d.nombre,'ciudad',d.ciudad,'direccion',d.direccion,'latitud',d.latitud,'longitud',d.longitud))
 FROM pasajeros_reserva p JOIN salidas s ON s.id=v_r.salida_id JOIN rutas ru ON ru.id=s.ruta_id
 JOIN terminales o ON o.id=ru.terminal_origen_id JOIN terminales d ON d.id=ru.terminal_destino_id
 WHERE p.reserva_id=p_id
 ON CONFLICT(reserva_id,numero_asiento) DO NOTHING;
 SELECT count(*),sum(importe_unitario) INTO v_total,v_importe FROM boletos_prueba WHERE reserva_id=p_id;
 IF v_total<>v_r.cantidad OR v_importe IS DISTINCT FROM v_r.total THEN RAISE EXCEPTION 'EMISION_INCONSISTENTE'; END IF;
 RETURN v_total;
END;
$f$;
REVOKE ALL ON FUNCTION emitir_boletos_prueba(UUID,BOOLEAN) FROM PUBLIC;

-- Si falla esta emision, PostgreSQL revierte tambien el pago y el saldo de la misma compra.
CREATE FUNCTION emitir_boletos_al_confirmar() RETURNS TRIGGER LANGUAGE plpgsql AS $f$
BEGIN
 PERFORM emitir_boletos_prueba(NEW.id,FALSE);
 RETURN NEW;
END;
$f$;
REVOKE ALL ON FUNCTION emitir_boletos_al_confirmar() FROM PUBLIC;
CREATE TRIGGER reserva_confirmada_emite_boletos AFTER UPDATE OF estado ON reservas
 FOR EACH ROW WHEN(NEW.estado='CONFIRMADA' AND OLD.estado IS DISTINCT FROM NEW.estado)
 EXECUTE FUNCTION emitir_boletos_al_confirmar();

-- Recupera compras de prueba anteriores sin cobrar otra vez; marca la procedencia de sus datos.
DO $f$
DECLARE v_id UUID;
BEGIN
 FOR v_id IN SELECT r.id FROM reservas r JOIN pagos_prueba p ON p.reserva_id=r.id WHERE r.estado='CONFIRMADA'
 LOOP PERFORM emitir_boletos_prueba(v_id,TRUE); END LOOP;
END;
$f$;
COMMIT;
