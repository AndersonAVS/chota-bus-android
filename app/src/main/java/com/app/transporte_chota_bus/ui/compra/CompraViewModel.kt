package com.app.transporte_chota_bus.ui.compra

import android.os.SystemClock
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class EstadoCompra(
    val cargando: Boolean = false, val procesando: Boolean = false,
    val inventario: InventarioAsientos? = null, val seleccion: Set<Int> = emptySet(),
    val reserva: ReservaAsientos? = null, val segundos: Long = 0,
    val pendienteConfirmacion: Boolean = false, val reservaDesconocida: Boolean = false,
    val error: String? = null, val sesionRequerida: Boolean = false, val salir: Boolean = false
) {
    val ocupada get() = cargando || procesando
    val bloqueada get() = ocupada || pendienteConfirmacion || reservaDesconocida || sesionRequerida ||
        reserva?.estado in setOf(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA)
}

class CompraViewModel(
    private val repository: ReservasRepository, private val guardado: SavedStateHandle,
    val salidaId: Int, val pasajeros: Int, private val reloj: () -> Long = { SystemClock.elapsedRealtime() }
) : ViewModel() {
    private val propietario = repository.usuarioActual()
    private var limiteMonotonico = 0L
    private val _estado = MutableStateFlow(EstadoCompra())
    val estado = _estado.asStateFlow()

    // Recupera solo ids y seleccion; el token nunca forma parte del estado guardado.
    init {
        if (guardado.get<String>("propietario_uid") != propietario) limpiarGuardado()
        guardado["propietario_uid"] = propietario
        _estado.value = EstadoCompra(
            seleccion = guardado.get<IntArray>("seleccion")?.toSet().orEmpty(),
            pendienteConfirmacion = guardado.get<String>("solicitud_id") != null,
            reservaDesconocida = guardado.get<String>("reserva_id") != null
        )
        if (salidaId <= 0 || pasajeros !in 1..9) _estado.value = EstadoCompra(error = "Los datos de la salida no son válidos.")
        else actualizar()
    }

    fun actualizar() {
        if (_estado.value.ocupada || !comprobarSesion()) return
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val id = guardado.get<String>("reserva_id")
            if (id != null) repository.consultarReserva(id).fold(
                onSuccess = { aplicarReserva(it) },
                onFailure = {
                    if ((it as? ErrorReserva)?.tipo == TipoErrorReserva.NO_ENCONTRADO) {
                        guardado.remove<String>("reserva_id")
                        _estado.update { estado -> estado.copy(reservaDesconocida = false, reserva = null, seleccion = emptySet()) }
                    }
                    mostrarError(it)
                }
            )
            if (!_estado.value.sesionRequerida) cargarInventario()
            _estado.update { it.copy(cargando = false) }
        }
    }

    // La seleccion debe coincidir con la cantidad buscada y con asientos disponibles.
    fun seleccionar(numero: Int) {
        val actual = _estado.value
        if (actual.bloqueada || !comprobarSesion()) return
        if (actual.inventario?.asientos?.none { it.numero == numero && it.disponible } != false) return
        val elegidos = actual.seleccion.toMutableSet()
        if (!elegidos.remove(numero)) {
            if (elegidos.size >= pasajeros) {
                _estado.update { it.copy(error = "Selecciona solamente $pasajeros asiento(s).") }
                return
            }
            elegidos.add(numero)
        }
        guardado["seleccion"] = elegidos.toIntArray()
        _estado.update { it.copy(seleccion = elegidos, error = null) }
    }

    // Mantiene el mismo UUID ante respuestas inciertas o renovacion del token.
    fun reservar() {
        val actual = _estado.value
        if (actual.ocupada || actual.reservaDesconocida || !comprobarSesion() ||
            actual.reserva?.estado in setOf(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA)) return
        val asientos = guardado.get<IntArray>("asientos_solicitud")?.toList() ?: actual.seleccion.sorted()
        if (asientos.size != pasajeros) {
            _estado.update { it.copy(error = "Selecciona $pasajeros asiento(s) para continuar.") }; return
        }
        val solicitud = guardado.get<String>("solicitud_id") ?: UUID.randomUUID().toString()
        guardado["solicitud_id"] = solicitud
        guardado["asientos_solicitud"] = asientos.toIntArray()
        _estado.update { it.copy(procesando = true, pendienteConfirmacion = true, error = null, reserva = null) }
        viewModelScope.launch {
            repository.reservar(salidaId, asientos, solicitud).fold(
                onSuccess = { aplicarReserva(it); if (!_estado.value.sesionRequerida) cargarInventario() },
                onFailure = { error ->
                    val tipo = (error as? ErrorReserva)?.tipo
                    if (tipo in setOf(TipoErrorReserva.CONFLICTO, TipoErrorReserva.RECHAZADA, TipoErrorReserva.NO_ENCONTRADO)) {
                        limpiarSolicitud()
                        _estado.update { it.copy(pendienteConfirmacion = false, seleccion = emptySet()) }
                        if (tipo != TipoErrorReserva.NO_ENCONTRADO) cargarInventario()
                    }
                    mostrarError(error)
                }
            )
            _estado.update { it.copy(procesando = false) }
        }
    }

    fun cancelar(salirAlTerminar: Boolean = false) {
        val actual = _estado.value
        if (actual.ocupada || !comprobarSesion()) return
        val reserva = actual.reserva ?: return
        if (reserva.estado != EstadoReserva.PENDIENTE) return
        _estado.update { it.copy(procesando = true, error = null) }
        viewModelScope.launch {
            repository.cancelarReserva(reserva.id).fold(
                onSuccess = {
                    aplicarReserva(it)
                    if (it.estado in setOf(EstadoReserva.CANCELADA, EstadoReserva.EXPIRADA)) {
                        if (salirAlTerminar) _estado.update { estado -> estado.copy(salir = true) }
                        else cargarInventario()
                    }
                }, onFailure = { mostrarError(it) }
            )
            _estado.update { it.copy(procesando = false) }
        }
    }

    // El contador usa hora del servidor y reloj monotono, no la hora editable del telefono.
    fun actualizarTiempo() {
        if (_estado.value.reserva?.estado != EstadoReserva.PENDIENTE || _estado.value.procesando) return
        val restante = (limiteMonotonico - reloj()).coerceAtLeast(0)
        _estado.update { it.copy(segundos = (restante + 999) / 1000) }
        if (restante == 0L) {
            guardado.remove<String>("reserva_id")
            guardado["seleccion"] = intArrayOf()
            _estado.update { it.copy(reserva = it.reserva?.copy(estado = EstadoReserva.EXPIRADA), seleccion = emptySet()) }
            actualizar()
        }
    }
    fun consumirSalida() { _estado.update { it.copy(salir = false) } }

    private suspend fun cargarInventario() {
        repository.consultarAsientos(salidaId).fold(onSuccess = { inventario ->
            if (!comprobarSesion()) return@fold
            val actual = _estado.value
            val conservar = actual.pendienteConfirmacion || actual.reserva?.estado in setOf(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA)
            val seleccion = if (conservar) actual.seleccion else actual.seleccion.filter { n -> inventario.asientos.any { it.numero == n && it.disponible } }.toSet()
            guardado["seleccion"] = seleccion.toIntArray()
            _estado.update { it.copy(inventario = inventario, seleccion = seleccion) }
        }, onFailure = { mostrarError(it) })
    }

    private fun aplicarReserva(reserva: ReservaAsientos) {
        if (!comprobarSesion()) return
        if (reserva.salidaId != salidaId || reserva.asientos.size != pasajeros) {
            _estado.update { it.copy(error = "La reserva no corresponde a esta salida.", reservaDesconocida = true) }; return
        }
        limpiarSolicitud()
        val activa = reserva.estado in setOf(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA)
        if (activa) guardado["reserva_id"] = reserva.id else guardado.remove<String>("reserva_id")
        val seleccion = if (activa) reserva.asientos.toSet() else emptySet()
        guardado["seleccion"] = seleccion.toIntArray()
        val duracion = Duration.between(com.app.transporte_chota_bus.domain.model.FechaHoraServidor.instante(reserva.horaServidor), com.app.transporte_chota_bus.domain.model.FechaHoraServidor.instante(reserva.venceEn)).toMillis().coerceAtLeast(0)
        limiteMonotonico = reloj() + duracion
        _estado.update { it.copy(reserva = reserva, seleccion = seleccion, segundos = (duracion + 999)/1000,
            pendienteConfirmacion = false, reservaDesconocida = false, error = null) }
    }
    private fun mostrarError(error: Throwable) {
        if ((error as? ErrorReserva)?.tipo == TipoErrorReserva.SESION) {
            limpiarGuardado()
            _estado.value = EstadoCompra(error = error.message, sesionRequerida = true)
        } else _estado.update { it.copy(error = error.message ?: "No se pudo completar la operación.") }
    }
    private fun comprobarSesion(): Boolean {
        if (propietario != null && repository.usuarioActual() == propietario) return true
        mostrarError(ErrorReserva(TipoErrorReserva.SESION, "Inicia sesión nuevamente para continuar."))
        return false
    }
    private fun limpiarSolicitud() {
        guardado.remove<String>("solicitud_id")
        guardado.remove<IntArray>("asientos_solicitud")
    }
    private fun limpiarGuardado() {
        limpiarSolicitud()
        guardado.remove<String>("reserva_id")
        guardado.remove<IntArray>("seleccion")
    }
}

class CompraViewModelFactory(private val repository: ReservasRepository, private val salidaId: Int, private val pasajeros: Int) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(CompraViewModel::class.java))
        return CompraViewModel(repository, extras.createSavedStateHandle(), salidaId, pasajeros) as T
    }
}
