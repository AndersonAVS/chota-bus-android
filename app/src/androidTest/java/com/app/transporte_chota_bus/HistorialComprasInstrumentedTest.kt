package com.app.transporte_chota_bus

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.ui.compra.HistorialComprasFragment
import com.app.transporte_chota_bus.ui.compra.BoletosCompraFragment
import com.app.transporte_chota_bus.ui.inicio.InicioFragment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistorialComprasInstrumentedTest{
    // Consulta historial existente: no modifica reservas ni crea cargos o boletos.
    @Test fun inicioHistorialBoletosYRegresoSinCargos()=runBlocking<Unit>{
        assumeTrue(InstrumentationRegistry.getArguments().getString("historialServidor")=="true"&&BuildConfig.DEBUG)
        checkNotNull(FirebaseAuth.getInstance().currentUser){"Inicia sesion antes de la prueba."}
        val repo=ContenedorDependencias.reservas
        val saldo=repo.consultarSaldoPrueba().getOrThrow().saldo
        val paginas=mutableListOf(repo.consultarHistorial().getOrThrow())
        while(paginas.flatMap{it.registros}.none{it.pagada}){
            val siguiente=paginas.last().siguiente?:error("Se requiere una compra ya pagada para esta prueba de lectura.")
            check(paginas.size<20){"Usa una cuenta de prueba con menos de 400 registros."}
            paginas.add(repo.consultarHistorial(siguiente).getOrThrow())
        }
        val registros=paginas.flatMap{it.registros};val compra=registros.first{it.pagada};val posicion=registros.indexOf(compra)
        val boletos=repo.consultarBoletos(compra.id).getOrThrow()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            esperar{var listo=false;scenario.onActivity{listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is InicioFragment};listo}
            scenario.onActivity{it.findViewById<View>(R.id.btn_historial).performClick()}
            var cantidad=paginas.first().registros.size
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<RecyclerView>(R.id.lista_historial)?.adapter?.itemCount==cantidad};listo}
            for(pagina in paginas.drop(1)){
                scenario.onActivity{it.findViewById<View>(R.id.btn_mas_historial).performClick()};cantidad+=pagina.registros.size
                esperar{var listo=false;scenario.onActivity{listo=it.findViewById<RecyclerView>(R.id.lista_historial)?.adapter?.itemCount==cantidad};listo}
            }
            scenario.onActivity{it.findViewById<RecyclerView>(R.id.lista_historial).scrollToPosition(posicion)}
            esperar{var listo=false;scenario.onActivity{listo=it.window.decorView.findViewWithTag<View>("boletos_historial_${compra.id}")?.visibility==View.VISIBLE};listo}
            scenario.onActivity{
                assertTrue(it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is HistorialComprasFragment)
                val fila=it.window.decorView.findViewWithTag<View>("historial_${compra.id}")
                assertTrue(fila.findViewById<TextView>(R.id.historial_importe).text==it.getString(R.string.historial_pagado,compra.importePagado))
            }
            delay(300)
            val contexto=InstrumentationRegistry.getInstrumentation().targetContext
            val archivo=java.io.File(contexto.getExternalFilesDir(null),"evidencia_historial_compras.png")
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->
                archivo.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            scenario.onActivity{it.window.decorView.findViewWithTag<View>("boletos_historial_${compra.id}").performClick()}
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<LinearLayout>(R.id.lista_boletos_compra)?.childCount==boletos.size};listo}
            scenario.recreate()
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<LinearLayout>(R.id.lista_boletos_compra)?.childCount==boletos.size};listo}
            scenario.onActivity{it.findViewById<View>(R.id.btn_volver_boletos_compra).performClick()}
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<RecyclerView>(R.id.lista_historial)?.adapter?.itemCount==registros.size};listo}
            scenario.onActivity{it.findViewById<View>(R.id.btn_volver_historial).performClick()}
            esperar{var listo=false;scenario.onActivity{listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is InicioFragment};listo}
        }
        assertTrue(saldo==repo.consultarSaldoPrueba().getOrThrow().saldo)
        assertTrue(repo.consultarBoletos(compra.id).getOrThrow().map{it.id}==boletos.map{it.id})
    }
    private suspend fun esperar(condicion:()->Boolean){repeat(120){if(condicion())return;delay(250)};error("La pantalla no alcanzo el estado esperado.")}
}
