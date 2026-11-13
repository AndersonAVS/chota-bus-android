package com.app.transporte_chota_bus.ui.compra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoBoletosCompra(val cargando:Boolean=false,val boletos:List<BoletoPrueba> = emptyList(),
    val error:String?=null,val sesionRequerida:Boolean=false)

class BoletosCompraViewModel(private val repository:ReservasRepository,private val reservaId:String):ViewModel(){
    private val propietario=repository.usuarioActual()
    private val _estado=MutableStateFlow(EstadoBoletosCompra())
    val estado=_estado.asStateFlow()
    init{cargar()}

    // Los boletos personales se mantienen en memoria y siempre se recuperan con sesion verificada.
    fun cargar(){
        if(_estado.value.cargando)return
        if(!verificarSesion())return
        _estado.value=EstadoBoletosCompra(cargando=true)
        viewModelScope.launch{
            repository.consultarBoletos(reservaId).fold(onSuccess={lista->
                if(verificarSesion()){
                    if(lista.any{it.reservaId!=reservaId})_estado.value=EstadoBoletosCompra(error="Los boletos no corresponden a esta compra.")
                    else _estado.value=EstadoBoletosCompra(boletos=lista)
                }
            },onFailure={e->
                if(verificarSesion())_estado.value=EstadoBoletosCompra(error=e.message?:"No se pudieron cargar los boletos.",
                    sesionRequerida=(e as? ErrorReserva)?.tipo==TipoErrorReserva.SESION)
            })
        }
    }
    fun verificarSesion():Boolean{
        if(propietario!=null&&repository.usuarioActual()==propietario)return true
        _estado.value=EstadoBoletosCompra(error="Inicia sesion nuevamente para consultar tus boletos.",sesionRequerida=true)
        return false
    }
}
class BoletosCompraViewModelFactory(private val repository:ReservasRepository,private val reservaId:String):ViewModelProvider.Factory{
    @Suppress("UNCHECKED_CAST")
    override fun<T:ViewModel>create(modelClass:Class<T>):T{
        require(modelClass.isAssignableFrom(BoletosCompraViewModel::class.java))
        return BoletosCompraViewModel(repository,reservaId) as T
    }
}
