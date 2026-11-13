package com.app.transporte_chota_bus.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import com.app.transporte_chota_bus.domain.model.FechaHoraServidor
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant

data class ConsultaGuardada(val resultado:ResultadoViajes,val guardadoEn:String)
interface CacheViajes {
    suspend fun guardar(filtros:FiltrosViaje,resultado:ResultadoViajes)
    suspend fun leer(filtros:FiltrosViaje):ConsultaGuardada?
    suspend fun borrar(filtros:FiltrosViaje)
}

// Solo conserva consultas PUBLICAS de viajes: no almacena perfiles, DNI, tokens ni pagos.
class CacheViajesSqlite(context:Context,nombre:String="consultas_viajes.db",private val reloj:()->Long={System.currentTimeMillis()}):CacheViajes,AutoCloseable{
    private val db=object:SQLiteOpenHelper(context.applicationContext,nombre,null,1){
        override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE consultas(clave TEXT PRIMARY KEY,json TEXT NOT NULL,guardado INTEGER NOT NULL)")}
        override fun onUpgrade(db:SQLiteDatabase,anterior:Int,nuevo:Int){error("Define una migracion para actualizar la cache.")}
    }
    private val gson=Gson()
    private fun clave(f:FiltrosViaje)=gson.toJson(listOf(f.origen,f.destino,f.fecha,f.pasajeros.toString()))
    override suspend fun guardar(filtros:FiltrosViaje,resultado:ResultadoViajes)=withContext(Dispatchers.IO){
        require(filtros.error()==null&&!resultado.desdeCache)
        val c=db.writableDatabase;c.beginTransaction()
        try{
            c.insertOrThrow("consultas",null,ContentValues().apply{
                put("clave",clave(filtros));put("json",gson.toJson(resultado));put("guardado",reloj())
            }.also { c.delete("consultas","clave=?",arrayOf(clave(filtros))) })
            // Limita el almacenamiento a las veinte consultas mas recientes.
            c.execSQL("DELETE FROM consultas WHERE clave NOT IN (SELECT clave FROM consultas ORDER BY guardado DESC,clave LIMIT 20)")
            c.setTransactionSuccessful()
        }finally{c.endTransaction()}
        Unit
    }
    override suspend fun leer(filtros:FiltrosViaje):ConsultaGuardada?=withContext(Dispatchers.IO){
        db.readableDatabase.query("consultas",arrayOf("json","guardado"),"clave=?",arrayOf(clave(filtros)),null,null,null).use{c->
            if(!c.moveToFirst())return@withContext null
            val fecha=c.getLong(1);val edad=reloj()-fecha
            if(edad !in 0..86_400_000){borrar(filtros);return@withContext null}
            try{
                val r=gson.fromJson(c.getString(0),ResultadoViajes::class.java)
                require(r.salidas.size<=100&&r.salidas.map{it.id}.distinct().size==r.salidas.size)
                for(s in r.salidas){
                    require(s.id>0&&s.capacidad>0&&s.moneda=="PEN"&&BigDecimal(s.precio)>BigDecimal.ZERO)
                    require(FechaHoraServidor.instante(s.llegada).isAfter(FechaHoraServidor.instante(s.salida)))
                    for(t in listOf(s.embarque,s.destino)){
                        require(t.id>0&&t.nombre.isNotBlank()&&t.ciudad.isNotBlank()&&t.direccion.isNotBlank())
                        require((t.latitud==null&&t.longitud==null)||(t.latitud!=null&&t.longitud!=null&&t.latitud in -90.0..90.0&&t.longitud in -180.0..180.0))
                    }
                }
                ConsultaGuardada(r,Instant.ofEpochMilli(fecha).toString())
            }catch(_:Exception){borrar(filtros);null}
        }
    }
    override suspend fun borrar(filtros:FiltrosViaje)=withContext(Dispatchers.IO){db.writableDatabase.delete("consultas","clave=?",arrayOf(clave(filtros)));Unit}
    override fun close(){db.close()}
}
