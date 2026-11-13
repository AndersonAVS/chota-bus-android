package com.app.transporte_chota_bus.data.model

// Copia del boleto emitido en SQL: los cambios del catalogo no modifican estos datos.
data class BoletoPrueba(
    val id:String,val codigo:String,val reservaId:String,val asiento:Int,val importe:String,
    val emitidoEn:String,val datosRecuperados:Boolean,val nombreCompleto:String,val dni:String,
    val salidaId:Int,val fechaSalida:String,val fechaLlegada:String,val embarque:TerminalViaje,val llegada:TerminalViaje
)

data class PaginaBoletos(val boletos:List<BoletoPrueba>,val siguiente:String?)
