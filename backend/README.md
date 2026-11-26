# Backend · API de la economía familiar

Spring Boot 4.1 (Java 21) + PostgreSQL 16. Implementa el contrato
[`docs/diseno/api/openapi.yaml`](../docs/diseno/api/openapi.yaml) por iteraciones.

**Iteración 1 (9-22 nov):** esqueleto, esquema núcleo con Flyway, CI, y familia, perfiles, inicio de sesión
y roles: 16 operaciones, todas las de *Acceso* y *Familia* salvo el resumen.

**Iteración 2 (23 nov-6 dic):** tareas y saldo. Crear, editar y archivar tareas, el ciclo de cada
asignación (el hijo la entrega, un adulto la aprueba y se paga, o la rechaza con un motivo), las cuentas
con su historial, los ajustes manuales, las correcciones, las categorías y el resumen de la familia.
Suma 15 operaciones: hay **31 de las 70** del contrato. Las transferencias entre hermanos (RF-13) quedan
para cuando estén las mecánicas por nivel.

## Cómo ejecutarlo

Necesitas **Java 21** y **Docker** (Docker Desktop en Windows/macOS). No hace falta instalar Maven ni
PostgreSQL: el *wrapper* `mvnw` descarga Maven y Docker pone la base de datos.

```bash
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Spring Boot arranca solo el PostgreSQL de [`compose.yaml`](compose.yaml), Flyway crea las tablas y la API
queda en `http://localhost:8080/api/v1`.

- Documentación interactiva: <http://localhost:8080/swagger-ui.html> (botón *Authorize* para pegar el
  `tokenAcceso`).
- Salud: <http://localhost:8080/actuator/health>
- Datos: `docker compose exec postgres psql -U tfg tfg` (los datos se guardan en el volumen `tfg-datos`;
  `docker compose down -v` los borra).

Prueba rápida con `curl`:

```bash
# 1. Registrar la familia (crea la familia y su adulto administrador, y abre sesión)
curl -i -X POST localhost:8080/api/v1/familias -H 'Content-Type: application/json' \
  -d '{"nombreFamilia":"Familia Morales","nombre":"Miguel","email":"miguel@example.org",
       "contrasena":"contrasena-segura","aceptaPrivacidad":true}'
# 201 Created, cabecera Location: /api/v1/familias/1, y en el cuerpo tokenAcceso y familiaId

# 2. Más tarde, iniciar sesión es crear una sesión
curl -X POST localhost:8080/api/v1/sesiones -H 'Content-Type: application/json' \
  -d '{"tipo":"CONTRASENA","email":"miguel@example.org","contrasena":"contrasena-segura"}'

# 3. Usar la API con el token
curl localhost:8080/api/v1/familias/1/miembros -H "Authorization: Bearer <tokenAcceso>"
```

Si el puerto 5432 ya lo usa un PostgreSQL instalado en el equipo, arranca con otro puerto:
`BD_PUERTO=5433 ./mvnw spring-boot:run` (en PowerShell: `$env:BD_PUERTO=5433; .\mvnw.cmd spring-boot:run`).

Desde IntelliJ: abre la carpeta `backend` como proyecto Maven y ejecuta `TfgApplication` (con Docker
abierto).

## Pruebas

```bash
./mvnw verify
```

Las pruebas arrancan un PostgreSQL 16 real en un contenedor (Testcontainers), aplican las migraciones y
prueban la API de punta a punta con MockMvc. GitHub Actions las pasa en cada push y pull request que toque
`backend/` o `docs/diseno/` ([`.github/workflows/backend.yml`](../.github/workflows/backend.yml)).

| Prueba | Qué comprueba |
|---|---|
| `AccesoTest` | Registro, login, rotación del token de refresco, cierre de sesión, vinculación con QR de un solo uso, invitación del segundo adulto, límite de intentos de PIN, permisos por rol |
| `FamiliaTest` | Editar familia, alta de hijo (monedero, solo año de nacimiento, consentimiento), aislamiento entre familias (404), reglas de edición, baja con anonimización, revocar dispositivo |
| `ServicioEtapasTest` | Etapa según edad y nivel, etapa fijada por los padres |
| `LibroContableTest` | Reglas del dinero en la base de datos: saldos por disparador, sin saldo negativo, libro de solo inserción, saldo protegido, sentido de cada tipo, correcciones completas, titular de la misma familia, borrado de una familia entera y solo de la marcada |
| `CuentasTest` | Quién ve qué cuentas, ajustes que ingresan o retiran sin dejar saldo negativo, `Idempotency-Key`, historial paginado con saldo tras cada movimiento, correcciones una sola vez, categorías |
| `TareasTest` | Plantillas, asignaciones creadas al consultar el día (diarias, semanales, únicas), entrega, aprobación que paga una sola vez, rechazo con motivo, caducidad, edición sin cambiar lo pactado, archivado, permisos, resumen, borrar una familia con tareas pagadas |
| `ContratoApiTest` | Cada operación implementada existe en `openapi.yaml` con la misma ruta, método y `operationId` |

## Dónde está cada cosa

```
backend/
├── pom.xml                       dependencias (Spring Boot 4.1.2, springdoc, Testcontainers)
├── compose.yaml                  PostgreSQL 16 para desarrollo
├── src/main/resources/
│   ├── application.yaml          configuración (base de datos, JWT, duraciones)
│   └── db/migration/             V1 núcleo, V2 revisión del modelo, V3 tareas (de docs/diseno/esquema.sql)
└── src/main/java/es/upm/tfg/     un paquete por módulo (package-by-feature)
    ├── comun/                    errores RFC 9457 con «codigo», hashes, reloj, springdoc
    ├── seguridad/                Spring Security, emisión y validación del JWT, UsuarioActual
    ├── familia/                  familia, miembros, etapas  (ControladorFamilia)
    ├── acceso/                   registro, sesiones (contraseña, refresco, QR, invitación), dispositivos
    ├── cuentas/                  cuentas, libro (ServicioLibro), historial, ajustes, correcciones
    └── tareas/                   tareas, plantillas, asignaciones, entregas y revisiones
```

Las migraciones crecen con las iteraciones: `V2__correcciones_modelo.sql` aplica la revisión del modelo de
datos a las tablas de V1, `V3__tareas.sql` crea las de tareas y limita `borrar_familia` a la familia marcada,
y después vendrá `V4__tienda.sql`. Una migración ya aplicada no se edita; los cambios van en una nueva.

## Diseño de las rutas

La API se organiza en recursos con sustantivos en plural, anidados como máximo colección/elemento/colección,
siguiendo las guías de [Microsoft](https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design),
[Zalando](https://opensource.zalando.com/restful-api-guidelines/) y [Google](https://google.aip.dev/122):

| Recurso | Rutas |
|---|---|
| Familia (raíz) | `POST /familias` (registro), `GET/PATCH /familias/{familiaId}` |
| Miembros | `GET/POST /familias/{familiaId}/miembros`, `GET/PATCH/DELETE /familias/{familiaId}/miembros/{miembroId}` |
| Sesiones | `POST /sesiones` (login, refresco, QR o invitación según `tipo`), `GET/PATCH/DELETE /sesiones/actual` |
| Datos de un miembro | `/familias/{familiaId}/miembros/{miembroId}/progreso`, `/paga`, `/presupuestos/{semana}`... |

- **Sin verbos en la URL.** Una acción que se repite es un recurso nuevo en una subcolección
  (`POST .../huchas/{huchaId}/aportaciones`, `POST .../asignaciones/{asignacionId}/revisiones`); un cambio
  de estado que ocurre una vez es un subrecurso único que se fija con `PUT` (`PUT .../compras/{compraId}/entrega`).
- **Iniciar sesión es crear una sesión.** Las cuatro formas de entrar comparten `POST /sesiones` y se
  distinguen por `tipo`, igual que los *grant types* de OAuth 2.0 (RFC 6749) en un único endpoint de token.
- **La familia va en la ruta y se comprueba contra el token.** Si `{familiaId}` no es la familia del token,
  la respuesta es 404. Lo hace un único interceptor, `seguridad/ComprobadorFamilia`, antes de cualquier
  controlador, así que ninguna ruta nueva puede olvidarlo.
- **`POST` que crea responde 201 con cabecera `Location`.**

La justificación completa, con la regla concreta de cada guía y la tabla de rutas antes y ahora, está en
[`docs/diseno/api/diseno-rest.md`](../docs/diseno/api/diseno-rest.md).

## Decisiones de la iteración 1

- **Autenticación.** El adulto entra con correo y contraseña (BCrypt). El hijo no tiene correo: su móvil
  canjea un código QR de un solo uso que caduca a los 10 minutos. Cada sesión es una fila de `dispositivo`
  con un **token de refresco** opaco (solo se guarda su SHA-256) que se rota en cada uso, y un **token de
  acceso JWT** de 15 minutos firmado con HMAC-SHA256. Spring Security valida el JWT como servidor de recursos
  OAuth2; no hay sesión en el servidor.
- **Roles.** El JWT lleva el miembro, su familia y su rol (`ADULTO_ADMIN`, `ADULTO`, `HIJO`). Las rutas se
  protegen con `@PreAuthorize` según la columna `x-roles` del contrato. Un adulto edita a los hijos y a sí
  mismo; solo el administrador edita a otros adultos, invita y da de baja.
- **Aislamiento entre familias (RNF-03).** Toda consulta filtra por la familia del token. Un id de otra
  familia responde 404, no 403, para no revelar que existe.
- **Datos del menor (RNF-05, RNF-06).** Del hijo solo se guardan nombre o apodo, avatar, año de nacimiento
  y hash del PIN. La baja borra esos datos pero conserva la fila, porque los movimientos del libro apuntan a
  ella y el libro es de solo inserción.
- **PIN.** Se comprueba al refrescar la sesión si el hijo entra con PIN en vez de huella. Tras 5 fallos el
  dispositivo queda bloqueado 15 minutos. El contador está en memoria (suficiente con un servidor).
- **Saldos.** Los mantiene el disparador de la base de datos; la entidad `Cuenta` no puede escribir el saldo.

**Limitaciones conocidas** (para iteraciones siguientes o el capítulo de trabajo futuro): un JWT ya emitido
sigue valiendo hasta 15 minutos después de revocar su dispositivo; los tokens de refresco no caducan por
inactividad; la clave JWT de `application.yaml` es solo de desarrollo (en producción, variable
`JWT_SECRETO`).

## Decisiones de la iteración 2

- **Un único punto mueve dinero.** `cuentas/ServicioLibro` inserta los movimientos; ningún módulo toca
  saldos. Comprueba antes el saldo para dar un mensaje claro ("Faltan 5 monedas"), y si dos operaciones
  llegan a la vez, la restricción `saldo_no_negativo` de la base de datos rechaza la segunda y la API
  responde igualmente `409 SALDO_INSUFICIENTE`.
- **Escrituras con JPA, lecturas con SQL.** Las entidades cambian estado; los listados (historial con saldo
  acumulado, resumen) son consultas SQL con `JdbcClient`, porque usan ventanas y sumas.
- **Asignaciones bajo demanda.** Las de las tareas diarias y semanales se crean al consultar el día de hoy,
  con `INSERT ... ON CONFLICT DO NOTHING` para que dos consultas a la vez no las dupliquen; no hay proceso
  nocturno. La de una tarea única se crea al crearla y sigue abierta hasta su fecha límite. Una pendiente
  caduca al acabar su día. "Hoy" se calcula con la zona horaria de la familia.
- **Lo pactado se respeta.** La asignación guarda la recompensa del día en que se creó; editar la tarea
  solo afecta a las siguientes. La periodicidad no se cambia: para eso se crea otra tarea. `PATCH` cambia
  solo los campos que vienen.
- **Revisión.** La fila de la asignación se bloquea (`SELECT ... FOR UPDATE`): si dos adultos aprueban a
  la vez, el segundo recibe 409 y solo se paga una vez. Rechazar exige un comentario y devuelve la tarea a
  PENDIENTE; cada revisión queda en `revision_tarea`. Aprobar publica el evento `TareaAprobada`, que en la
  iteración 4 escucharán la experiencia y los logros.
- **Idempotencia.** Ajustes y aprobaciones aceptan la cabecera `Idempotency-Key`: si la app repite la
  petición, devuelve el resultado ya hecho sin cobrar dos veces.
- **El banco no se lista por defecto.** Su saldo negativo (las monedas en circulación) confunde; un adulto
  lo ve con `?tipo=BANCO`.
- **`borrar_familia` solo borra la familia marcada.** Antes bastaba un `SET tfg.borrando_familia = 'si'`
  para borrar movimientos de cualquier familia. Ahora la marca es el id de la familia. Las FK de autoría de
  las tablas nuevas son diferibles, porque si no la cascada fallaba con tareas ya revisadas.

**Limitaciones de la iteración 2:** corregir el pago de una tarea no la desaprueba (queda APROBADA con su
corrección en el historial); `comprasPorEntregar` y `totalDebidoCentimos` del resumen valen 0 hasta la
iteración 3; la aplicación usa un único usuario de base de datos, dueño de las tablas, que podría
desactivar los disparadores (cerrarlo pide un segundo usuario sin ese permiso).
