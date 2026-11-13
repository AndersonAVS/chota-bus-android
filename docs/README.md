# Documentacion de desarrollo de Chota Bus

Estado actual: demostracion local con Firebase, REST/PostgreSQL, DataStore, SQLite, mapas, reservas, saldo ficticio, boletos/QR e historial. El proyecto no esta declarado listo para produccion.

La [revision de integracion](validacion/informe-validacion.md) es el punto de entrada actual. Incluye matrices funcionales, no funcionales y de las 12 semanas, cruces de identificadores, pruebas, ajustes aprobados y pendientes priorizados. Los Word originales no se modificaron.

Guias por modulo:

- [Autenticacion y perfil](firebase-autenticacion.md)
- [DataStore](persistencia-datastore.md)
- [Busqueda REST](consulta-viajes-retrofit.md)
- [Mapas](mapa-terminal.md)
- [Reservas SQL](sprint-4-modelo-reservas.md) y [API protegida](sprint-4-api-reservas.md)
- [Asientos Android](sprint-4-android-asientos.md)
- [Pasajeros y resumen](sprint-4-pasajeros-resumen.md)
- [Saldo ficticio](sprint-4-saldo-pruebas.md)
- [Emision de boletos](sprint-4-emision-boletos.md)
- [Confirmacion Android](sprint-4-confirmacion-android.md)
- [Mis boletos y QR](mis-boletos-detalle-qr.md)
- [Historial](historial-compras.md)

Las notas de pendientes de las guias antiguas reflejan el momento del incremento. La revision central indica cuales se resolvieron y cuales siguen abiertos. No se reconstruyen ceremonias Scrum ni resultados de campo como hechos sin evidencia.
