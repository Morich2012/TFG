# Análisis y diseño de la app

Borradores de las tareas T3 (análisis) y T4 (diseño) en formato revisable en el repositorio. Los textos
largos del análisis y del diseño están en los documentos de trabajo
(análisis y
diseño); aquí está lo que el backend y la app
usarán directamente.

| Fichero | Qué es | Capítulo de la memoria |
|---|---|---|
| [`../analisis/requisitos.md`](../analisis/requisitos.md) | Requisitos y matriz de trazabilidad RF → CU → API → tablas | 3.3 |
| [`../analisis/casos-de-uso.md`](../analisis/casos-de-uso.md) | Actores, diagrama, catálogo de 29 casos de uso y casos detallados del MVP | 3.4 |
| [`../analisis/casos-de-uso.puml`](../analisis/casos-de-uso.puml) | Diagrama UML de casos de uso en PlantUML, para la memoria | 3.4 |
| [`modelo-datos.md`](modelo-datos.md) | Diagrama entidad-relación, libro contable y reglas de cálculo | 4.2 |
| [`esquema.sql`](esquema.sql) | Esquema de PostgreSQL 16 con restricciones, disparadores y datos iniciales | 4.2 |
| [`pruebas/prueba-libro.sql`](pruebas/prueba-libro.sql) | Prueba de las reglas del dinero sobre el esquema | 4.2 y 7.1 |
| [`api/openapi.yaml`](api/openapi.yaml) | Contrato de la API REST (OpenAPI 3.1, 70 operaciones) | 4.3 |
| [`api/diseno-rest.md`](api/diseno-rest.md) | Reglas de diseño de las rutas con sus fuentes, y tabla de rutas antes y ahora | 4.3 |

## Cómo verlo y comprobarlo

**Esquema y prueba del libro** (con Docker, sin instalar PostgreSQL):

```bash
cd docs/diseno
docker run -d --name tfg-db -e POSTGRES_PASSWORD=tfg -p 5432:5432 postgres:16
sleep 5   # espera a que arranque PostgreSQL
docker cp esquema.sql tfg-db:/esquema.sql
docker cp pruebas/prueba-libro.sql tfg-db:/prueba-libro.sql
docker exec tfg-db psql -U postgres -v ON_ERROR_STOP=1 -f /esquema.sql
docker exec tfg-db psql -U postgres -f /prueba-libro.sql   # termina con TODAS LAS PRUEBAS DEL LIBRO PASAN
docker rm -f tfg-db
```

**API:** abre `api/openapi.yaml` en [editor.swagger.io](https://editor.swagger.io) (Archivo → Importar)
o, con Node instalado:

```bash
npx @redocly/cli lint docs/diseno/api/openapi.yaml      # comprueba el contrato
npx @redocly/cli preview -d docs/diseno/api/openapi.yaml  # documentación navegable en localhost
```

## Del diseño al código

- `esquema.sql` se divide en migraciones de Flyway en `backend/src/main/resources/db/migration`
  (`V1__nucleo.sql` con familia, acceso, etapas y libro; `V2__correcciones_modelo.sql`; `V3__tareas.sql`; ...),
  una por iteración, para que el esquema crezca con los módulos. `V1__nucleo.sql` ya está hecha.
- `openapi.yaml` es el contrato de partida (*contract-first*). El backend publica su propia versión con
  springdoc en Swagger UI, y `ContratoApiTest` comprueba que cada operación implementada está en este
  fichero con la misma ruta, método y `operationId`.
- `prueba-libro.sql` se convertirá en pruebas de integración con Testcontainers.
