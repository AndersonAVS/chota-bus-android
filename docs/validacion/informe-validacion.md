# Revision de integracion de Chota Bus

Revision del 13 de noviembre de 2026. La aplicacion tiene un flujo funcional de demostracion, pero el proyecto completo no se declara terminado. Se contrastaron los documentos originales con el codigo, las pruebas y las decisiones aprobadas del usuario. Los maestros se leyeron sin modificarlos; fuentes.json registra sus hashes SHA256.

## Documentos y numeracion

El documento de requisitos usa REQ10 y REQ11 para terminales, mientras el informe maestro usa esos numeros para distribucion y seleccion de asientos. Tambien difieren varias referencias RNF. La matriz funcional conserva el identificador de ambos documentos y las historias HU001 a HU020. La matriz RNF usa la numeracion del documento de requisitos; no debe trasladarse al maestro por numero sin revisar el texto.

## Correcciones realizadas

1. La busqueda filtraba por capacidad total y no por cupos. Ahora cuenta disponibilidad_asientos, excluye salidas sin plazas para el grupo y muestra el numero libre al consultar. La reserva transaccional sigue revalidando los asientos.
2. La semana7 requeria DataStore y SQLite/Room. Se agrego SQLiteOpenHelper para consultas publicas de viajes: guarda, lee, reemplaza y elimina consultas, persiste entre aperturas y caduca a las 24 horas. Mantiene como maximo veinte consultas. No contiene perfiles, DNI, tokens ni pagos.
3. Cuando falla el servicio se puede leer una consulta guardada, identificada con fecha y aviso de datos no actuales. Se elimina su conteo de plazas y se deshabilita Seleccionar salida. Los mapas de terminales pueden consultarse, pero las reservas y compras requieren servicio actualizado. La cache no bloquea una respuesta valida si falla la escritura local.
4. Los resultados muestran la duracion estimada calculada de los horarios.
5. La conversion de vencimiento de CompraViewModel usa el mismo conversor de fechas con offset y microsegundos comprobado en los demas modulos.
6. Los enlaces de terminos/privacidad estaban inactivos. Ahora informan que el contenido oficial esta pendiente; en debug la casilla identifica la demostracion y el uso de datos ficticios. Esto corrige el control inactivo, no reemplaza documentos oficiales.

Las correcciones conservan Kotlin, XML y comentarios generales por bloque. SQLite es una de las alternativas SQLite/Room indicadas en el trabajo de campo; no se afirma que se haya implementado Room.

## Ajustes de alcance aprobados

- XML permitido por el profesor segun respuesta del usuario, aunque la semana6 mencione Compose.
- Perfiles en Firebase Auth/Firestore y viajes/compras en PostgreSQL, por decision expresa del usuario. Es un ajuste del texto SQL de REQ21, no una migracion del perfil a SQL.
- S/300 ficticios por cuenta para pruebas hasta el final del proyecto. Pago real, comprobante fiscal, autorizacion de embarque y despliegue productivo son pendientes distintos.

## Matrices y seguimiento

- matriz-requerimientos.csv: 25 requisitos funcionales y cruce con maestro/HU.
- matriz-no-funcionales.csv: 22 condiciones de calidad, con limites de evidencia.
- matriz-trabajos-campo.csv: semanas1 a12, sin omitir investigacion, etica ni ODS12.
- pendientes-priorizados.csv: trabajo que todavia requiere desarrollo, validacion o informacion empresarial.

Los CSV estan codificados UTF8 con BOM para abrirlos en Excel. No se inventan actas Daily, Review ni Retrospective; las guias existentes describen implementaciones y no prueban por si mismas ceremonias Scrum.

## Interpretacion del cierre

El flujo de pasajeros esta implementado en modo DEMO. La administracion de catalogo por roles, los textos oficiales, las direcciones postales, los respaldos/restauracion, las comprobaciones academicas ODS12/etica y las pruebas amplias de rendimiento y accesibilidad impiden afirmar cumplimiento integral. RNF03 pide verificar conectividad antes de operar: el manejo actual detecta fallos y ofrece cache, pero no equivale a un preflight de red para cada operacion.

La semana9 muestra POST/GET/PUT y cancelacion logica; no se presenta esa cancelacion como un DELETE HTTP o un CRUD administrativo completo. No se implementan borrados de compras para cumplir un verbo tecnico.

## Reproduccion

Desde backend: npm test y npm run test:db. Desde la raiz Android: ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug.

Las pruebas SQL usan un esquema aislado. Las del telefono usan adb install -r y am instrument, nunca connectedDebugAndroidTest en el telefono personal. No se repite un pago en la cuenta personal. Los positivos de alta/login/correo reales corresponden a evidencia historica del usuario y pruebas de coordinacion; esta revision no envia correos ni crea cuentas reales. La compra completa se valida transaccionalmente en el esquema SQL de prueba; la interfaz del telefono se verifica por tramos con reservas temporales que se cancelan y compras ya pagadas.

Los resultados finales y el registro Git se agregan al terminar las comprobaciones. El funcionamiento del simulador no certifica preparacion para produccion.

## Resultados finales de esta revision

- 180 pruebas unitarias Android aprobadas, cero fallos.
- 89 pruebas unitarias/HTTP de servidor y 52 pruebas PostgreSQL/HTTP aprobadas.
- 9 pruebas en el telefono: 8 del flujo y una de SQLite. Mapa real cargado, giro de pantalla, cupos, asientos, pasajeros, consulta de pagos existentes, boletos, QR e historial comprobados.
- Compilacion y lint sin errores; se conservan las advertencias conocidas de compatibilidad y estilo.
- Muestra funcional con sesion ya disponible: inicio 2082 ms y busqueda por USB 294 ms. Es una observacion de un dispositivo y una muestra, no un benchmark ni validacion de todos los RNF.
- Hashes de documentos originales sin cambios; cero indicadores de secretos en la revision de archivos candidatos. La configuracion local de Firebase, Maps, PostgreSQL y SDK esta ignorada.

casos-prueba.csv distingue pruebas aprobadas, simuladas, historicas y pendientes manuales. resultados.json conserva los conteos y limites para repetir la verificacion. Las guias antiguas corresponden a incrementos historicos; esta revision es el punto de entrada actual.

## Registro del avance en Git

El 13 de noviembre de 2026 se registro la integracion revisada desde Git Bash en la rama sprint/05-validacion-integracion, commit [235d8f6](https://github.com/AndersonAVS/chota-bus-android/commit/235d8f6). La rama se publico en origin y quedo configurada para seguimiento. No se fusiono con main ni se creo una solicitud de cambios automaticamente.

Se incluyeron implementaciones de reservas, compra ficticia, emision/consulta de boletos, QR, historial, SQLite y la revision trazable. No se incluyeron google-services.json, secrets.properties, local.properties, backend/.env, node_modules ni artefactos build. No se fabrica una secuencia de commits historicos por sprint: esta entrega registra el estado integrado efectivamente validado. Este registro documental se guarda en un commit posterior al de integracion.
