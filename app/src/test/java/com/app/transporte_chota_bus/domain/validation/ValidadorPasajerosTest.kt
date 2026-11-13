package com.app.transporte_chota_bus.domain.validation
import com.app.transporte_chota_bus.data.model.PasajeroReserva
import org.junit.Test
import org.junit.Assert.*

class ValidadorPasajerosTest{
    private val p=PasajeroReserva(1,"Pasajero de prueba","00000001","900000001")
    @Test fun aceptaDatosCompletos(){assertTrue(ValidadorPasajeros.validar(listOf(p),listOf(1)).valido)}
    @Test fun exigeTodosLosAsientos(){assertFalse(ValidadorPasajeros.validar(listOf(p),listOf(1,2)).valido)}
    @Test fun rechazaDNIDuplicado(){
        val r=ValidadorPasajeros.validar(listOf(p,p.copy(asiento=2)),listOf(1,2))
        assertNotNull(r.errores[1]?.get(CampoPerfil.DNI));assertNotNull(r.errores[2]?.get(CampoPerfil.DNI))
    }
    @Test fun validaNombreDNIYCelular(){
        val r=ValidadorPasajeros.validar(listOf(p.copy(nombreCompleto=" ",dni="123",celular="99")),listOf(1))
        assertEquals(3,r.errores[1]!!.size)
    }
    @Test fun permiteRepetirCelularDeContacto(){
        assertTrue(ValidadorPasajeros.validar(listOf(p,p.copy(asiento=2,dni="00000002")),listOf(1,2)).valido)
    }
}
