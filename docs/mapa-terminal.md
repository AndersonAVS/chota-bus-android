# Sprint 3 — mapa del terminal de embarque y llegada

## Incremento implementado

Resultados -> Ver terminales -> MapaTerminalFragment. La pantalla XML recibe ambos terminales de la salida seleccionada, permite cambiar Embarque/Llegada y conserva esa eleccion al girar. Los argumentos Bundle conservan los datos al recrear la pantalla. No hace falta otro ViewModel para este contenido estatico: usa el resultado ya consultado por ResultadosViewModel, sin volver a pedir la API ni mantener una segunda fuente de datos.

UbicacionTerminal valida coordenadas completas, finitas y dentro de rango. SupportMapFragment presenta un marcador y centra la camara. Un estado de carga y un tiempo de espera de 20 segundos informan si el mapa no termina de cargar. Las referencias y callbacks se limpian al destruir la vista.

Como llegar usa una URL HTTPS de Google Maps con el destino; puede abrir la app de mapas o el navegador. No envia la ubicacion personal ni solicita permisos ACCESS_FINE_LOCATION o ACCESS_COARSE_LOCATION. El usuario maneja el punto de inicio en la aplicacion externa. No se activa My Location. La ubicacion del terminal no se debe confundir con la posicion del pasajero.

Los registros DEMO sin coordenadas muestran ubicacion pendiente y desactivan Como llegar. El registro de ubicaciones proporcionadas por el usuario se detalla al final de esta guia. No se ha deducido una ubicacion a partir del centro de una ciudad ni se ha reutilizado una direccion del boceto como dato validado. Las coordenadas validas de PostgreSQL se consideran confirmadas por el responsable que las registra.

## Configuracion pendiente de Google Cloud

1. Abrir https://console.cloud.google.com/ y seleccionar el proyecto correspondiente a Chota Bus. Un proyecto Firebase tambien tiene un proyecto Google Cloud asociado.
2. Revisar los requisitos y condiciones de facturacion de Google Maps Platform antes de habilitarlo. La cuenta debe tener la facturacion requerida por Google; este cambio lo realiza el usuario en su consola. No se ha habilitado ningun recurso ni vinculacion de pago automaticamente.
3. APIs y servicios -> Biblioteca -> Maps SDK for Android -> Habilitar.
4. APIs y servicios -> Credenciales -> Crear credenciales -> Clave de API.
5. Restringir aplicaciones a Aplicaciones Android. Paquete: com.app.transporte_chota_bus. Obtener la huella SHA-1 debug con ./gradlew signingReport en Git Bash. Usar el bloque Variant: debug. Para publicar se necesitara tambien la huella del certificado de distribucion.
6. Restringir la API a Maps SDK for Android y guardar.
7. En secrets.properties de la raiz completar MAPS_API_KEY= seguido de la clave real, sin comillas. El archivo local vacio ya esta creado y excluido de Git. secrets.properties.example permite recrearlo en otro equipo. No enviar la clave por chat. local.properties conserva solo la configuracion del SDK de Android. Se lee Properties directamente desde Gradle; no es necesario instalar otro plugin para sustituir el manifest placeholder.
8. Sincronizar Gradle y volver a ejecutar la app. La clave se empaqueta en el manifest como com.google.android.geo.API_KEY; las restricciones de Android/SHA-1 y API son necesarias, aunque el archivo local no se suba al repositorio.

Si falta la clave, el proyecto compila pero no crea el mapa y muestra un estado no disponible. El SDK no ofrece un callback especifico para todos los errores de autorizacion: si no aparecen los mosaicos, verificar SDK habilitado, facturacion, clave y restricciones, ademas de la conexion de internet. No publicar registros o capturas que contengan claves.

## Coordenadas y prueba manual

El usuario debe proporcionar enlaces o coordenadas confirmadas de cada terminal. Posteriormente registrar esos valores en terminales.latitud y terminales.longitud junto con la direccion verificada, identificando el registro por id. No aplicar cambios masivos por ciudad ni asignar coordenadas reales a una sede DEMO sin distinguir los datos de prueba.

Antes de cambiar un registro, consultar SELECT id, nombre, ciudad, direccion, latitud, longitud FROM terminales ORDER BY id; confirmar el punto de la entrada del terminal, no solo la ciudad. Esta entrega no modifica datos SQL ni necesita cambios en la API: terminales y salidas ya incluyen latitud y longitud.

Prueba: servidor activo y adb reverse configurado -> iniciar sesion -> buscar salida -> Ver terminales. Probar DEMO sin coordenadas (pendiente, sin indicaciones), terminal con coordenadas y clave (mapa y marcador), cambiar Llegada/Embarque, girar, volver a resultados y abrir Como llegar. Sin clave pero con coordenadas el enlace externo sigue disponible. No se ha validado visualmente el mapa real hasta configurar la clave y los puntos confirmados.

## Evidencias

Pruebas unitarias: ausencia de coordenadas, par incompleto, rangos, NaN/infinito, configuracion y servicios de Google, estado disponible y URL de indicaciones sin origen personal. Ejecutar :app:testDebugUnitTest :app:assembleDebug :app:lintDebug.

Referencias oficiales: https://developers.google.com/maps/documentation/android-sdk/start y https://developers.google.com/maps/documentation/android-sdk/releases y https://developers.google.com/maps/documentation/urls/guide


## Identificacion de la compilacion debug actual

Paquete: com.app.transporte_chota_bus

SHA-1 del certificado debug obtenido con :app:signingReport:

```
E7:D1:FF:74:9F:FB:AC:ED:4C:DD:2F:69:F2:31:00:59:3C:24:06:17
```

La huella identifica el certificado y no es una clave privada. Cambia si se genera otro debug.keystore o se trabaja desde otro equipo con otro certificado. No sirve como huella de publicacion: release todavia no tiene un certificado de firma configurado.


## Ubicaciones proporcionadas el 2026-11-13

El usuario suministro codigos Plus Code cortos para Chota, Cajamarca y Jaen. Se recupero el prefijo mediante recoverNearest y se decodifico el centro con la implementacion oficial de Google Open Location Code. Las referencias de ciudades sirven solo para recuperar el prefijo omitido; no se guardan sus centros como ubicaciones de terminales. Se comprobo el round-trip encode/decode de cada codigo completo. Los centros se redondean a seis decimales, segun las columnas NUMERIC(9,6), usando redondeo decimal HALF_UP.

| Terminal | Plus Code completo | Latitud | Longitud |
| --- | --- | --- | --- |
| Chota | 6753C9P3+QG | -6.563063 | -78.646188 |
| Cajamarca | 6743RFXG+CQ | -7.151438 | -78.523063 |
| Jaén | 676376J2+9W | -5.719063 | -78.797688 |

Los puntos proceden de la informacion del usuario; no equivalen a una verificacion independiente de la puerta de acceso o de la titularidad del establecimiento. Un Plus Code representa una celda geografica: el marcador usa su centro. La direccion postal todavia no fue confirmada; se guarda el codigo como referencia, sin inventar una calle.

backend/sql/003_ubicaciones_proporcionadas.sql actualiza exclusivamente los ids/nombres/ciudades de los tres terminales DEMO inspeccionados. No renombra ni convierte los viajes de demostracion en viajes comerciales reales. Reejecutarlo con los mismos datos es valido; si detecta otra coordenada o no encuentra los tres registros esperados, revierte todo el bloque. La procedencia tambien queda en docs/ubicaciones-terminales.json. No ejecutar en otra base sin revisar las identidades primero.

Lajas: el usuario descarto C9Q2+3M Chota y proporciono C7R8+238, 06601, Peru. El codigo completo corregido es 6753C7R8+238. El script 004_ubicacion_lajas.sql registra latitud -6.559963 y longitud -78.734797 en el terminal id 4, con las mismas guardas de identidad y coordenadas previas. La referencia postal coincide con Lajas: https://www.gob.pe/institucion/munilajas/contacto-y-numeros-de-emergencias. No se ha verificado independientemente la puerta de acceso del terminal.

Para ver cambios ya guardados: volver a Inicio y hacer otra busqueda. Una pantalla de resultados o mapa ya abierta conserva los datos que recibio y no se actualiza por cambiar PostgreSQL. No hace falta reinstalar la app ni reiniciar el servidor por cambiar solamente las filas SQL. La clave de Maps sigue siendo necesaria para el mapa interno; Como llegar puede abrirse con coordenadas aunque falte la clave interna.

Referencias de conversion: https://github.com/google/open-location-code/blob/main/js/README.md y https://github.com/google/open-location-code/blob/main/python/openlocationcode/openlocationcode.py

Referencias para recuperar las ciudades: https://www.gob.pe/institucion/munichota/institucional ; https://www.diresacajamarca.gob.pe/media/portal/TUNGW/documento/82995/POI_2025_CONSISTENCIADO_actualizado_V.01_4.pdf?r=1761834308 ; https://munijaen.gob.pe/documentos/doc_gob/DG_MEMORIA-ANUAL-2022-DIGITAL_4298015673.pdf?n=1770817780


## Paso actual: completar Lajas y habilitar el mapa

El ultimo punto proporcionado se registra mediante backend/sql/004_ubicacion_lajas.sql. Las coordenadas de PostgreSQL usan seis decimales: -6.559963, -78.734797. La diferencia de la ultima cifra frente a otras representaciones del centro depende del redondeo decimal y es inferior a un metro.

Tras registrar el punto, comprobar GET /api/v1/terminales y una nueva busqueda Chota -> Lajas. La llegada debe incluir coordenadas numericas. Las cuatro ubicaciones ya pueden alimentar la pantalla existente; no es necesario duplicarlas en Kotlin o XML.

Siguiente paso del usuario: seleccionar Chota Bus en Google Cloud, habilitar Maps SDK for Android con la facturacion requerida y crear una clave restringida al paquete y SHA-1 indicados arriba. Completar secrets.properties y sincronizar/recompilar. Hasta configurar la clave no se puede verificar visualmente el mapa interno.
