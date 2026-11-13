package com.app.transporte_chota_bus.ui.compra

import androidx.lifecycle.*
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoHistorialCompras(val cargando:Boolean=false,val registros:List<RegistroHistorial> = emptyList(),
    val siguiente:String?=null,val error:String?=null,val sesionRequerida:Boolean=false)

class HistorialComprasViewModel(private val repo:ReservasRepository):ViewModel(){
    private val uid=repo.usuarioActual()
    private val _estado=MutableStateFlow(EstadoHistorialCompras())
    val estado=_estado.asStateFlow()
    init{cargar()}
    // Pagina con el cursor del servidor y conserva la lista si falla una pagina posterior.
    fun cargar(mas:Boolean=false){
        if(_estado.value.cargando||!verificarSesion())return
        val previo=_estado.value
        val cursor=if(mas)previo.siguiente?:return else null
        _estado.value=if(mas)previo.copy(cargando=true,error=null) else EstadoHistorialCompras(cargando=true)
        viewModelScope.launch{
            repo.consultarHistorial(cursor).fold(onSuccess={p->
                if(verificarSesion()){
                    val anteriores=if(mas)previo.registros else emptyList()
                    if(p.registros.any{b->anteriores.any{it.id==b.id}}||p.siguiente!=null&&p.siguiente==cursor)
                        _estado.value=previo.copy(cargando=false,error="La lista cambió. Actualiza tu historial.")
                    else _estado.value=EstadoHistorialCompras(registros=anteriores+p.registros,siguiente=p.siguiente)
                }
            },onFailure={e->if(verificarSesion()){
                if((e as? ErrorReserva)?.tipo==TipoErrorReserva.SESION)_estado.value=EstadoHistorialCompras(error=e.message,sesionRequerida=true)
                else _estado.value=(if(mas)previo else EstadoHistorialCompras()).copy(error=e.message?:"No se pudo cargar tu historial.")
            }})
        }
    }
    fun verificarSesion():Boolean{
        if(uid!=null&&repo.usuarioActual()==uid)return true
        _estado.value=EstadoHistorialCompras(error="Inicia sesión para consultar tu historial.",sesionRequerida=true);return false
    }
}
class HistorialComprasViewModelFactory(private val repo:ReservasRepository):ViewModelProvider.Factory{
    @Suppress("UNCHECKED_CAST")
    override fun<T:ViewModel>create(modelClass:Class<T>):T{require(modelClass.isAssignableFrom(HistorialComprasViewModel::class.java));return HistorialComprasViewModel(repo) as T}
}
