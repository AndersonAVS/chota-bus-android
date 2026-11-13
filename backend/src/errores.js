// Mantiene los errores de negocio separados de los detalles internos de SQL/Firebase.
export class ErrorHttp extends Error {
  constructor(estado, codigo, mensaje) {
    super(mensaje);
    this.estado = estado;
    this.codigo = codigo;
  }
}

export function traducirErrorReserva(error) {
  const errores = {
    SALDO_INSUFICIENTE: [422, 'Tu saldo ficticio no alcanza para esta compra.'],
    RESERVA_NO_VIGENTE: [409, 'La reserva ya no esta vigente. Vuelve a consultar la disponibilidad.'],
    PASAJEROS_INVALIDOS: [400, 'Revisa los datos de los pasajeros.'],
    PASAJEROS_INCOMPLETOS: [400, 'Completa un pasajero por cada asiento reservado.'],
    PASAJEROS_ASIENTOS_INVALIDOS: [400, 'Los pasajeros deben corresponder a los asientos reservados.'],
    DNI_REPETIDO: [400, 'Cada pasajero debe tener un DNI diferente.'],
    DATOS_RESERVA_INVALIDOS: [400, 'Revisa los datos de la reserva.'],
    SALIDA_NO_ENCONTRADA: [404, 'No se encontro la salida.'],
    RESERVA_NO_ENCONTRADA: [404, 'No se encontro la reserva.'],
    ASIENTO_NO_DISPONIBLE: [409, 'Uno de los asientos ya no esta disponible. Actualiza la seleccion.'],
    SALIDA_NO_DISPONIBLE: [409, 'Esta salida ya no admite reservas.'],
    SOLICITUD_REUTILIZADA_CON_OTROS_DATOS: [409, 'Esta solicitud ya se utilizo con otros datos.'],
    RESERVA_NO_CANCELABLE: [409, 'Esta reserva no admite cancelacion desde esta operacion.'],
  };
  const definido = error.code === 'P0001' ? errores[error.message] : null;
  if (definido) return new ErrorHttp(definido[0], error.message, definido[1]);
  if (error.code === '23505') return new ErrorHttp(409, 'RESERVA_EN_CONFLICTO', 'La reserva entro en conflicto. Actualiza la seleccion.');
  if (error.code === '23503') return new ErrorHttp(409, 'SALIDA_MODIFICADA', 'Los datos de la salida cambiaron. Consulta nuevamente.');
  return error;
}
