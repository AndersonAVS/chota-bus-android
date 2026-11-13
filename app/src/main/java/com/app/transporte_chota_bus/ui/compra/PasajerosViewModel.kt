package com.app.transporte_chota_bus.ui.compra

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import com.app.transporte_chota_bus.domain.validation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import com.app.transporte_chota_bus.domain.model.FechaHoraServidor

data class EstadoPasajeros(
    val cargando:Boolean=false,val guardando:Boolean=false,val resumen:ResumenCompra?=null,
    val borradores:List<PasajeroReserva> = emptyList(),val errores:Map<Int,Map<CampoPerfil,String>> = emptyMap(),
    val error:String?=null,val segundos:Long=0,val modificado:Boolean=false,
    val mostrandoResumen:Boolean=false,val sesionRequerida:Boolean=false,
    val saldoPrueba:String?=null,val errorSaldo:String?=null,val pagando:Boolean=false,val pagoId:String?=null
) {
    val ocupada get()=cargando||guardando||pagando
    val vigente get()=resumen?.reserva?.estado==EstadoReserva.PENDIENTE&&segundos>0&&!sesionRequerida
}

class PasajerosViewModel(private val repository:ReservasRepository,val reservaId:String,
    private val reloj:()->Long={SystemClock.elapsedRealtime()}) : ViewModel() {
    private val propietario=repository.usuarioActual()
    private var limite=0L
    private val _estado=MutableStateFlow(EstadoPasajeros())
    val estado=_estado.asStateFlow()
    // Conserva borradores solo en memoria al girar; tras recrear el proceso lee lo guardado en SQL.
    init{cargar()}

    fun cargar(){
        if(_estado.value.ocupada||!comprobarSesion())return
        _estado.update{it.copy(cargando=true,error=null)}
        viewModelScope.launch{
            repository.consultarResumen(reservaId).fold(onSuccess={aplicar(it,true)},onFailure={mostrarError(it)})
            if(comprobarSesion())repository.consultarSaldoPrueba().fold(onSuccess={s->
                if(comprobarSesion())_estado.update{it.copy(saldoPrueba=s.saldo,errorSaldo=null)}
            },onFailure={e->if((e as? ErrorReserva)?.tipo==TipoErrorReserva.SESION)mostrarError(e)
                else _estado.update{it.copy(saldoPrueba=null,errorSaldo=e.message)}})
            _estado.update{it.copy(cargando=false)}
        }
    }

    fun editar(asiento:Int,campo:CampoPerfil,valor:String){
        val actual=_estado.value
        if(actual.ocupada||!actual.vigente||actual.mostrandoResumen||!comprobarSesion())return
        val previo=actual.borradores.find{it.asiento==asiento}?:return
        val nuevo=when(campo){
            CampoPerfil.NOMBRE->previo.copy(nombreCompleto=valor)
            CampoPerfil.DNI->previo.copy(dni=valor)
            CampoPerfil.CELULAR->previo.copy(celular=valor)
        }
        if(nuevo==previo)return
        _estado.update{it.copy(borradores=it.borradores.map{p->if(p.asiento==asiento)nuevo else p},
            errores=it.errores-asiento,error=null,modificado=true)}
    }

    // Valida todo el formulario y repite el mismo PUT si una respuesta se pierde.
    fun guardar(){
        val actual=_estado.value
        if(actual.ocupada||!comprobarSesion())return
        actualizarTiempo()
        if(!_estado.value.vigente){_estado.update{it.copy(error="La reserva ya no está vigente. Vuelve a seleccionar tus asientos.")};return}
        val resumen=actual.resumen?:return
        val datos=actual.borradores.map{it.copy(nombreCompleto=it.nombreCompleto.trim(),dni=it.dni.trim(),celular=it.celular.trim())}
        val validacion=ValidadorPasajeros.validar(datos,resumen.reserva.asientos)
        if(!validacion.valido){_estado.update{it.copy(errores=validacion.errores,error=validacion.errorGeneral?:"Revisa los datos marcados de cada pasajero.")};return}
        _estado.update{it.copy(guardando=true,error=null,errores=emptyMap())}
        viewModelScope.launch{
            repository.guardarPasajeros(reservaId,datos).fold(onSuccess={
                aplicar(it,false)
                if(!_estado.value.sesionRequerida)_estado.update{e->e.copy(mostrandoResumen=true)}
            },onFailure={error->
                if((error as? ErrorReserva)?.tipo==TipoErrorReserva.CONFLICTO){
                    repository.consultarResumen(reservaId).fold(onSuccess={aplicar(it,true)},onFailure={mostrarError(it)})
                }
                if(!_estado.value.sesionRequerida)mostrarError(error)
            })
            _estado.update{it.copy(guardando=false)}
        }
    }
    // El cargo y la confirmacion son atomicos en SQL; repetir la reserva no vuelve a cobrar.
    fun pagarPrueba(){
        if(_estado.value.ocupada||!comprobarSesion())return
        actualizarTiempo()
        val actual=_estado.value
        if(!actual.vigente||actual.modificado||actual.resumen?.puedeContinuar!=true)return
        _estado.update{it.copy(pagando=true,error=null)}
        viewModelScope.launch{
            repository.pagarConSaldoPrueba(reservaId).fold(onSuccess={p->
                if(comprobarSesion())_estado.update{e->e.copy(pagoId=p.id,saldoPrueba=p.saldo,errorSaldo=null,
                    resumen=e.resumen?.let{it.copy(reserva=it.reserva.copy(estado=EstadoReserva.CONFIRMADA),puedeContinuar=false)})}
            },onFailure={error->
                // Si se pierde la respuesta, consulta el estado antes de permitir otro intento.
                repository.consultarResumen(reservaId).fold(onSuccess={aplicar(it,true)},onFailure={mostrarError(it)})
                if(!_estado.value.sesionRequerida){
                    if(_estado.value.resumen?.reserva?.estado==EstadoReserva.CONFIRMADA){
                        _estado.update{it.copy(error=null,saldoPrueba=null)}
                        repository.consultarSaldoPrueba().fold(onSuccess={s->if(comprobarSesion())_estado.update{it.copy(saldoPrueba=s.saldo,errorSaldo=null)}},
                            onFailure={e->if((e as? ErrorReserva)?.tipo==TipoErrorReserva.SESION)mostrarError(e) else _estado.update{it.copy(errorSaldo=e.message)}})
                    }else mostrarError(error)
                }
            })
            _estado.update{it.copy(pagando=false)}
        }
    }
    fun verResumen(){if(!_estado.value.ocupada&&!_estado.value.modificado&&_estado.value.resumen?.datosCompletos==true)_estado.update{it.copy(mostrandoResumen=true)}}
    fun editarFormulario(){if(!_estado.value.ocupada)_estado.update{it.copy(mostrandoResumen=false)}}

    // El plazo sigue siendo el original de la reserva; guardar pasajeros no lo renueva.
    fun actualizarTiempo(){
        if(!comprobarSesion())return
        if(_estado.value.resumen?.reserva?.estado!=EstadoReserva.PENDIENTE)return
        val restante=(limite-reloj()).coerceAtLeast(0)
        _estado.update{it.copy(segundos=(restante+999)/1000)}
        if(restante==0L)_estado.update{it.copy(resumen=it.resumen?.let{r->r.copy(reserva=r.reserva.copy(estado=EstadoReserva.EXPIRADA),puedeContinuar=false)})}
    }

    private fun aplicar(resumen:ResumenCompra,conservar:Boolean){
        if(!comprobarSesion())return
        if(resumen.reserva.id!=reservaId){mostrarError(ErrorReserva(TipoErrorReserva.DATOS,"El resumen no corresponde a esta reserva."));return}
        val actual=_estado.value
        val mantener=conservar&&actual.modificado&&actual.borradores.map{it.asiento}.sorted()==resumen.reserva.asientos.sorted()
        val formularios=if(mantener)actual.borradores else resumen.reserva.asientos.sorted().map{asiento->
            resumen.pasajeros.find{it.asiento==asiento}?:PasajeroReserva(asiento)
        }
        val milisegundos=Duration.between(FechaHoraServidor.instante(resumen.reserva.horaServidor),FechaHoraServidor.instante(resumen.reserva.venceEn)).toMillis().coerceAtLeast(0)
        limite=reloj()+milisegundos
        _estado.update{it.copy(resumen=resumen,borradores=formularios,segundos=(milisegundos+999)/1000,
            modificado=mantener,errores=emptyMap(),error=null,
            mostrandoResumen=it.mostrandoResumen||(it.resumen==null&&resumen.datosCompletos))}
        actualizarTiempo()
    }
    private fun comprobarSesion():Boolean{
        if(propietario!=null&&repository.usuarioActual()==propietario)return true
        mostrarError(ErrorReserva(TipoErrorReserva.SESION,"Inicia sesión nuevamente para continuar."));return false
    }
    private fun mostrarError(error:Throwable){
        if((error as? ErrorReserva)?.tipo==TipoErrorReserva.SESION)_estado.value=EstadoPasajeros(error=error.message,sesionRequerida=true)
        else _estado.update{it.copy(error=error.message?:"No se pudo completar la operación.")}
    }
}

class PasajerosViewModelFactory(private val repository:ReservasRepository,private val reservaId:String):ViewModelProvider.Factory{
    @Suppress("UNCHECKED_CAST")
    override fun<T:ViewModel>create(modelClass:Class<T>):T{
        require(modelClass.isAssignableFrom(PasajerosViewModel::class.java))
        return PasajerosViewModel(repository,reservaId) as T
    }
}
