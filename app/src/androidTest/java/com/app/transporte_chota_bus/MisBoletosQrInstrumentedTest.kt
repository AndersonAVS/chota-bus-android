package com.app.transporte_chota_bus

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.domain.model.QrBoletoPrueba
import com.app.transporte_chota_bus.ui.compra.*
import com.app.transporte_chota_bus.ui.inicio.InicioFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MisBoletosQrInstrumentedTest{
    // Solo consulta boletos existentes de la sesion y comprueba el QR sin realizar compras.
    @Test fun inicioListaDetalleQrYRegresoSinCargos()=runBlocking<Unit>{
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("misBoletosServidor")=="true"&&BuildConfig.DEBUG)
        checkNotNull(FirebaseAuth.getInstance().currentUser){"Inicia sesion antes de ejecutar la prueba."}
        val repo=ContenedorDependencias.reservas
        val saldo=repo.consultarSaldoPrueba().getOrThrow().saldo
        val pagina=repo.consultarMisBoletos().getOrThrow();check(pagina.boletos.isNotEmpty())
        val b=pagina.boletos.first();val detalle=repo.consultarBoleto(b.id).getOrThrow()
        assertTrue(detalle.id==b.id&&detalle.codigo==b.codigo&&detalle.importe==b.importe)
        assertTrue(com.app.transporte_chota_bus.domain.model.FechaHoraServidor.instante(detalle.emitidoEn).toEpochMilli()==
            com.app.transporte_chota_bus.domain.model.FechaHoraServidor.instante(b.emitidoEn).toEpochMilli())
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            esperar{var listo=false;scenario.onActivity{listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is InicioFragment};listo}
            scenario.onActivity{it.findViewById<View>(R.id.btn_mis_boletos).performClick()}
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<RecyclerView>(R.id.lista_mis_boletos)?.adapter?.itemCount==pagina.boletos.size};listo}
            delay(300)
            // La lista no muestra nombre ni DNI; solo se guarda esta pantalla como evidencia.
            val contexto=InstrumentationRegistry.getInstrumentation().targetContext
            val archivo=java.io.File(contexto.getExternalFilesDir(null),"evidencia_mis_boletos.png")
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->
                archivo.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            scenario.onActivity{it.window.decorView.findViewWithTag<View>("ver_boleto_${b.id}").performClick()}
            esperar{var listo=false;scenario.onActivity{listo=(it.findViewById<ImageView>(R.id.imagen_qr_boleto)?.drawable as? BitmapDrawable)!=null};listo}
            scenario.onActivity{
                assertTrue(it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is DetalleBoletoFragment)
                assertTrue(it.findViewById<TextView>(R.id.boleto_persona).text.contains(b.dni))
                val bitmap=(it.findViewById<ImageView>(R.id.imagen_qr_boleto).drawable as BitmapDrawable).bitmap
                val pixeles=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixeles,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val contenido=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixeles)))).text
                assertTrue(contenido==QrBoletoPrueba.contenido(b.codigo))
                // Exporta solo la imagen generada del QR, sin el detalle personal del boleto.
                java.io.File(contexto.getExternalFilesDir(null),"evidencia_qr_boleto.png").outputStream().use{archivoQr->bitmap.compress(Bitmap.CompressFormat.PNG,100,archivoQr)}
            }
            scenario.recreate()
            esperar{var listo=false;scenario.onActivity{listo=(it.findViewById<ImageView>(R.id.imagen_qr_boleto)?.drawable as? BitmapDrawable)!=null};listo}
            scenario.onActivity{it.findViewById<View>(R.id.btn_volver_detalle_boleto).performClick()}
            esperar{var listo=false;scenario.onActivity{listo=it.findViewById<RecyclerView>(R.id.lista_mis_boletos)?.adapter?.itemCount==pagina.boletos.size};listo}
            scenario.onActivity{it.findViewById<View>(R.id.btn_volver_mis_boletos).performClick()}
            esperar{var listo=false;scenario.onActivity{listo=it.supportFragmentManager.findFragmentById(R.id.contenedor_pantallas) is InicioFragment};listo}
        }
        assertTrue(saldo==repo.consultarSaldoPrueba().getOrThrow().saldo)
        assertTrue(repo.consultarMisBoletos().getOrThrow()==pagina)
    }
    private suspend fun esperar(condicion:()->Boolean){repeat(120){if(condicion())return;delay(250)};error("La pantalla no alcanzo el estado esperado.")}
}
