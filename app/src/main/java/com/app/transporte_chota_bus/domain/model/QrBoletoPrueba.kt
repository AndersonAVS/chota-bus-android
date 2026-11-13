package com.app.transporte_chota_bus.domain.model

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrBoletoPrueba {
    // El QR solo identifica el codigo de prueba: no contiene DNI, token ni autorizacion de embarque.
    fun contenido(codigo:String):String{
        require(Regex("CBP-[A-F0-9]{32}").matches(codigo))
        return "CHOTA_BUS:PRUEBA:v1:$codigo"
    }
    fun matriz(codigo:String)=QRCodeWriter().encode(contenido(codigo),BarcodeFormat.QR_CODE,0,0,
        mapOf(EncodeHintType.MARGIN to 4,EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
}
