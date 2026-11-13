package com.app.transporte_chota_bus.ui.compra

import androidx.lifecycle.*
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoDetalleBoleto(val cargando:Boolean=false,val boleto:BoletoPrueba?=null,val error:String?=null,val sesionRequerida:Boolean=false)
class DetalleBoletoViewModel(private val repo:ReservasRepository,private val id:String):ViewModel(){
    private val uid=repo.usuarioActual()
    private val _estado=MutableStateFlow(EstadoDetalleBoleto())
    val estado=_estado.asStateFlow()
    init{cargar()}
    // Reconsulta el boleto por identificador; no recibe datos personales en el Bundle.
    fun cargar(){
        if(_estado.value.cargando||!verificarSesion())return
        _estado.value=EstadoDetalleBoleto(cargando=true)
        viewModelScope.launch{
            repo.consultarBoleto(id).fold(onSuccess={b->if(verificarSesion()){
                _estado.value=if(b.id==id)EstadoDetalleBoleto(boleto=b) else EstadoDetalleBoleto(error="El boleto no corresponde a la consulta.")
            }},onFailure={e->if(verificarSesion())_estado.value=EstadoDetalleBoleto(error=e.message?:"No se pudo cargar el boleto.",
                sesionRequerida=(e as? ErrorReserva)?.tipo==TipoErrorReserva.SESION)})
        }
    }
    fun verificarSesion():Boolean{
        if(uid!=null&&repo.usuarioActual()==uid)return true
        _estado.value=EstadoDetalleBoleto(error="Inicia sesión para consultar el boleto.",sesionRequerida=true);return false
    }
}
class DetalleBoletoViewModelFactory(private val repo:ReservasRepository,private val id:String):ViewModelProvider.Factory{
    @Suppress("UNCHECKED_CAST")
    override fun<T:ViewModel>create(modelClass:Class<T>):T{require(modelClass.isAssignableFrom(DetalleBoletoViewModel::class.java));return DetalleBoletoViewModel(repo,id) as T}
}
