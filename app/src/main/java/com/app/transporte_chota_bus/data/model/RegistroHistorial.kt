package com.app.transporte_chota_bus.data.model

// Resume una reserva completa: el total reservado no implica que haya sido pagado.
data class RegistroHistorial(val id:String,val salidaId:Int,val estado:EstadoReserva,val creadoEn:String,
    val fechaSalida:String,val origen:String,val destino:String,val cantidad:Int,val totalReserva:String,
    val importePagado:String,val pagada:Boolean,val fechaPago:String?,val asientos:List<Int>,val boletosEmitidos:Int)
data class PaginaHistorial(val registros:List<RegistroHistorial>,val siguiente:String?)
