package com.app.transporte_chota_bus.domain.validation

import com.app.transporte_chota_bus.data.model.PasajeroReserva

data class ValidacionPasajeros(val errores: Map<Int, Map<CampoPerfil,String>>, val errorGeneral: String? = null) {
    val valido get() = errores.isEmpty() && errorGeneral == null
}

// Reutiliza las reglas del perfil y comprueba un DNI diferente por asiento reservado.
object ValidadorPasajeros {
    fun validar(pasajeros: List<PasajeroReserva>, asientos: List<Int>): ValidacionPasajeros {
        if (pasajeros.size !in 1..9 || pasajeros.map { it.asiento }.sorted() != asientos.sorted() ||
            pasajeros.map { it.asiento }.distinct().size != pasajeros.size) {
            return ValidacionPasajeros(emptyMap(), "Completa un pasajero por cada asiento reservado.")
        }
        val repetidos = pasajeros.groupingBy { it.dni.trim() }.eachCount().filterValues { it > 1 }.keys
        val errores = pasajeros.mapNotNull { p ->
            val campos = ValidadorPerfil.validar(p.nombreCompleto,p.dni,p.celular).toMutableMap()
            if (p.dni.trim() in repetidos && p.dni.isNotBlank()) campos[CampoPerfil.DNI] = "Cada pasajero debe tener un DNI diferente"
            if (campos.isEmpty()) null else p.asiento to campos.toMap()
        }.toMap()
        return ValidacionPasajeros(errores)
    }
}
