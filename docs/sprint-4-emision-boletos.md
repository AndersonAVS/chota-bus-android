# Sprint 4: emision de boletos de prueba en el servidor

## Objetivo de este paso

Emitir un boleto por pasajero/asiento cuando la compra con saldo ficticio queda confirmada. La emision ocurre dentro de la misma transaccion que el descuento: todos los cambios se confirman juntos o ninguno se guarda. La app conserva su flujo actual; la pantalla de confirmacion, el detalle con QR y la conexion de Mis boletos/Historial corresponden a los siguientes pasos.

## Paso a paso para explicar en la exposicion

1. Android solicita pagar una reserva usando la sesion Firebase; no envia UID, saldo ni importe.
2. El servidor verifica el token y PostgreSQL valida la reserva, los pasajeros, los asientos y el saldo.
3. La funcion de pago registra el cargo y cambia la reserva a CONFIRMADA.
4. Un trigger (accion automatica de la base de datos) ejecuta emitir_boletos_prueba antes de finalizar la transaccion.
5. La funcion comprueba que el pago pertenece a la misma cuenta y que coincide con el total. Crea una fila por asiento con codigo unico CBP-, pasajero, precio y copia del viaje.
6. Se verifica la cantidad de boletos y que la suma de sus importes sea igual al total de la reserva.
7. PostgreSQL confirma el conjunto. El servidor devuelve el pago y un arreglo boletos con sus identificadores, codigos y asientos.

Si falla la emision, la compra permanece PENDIENTE y no se descuenta saldo. Una reserva no pagada no puede confirmarse directamente sin un pago consistente. Los reintentos de pago devuelven los mismos boletos; la restriccion UNIQUE(reserva_id,numero_asiento) impide emitir dos por el mismo asiento de una compra.

## Datos del boleto

- UUID interno y codigo unico CBP- seguido de 32 caracteres hexadecimales.
- Reserva, pago de prueba y numero de asiento.
- Nombre y DNI del pasajero. El telefono no se copia al boleto porque no es necesario para mostrarlo.
- Precio unitario, moneda PEN, estado EMITIDO, modo PRUEBA y fecha de emision.
- Copia del viaje: identificador de salida, horas de salida/llegada, ciudades, nombres, direcciones y coordenadas de terminales disponibles al emitir.
- Aviso de la API: BOLETO DE PRUEBA - SIN VALOR FISCAL.

La copia del pasajero y del viaje evita que editar despues el catalogo cambie el boleto emitido. Las modificaciones administrativas de un viaje requeriran un flujo posterior de notificacion/reprogramacion; no se implementa aqui. El codigo no es una credencial de acceso ni una validacion de embarque: las consultas exigen sesion y propietario. Este paso tampoco emite comprobantes SUNAT ni archivos PDF.

## Rutas protegidas

| Metodo y ruta | Resultado |
| --- | --- |
| POST /api/v1/reservas/:id/pago-prueba | Conserva los campos del pago y agrega boletos: [{id,codigo,asiento,modo}] |
| GET /api/v1/reservas/:id/boletos | datos: arreglo de boletos de una reserva propia, ordenados por asiento |
| GET /api/v1/boletos/:id | datos: detalle de un boleto propio |

El usuario se obtiene exclusivamente del token verificado. Una reserva propia sin pago devuelve un arreglo vacio; una reserva o boleto ajeno devuelve 404, igual que uno inexistente. Sin sesion devuelve 401; un identificador invalido devuelve 400. Las respuestas protegidas usan Cache-Control: no-store. Las lecturas permanecen disponibles aunque se deshabilite efectuar nuevos pagos de prueba.

Android actual sigue funcionando: el parser de pago lee sus campos conocidos y tolera la nueva propiedad boletos. En este paso no se modifican Kotlin ni los XML; en el siguiente se conectaran los boletos a la confirmacion de compra.

## Archivos

- backend/sql/009_boletos_prueba.sql: tabla, funcion de emision, trigger y recuperacion de compras anteriores.
- backend/src/boletos-repository.js: consultas parametrizadas filtradas por propietario.
- backend/src/boletos-routes.js: autenticacion y rutas de lectura.
- backend/src/pagos-prueba.js: respuesta del pago con los boletos generados.
- backend/src/app.js: registro de las rutas.
- backend/test/boletos.test.js: autenticacion, validacion de identificadores y ausencia de cache.
- backend/integration/reservas.pg.js: pruebas completas en PostgreSQL aislado.

Los bloques principales incluyen comentarios generales para facilitar la exposicion.

## Migracion y compras anteriores

La migracion 009 ya se aplico una sola vez sobre la base local, despues de 008. No volver a ejecutar las migraciones de creacion sobre esa base. No elimina compras ni reinicia saldos.

Se encontro una compra de prueba anterior y se recupero su boleto sin repetir el cargo. Estos boletos llevan datos_recuperados=true: sus datos personales y de viaje se copian al ejecutar la migracion, porque antes no existia una copia historica del boleto. No se puede asegurar que reflejen cambios anteriores a la migracion. Los boletos de compras nuevas llevan datos_recuperados=false y se copian al confirmar el pago.

Los registros se conservan exclusivamente como PRUEBA. La integracion de pagos reales necesitara otro flujo y no debe convertir estos boletos en documentos productivos. El trigger actual corresponde al simulador y debera adaptarse expresamente al incorporar esa integracion.

## Verificacion realizada — 2026-11-13

- 74 pruebas HTTP/unitarias de servidor aprobadas.
- 45 pruebas PostgreSQL/HTTP aprobadas en un esquema temporal eliminado al terminar.
- Cobertura nueva: un boleto por pasajero, codigos unicos, suma de importes, reintentos simultaneos, copias historicas, ausencia de emision para reservas no pagadas, saldo insuficiente, propietario y recuperacion sin cobrar.
- Se provoco un fallo de INSERT en boletos solo dentro del esquema de pruebas: se comprobo que revirtio el cargo, el saldo y la confirmacion, y que despues se pudo reintentar correctamente.
- Se actualizo una prueba antigua que cambiaba CONFIRMADA directamente para que utilice el flujo de pago validado.
- Migracion aplicada y servidor reiniciado por el usuario. No se efectuaron compras nuevas sobre su cuenta para probar este incremento.

Repetir las pruebas desde Git Bash dentro de backend:

```bash
npm test
npm run test:db
```

Para inspeccionar la emision en pgAdmin sin mostrar datos personales:

```sql
SELECT codigo, numero_asiento, importe_unitario, moneda, modo, estado,
       emitido_en, datos_recuperados
FROM boletos_prueba
ORDER BY emitido_en DESC;
```

El servidor local ya tiene las rutas; tras cualquier cambio posterior reiniciar con Ctrl+C y npm start. Los documentos maestros de Word no se modificaron.

Actualizacion: ya se conectaron la [confirmacion y los boletos de la compra en Android](sprint-4-confirmacion-android.md). La consulta general desde Inicio y el QR son los siguientes incrementos.
