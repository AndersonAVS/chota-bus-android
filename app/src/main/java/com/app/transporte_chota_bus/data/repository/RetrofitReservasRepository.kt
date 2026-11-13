package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.remote.*
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import com.app.transporte_chota_bus.domain.model.FechaHoraServidor
import java.util.UUID

class RetrofitReservasRepository(private val api: ReservasApi, private val sesion: ProveedorTokenSesion) : ReservasRepository {
    // Valida el historial sin sumar reservas sin pagar como si fueran compras.
    override suspend fun consultarHistorial(despues:String?) = ejecutar { token ->
        despues?.let { UUID.fromString(it) }
        val json=api.historial(token,despues)
        val lista=json.getAsJsonArray("datos").map { e->
            val d=e.asJsonObject
            require(d.texto("modo")=="PRUEBA"&&d.texto("moneda")=="PEN")
            val id=d.texto("id").also { UUID.fromString(it) }
            val estado=EstadoReserva.valueOf(d.texto("estado"));val pagada=d.booleano("pagada")
            val creado=d.texto("creado_en").also { FechaHoraServidor.instante(it) }
            val salida=d.texto("fecha_hora_salida").also { FechaHoraServidor.instante(it) }
            val pago=d.get("fecha_pago").let { v->require(v!=null);if(v.isJsonNull)null else v.asString.also { FechaHoraServidor.instante(it) } }
            val total=d.texto("total_reserva");val importe=d.texto("importe_pagado")
            require(Regex("[0-9]+\\.[0-9]{2}").matches(total)&&Regex("[0-9]+\\.[0-9]{2}").matches(importe))
            require(BigDecimal(total)>BigDecimal.ZERO&&BigDecimal(importe)>=BigDecimal.ZERO)
            require(pagada==(estado==EstadoReserva.CONFIRMADA)&&pagada==(pago!=null))
            require(if(pagada)BigDecimal(importe).compareTo(BigDecimal(total))==0 else BigDecimal(importe).compareTo(BigDecimal.ZERO)==0)
            val cantidad=d.entero("cantidad").also { require(it in 1..9) }
            val asientos=d.getAsJsonArray("asientos").map { it.asBigDecimal.intValueExact() }
            require(asientos.size==cantidad&&asientos.distinct().size==cantidad&&asientos.all { it in 1..200 })
            val boletos=d.entero("boletos_emitidos");require(boletos==if(pagada)cantidad else 0)
            RegistroHistorial(id,d.entero("salida_id").also { require(it>0) },estado,creado,salida,d.texto("origen"),d.texto("destino"),
                cantidad,total,importe,pagada,pago,asientos.sorted(),boletos)
        }
        require(lista.size<=20&&lista.map { it.id }.distinct().size==lista.size)
        val siguiente=json.get("siguiente").let { e->require(e!=null);if(e.isJsonNull)null else e.asString.also { UUID.fromString(it) } }
        require(siguiente==null||(lista.size==20&&siguiente==lista.last().id&&siguiente!=despues))
        PaginaHistorial(lista,siguiente)
    }
    override suspend fun consultarMisBoletos(despues:String?) = ejecutar { token ->
        despues?.let { UUID.fromString(it) }
        val json=api.misBoletos(token,despues)
        val lista=json.getAsJsonArray("datos").map { leerBoleto(it.asJsonObject) }
        require(lista.size<=20 && lista.map { it.id }.distinct().size==lista.size && lista.map { it.codigo }.distinct().size==lista.size)
        val siguiente=json.get("siguiente").let { e->
            require(e!=null)
            if(e.isJsonNull)null else e.asJsonPrimitive.also { require(it.isString) }.asString.also { UUID.fromString(it) }
        }
        require(siguiente==null||(lista.size==20&&siguiente==lista.last().id&&siguiente!=despues))
        PaginaBoletos(lista,siguiente)
    }
    override suspend fun consultarBoleto(id:String) = ejecutar { token ->
        UUID.fromString(id)
        leerBoleto(api.boleto(token,id).getAsJsonObject("datos")).also { require(it.id==id) }
    }
    // Consulta solo la compra solicitada y rechaza boletos mezclados o duplicados.
    override suspend fun consultarBoletos(id:String) = ejecutar { token ->
        UUID.fromString(id)
        val lista=api.boletos(token,id).getAsJsonArray("datos").map { leerBoleto(it.asJsonObject) }
        require(lista.size<=9 && lista.all { it.reservaId==id })
        require(lista.map { it.id }.distinct().size==lista.size && lista.map { it.codigo }.distinct().size==lista.size)
        require(lista.map { it.asiento }.distinct().size==lista.size)
        lista.sortedBy { it.asiento }
    }
    override suspend fun consultarSaldoPrueba() = ejecutar { token ->
        val d=api.saldoPrueba(token).getAsJsonObject("datos")
        require(d.texto("modo")=="PRUEBA" && d.texto("moneda")=="PEN")
        SaldoPrueba(d.texto("saldo").also { require(BigDecimal(it)>=BigDecimal.ZERO) })
    }
    // La reserva es la clave de reintento: Android no envia precio, saldo ni UID.
    override suspend fun pagarConSaldoPrueba(id:String) = ejecutar { token ->
        val d=api.pagarPrueba(token,id).getAsJsonObject("datos")
        require(d.texto("modo")=="PRUEBA" && d.texto("moneda")=="PEN" && d.texto("reserva_id")==id)
        PagoPrueba(d.texto("id").also { UUID.fromString(it) },id,
            d.texto("importe").also { require(BigDecimal(it)>BigDecimal.ZERO) },
            d.texto("saldo").also { require(BigDecimal(it)>=BigDecimal.ZERO) })
    }
    override suspend fun consultarResumen(id: String) = ejecutar { token ->
        leerResumen(api.resumen(token,id)).also { require(it.reserva.id == id) }
    }
    override suspend fun guardarPasajeros(id: String, pasajeros: List<PasajeroReserva>) = ejecutar { token ->
        val normalizados = pasajeros.map { it.copy(nombreCompleto=it.nombreCompleto.trim(), dni=it.dni.trim(), celular=it.celular.trim()) }.sortedBy { it.asiento }
        require(com.app.transporte_chota_bus.domain.validation.ValidadorPasajeros.validar(normalizados,normalizados.map { it.asiento }).valido)
        leerResumen(api.pasajeros(token,id,SolicitudPasajeros(normalizados))).also {
            require(it.reserva.id == id && it.pasajeros == normalizados && it.datosCompletos)
        }
    }
    override fun usuarioActual() = sesion.usuarioActual()
    override suspend fun consultarAsientos(salidaId: Int) = ejecutar { token ->
        val dato = api.asientos(token, salidaId).getAsJsonObject("datos")
        require(dato.entero("salida_id") == salidaId && dato.texto("moneda") == "PEN")
        FechaHoraServidor.instante(dato.texto("hora_servidor"))
        val precio = dato.texto("precio_unitario").also { require(BigDecimal(it) > BigDecimal.ZERO) }
        val asientos = dato.getAsJsonArray("asientos").map {
            val a = it.asJsonObject
            AsientoDisponible(a.entero("numero"), a.entero("fila"), a.entero("columna"), a.booleano("disponible"))
        }
        require(asientos.size <= 200 && asientos.all { it.numero > 0 && it.fila in 1..100 && it.columna in 1..6 })
        require(asientos.map { it.numero }.distinct().size == asientos.size)
        require(asientos.map { it.fila to it.columna }.distinct().size == asientos.size)
        require(dato.entero("asientos_totales") == asientos.size)
        require(dato.entero("asientos_libres") == asientos.count { it.disponible })
        InventarioAsientos(salidaId, precio, asientos)
    }
    override suspend fun reservar(salidaId: Int, asientos: List<Int>, solicitudId: String) = ejecutar { token ->
        require(salidaId > 0 && asientos.size in 1..9 && asientos.all { it > 0 } && asientos.distinct().size == asientos.size)
        UUID.fromString(solicitudId)
        leerReserva(api.reservar(token, SolicitudReserva(salidaId, asientos.sorted(), solicitudId))).also {
            require(it.salidaId == salidaId && it.asientos.sorted() == asientos.sorted())
        }
    }
    override suspend fun consultarReserva(id: String) = ejecutar { token ->
        UUID.fromString(id)
        leerReserva(api.consultar(token, id)).also { require(it.id == id) }
    }
    override suspend fun cancelarReserva(id: String) = ejecutar { token ->
        UUID.fromString(id)
        leerReserva(api.cancelar(token, id)).also { require(it.id == id) }
    }

    // Ante un 401 renueva el token una sola vez, sin cambiar de usuario ni de solicitud.
    private suspend fun <T> ejecutar(operacion: suspend (String) -> T): Result<T> = try {
        val inicial = sesion.obtener(false)
        fun comprobarUsuario() {
            if (sesion.usuarioActual() != inicial.uid) throw ErrorReserva(TipoErrorReserva.SESION, "La sesión cambió. Vuelve a ingresar.")
        }
        comprobarUsuario()
        val resultado = try { operacion("Bearer ${inicial.token}") }
        catch (error: HttpException) {
            if (error.code() != 401) throw error
            val renovado = sesion.obtener(true)
            if (renovado.uid != inicial.uid) throw ErrorReserva(TipoErrorReserva.SESION, "La sesión cambió. Vuelve a ingresar.")
            comprobarUsuario()
            operacion("Bearer ${renovado.token}")
        }
        comprobarUsuario()
        Result.success(resultado)
    } catch (error: CancellationException) { throw error
    } catch (error: ErrorReserva) { Result.failure(error)
    } catch (error: HttpException) {
        val (tipo, mensaje) = when (error.code()) {
            422 -> TipoErrorReserva.RECHAZADA to "Tu saldo no alcanza para esta compra."
            401 -> TipoErrorReserva.SESION to "Inicia sesión nuevamente para continuar."
            409 -> TipoErrorReserva.CONFLICTO to "La reserva cambió o un asiento ya no está disponible. Actualiza la selección."
            404 -> TipoErrorReserva.NO_ENCONTRADO to "No se encontró la salida o la reserva."
            400, 413, 415 -> TipoErrorReserva.RECHAZADA to "Revisa los datos de la reserva."
            else -> TipoErrorReserva.RED to "No se pudo confirmar la operación. Intenta nuevamente."
        }
        Result.failure(ErrorReserva(tipo, mensaje))
    } catch (_: IOException) {
        Result.failure(ErrorReserva(TipoErrorReserva.RED, "No se pudo confirmar la operación. Revisa tu conexión e intenta nuevamente."))
    } catch (_: Exception) {
        Result.failure(ErrorReserva(TipoErrorReserva.DATOS, "El servicio devolvió datos inválidos. Intenta nuevamente."))
    }

    // Valida importes y fechas; el total recibido corresponde al calculo del servidor.
    private fun leerReserva(json: JsonObject): ReservaAsientos {
        val d = json.getAsJsonObject("datos")
        val id = d.texto("id").also { UUID.fromString(it) }
        val salida = d.entero("salida_id").also { require(it > 0) }
        val numeros = d.getAsJsonArray("asientos").map { it.asBigDecimal.intValueExact() }
        require(numeros.size in 1..9 && numeros.all { it > 0 } && numeros.distinct().size == numeros.size)
        require(d.entero("cantidad") == numeros.size && d.texto("moneda") == "PEN")
        val precio = d.texto("precio_unitario")
        val total = d.texto("total")
        require(BigDecimal(precio) > BigDecimal.ZERO && BigDecimal(total).compareTo(BigDecimal(precio).multiply(BigDecimal(numeros.size))) == 0)
        val creado = FechaHoraServidor.instante(d.texto("creado_en"))
        val vence = d.texto("vence_en").also { require(FechaHoraServidor.instante(it).isAfter(creado)) }
        val hora = d.texto("hora_servidor").also { FechaHoraServidor.instante(it) }
        return ReservaAsientos(id, salida, EstadoReserva.valueOf(d.texto("estado")), numeros.sorted(), precio, total, vence, hora)
    }
    // Valida el resumen recibido sin recalcular ni aceptar un total del formulario.
    private fun leerResumen(json: JsonObject): ResumenCompra {
        val datos = json.getAsJsonObject("datos")
        val reserva = leerReserva(JsonObject().apply { add("datos",datos.getAsJsonObject("reserva")) })
        val viaje = datos.getAsJsonObject("viaje")
        val salida = viaje.texto("fecha_hora_salida")
        val llegada = viaje.texto("fecha_hora_llegada")
        require(FechaHoraServidor.instante(llegada).isAfter(FechaHoraServidor.instante(salida)))
        fun terminal(d: JsonObject): TerminalViaje {
            val lat = d.get("latitud")?.takeUnless { it.isJsonNull }?.asDouble
            val lon = d.get("longitud")?.takeUnless { it.isJsonNull }?.asDouble
            require((lat == null && lon == null) || (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0))
            return TerminalViaje(d.entero("id").also { require(it>0) },d.texto("nombre"),d.texto("ciudad"),d.texto("direccion"),lat,lon)
        }
        val pasajeros = datos.getAsJsonArray("pasajeros").map { e ->
            val p=e.asJsonObject
            PasajeroReserva(p.entero("asiento"),p.texto("nombreCompleto"),p.texto("dni"),p.texto("celular"))
        }.sortedBy { it.asiento }
        require(pasajeros.size <= reserva.asientos.size && pasajeros.map { it.asiento }.distinct().size == pasajeros.size)
        require(pasajeros.all { it.asiento in reserva.asientos && com.app.transporte_chota_bus.domain.validation.ValidadorPerfil.validar(it.nombreCompleto,it.dni,it.celular).isEmpty() })
        require(pasajeros.map { it.dni }.distinct().size == pasajeros.size)
        val completos = datos.booleano("datos_completos")
        require(completos == (pasajeros.map { it.asiento }.sorted() == reserva.asientos.sorted()))
        val continuar = datos.booleano("puede_continuar")
        require(!continuar || (completos && reserva.estado == EstadoReserva.PENDIENTE))
        return ResumenCompra(reserva,salida,llegada,terminal(viaje.getAsJsonObject("embarque")),terminal(viaje.getAsJsonObject("llegada")),pasajeros,completos,continuar)
    }
    // Valida la identidad, el modo de prueba y las copias historicas antes de mostrarlas.
    private fun leerBoleto(d:JsonObject):BoletoPrueba {
        fun texto(o:JsonObject,c:String)=o.getAsJsonPrimitive(c).also { require(it.isString) }.asString.also { require(it.isNotBlank()) }
        fun uuid(c:String)=texto(d,c).also { UUID.fromString(it) }
        fun terminal(o:JsonObject):TerminalViaje {
            val lat=o.get("latitud")?.takeUnless { it.isJsonNull }?.asDouble
            val lon=o.get("longitud")?.takeUnless { it.isJsonNull }?.asDouble
            require((lat==null&&lon==null)||(lat!=null&&lon!=null&&lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0))
            return TerminalViaje(o.entero("id").also { require(it>0) },texto(o,"nombre"),texto(o,"ciudad"),texto(o,"direccion"),lat,lon)
        }
        require(texto(d,"modo")=="PRUEBA" && texto(d,"moneda")=="PEN" && texto(d,"estado")=="EMITIDO")
        val codigo=texto(d,"codigo").also { require(Regex("CBP-[A-F0-9]{32}").matches(it)) }
        val importe=texto(d,"importe_unitario").also { require(Regex("[0-9]+\\.[0-9]{2}").matches(it)&&BigDecimal(it)>BigDecimal.ZERO) }
        val fecha=texto(d,"emitido_en").also { FechaHoraServidor.instante(it) }
        val p=d.getAsJsonObject("pasajero");val viaje=d.getAsJsonObject("viaje")
        val nombre=texto(p,"nombreCompleto").also { require(it.trim().length in 1..200) }
        val dni=texto(p,"dni").also { require(Regex("[0-9]{8}").matches(it)) }
        val salida=texto(viaje,"fecha_hora_salida");val llegada=texto(viaje,"fecha_hora_llegada")
        require(FechaHoraServidor.instante(llegada).isAfter(FechaHoraServidor.instante(salida)))
        return BoletoPrueba(uuid("id"),codigo,uuid("reserva_id"),d.entero("asiento").also { require(it in 1..200) },importe,
            fecha,d.booleano("datos_recuperados"),nombre,dni,viaje.entero("salida_id").also { require(it>0) },
            salida,llegada,terminal(viaje.getAsJsonObject("embarque")),terminal(viaje.getAsJsonObject("llegada")))
    }
    private fun JsonObject.texto(clave: String) = get(clave).asString.also { require(it.isNotBlank()) }
    private fun JsonObject.entero(clave: String) = get(clave).asBigDecimal.intValueExact()
    private fun JsonObject.booleano(clave: String): Boolean {
        val valor = getAsJsonPrimitive(clave)
        require(valor.isBoolean)
        return valor.asBoolean
    }
}
