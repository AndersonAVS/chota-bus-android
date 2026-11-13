# Sprint 3: consulta REST desde Android XML

Esta entrega conecta Inicio con Resultados mediante Retrofit 2.11.0 y Gson. Los datos vienen de PostgreSQL a traves de la API Node; la aplicacion no contiene credenciales SQL. Conserva MVVM y Repository. Se mantienen las pantallas XML permitidas por el profesor.

## Responsabilidades para la exposicion

- FormularioViajeViewModel conserva fecha y cantidad en SavedStateHandle. Origen y destino siguen usando DataStore.
- FiltrosViaje valida sedes, fechas del calendario de Peru desde hoy y cantidad de 1 a 9.
- ViajesApi define la peticion GET y sus parametros. Retrofit codifica caracteres como el acento de Jaen.
- RetrofitViajesRepository transforma JSON, valida precios, horarios y terminales, y maneja errores sin mostrar respuestas internas. Propaga cancelaciones de coroutines.
- ResultadosViewModel mantiene carga, lista, vacio y error; evita solicitudes simultaneas y permite reintentar.
- ResultadosFragment observa el estado con el ciclo de vida de la vista y presenta tarjetas XML mediante RecyclerView. Ver terminales abre la pantalla de embarque/llegada documentada en mapa-terminal.md; no inicia compras ni promete asientos libres.

Se agregan comentarios generales por bloque. java.time usa coreLibraryDesugaring para funcionar desde Android API 24. Horarios en resultados: America/Lima.

## Prueba manual en dispositivo por USB

1. En pgAdmin ejecutar backend/sql/002_datos_demo.sql y tomar la fecha_de_prueba devuelta. Precios, horarios, direcciones y capacidades son ficticios. Si no se cargan datos, una lista vacia es correcta.
2. En Git Bash de backend ejecutar npm start y mantener abierto.
3. En otra terminal Git Bash ejecutar:

```bash
"/c/Users/varga/AppData/Local/Android/Sdk/platform-tools/adb.exe" devices
"/c/Users/varga/AppData/Local/Android/Sdk/platform-tools/adb.exe" -s SERIAL_DEL_DISPOSITIVO reverse tcp:3000 tcp:3000
```

Sustituir SERIAL_DEL_DISPOSITIVO por el identificador de una fila con estado device. Solo si hay un dispositivo conectado puede omitirse -s y el identificador. Repetir reverse despues de reconectar el cable o reiniciar el dispositivo.

4. Ejecutar app debug desde Android Studio, iniciar sesion, seleccionar Chota, Cajamarca, fecha_de_prueba y 1 pasajero. Buscar viajes debe mostrar dos salidas DEMO. Ver terminales presenta las direcciones.
5. Probar una fecha sin datos: aparece el estado vacio. Detener npm start, buscar: aparece error. Reiniciar el servidor y pulsar Reintentar.
6. Girar en Inicio: mantiene fecha y pasajeros. Girar en Resultados: conserva estado sin otra consulta. Volver permite corregir filtros.

## Conexion local y alcance

La URL debug es http://127.0.0.1:3000/ y adb reverse dirige ese puerto a la computadora. El permiso HTTP solo se incluye en src/debug y se limita al bucle local; el resto del trafico sigue usando HTTPS. No se necesita abrir PostgreSQL o el servidor a la red Wi-Fi. La compilacion release no configura un servicio de viajes hasta contar con una API HTTPS real; muestra un error explicito en ese caso.

La integracion posterior del mapa de terminales se documenta en mapa-terminal.md; requiere configurar Maps y registrar coordenadas confirmadas. Reservas, pagos y generacion de boletos siguen pendientes. Capacidad total no equivale a disponibilidad. La pantalla evita cifras ficticias de asientos libres; el backend debe incorporar reservas transaccionales antes de permitir comprar. La lista muestra hasta 100 salidas y avisa si hay mas.

## Verificacion

Ejecutar :app:testDebugUnitTest y :app:assembleDebug. Las nuevas pruebas cubren filtros, conservacion de formulario, estados del ViewModel y peticiones Retrofit/JSON con MockWebServer. No modifican cuentas ni compras. La verificacion manual por USB requiere un dispositivo autorizado y el servidor ejecutandose.

Referencias: https://square.github.io/retrofit/ y https://developer.android.com/privacy-and-security/security-config y https://developer.android.com/studio/write/java8-support

Actualizacion de validacion: la [revision integral](validacion/informe-validacion.md) incorpora cupos reales en busqueda, duracion estimada y SQLite para consultas publicas de solo lectura ante fallo del servicio. DataStore conserva las preferencias; la cache no permite reservar ni comprar.
