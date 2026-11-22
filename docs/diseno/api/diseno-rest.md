# Diseño de las rutas de la API REST

Por qué las rutas de [`openapi.yaml`](openapi.yaml) tienen la forma que tienen, con la fuente de cada regla.
Sirve de base para el apartado de diseño de la API de la memoria (4.3).

## Guías de referencia

- Microsoft, *Web API design best practices* (Azure Architecture Center):
  <https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design>
- Zalando, *RESTful API and Event Guidelines*: <https://opensource.zalando.com/restful-api-guidelines/>
- Google, *API Improvement Proposals*: [AIP-121](https://google.aip.dev/121) (diseño orientado a recursos),
  [AIP-122](https://google.aip.dev/122) (nombres de recursos), [AIP-136](https://google.aip.dev/136) (métodos personalizados)
- IETF: [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110) (semántica HTTP), [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749)
  (OAuth 2.0), [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (errores *Problem Details*)

## Reglas aplicadas

| Regla | Fuente | Cómo se aplica |
|---|---|---|
| Recursos con sustantivos en plural | Zalando 134; Microsoft; AIP-122 | `/familias`, `/miembros`, `/huchas`, `/compras`... |
| Sin verbos en la URL: la acción la dice el método HTTP | Zalando 141 y 138; Microsoft | No hay `/aprobar`, `/comprar` ni `/auth/login` |
| Rutas en *kebab-case* | Zalando 129 | `/codigos-vinculacion`, `/pagos-pendientes`, `/plantillas-tarea` |
| La jerarquía refleja la pertenencia | Zalando 145; AIP-122 | Todo lo de una familia cuelga de `/familias/{familiaId}`; lo de un miembro, de `.../miembros/{miembroId}` |
| Anidar como mucho colección/elemento/colección | Microsoft; Zalando 147 | Cuentas, huchas, tareas o compras son de la familia aunque tengan dueño: `/familias/{f}/huchas/{h}`, no `/familias/{f}/miembros/{m}/huchas/{h}` |
| Una acción que se repite es un recurso nuevo | Zalando 138 | `POST .../huchas/{h}/aportaciones`, `POST .../asignaciones/{a}/entregas`, `POST .../asignaciones/{a}/revisiones` |
| Un cambio de estado que ocurre una vez es un subrecurso único, y se fija con `PUT` | Zalando 138; RFC 9110 §9.2.2 (idempotencia) | `PUT .../huchas/{h}/bloqueo`, `PUT .../compras/{c}/entrega`, `PUT .../pagos-pendientes/{p}/pago`. Si el móvil repite la petición tras un corte de red, no pasa dos veces |
| Iniciar sesión es crear una sesión | Microsoft; RFC 6749 §4 | `POST /sesiones` con `tipo` (`CONTRASENA`, `TOKEN_REFRESCO`, `CODIGO_VINCULACION`, `CODIGO_INVITACION`), como los *grant types* de OAuth 2.0 en un único endpoint de token. `GET`, `PATCH` y `DELETE /sesiones/actual` |
| `POST` que crea responde 201 con `Location` | RFC 9110 §15.3.2; Microsoft | `POST /familias` → `Location: /api/v1/familias/{id}` |
| Un recurso ajeno responde 404, no 403 | RNF-03 (no revelar que existe) | Si `{familiaId}` no es la familia del token, 404 |
| Errores con *Problem Details* | RFC 9457; Zalando 176 | Cuerpo `application/problem+json` con un campo `codigo` estable |

**Por qué no métodos personalizados `:verbo`.** Google (AIP-136) admite `POST /recurso:aprobar` cuando una
operación no encaja en un recurso. Aquí todas encajan como subcolección o subrecurso, y los dos puntos en la ruta
complican el enrutado y la documentación, así que no se usan.

**Por qué la familia va en la ruta aunque ya está en el token.** La URL identifica el recurso sin depender de
quién pregunta (AIP-122), se puede enlazar y cachear, y deja abierto que un adulto pertenezca a más de una
familia en el futuro. El backend lo comprueba en un único sitio, `ComprobadorFamilia`, antes de llegar a
cualquier controlador.

## Todas las rutas, antes y ahora

Prefijo común: `/api/v1`. «(nueva)» es una operación que no existía.

| Etiqueta | Ahora | Antes |
|---|---|---|
| Acceso | `POST /familias` | `POST /auth/registro` |
| Acceso | `POST /sesiones` | `POST /auth/login`<br>`POST /auth/refrescar`<br>`POST /auth/vincular`<br>`POST /auth/unirse` |
| Acceso | `GET /sesiones/actual` | (nueva) |
| Acceso | `PATCH /sesiones/actual` | `PUT /dispositivos/actual/token-push` |
| Acceso | `DELETE /sesiones/actual` | `POST /auth/cerrar-sesion` |
| Familia | `GET /familias/{familiaId}` | `GET /familia` |
| Familia | `PATCH /familias/{familiaId}` | `PATCH /familia` |
| Familia | `GET /familias/{familiaId}/miembros` | `GET /familia/miembros` |
| Familia | `POST /familias/{familiaId}/miembros` | `POST /familia/miembros` |
| Familia | `GET /familias/{familiaId}/miembros/{miembroId}` | (nueva) |
| Familia | `PATCH /familias/{familiaId}/miembros/{miembroId}` | `PATCH /familia/miembros/{miembroId}` |
| Familia | `DELETE /familias/{familiaId}/miembros/{miembroId}` | `DELETE /familia/miembros/{miembroId}` |
| Familia | `POST /familias/{familiaId}/miembros/{miembroId}/codigos-vinculacion` | `POST /familia/miembros/{miembroId}/codigo-vinculacion` |
| Familia | `GET /familias/{familiaId}/miembros/{miembroId}/dispositivos` | `GET /familia/miembros/{miembroId}/dispositivos` |
| Familia | `DELETE /familias/{familiaId}/miembros/{miembroId}/dispositivos/{dispositivoId}` | `DELETE /dispositivos/{dispositivoId}` |
| Familia | `POST /familias/{familiaId}/invitaciones` | `POST /familia/invitaciones` |
| Familia | `GET /familias/{familiaId}/resumen` | `GET /familia/resumen` |
| Progreso | `GET /familias/{familiaId}/miembros/{miembroId}/progreso` | `GET /miembros/{miembroId}/progreso` |
| Progreso | `GET /familias/{familiaId}/miembros/{miembroId}/eventos-experiencia` | `GET /miembros/{miembroId}/experiencia` |
| Progreso | `GET /familias/{familiaId}/miembros/{miembroId}/mecanicas` | `GET /miembros/{miembroId}/mecanicas` |
| Progreso | `PUT /familias/{familiaId}/miembros/{miembroId}/mecanicas/{codigo}` | `PUT /miembros/{miembroId}/mecanicas/{codigo}` |
| Progreso | `PUT /familias/{familiaId}/miembros/{miembroId}/etapa` | `PUT /miembros/{miembroId}/etapa` |
| Progreso | `GET /familias/{familiaId}/hitos` | `GET /familia/hitos` |
| Cuentas | `GET /familias/{familiaId}/cuentas` | `GET /cuentas` |
| Cuentas | `GET /familias/{familiaId}/cuentas/{cuentaId}` | `GET /cuentas/{cuentaId}` |
| Cuentas | `GET /familias/{familiaId}/cuentas/{cuentaId}/movimientos` | `GET /cuentas/{cuentaId}/movimientos` |
| Cuentas | `POST /familias/{familiaId}/transferencias` | `POST /transferencias` |
| Cuentas | `POST /familias/{familiaId}/ajustes` | `POST /ajustes` |
| Cuentas | `PUT /familias/{familiaId}/movimientos/{movimientoId}/correccion` | `POST /movimientos/{movimientoId}/correccion` |
| Huchas | `POST /familias/{familiaId}/huchas` | `POST /huchas` |
| Huchas | `PATCH /familias/{familiaId}/huchas/{huchaId}` | `PATCH /huchas/{cuentaId}` |
| Huchas | `DELETE /familias/{familiaId}/huchas/{huchaId}` | `DELETE /huchas/{cuentaId}` |
| Huchas | `POST /familias/{familiaId}/huchas/{huchaId}/aportaciones` | `POST /huchas/{cuentaId}/aportar` |
| Huchas | `POST /familias/{familiaId}/huchas/{huchaId}/retiradas` | `POST /huchas/{cuentaId}/retirar` |
| Huchas | `PUT /familias/{familiaId}/huchas/{huchaId}/bloqueo` | `POST /huchas/{cuentaId}/bloquear` |
| Reglas | `GET /familias/{familiaId}/miembros/{miembroId}/paga` | `GET /miembros/{miembroId}/paga` |
| Reglas | `PUT /familias/{familiaId}/miembros/{miembroId}/paga` | `PUT /miembros/{miembroId}/paga` |
| Reglas | `DELETE /familias/{familiaId}/miembros/{miembroId}/paga` | `DELETE /miembros/{miembroId}/paga` |
| Reglas | `GET /familias/{familiaId}/reglas/interes` | `GET /reglas/interes` |
| Reglas | `PUT /familias/{familiaId}/reglas/interes` | `PUT /reglas/interes` |
| Reglas | `GET /familias/{familiaId}/reglas/inflacion` | `GET /reglas/inflacion` |
| Reglas | `PUT /familias/{familiaId}/reglas/inflacion` | `PUT /reglas/inflacion` |
| Tareas | `GET /plantillas-tarea` | `GET /plantillas/tareas` |
| Tareas | `GET /familias/{familiaId}/tareas` | `GET /tareas` |
| Tareas | `POST /familias/{familiaId}/tareas` | `POST /tareas` |
| Tareas | `PATCH /familias/{familiaId}/tareas/{tareaId}` | `PATCH /tareas/{tareaId}` |
| Tareas | `DELETE /familias/{familiaId}/tareas/{tareaId}` | `DELETE /tareas/{tareaId}` |
| Tareas | `GET /familias/{familiaId}/asignaciones` | `GET /asignaciones` |
| Tareas | `POST /familias/{familiaId}/asignaciones/{asignacionId}/entregas` | `POST /asignaciones/{asignacionId}/hecha` |
| Tareas | `POST /familias/{familiaId}/asignaciones/{asignacionId}/revisiones` | `POST /asignaciones/{asignacionId}/aprobar`<br>`POST /asignaciones/{asignacionId}/rechazar` |
| Tienda | `GET /familias/{familiaId}/categorias` | `GET /categorias` |
| Tienda | `GET /plantillas-recompensa` | `GET /plantillas/recompensas` |
| Tienda | `GET /familias/{familiaId}/recompensas` | `GET /recompensas` |
| Tienda | `POST /familias/{familiaId}/recompensas` | `POST /recompensas` |
| Tienda | `PATCH /familias/{familiaId}/recompensas/{recompensaId}` | `PATCH /recompensas/{recompensaId}` |
| Tienda | `DELETE /familias/{familiaId}/recompensas/{recompensaId}` | `DELETE /recompensas/{recompensaId}` |
| Tienda | `GET /familias/{familiaId}/recompensas/{recompensaId}/precios` | `GET /recompensas/{recompensaId}/precios` |
| Tienda | `POST /familias/{familiaId}/compras` | `POST /recompensas/{recompensaId}/comprar` |
| Tienda | `GET /familias/{familiaId}/compras` | `GET /compras` |
| Tienda | `PUT /familias/{familiaId}/compras/{compraId}/entrega` | `POST /compras/{compraId}/entregar` |
| Tienda | `PUT /familias/{familiaId}/compras/{compraId}/cancelacion` | `POST /compras/{compraId}/cancelar` |
| Tienda | `POST /familias/{familiaId}/canjes` | `POST /canjes` |
| Tienda | `GET /familias/{familiaId}/pagos-pendientes` | `GET /pagos-pendientes` |
| Tienda | `PUT /familias/{familiaId}/pagos-pendientes/{pagoId}/pago` | `POST /pagos-pendientes/{pagoId}/pagado` |
| Presupuesto | `GET /familias/{familiaId}/miembros/{miembroId}/presupuestos/{semana}` | `GET /miembros/{miembroId}/presupuestos/{semana}` |
| Presupuesto | `PUT /familias/{familiaId}/miembros/{miembroId}/presupuestos/{semana}` | `PUT /miembros/{miembroId}/presupuestos/{semana}` |
| Estadísticas | `GET /familias/{familiaId}/miembros/{miembroId}/estadisticas` | `GET /miembros/{miembroId}/estadisticas` |
| Cuestionarios | `GET /cuestionarios/{codigo}` | `GET /cuestionarios/{codigo}` |
| Cuestionarios | `POST /familias/{familiaId}/miembros/{miembroId}/intentos-cuestionario` | `POST /cuestionarios/{codigo}/intentos` |
| Cuestionarios | `GET /familias/{familiaId}/resultados-cuestionarios` | `GET /cuestionarios/resultados` |
