package com.app.transporte_chota_bus.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Mantiene una única instancia de DataStore para este archivo del dispositivo.
val Context.preferenciasBusquedaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "preferencias_busqueda"
)
