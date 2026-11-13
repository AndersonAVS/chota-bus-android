package com.app.transporte_chota_bus.data.model

// Importes decimales del servidor, siempre identificados como dinero ficticio.
data class SaldoPrueba(val saldo:String)
data class PagoPrueba(val id:String,val reservaId:String,val importe:String,val saldo:String)
