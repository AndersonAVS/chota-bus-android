package com.app.transporte_chota_bus.ui.compra

import androidx.lifecycle.*
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoMisBoletos(val cargando:Boolean=false,val boletos:List<BoletoPrueba> = emptyList(),
    val siguiente:String?=null,val error:String?=null,val sesionRequerida:Boolean=false)

class MisBoletosViewModel(private val repo:ReservasRepository):ViewModel(){
    private val uid=repo.usuarioActual()
    private val _estado=MutableStateFlow(EstadoMisBoletos())
    val estado=_estado.asStateFlow()
    init{cargar()}
    // Pagina con el cursor del servidor y conserva la lista si falla una pagina posterior.
    fun cargar(mas:Boolean=false){
        if(_estado.value.cargando||!verificarSesion())return
        val previo=_estado.value
        val cursor=if(mas)previo.siguiente?:return else null
        _estado.value=if(mas)previo.copy(cargando=true,error=null) else EstadoMisBoletos(cargando=true)
        viewModelScope.launch{
            repo.consultarMisBoletos(cursor).fold(onSuccess={p->
                if(verificarSesion()){
                    val anteriores=if(mas)previo.boletos else emptyList()
                    if(p.boletos.any{b->anteriores.any{it.id==b.id}}||p.siguiente!=null&&p.siguiente==cursor)
                        _estado.value=previo.copy(cargando=false,error="La lista cambió. Actualiza tus boletos.")
                    else _estado.value=EstadoMisBoletos(boletos=anteriores+p.boletos,siguiente=p.siguiente)
                }
            },onFailure={e->if(verificarSesion()){
                if((e as? ErrorReserva)?.tipo==TipoErrorReserva.SESION)_estado.value=EstadoMisBoletos(error=e.message,sesionRequerida=true)
                else _estado.value=(if(mas)previo else EstadoMisBoletos()).copy(error=e.message?:"No se pudieron cargar tus boletos.")
            }})
        }
    }
    fun verificarSesion():Boolean{
        if(uid!=null&&repo.usuarioActual()==uid)return true
        _estado.value=EstadoMisBoletos(error="Inicia sesión para consultar tus boletos.",sesionRequerida=true);return false
    }
}
class MisBoletosViewModelFactory(private val repo:ReservasRepository):ViewModelProvider.Factory{
    @Suppress("UNCHECKED_CAST")
    override fun<T:ViewModel>create(modelClass:Class<T>):T{require(modelClass.isAssignableFrom(MisBoletosViewModel::class.java));return MisBoletosViewModel(repo) as T}
}
