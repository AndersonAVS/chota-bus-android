package com.app.transporte_chota_bus
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import com.app.transporte_chota_bus.ui.auth.RegistroFragment
import com.app.transporte_chota_bus.ui.inicio.InicioFragment
import com.app.transporte_chota_bus.ui.viajes.ResultadosFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RevisionIntegralInstrumentedTest{
    // Verifica validacion vacia, busqueda y mapa con la sesion existente, sin registrar usuarios o cobrar.
    @Test fun busquedaCuposMapaYRegistroInvalido()=runBlocking<Unit>{
        val args=InstrumentationRegistry.getArguments();assumeTrue(args.getString("revisionIntegral")=="true"&&BuildConfig.DEBUG)
        val fecha=requireNotNull(args.getString("fechaDemo"));val contexto=InstrumentationRegistry.getInstrumentation().targetContext
        val inicio=android.os.SystemClock.elapsedRealtime();var inicioMs=0L;var busquedaMs=0L
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            esperar{var listo=false;scenario.onActivity{listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is InicioFragment};listo}
            inicioMs=android.os.SystemClock.elapsedRealtime()-inicio
            scenario.onActivity{it.abrirPantalla(RegistroFragment())}
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<View>(R.id.btn_crear_cuenta)!=null};listo}
            scenario.onActivity{it.findViewById<View>(R.id.btn_crear_cuenta).performClick();assertNotNull(it.findViewById<EditText>(R.id.input_nombre_registro).error);it.entrarAlInicio()}
            esperar{var listo=false;scenario.onActivity{listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is InicioFragment};listo}
            val marca=android.os.SystemClock.elapsedRealtime()
            scenario.onActivity{it.abrirPantalla(ResultadosFragment.crear(FiltrosViaje("Chota","Cajamarca",fecha,1)))}
            esperar{var listo=false;scenario.onActivity{listo=(it.findViewById<RecyclerView>(R.id.lista_salidas)?.adapter?.itemCount?:0)>0};listo}
            busquedaMs=android.os.SystemClock.elapsedRealtime()-marca
            scenario.onActivity{
                val fila=it.findViewById<RecyclerView>(R.id.lista_salidas).getChildAt(0)
                assertTrue(fila.findViewById<TextView>(R.id.salida_disponibilidad).text.contains("libres"))
                assertTrue(fila.findViewById<View>(R.id.btn_seleccionar_salida).isEnabled)
                fila.findViewById<View>(R.id.btn_ver_terminales).performClick()
            }
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<TextView>(R.id.texto_ciudad_terminal)?.text=="Chota"};listo}
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<TextView>(R.id.texto_estado_mapa)?.visibility==View.GONE};listo}
            scenario.onActivity{assertTrue(it.findViewById<View>(R.id.btn_como_llegar).isEnabled);it.findViewById<View>(R.id.btn_terminal_llegada).performClick()}
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<TextView>(R.id.texto_ciudad_terminal)?.text=="Cajamarca"};listo}
            scenario.recreate()
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<TextView>(R.id.texto_ciudad_terminal)?.text=="Cajamarca"};listo}
            scenario.onActivity{it.entrarAlInicio()}
        }
        File(contexto.getExternalFilesDir(null),"revision_tiempos.json").writeText("""{"inicio_con_sesion_ms":$inicioMs,"busqueda_usb_ms":$busquedaMs,"muestras":1,"alcance":"muestra funcional, no benchmark de produccion"}""")
    }
    private suspend fun esperar(condicion:()->Boolean){repeat(120){if(condicion())return;delay(250)};error("La pantalla no alcanzo el estado esperado.")}
}
