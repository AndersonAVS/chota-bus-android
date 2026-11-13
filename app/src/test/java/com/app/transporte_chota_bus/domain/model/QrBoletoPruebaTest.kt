package com.app.transporte_chota_bus.domain.model

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Test
import org.junit.Assert.*

class QrBoletoPruebaTest{
    private val codigo="CBP-00000000000040008000000000000001"
    @Test fun qrSePuedeLeerYContieneSoloIdentificadorDePrueba(){
        val m=QrBoletoPrueba.matriz(codigo);val lado=m.width*8
        val pixeles=IntArray(lado*lado){n->if(m[n%lado/8,n/lado/8])0xff000000.toInt() else 0xffffffff.toInt()}
        val leido=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(lado,lado,pixeles)))).text
        assertEquals("CHOTA_BUS:PRUEBA:v1:$codigo",leido)
        assertFalse(leido.contains("dni",true));assertFalse(leido.contains("token",true));assertFalse(leido.startsWith("http"))
    }
    @Test(expected=IllegalArgumentException::class) fun noGeneraQrParaTextoAjeno(){QrBoletoPrueba.matriz("https://ejemplo.com")}
}
