package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.local.CacheViajes
import com.app.transporte_chota_bus.data.model.ResultadoViajes
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import kotlinx.coroutines.CancellationException

class ViajesConCacheRepository(private val remoto:ViajesRepository,private val cache:CacheViajes):ViajesRepository{
    // La cache solo permite leer: nunca conserva disponibilidad ni autoriza reservar o comprar.
    override suspend fun buscar(filtros:FiltrosViaje):Result<ResultadoViajes>{
        filtros.error()?.let{return Result.failure(IllegalArgumentException(it))}
        val respuesta=remoto.buscar(filtros)
        if(respuesta.isSuccess){
            try{cache.guardar(filtros,respuesta.getOrThrow())}catch(e:CancellationException){throw e}catch(_:Exception){ /* La cache no bloquea una respuesta valida. */ }
            return respuesta
        }
        val guardada=try{cache.leer(filtros)}catch(e:CancellationException){throw e}catch(_:Exception){null}
        return guardada?.let{g->Result.success(g.resultado.copy(desdeCache=true,guardadoEn=g.guardadoEn,
            salidas=g.resultado.salidas.map{it.copy(asientosLibres=null)}))}?:respuesta
    }
}
