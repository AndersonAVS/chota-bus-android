package com.app.transporte_chota_bus.ui.compra

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.app.transporte_chota_bus.MainActivity
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.domain.model.FechaHoraServidor
import com.app.transporte_chota_bus.domain.model.QrBoletoPrueba
import kotlinx.coroutines.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DetalleBoletoFragment:Fragment(R.layout.detalle_boleto){
    private val vm:DetalleBoletoViewModel by viewModels{DetalleBoletoViewModelFactory(ContenedorDependencias.reservas,requireArguments().getString("boleto_id").orEmpty())}
    private var qr:Job?=null
    override fun onViewCreated(view:View,savedInstanceState:Bundle?){
        view.findViewById<View>(R.id.logo_chota_boleto).isVisible=false
        view.findViewById<View>(R.id.btn_volver_detalle_boleto).setOnClickListener{parentFragmentManager.popBackStack()}
        view.findViewById<View>(R.id.btn_actualizar_detalle_boleto).setOnClickListener{vm.cargar()}
        view.findViewById<View>(R.id.btn_login_detalle_boleto).setOnClickListener{(requireActivity() as MainActivity).volverAlLogin()}
        viewLifecycleOwner.lifecycleScope.launch{viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){vm.estado.collect{mostrar(it)}}}
    }
    override fun onResume(){super.onResume();vm.verificarSesion()}
    private fun mostrar(e:EstadoDetalleBoleto){
        val v=view?:return;val b=e.boleto
        qr?.cancel();v.findViewById<ImageView>(R.id.imagen_qr_boleto).setImageDrawable(null)
        v.findViewById<View>(R.id.progreso_detalle_boleto).isVisible=e.cargando
        v.findViewById<TextView>(R.id.error_detalle_boleto).apply{text=e.error.orEmpty();isVisible=e.error!=null}
        v.findViewById<View>(R.id.btn_actualizar_detalle_boleto).isEnabled=!e.cargando&&!e.sesionRequerida
        v.findViewById<View>(R.id.btn_login_detalle_boleto).isVisible=e.sesionRequerida
        v.findViewById<View>(R.id.contenido_detalle_boleto).isVisible=b!=null
        if(b==null){for(id in listOf(R.id.boleto_codigo,R.id.boleto_persona,R.id.boleto_viaje,R.id.boleto_importe,R.id.boleto_emision))v.findViewById<TextView>(id).text="";return}
        val f=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale.forLanguageTag("es-PE")).withZone(ZoneId.of("America/Lima"))
        v.findViewById<TextView>(R.id.boleto_codigo).text=getString(R.string.boletos_codigo,b.codigo)
        v.findViewById<TextView>(R.id.boleto_persona).text=getString(R.string.boletos_persona,b.asiento,b.nombreCompleto,b.dni)
        v.findViewById<TextView>(R.id.boleto_viaje).text=getString(R.string.boletos_viaje,b.embarque.ciudad,b.llegada.ciudad,
            f.format(FechaHoraServidor.instante(b.fechaSalida)),f.format(FechaHoraServidor.instante(b.fechaLlegada)),b.embarque.nombre,b.embarque.direccion,b.llegada.nombre,b.llegada.direccion)
        v.findViewById<TextView>(R.id.boleto_importe).text=getString(R.string.boletos_importe,b.importe)
        v.findViewById<TextView>(R.id.boleto_emision).text=getString(R.string.boletos_emision,f.format(FechaHoraServidor.instante(b.emitidoEn)))
        v.findViewById<View>(R.id.boleto_recuperado).isVisible=b.datosRecuperados
        // Genera pixeles en segundo plano, sin servicios externos ni datos personales en el QR.
        qr=viewLifecycleOwner.lifecycleScope.launch{
            try{
                val bitmap=withContext(Dispatchers.Default){
                    val matriz=QrBoletoPrueba.matriz(b.codigo);val escala=(768/matriz.width).coerceAtLeast(1);val lado=matriz.width*escala
                    val pixeles=IntArray(lado*lado){n->if(matriz[n%lado/escala,n/lado/escala])android.graphics.Color.BLACK else android.graphics.Color.WHITE}
                    Bitmap.createBitmap(pixeles,lado,lado,Bitmap.Config.ARGB_8888)
                }
                v.findViewById<ImageView>(R.id.imagen_qr_boleto).setImageBitmap(bitmap)
                v.findViewById<TextView>(R.id.aviso_qr_boleto).setText(R.string.detalle_boleto_qr_aviso)
            }catch(e:CancellationException){throw e}catch(_:Exception){v.findViewById<TextView>(R.id.aviso_qr_boleto).setText(R.string.detalle_boleto_qr_error)}
        }
    }
    override fun onDestroyView(){qr?.cancel();qr=null;view?.findViewById<ImageView>(R.id.imagen_qr_boleto)?.setImageDrawable(null);super.onDestroyView()}
    companion object{fun crear(id:String)=DetalleBoletoFragment().apply{arguments=Bundle().apply{putString("boleto_id",id)}}}
}
