package com.app.transporte_chota_bus.di

import com.app.transporte_chota_bus.data.repository.AutenticacionRepository
import com.app.transporte_chota_bus.data.repository.FirebaseAutenticacionRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

// Proporciona las dependencias compartidas de autenticación y perfiles.
object ContenedorDependencias {
    val autenticacion: AutenticacionRepository by lazy {
        val firestore = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        FirebaseAutenticacionRepository(FirebaseAuth.getInstance(), firestore)
    }
}
