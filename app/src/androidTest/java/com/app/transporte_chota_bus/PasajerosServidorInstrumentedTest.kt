package com.app.transporte_chota_bus

import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import com.app.transporte_chota_bus.ui.compra.PasajerosFragment
import com.app.transporte_chota_bus.ui.inicio.InicioFragment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PasajerosServidorInstrumentedTest{
    @Test fun fechasConOffsetYMicrosegundosSonCompatibles(){
        val peru=com.app.transporte_chota_bus.domain.model.FechaHoraServidor.instante("2026-11-13T13:51:36.376233-05:00")
        val utc=com.app.transporte_chota_bus.domain.model.FechaHoraServidor.instante("2026-11-13T18:51:36.376233Z")
        assertEquals(utc,peru)
    }

    // Solo crea datos FICTICIOS en una salida DEMO y cancela su reserva al terminar.
    @Test fun formularioYResumenConTokenReal()=runBlocking<Unit>{
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("pasajerosServidor")=="true")
        assumeTrue(BuildConfig.DEBUG)
        assumeNotNull(FirebaseAuth.getInstance().currentUser)
        val salidaId=requireNotNull(args.getString("salidaDemoId")).toInt()
        val fecha=requireNotNull(args.getString("fechaDemo"))
        val salida=ContenedorDependencias.viajes.buscar(FiltrosViaje("Chota","Cajamarca",fecha,2)).getOrThrow().salidas.single{it.id==salidaId}
        check(salida.embarque.nombre.startsWith("DEMO - ")&&salida.destino.nombre.startsWith("DEMO - "))
        val repo=ContenedorDependencias.reservas
        val asientos=repo.consultarAsientos(salidaId).getOrThrow().asientos.filter{it.disponible}.take(2).map{it.numero}
        check(asientos.size==2)
        val reserva=repo.reservar(salidaId,asientos,UUID.randomUUID().toString()).getOrThrow()
        try{
            val pasajeros=asientos.mapIndexed{i,n->PasajeroReserva(n,"PASAJERO DE PRUEBA ${i+1}","0000000${i+1}","900000001")}
            val resumen=repo.guardarPasajeros(reserva.id,pasajeros).getOrThrow()
            assertTrue(resumen.datosCompletos);assertTrue(resumen.puedeContinuar)
            assertEquals(reserva.total,resumen.reserva.total)
            assertEquals(com.app.transporte_chota_bus.domain.model.FechaHoraServidor.instante(reserva.venceEn).toEpochMilli(),
                com.app.transporte_chota_bus.domain.model.FechaHoraServidor.instante(resumen.reserva.venceEn).toEpochMilli())
            val verificarSaldo=args.getString("saldoServidor")=="true"
            val saldoInicial=if(verificarSaldo)repo.consultarSaldoPrueba().getOrThrow().saldo else null
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                esperar{var listo=false;scenario.onActivity{listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is InicioFragment};listo}
                scenario.onActivity{it.abrirPantalla(PasajerosFragment.crear(reserva.id))}
                esperar{var listo=false;scenario.onActivity{listo=it.findViewById<TextView>(R.id.titulo_pasajeros)?.text?.toString()==it.getString(R.string.pasajeros_resumen)};listo}
                scenario.onActivity{
                    assertEquals(it.getString(R.string.compra_importe,reserva.total),it.findViewById<TextView>(R.id.resumen_total).text.toString())
                    it.findViewById<android.view.View>(R.id.btn_editar_pasajeros).performClick()
                }
                esperar{var listo=false;scenario.onActivity{listo=it.findViewById<TextView>(R.id.titulo_pasajeros).text.toString()==it.getString(R.string.pasajeros_titulo)};listo}
                val tag="NOMBRE_${asientos.first()}"
                scenario.onActivity{it.window.decorView.findViewWithTag<EditText>(tag).setText("PASAJERO DE PRUEBA EDITADO")}
                scenario.recreate()
                esperar{
                    var listo=false
                    scenario.onActivity{
                        listo=it.window.decorView.findViewWithTag<EditText>(tag)?.text?.toString()=="PASAJERO DE PRUEBA EDITADO"&&
                            it.findViewById<android.view.View>(R.id.btn_guardar_pasajeros)?.isEnabled==true
                    };listo
                }
                scenario.onActivity{it.findViewById<android.view.View>(R.id.btn_guardar_pasajeros).performClick()}
                esperar{
                    var listo=false
                    scenario.onActivity{
                        listo=it.findViewById<TextView>(R.id.titulo_pasajeros).text.toString()==it.getString(R.string.pasajeros_resumen)&&
                            it.findViewById<TextView>(R.id.resumen_pasajeros).text.contains("PASAJERO DE PRUEBA EDITADO")
                    };listo
                }
                if(verificarSaldo){
                    // Comprueba el simulador sin gastar el saldo de la cuenta personal.
                    esperar{var listo=false;scenario.onActivity{listo=it.findViewById<android.view.View>(R.id.btn_pagar_prueba)?.isEnabled==true};listo}
                    scenario.onActivity{
                        assertTrue(it.findViewById<TextView>(R.id.saldo_prueba).text.contains("MODO DE PRUEBA"))
                        assertTrue(it.findViewById<TextView>(R.id.saldo_prueba).text.contains(saldoInicial!!))
                        val scroll=it.findViewById<android.widget.ScrollView>(R.id.scroll_pasajeros)
                        scroll.fullScroll(android.view.View.FOCUS_DOWN)
                    }
                    assertEquals(saldoInicial,repo.consultarSaldoPrueba().getOrThrow().saldo)
                }
                delay(400)
                val contexto=InstrumentationRegistry.getInstrumentation().targetContext
                val archivo=java.io.File(contexto.getExternalFilesDir(null),if(verificarSaldo)"evidencia_saldo_prueba.png" else "evidencia_resumen_compra.png")
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->
                    archivo.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                }
            }
            assertEquals("PASAJERO DE PRUEBA EDITADO",repo.consultarResumen(reserva.id).getOrThrow().pasajeros.first().nombreCompleto)
        }finally{
            val primer=repo.cancelarReserva(reserva.id)
            val cancelada=(if(primer.isSuccess)primer else repo.cancelarReserva(reserva.id)).getOrThrow()
            assertEquals(EstadoReserva.CANCELADA,cancelada.estado)
        }
    }
    private var paso=0
    private suspend fun esperar(condicion:()->Boolean){
        paso++;println("Comprobacion visual de saldo: paso $paso")
        repeat(120){if(condicion())return;delay(250)}
        error("La pantalla no alcanzo el estado esperado en el paso $paso.")
    }
}
