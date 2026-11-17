# Requisitos y trazabilidad

Borrador de la tarea T3 (capítulo 3.3 de la memoria). Los textos de los requisitos son los del
[análisis](https://claude.ai/artifact/HSPzCVVJpDacbcAipTeD48); lo nuevo aquí es la **matriz de
trazabilidad**, que enlaza cada requisito con su caso de uso ([casos-de-uso.md](casos-de-uso.md)), sus
operaciones de la [API](../diseno/api/openapi.yaml) y sus tablas del
[modelo de datos](../diseno/modelo-datos.md). Al implementar se añadirá una columna con las pruebas.

Prioridad MoSCoW e iteración según las historias de usuario del análisis.

## Requisitos funcionales del MVP

| RF | Requisito | Prior. | CU | API | Tablas |
|---|---|---|---|---|---|
| RF-01 | Un adulto puede registrarse con correo y contraseña y crear una familia | Must | CU-01, CU-05 | `POST /auth/registro`, `POST /auth/login` | familia, miembro, cuenta |
| RF-02 | El administrador puede invitar a un segundo adulto con un código | Could | CU-02 | `POST /familia/invitaciones`, `POST /auth/unirse` | codigo_acceso |
| RF-03 | Un adulto puede dar de alta a un hijo con nombre, avatar y PIN, sin correo | Must | CU-03 | `POST /familia/miembros` | miembro, cuenta |
| RF-04 | Un hijo puede entrar con su PIN o vinculando su móvil con un código QR que genera un adulto | Must | CU-04, CU-05 | `POST /familia/miembros/{id}/codigo-vinculacion`, `POST /auth/vincular`, `POST /auth/refrescar` | codigo_acceso, dispositivo |
| RF-05 | Un adulto puede editar o dar de baja a un miembro de la familia | Must | CU-03 | `PATCH`, `DELETE /familia/miembros/{id}` | miembro, dispositivo |
| RF-06 | Un adulto puede crear tareas puntuales o periódicas con recompensa, asignadas a uno o varios hijos | Must | CU-07 | `POST /tareas` | tarea, tarea_hijo |
| RF-07 | Un hijo puede marcar una tarea como hecha | Must | CU-08 | `POST /asignaciones/{id}/hecha` | asignacion_tarea |
| RF-08 | Un adulto puede aprobar o rechazar una tarea hecha; al aprobarla se ingresa la recompensa | Must | CU-09 | `POST /asignaciones/{id}/aprobar`, `/rechazar` | asignacion_tarea, movimiento |
| RF-09 | Cada hijo tiene un monedero con su saldo, calculado a partir de sus movimientos | Must | CU-10 | `GET /cuentas` | cuenta, vista cuenta_cuadre |
| RF-10 | Toda operación genera un movimiento con fecha, tipo, cantidad, concepto y autor, que no se puede modificar ni borrar | Must | CU-10 | `GET /cuentas/{id}/movimientos` | movimiento (disparador de inmutabilidad) |
| RF-11 | Un adulto puede configurar una paga periódica por hijo y su reparto entre monedero y ahorro | Should | CU-12, CU-13 | `PUT /miembros/{id}/paga` | regla_paga, liquidacion |
| RF-12 | Un adulto puede hacer ingresos o retiradas manuales justificadas | Should | CU-11 | `POST /ajustes`, `POST /movimientos/{id}/correccion` | movimiento |
| RF-13 | Un hijo puede transferir dinero a un hermano | Could | CU-14 | `POST /transferencias` | movimiento |
| RF-14 | Un adulto puede publicar recompensas con nombre, precio y existencias | Must | CU-15 | `POST /recompensas` | recompensa, precio_recompensa |
| RF-15 | Un hijo puede comprar una recompensa si tiene saldo; el adulto confirma la entrega | Must | CU-16, CU-17 | `POST /recompensas/{id}/comprar`, `POST /compras/{id}/entregar` | compra, movimiento |
| RF-16 | Cada gasto se clasifica en una categoría | Must | CU-10, CU-16 | `GET /categorias` | categoria, movimiento.categoria_id |
| RF-17 | Un adulto puede activar la inflación en la tienda con un porcentaje mensual | Should | CU-22, CU-23 | `PUT /reglas/inflacion`, `GET /recompensas/{id}/precios` | regla_inflacion, precio_recompensa |
| RF-18 | Un hijo puede crear huchas con nombre, meta y fecha opcional, y mover dinero entre el monedero y sus huchas | Must | CU-20 | `POST /huchas`, `/aportar`, `/retirar` | cuenta (HUCHA), movimiento |
| RF-19 | Un adulto puede fijar un tipo de interés; el sistema lo liquida periódicamente sobre el saldo de las huchas | Must | CU-22, CU-23 | `PUT /reglas/interes` | regla_interes, liquidacion |
| RF-20 | Un hijo puede bloquear una hucha hasta su fecha a cambio de un extra | Could | CU-21 | `POST /huchas/{id}/bloquear` | cuenta.bloqueada_hasta |
| RF-21 | Un hijo puede definir un presupuesto semanal por categorías; la app avisa al llegar al 80 % y al superarlo | Should | CU-24, CU-25 | `PUT /miembros/{id}/presupuestos/{semana}` | presupuesto, partida_presupuesto |
| RF-22 | El sistema otorga experiencia por comportamientos financieros y calcula el nivel | Must | CU-26, CU-27 | `GET /miembros/{id}/progreso`, `/experiencia` | evento_experiencia, miembro |
| RF-23 | El sistema concede logros automáticamente al cumplirse sus condiciones | Must | CU-26, CU-27 | `GET /miembros/{id}/progreso` | logro, logro_miembro |
| RF-24 | Un adulto puede proponer retos semanales y el hijo aceptarlos | Should | — | Pendiente (iteración 4) | Pendiente |
| RF-25 | Las mecánicas avanzadas se desbloquean al alcanzar ciertos niveles | Must | CU-06, CU-26 | `GET /miembros/{id}/mecanicas` | mecanica, mecanica_miembro |
| RF-26 | Cada miembro puede ver la evolución de su saldo, sus gastos por categoría y su ahorro en gráficas | Must | CU-28 | `GET /miembros/{id}/estadisticas` | movimiento |
| RF-27 | Un adulto puede ver un resumen de cada hijo | Must | CU-28 | `GET /familia/resumen` | cuenta, asignacion_tarea, compra, pago_pendiente |
| RF-28 | La app avisa de tareas pendientes de aprobar, objetivos cumplidos, logros y límites de presupuesto | Could | CU-08, CU-16, CU-25, CU-26 | `PUT /dispositivos/actual/token-push` | dispositivo.token_push |
| RF-34 | Las mecánicas se agrupan en etapas que se desbloquean por edad mínima y nivel; los padres pueden adelantarlas o retrasarlas | Must | CU-06 | `PUT /miembros/{id}/etapa`, `PUT /miembros/{id}/mecanicas/{codigo}` | etapa, miembro.etapa_fijada |
| RF-35 | Un adulto puede fijar el tipo de cambio entre monedas y euros, e introducir precios en euros que la app convierte | Must | CU-15 | `PATCH /familia`, `POST /recompensas` (`precioEuros`) | familia.monedas_por_euro |
| RF-36 | Cuando un hijo canjea monedas por euros o compra una recompensa de dinero real, se crea para los padres un pago pendiente que marcan como pagado | Must | CU-16, CU-18, CU-19 | `POST /canjes`, `GET /pagos-pendientes`, `POST /pagos-pendientes/{id}/pagado` | pago_pendiente |
| RF-37 | La app ofrece plantillas de tareas y de recompensas | Should | CU-07, CU-15 | `GET /plantillas/tareas`, `GET /plantillas/recompensas` | plantilla_tarea, plantilla_recompensa |
| RF-43 | La pestaña de hitos muestra los logros de cada miembro, los comunes y quién falta | Should | CU-27 | `GET /familia/hitos` | logro_miembro |
| RF-44 | La app permite hacer un cuestionario de conocimientos al empezar y repetirlo después | Must | CU-29 | `GET /cuestionarios/{codigo}`, `POST /cuestionarios/{codigo}/intentos` | cuestionario, pregunta, intento_cuestionario |

**Fuera del MVP** (diseñados, se implementan si da tiempo antes del 3 de enero o quedan como trabajo
futuro): RF-29 a RF-33 y RF-45, RF-49 a RF-51 (Aprende), RF-38 y RF-52 (préstamos), RF-39 (oferta y
demanda), RF-40 y RF-53 (Estado familiar), RF-41 (servicios), RF-42 (personalización), RF-46 a RF-48
(clan).

**Hueco detectado:** RF-24 (retos semanales, Should) no tiene todavía ni tablas ni API. Propongo
dejarlo para la iteración 4 y diseñarlo entonces junto a la experiencia, porque un reto es un logro
temporal que el hijo acepta.

## Requisitos no funcionales

| RNF | Tipo | Requisito | Cómo se cumple en el diseño |
|---|---|---|---|
| RNF-01 | Seguridad | Autenticación con JWT y contraseñas con hash robusto | JWT de 15 min + token de refresco opaco rotado y guardado con hash; BCrypt para contraseñas y PIN |
| RNF-02 | Seguridad | Autorización por rol | `x-roles` en cada operación de la API; comprobación en Spring Security |
| RNF-03 | Seguridad | Aislamiento entre familias | `familia_id` en todas las tablas de datos; el disparador del libro rechaza movimientos entre familias; un id ajeno responde 404 |
| RNF-04 | Seguridad | HTTPS | Proxy inverso con TLS en el despliegue |
| RNF-05 | Privacidad | Del menor solo nombre, avatar y edad | `miembro` guarda solo `anio_nacimiento`, sin correo ni teléfono (restricción `hijo_sin_correo`) |
| RNF-06 | Privacidad | Consentimiento del adulto y borrado | `consentimiento_en` obligatorio en el alta; baja que anonimiza el miembro |
| RNF-07 | Integridad | Cantidades enteras | `BIGINT` en monedas y céntimos; interés y conversiones redondean hacia abajo |
| RNF-08 | Integridad | Ningún saldo negativo; operaciones transaccionales | Restricción `saldo_no_negativo` y bloqueo de filas en el disparador (probado con dos compras simultáneas) |
| RNF-09 | Trazabilidad | Historial de solo inserción | Disparador que impide `UPDATE` y `DELETE` en `movimiento` y `evento_experiencia`; correcciones enlazadas |
| RNF-10 | Usabilidad | Modo hijo con acciones en tres pasos como máximo | Diseño de pantallas (T4) |
| RNF-11 | Usabilidad | SUS ≥ 68 | Prueba con familias (T10) |
| RNF-12 | Rendimiento | Respuesta < 1 s | Saldo cacheado en `cuenta` e índices por cuenta y fecha |
| RNF-13 | Compatibilidad | Android 8.0 (API 26) | Decisión técnica de la app |
| RNF-14 | Mantenibilidad | Pruebas automáticas e integración continua | Pruebas del libro en SQL ([`prueba-libro.sql`](../diseno/pruebas/prueba-libro.sql)); Testcontainers y GitHub Actions en el backend |
| RNF-15 | Portabilidad | Despliegue con Docker | Docker Compose con backend y PostgreSQL |
