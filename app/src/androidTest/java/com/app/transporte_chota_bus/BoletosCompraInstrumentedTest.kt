package com.app.transporte_chota_bus

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.transporte_chota_bus.data.model.EstadoReserva
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.ui.compra.BoletosCompraFragment
import com.app.transporte_chota_bus.ui.compra.PasajerosFragment
import com.app.transporte_chota_bus.ui.inicio.InicioFragment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoletosCompraInstrumentedTest{
    // Solo lee una compra propia ya pagada: no crea, modifica, cancela ni vuelve a cobrar.
    @Test fun confirmacionYBoletosConservanLaCompra()=runBlocking<Unit>{
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("boletosServidor")=="true"&&BuildConfig.DEBUG)
        checkNotNull(FirebaseAuth.getInstance().currentUser){"Se requiere una sesion iniciada."}
        val id=requireNotNull(args.getString("reservaConfirmadaId"))
        val repo=ContenedorDependencias.reservas
        val resumen=repo.consultarResumen(id).getOrThrow()
        check(resumen.reserva.estado==EstadoReserva.CONFIRMADA)
        val saldoAntes=repo.consultarSaldoPrueba().getOrThrow().saldo
        val boletos=repo.consultarBoletos(id).getOrThrow()
        assertTrue(boletos.isNotEmpty());assertTrue(boletos.map{it.asiento}.sorted()==resumen.reserva.asientos.sorted())
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            esperar{var listo=false;scenario.onActivity{listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is InicioFragment};listo}
            scenario.onActivity{it.abrirPantalla(PasajerosFragment.crear(id))}
            esperar{var listo=false;scenario.onActivity{
                listo=it.findViewById<TextView>(R.id.titulo_pasajeros)?.text==it.getString(R.string.confirmacion_titulo)&&
                    it.findViewById<View>(R.id.btn_ver_boletos_compra)?.isEnabled==true
            };listo}
            scenario.onActivity{
                assertTrue(it.findViewById<View>(R.id.panel_confirmacion_compra).visibility==View.VISIBLE)
                assertTrue(it.findViewById<View>(R.id.panel_resumen_compra).visibility==View.GONE)
                assertTrue(it.findViewById<View>(R.id.panel_formulario_pasajeros).visibility==View.GONE)
                assertTrue(it.findViewById<TextView>(R.id.confirmacion_datos).text.contains(resumen.reserva.total))
            }
            // La evidencia de confirmacion no contiene nombre ni DNI del pasajero.
            delay(400)
            val contexto=InstrumentationRegistry.getInstrumentation().targetContext
            val archivo=java.io.File(contexto.getExternalFilesDir(null),"evidencia_confirmacion_compra.png")
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->
                archivo.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            scenario.onActivity{it.findViewById<View>(R.id.btn_ver_boletos_compra).performClick()}
            esperar{var listo=false;scenario.onActivity{
                listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is BoletosCompraFragment&&
                    it.findViewById<LinearLayout>(R.id.lista_boletos_compra)?.childCount==boletos.size
            };listo}
            scenario.onActivity{activity->
                for(b in boletos){
                    val fila=activity.window.decorView.findViewWithTag<View>("boleto_${b.id}")
                    assertNotNull(fila)
                    assertTrue(fila.findViewById<TextView>(R.id.boleto_codigo).text.contains(b.codigo))
                    assertTrue(fila.findViewById<TextView>(R.id.boleto_persona).text.contains(b.dni))
                }
            }
            scenario.recreate()
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<LinearLayout>(R.id.lista_boletos_compra)?.childCount==boletos.size};listo}
            scenario.onActivity{it.findViewById<View>(R.id.btn_volver_boletos_compra).performClick()}
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<View>(R.id.btn_ver_boletos_compra)?.isEnabled==true};listo}
            scenario.onActivity{it.findViewById<View>(R.id.btn_inicio_confirmacion).performClick()}
            esperar{var listo=false;scenario.onActivity{listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is InicioFragment};listo}
        }
        assertTrue(saldoAntes==repo.consultarSaldoPrueba().getOrThrow().saldo)
        assertTrue(repo.consultarBoletos(id).getOrThrow().map{it.id}==boletos.map{it.id})
    }
    private suspend fun esperar(condicion:()->Boolean){
        repeat(120){if(condicion())return;delay(250)}
        error("La pantalla no alcanzo el estado esperado.")
    }
}
