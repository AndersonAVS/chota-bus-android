# Sprint 4 — vehiculos, asientos y reservas

Este incremento prepara el modelo SQL y las operaciones transaccionales. No implementa todavia la interfaz de compra, API de escritura, pasajeros, pagos o boletos. Los perfiles permanecen en Firestore y la identidad es el UID de Firebase Authentication. Las reservas de las pruebas usan UIDs sinteticos en un esquema aislado, no cuentas reales.

## Paso a paso y comentarios para la exposicion

1. `005_modelo_reservas.sql` crea vehiculos y sus asientos fisicos. Una salida recibe un vehiculo y un inventario propio en salida_asientos. El mismo numero de asiento puede estar disponible en diferentes salidas.
2. reservas guarda el propietario, la salida, los importes y el vencimiento. reserva_asientos conserva el detalle y su liberacion, incluso al cancelar o expirar. No se borra el historial.
3. El indice unico parcial asiento_una_reserva_activa impide repetir salida y numero mientras el asiento este ocupado. Las claves foraneas impiden mezclar un asiento con otro vehiculo o con otra salida.
4. reservar_asientos bloquea una sola salida con FOR UPDATE y crea todos los asientos solicitados o ninguno. El precio viene de salidas; el total se calcula en una columna generada. Nunca se acepta un importe calculado por Android.
5. La solicitud incluye un UUID de idempotencia: un reintento del mismo usuario con los mismos asientos devuelve la misma reserva. Si cambia los datos con la misma clave, se rechaza. Un bloqueo asesor transaccional serializa la misma clave antes del bloqueo de salida.
6. Cada reserva pendiente vence a los 10 minutos desde que se acepta. La vista disponibilidad_asientos ya considera libres los asientos vencidos. La limpieza de estado y la liberacion del indice se ejecutan al reservar, cancelar o invocar liberar_reservas_vencidas. No se ha instalado un proceso programado de limpieza; el registro puede seguir PENDIENTE hasta esa operacion, aunque ya no reste disponibilidad.
7. cancelar_reserva comprueba el propietario y libera los asientos de reservas pendientes. Repetir la cancelacion no duplica efectos. CONFIRMADA es un estado reservado para el futuro pago: en este incremento no existe una operacion de confirmacion y no se implementan devoluciones.

Todos los bloques SQL incluyen un comentario general. Las funciones usan permisos del invocador; la ejecucion publica se revoca. Actualmente el servidor local utiliza el propietario de la base. Antes de desplegar se necesita un rol exclusivo y permisos minimos, ademas de las restricciones de red correspondientes.

## Scripts y aplicacion

- 001_esquema_inicial.sql reproduce las tablas del Sprint 3 y se usa solo para bases vacias. No se vuelve a ejecutar en chota_bus.
- 005_modelo_reservas.sql es una migracion atomica de una sola ejecucion. Sobre la base actual se aplica despues del esquema inicial ya existente.
- 006_vehiculos_asientos_demo.sql crea un vehiculo DEMO distinto por salida DEMO sin vehiculo. Repetirlo no duplica los inventarios ya asignados. La distribucion sintetica usa tres columnas y la capacidad existente de la salida; no representa la distribucion oficial de la flota.
- Para nuevas salidas DEMO de otro dia se puede repetir 002 y despues 006. Las coordenadas registradas no cambian. Los scripts 003 y 004 se limitan a las identidades previamente inspeccionadas; revisar antes de usarlos en otra base.

No se sustituye la capacidad historica de salidas: para vender, la fuente de disponibilidad es el inventario salida_asientos menos reservas vigentes/confirmadas. Una salida sin vehiculo o sin inventario no puede reservarse aunque el campo capacidad tenga un valor.

## Verificacion en pgAdmin (solo lectura)

```sql
SELECT s.id AS salida, v.placa, count(da.numero) AS asientos,
       count(da.numero) FILTER (WHERE da.disponible) AS disponibles
FROM salidas s
LEFT JOIN vehiculos v ON v.id = s.vehiculo_id
LEFT JOIN disponibilidad_asientos da ON da.salida_id = s.id
GROUP BY s.id, v.placa ORDER BY s.id;
```

Las salidas DEMO actuales deben mostrar 15 asientos cada una. Los asientos y placas son ficticios; no se inventa una placa real para la empresa. No se crean reservas reales al cargar estos scripts.

## Pruebas

En Git Bash, desde backend: `npm run test:db`. Lee .env y crea un esquema con nombre pruebas_reservas_ seguido de un UUID hexadecimal; al terminar elimina exclusivamente ese esquema. Necesita permisos para crear esquemas. No elimina tablas de public ni modifica las reservas locales del usuario. No ejecutar con una cuenta de produccion; utilizar siempre una base de desarrollo o pruebas. `npm test` sigue ejecutando solo las pruebas HTTP existentes sin requerir PostgreSQL.

La prueba levanta el esquema real 001, aplica 005 y verifica 006. Comprueba concurrencia sobre el mismo asiento, reserva atomica de varios asientos, precio SQL, idempotencia, vencimiento, cancelacion por propietario, limite de pasajeros, salidas pasadas/canceladas y restricciones SQL. Los ids, UIDs y reservas de prueba no se trasladan a public.

## Proximo incremento

Crear endpoints autenticados para consultar asientos y reservar/cancelar. Verificar el ID token de Firebase en el servidor y tomar el UID del token; no confiar en un UID recibido en JSON. Los endpoints actuales de consulta siguen funcionando y todavia no consumen el nuevo inventario. Android mantiene su aviso de disponibilidad no confirmada hasta conectar el nuevo contrato y la pantalla de seleccion. El estado CONFIRMADA solo debe establecerse posteriormente a partir de un pago verificado en el servidor, bajo el mismo bloqueo de salida.

Referencias: https://www.postgresql.org/docs/current/explicit-locking.html ; https://www.postgresql.org/docs/current/indexes-partial.html ; https://firebase.google.com/docs/auth/admin/verify-id-tokens

## Resultado de la aplicacion local — 2026-11-13

005 y 006 ya fueron aplicados a chota_bus.public. Se verificaron seis salidas DEMO con 15 asientos totales y 15 disponibles por salida; reservas contiene cero registros. No se aplico 001 sobre la base existente. Las 18 pruebas PostgreSQL y las 16 HTTP pasaron. Android no se modifico en este bloque y sus pantallas todavia no consultan ni crean reservas.


Actualizacion: los endpoints autenticados ya estan implementados y se documentan en sprint-4-api-reservas.md. La pantalla Android y la validacion positiva con el token real del dispositivo siguen pendientes del proximo incremento.
