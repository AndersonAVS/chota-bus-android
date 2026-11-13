package com.app.transporte_chota_bus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.transporte_chota_bus.data.model.EstadoReserva
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReservasServidorInstrumentedTest {
    // Comprueba la pantalla real sin crear otra reserva ni modificar datos de cuenta.
    @Test fun pantallaCargaDistribucionReal() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("reservasServidor") == "true")
        assumeTrue(BuildConfig.DEBUG)
        assumeNotNull(FirebaseAuth.getInstance().currentUser)
        val id = requireNotNull(args.getString("salidaDemoId")).toInt()
        val fecha = requireNotNull(args.getString("fechaDemo"))
        val salida = ContenedorDependencias.viajes.buscar(FiltrosViaje("Chota", "Cajamarca", fecha, 1)).getOrThrow()
            .salidas.single { it.id == id }
        check(salida.embarque.nombre.startsWith("DEMO - ") && salida.destino.nombre.startsWith("DEMO - "))
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var inicio = false
            for (intento in 1..80) {
                scenario.onActivity { inicio = it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is com.app.transporte_chota_bus.ui.inicio.InicioFragment }
                if (inicio) break
                kotlinx.coroutines.delay(250)
            }
            check(inicio) { "No se pudo recuperar la pantalla de inicio." }
            scenario.onActivity { it.abrirPantalla(com.app.transporte_chota_bus.ui.compra.CompraFragment.crear(salida,1)) }
            var botones = 0
            for (intento in 1..100) {
                scenario.onActivity { actividad ->
                    val grid = actividad.findViewById<android.widget.LinearLayout>(R.id.filas_asientos)
                    botones = if (grid == null) 0 else (0 until grid.childCount).sumOf { fila -> (grid.getChildAt(fila) as android.view.ViewGroup).let{linea->(0 until linea.childCount).count{linea.getChildAt(it) is android.widget.Button}} }
                }
                if (botones == 15) break
                kotlinx.coroutines.delay(250)
            }
            assertEquals(15, botones)
            scenario.onActivity { actividad ->
                val grid = actividad.findViewById<android.widget.LinearLayout>(R.id.filas_asientos)
                val asiento = (0 until grid.childCount).flatMap { fila ->
                    val linea = grid.getChildAt(fila) as android.view.ViewGroup
                    (0 until linea.childCount).map { linea.getChildAt(it) }
                }.filterIsInstance<android.widget.Button>().first { it.isEnabled }
                asiento.performClick()
            }
            kotlinx.coroutines.delay(500)
            scenario.onActivity { actividad -> assertTrue(actividad.findViewById<android.view.View>(R.id.btn_reservar_asientos).isEnabled) }
            scenario.onActivity{actividad->
                val marco=actividad.findViewById<android.view.View>(R.id.marco_combi)
                marco.requestRectangleOnScreen(android.graphics.Rect(0,0,marco.width,marco.height),true)
            }
            kotlinx.coroutines.delay(300)
            val contexto = InstrumentationRegistry.getInstrumentation().targetContext
            scenario.onActivity{actividad->
                val marco=actividad.findViewById<android.view.View>(R.id.marco_combi)
                val imagen=android.graphics.Bitmap.createBitmap(marco.width,marco.height,android.graphics.Bitmap.Config.ARGB_8888)
                marco.draw(android.graphics.Canvas(imagen))
                java.io.File(contexto.getExternalFilesDir(null),"evidencia_combi.png").outputStream().use{imagen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                imagen.recycle()
            }
            val destino = java.io.File(contexto.getExternalFilesDir(null), "evidencia_asientos_combi.png")
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                destino.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        }
    }

    // Prueba optativa: usa la sesion existente, solo una salida DEMO y cancela al finalizar.
    // No registra usuarios, no imprime tokens y no realiza pagos.
    @Test fun reservaConTokenRealYLiberaElAsiento() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("reservasServidor") == "true")
        assumeTrue(BuildConfig.DEBUG)
        assumeNotNull(FirebaseAuth.getInstance().currentUser)
        val id = requireNotNull(args.getString("salidaDemoId")).toInt()
        val fecha = requireNotNull(args.getString("fechaDemo"))
        val salida = ContenedorDependencias.viajes.buscar(FiltrosViaje("Chota", "Cajamarca", fecha, 1)).getOrThrow()
            .salidas.single { it.id == id }
        check(salida.embarque.nombre.startsWith("DEMO - ") && salida.destino.nombre.startsWith("DEMO - "))
        val repository = ContenedorDependencias.reservas
        val inventario = repository.consultarAsientos(id).getOrThrow()
        val asiento = inventario.asientos.first { it.disponible }.numero
        val solicitud = UUID.randomUUID().toString()
        var reservaId: String? = null
        try {
            val intento = repository.reservar(id, listOf(asiento), solicitud)
            val reserva = (if (intento.isSuccess) intento else repository.reservar(id, listOf(asiento), solicitud)).getOrThrow()
            reservaId = reserva.id
            assertEquals(EstadoReserva.PENDIENTE, reserva.estado)
            assertEquals(listOf(asiento), reserva.asientos)
            assertEquals(reserva.id, repository.consultarReserva(reserva.id).getOrThrow().id)
            assertFalse(repository.consultarAsientos(id).getOrThrow().asientos.single { it.numero == asiento }.disponible)
        } finally {
            reservaId?.let {
                val primerIntento = repository.cancelarReserva(it)
                val cancelada = (if (primerIntento.isSuccess) primerIntento else repository.cancelarReserva(it)).getOrThrow()
                assertEquals(EstadoReserva.CANCELADA, cancelada.estado)
            }
        }
        assertTrue(repository.consultarAsientos(id).getOrThrow().asientos.single { it.numero == asiento }.disponible)
    }
}
