-- Dinero ficticio: una asignacion unica por UID y un cargo por reserva.
BEGIN;
CREATE TABLE saldos_prueba (
 usuario_uid VARCHAR(128) PRIMARY KEY CHECK(length(trim(usuario_uid))>0),
 saldo NUMERIC(12,2) NOT NULL DEFAULT 300.00 CHECK(saldo>=0),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE pagos_prueba (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 reserva_id UUID NOT NULL UNIQUE REFERENCES reservas(id),
 usuario_uid VARCHAR(128) NOT NULL REFERENCES saldos_prueba(usuario_uid),
 importe NUMERIC(12,2) NOT NULL CHECK(importe>0),
 saldo_restante NUMERIC(12,2) NOT NULL CHECK(saldo_restante>=0),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
-- Conserva el orden de bloqueos de reservas y serializa compras del mismo cliente.
CREATE FUNCTION pagar_reserva_prueba(p_id UUID,p_uid TEXT) RETURNS UUID LANGUAGE plpgsql AS $f$
DECLARE v_salida INTEGER; v_r reservas%ROWTYPE; v_pago UUID; v_datos JSONB; v_saldo NUMERIC(12,2);
BEGIN
 SELECT salida_id INTO v_salida FROM reservas WHERE id=p_id AND usuario_uid=p_uid;
 IF NOT FOUND THEN RAISE EXCEPTION 'RESERVA_NO_ENCONTRADA'; END IF;
 PERFORM id FROM salidas WHERE id=v_salida FOR UPDATE;
 SELECT * INTO v_r FROM reservas WHERE id=p_id AND usuario_uid=p_uid FOR UPDATE;
 SELECT id INTO v_pago FROM pagos_prueba WHERE reserva_id=p_id AND usuario_uid=p_uid;
 IF FOUND THEN RETURN v_pago; END IF;
 IF v_r.estado<>'PENDIENTE' OR v_r.vence_en<=clock_timestamp() THEN RAISE EXCEPTION 'RESERVA_NO_VIGENTE'; END IF;
 SELECT coalesce(jsonb_agg(jsonb_build_object('asiento',numero_asiento,'nombreCompleto',nombre_completo,'dni',dni,'celular',celular)),'[]'::jsonb)
 INTO v_datos FROM pasajeros_reserva WHERE reserva_id=p_id;
 -- Reutiliza las reglas de pasajeros, viaje y asientos antes de efectuar el cargo.
 PERFORM guardar_pasajeros_reserva(p_id,p_uid,v_datos);
 INSERT INTO saldos_prueba(usuario_uid) VALUES(p_uid) ON CONFLICT DO NOTHING;
 SELECT saldo INTO v_saldo FROM saldos_prueba WHERE usuario_uid=p_uid FOR UPDATE;
 IF v_r.vence_en<=clock_timestamp() THEN RAISE EXCEPTION 'RESERVA_NO_VIGENTE'; END IF;
 IF v_saldo<v_r.total THEN RAISE EXCEPTION 'SALDO_INSUFICIENTE'; END IF;
 UPDATE saldos_prueba SET saldo=saldo-v_r.total WHERE usuario_uid=p_uid RETURNING saldo INTO v_saldo;
 INSERT INTO pagos_prueba(reserva_id,usuario_uid,importe,saldo_restante)
 VALUES(p_id,p_uid,v_r.total,v_saldo) RETURNING id INTO v_pago;
 UPDATE reservas SET estado='CONFIRMADA' WHERE id=p_id;
 RETURN v_pago;
END;
$f$;
REVOKE ALL ON FUNCTION pagar_reserva_prueba(UUID,TEXT) FROM PUBLIC;
COMMIT;
