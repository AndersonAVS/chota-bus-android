package com.app.transporte_chota_bus
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.transporte_chota_bus.data.local.CacheViajesSqlite
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.*
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import com.app.transporte_chota_bus.ui.viajes.SalidasAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CacheViajesSqliteInstrumentedTest{
    // La base temporal se elimina al terminar y nunca contiene datos personales o compras.
    @Test fun sqliteCreaLeeActualizaBorraYCaduca()=runBlocking<Unit>{
        val c=InstrumentationRegistry.getInstrumentation().targetContext;val nombre="prueba_cache_${UUID.randomUUID()}.db";var ahora=1000L
        val f=FiltrosViaje("Chota","Cajamarca",LocalDate.now().plusDays(1).toString(),1)
        val t=TerminalViaje(1,"DEMO","Chota","Referencia",null,null)
        val r=ResultadoViajes(listOf(SalidaViaje(3,"2026-12-20T13:00:00Z","2026-12-20T16:00:00Z","30.00","PEN",15,t,t,10)),false)
        try{
            CacheViajesSqlite(c,nombre){ahora}.use{cache->
                assertNull(cache.leer(f));cache.guardar(f,r);assertEquals("30.00",cache.leer(f)!!.resultado.salidas.single().precio)
                cache.guardar(f,r.copy(salidas=r.salidas.map{it.copy(precio="40.00")}))
            }
            CacheViajesSqlite(c,nombre){ahora}.use{cache->
                assertEquals("40.00",cache.leer(f)!!.resultado.salidas.single().precio)
                val remoto=object:ViajesRepository{override suspend fun buscar(filtros:FiltrosViaje)=Result.failure<ResultadoViajes>(java.io.IOException("Sin red simulada"))}
                val guardado=ViajesConCacheRepository(remoto,cache).buscar(f).getOrThrow();assertTrue(guardado.desdeCache)
                InstrumentationRegistry.getInstrumentation().runOnMainSync{
                    val adapter=SalidasAdapter({}, {error("No se debe permitir comprar con cache")});adapter.soloConsulta=true
                    val holder=adapter.onCreateViewHolder(FrameLayout(c),0)
                    adapter.submitList(guardado.salidas);adapter.onBindViewHolder(holder,0)
                    assertFalse(holder.itemView.findViewById<Button>(R.id.btn_seleccionar_salida).isEnabled)
                }
                cache.borrar(f);assertNull(cache.leer(f));cache.guardar(f,r);ahora+=86_400_001;assertNull(cache.leer(f))
            }
        }finally{c.deleteDatabase(nombre)}
    }
}
