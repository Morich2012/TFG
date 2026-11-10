-- =============================================================================
-- Esquema de la base de datos (PostgreSQL 16) · borrador de diseño T4
--
-- Cubre el núcleo y los módulos del MVP (historias Must y Should de las
-- iteraciones 1 a 5). Los módulos posteriores (préstamos, Estado familiar,
-- clan, Aprende, personalización) se añadirán con sus propias migraciones.
--
-- En el backend este fichero se dividirá en migraciones de Flyway
-- (V1__nucleo.sql, V2__tareas.sql, ...). Aquí está junto para poder revisarlo
-- y probarlo de una vez:  psql -v ON_ERROR_STOP=1 -f esquema.sql
--
-- Convenciones:
--   * Cantidades de monedas: BIGINT (RNF-07). Euros: BIGINT en céntimos.
--   * Fechas y horas: TIMESTAMPTZ; los periodos (semana, mes) se calculan en
--     Europe/Madrid.
--   * Toda tabla con datos de una familia lleva familia_id para filtrar cada
--     consulta por la familia del token (RNF-03).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Familias, miembros y acceso
-- -----------------------------------------------------------------------------

CREATE TABLE familia (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre                  VARCHAR(60)  NOT NULL,
    nombre_moneda           VARCHAR(30)  NOT NULL DEFAULT 'monedas',
    -- Cuántas monedas equivalen a 1 euro (RF-35). Ej.: 10 -> 10 monedas = 1 €.
    monedas_por_euro        INTEGER      NOT NULL DEFAULT 10 CHECK (monedas_por_euro > 0),
    creada_en               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE miembro (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    rol                     VARCHAR(20)  NOT NULL CHECK (rol IN ('ADULTO_ADMIN', 'ADULTO', 'HIJO')),
    nombre                  VARCHAR(40)  NOT NULL,          -- nombre o apodo (RNF-05)
    avatar                  VARCHAR(40)  NOT NULL DEFAULT 'avatar-1',
    -- Solo adultos: credenciales de acceso (RF-01, RNF-01).
    email                   VARCHAR(254) UNIQUE,
    hash_contrasena         VARCHAR(100),
    -- Solo hijos: año de nacimiento, para abrir etapas por edad (RF-34).
    -- Se guarda solo el año por minimización de datos (RNF-05).
    anio_nacimiento         SMALLINT     CHECK (anio_nacimiento BETWEEN 1990 AND 2100),
    -- Solo hijos: PIN de 4-6 cifras con hash, para entrar en un dispositivo
    -- compartido o si falla la huella (RF-04).
    hash_pin                VARCHAR(100),
    -- Gamificación (RF-22). Se recalculan desde evento_experiencia.
    experiencia             INTEGER      NOT NULL DEFAULT 0 CHECK (experiencia >= 0),
    nivel                   SMALLINT     NOT NULL DEFAULT 1 CHECK (nivel >= 1),
    -- Los padres pueden fijar la etapa a mano (RF-34). NULL = automática.
    etapa_fijada            SMALLINT     CHECK (etapa_fijada BETWEEN 1 AND 5),
    consentimiento_en       TIMESTAMPTZ,                    -- RNF-06, lo da el adulto al dar de alta
    creado_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    dado_de_baja_en         TIMESTAMPTZ,
    CONSTRAINT adulto_con_credenciales CHECK (
        rol = 'HIJO' OR (email IS NOT NULL AND hash_contrasena IS NOT NULL)),
    CONSTRAINT hijo_sin_correo CHECK (
        rol <> 'HIJO' OR (email IS NULL AND hash_contrasena IS NULL))
);
CREATE INDEX miembro_familia_idx ON miembro(familia_id);
-- Solo un administrador por familia.
CREATE UNIQUE INDEX miembro_un_admin_idx ON miembro(familia_id) WHERE rol = 'ADULTO_ADMIN';

-- Dispositivo vinculado de un hijo (o sesión de un adulto). Guarda el hash del
-- token de refresco; el token de acceso (JWT) dura poco y no se guarda.
CREATE TABLE dispositivo (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    miembro_id              BIGINT       NOT NULL REFERENCES miembro(id) ON DELETE CASCADE,
    nombre                  VARCHAR(60)  NOT NULL,          -- "Tablet de Lucía"
    hash_token_refresco     VARCHAR(100) NOT NULL UNIQUE,
    token_push              VARCHAR(255),            -- token de Firebase Cloud Messaging (RF-28)
    creado_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ultimo_uso_en           TIMESTAMPTZ,
    revocado_en             TIMESTAMPTZ
);
CREATE INDEX dispositivo_miembro_idx ON dispositivo(miembro_id);

-- Códigos de un solo uso: vincular el dispositivo de un hijo con un QR (RF-04)
-- o invitar a un segundo adulto (RF-02).
CREATE TABLE codigo_acceso (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    tipo                    VARCHAR(20)  NOT NULL CHECK (tipo IN ('VINCULACION', 'INVITACION')),
    miembro_id              BIGINT       REFERENCES miembro(id) ON DELETE CASCADE, -- hijo a vincular
    hash_codigo             VARCHAR(100) NOT NULL UNIQUE,
    creado_por              BIGINT       NOT NULL REFERENCES miembro(id),
    expira_en               TIMESTAMPTZ  NOT NULL,
    usado_en                TIMESTAMPTZ,
    CONSTRAINT vinculacion_con_hijo CHECK (tipo <> 'VINCULACION' OR miembro_id IS NOT NULL)
);

-- -----------------------------------------------------------------------------
-- 2. Etapas, mecánicas y desbloqueos (RF-25, RF-34)
-- -----------------------------------------------------------------------------

CREATE TABLE etapa (
    numero                  SMALLINT     PRIMARY KEY CHECK (numero BETWEEN 1 AND 5),
    nombre                  VARCHAR(60)  NOT NULL,
    edad_minima             SMALLINT     NOT NULL,
    nivel_minimo            SMALLINT     NOT NULL
);

-- Catálogo de mecánicas. Lo rellena una migración, no los usuarios.
CREATE TABLE mecanica (
    codigo                  VARCHAR(40)  PRIMARY KEY,       -- 'HUCHA_META', 'INTERES_SIMPLE'...
    nombre                  VARCHAR(80)  NOT NULL,
    concepto                VARCHAR(80)  NOT NULL,          -- concepto financiero que enseña
    area_pisa               VARCHAR(2)   NOT NULL CHECK (area_pisa IN ('DT', 'PG', 'RR', 'PF')),
    etapa                   SMALLINT     NOT NULL REFERENCES etapa(numero),
    nivel_minimo            SMALLINT     NOT NULL DEFAULT 1,
    implementada            BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Excepciones que fijan los padres para un hijo: adelantar o desactivar.
CREATE TABLE mecanica_miembro (
    miembro_id              BIGINT       NOT NULL REFERENCES miembro(id) ON DELETE CASCADE,
    mecanica_codigo         VARCHAR(40)  NOT NULL REFERENCES mecanica(codigo),
    estado                  VARCHAR(12)  NOT NULL CHECK (estado IN ('ACTIVADA', 'DESACTIVADA')),
    cambiado_por            BIGINT       NOT NULL REFERENCES miembro(id),
    cambiado_en             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (miembro_id, mecanica_codigo)
);

-- -----------------------------------------------------------------------------
-- 3. Cuentas y movimientos: el libro contable (RF-09, RF-10, RNF-07..09)
-- -----------------------------------------------------------------------------

-- Categorías de gasto (RF-16). familia_id NULL = categoría común a todas.
CREATE TABLE categoria (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       REFERENCES familia(id) ON DELETE CASCADE,
    nombre                  VARCHAR(40)  NOT NULL,
    icono                   VARCHAR(40)  NOT NULL DEFAULT 'categoria',
    UNIQUE (familia_id, nombre)
);

-- Cada monedero, hucha o fondo es una cuenta. La cuenta BANCO de la familia
-- emite el dinero nuevo (recompensas, paga, intereses) y recibe el gastado;
-- es la única que puede quedar en negativo: su saldo negativo es el total de
-- monedas en circulación en la familia.
CREATE TABLE cuenta (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    tipo                    VARCHAR(12)  NOT NULL CHECK (tipo IN ('BANCO', 'MONEDERO', 'HUCHA')),
    titular_id              BIGINT       REFERENCES miembro(id) ON DELETE CASCADE,
    nombre                  VARCHAR(60)  NOT NULL,
    -- Saldo cacheado. Solo lo actualiza el disparador de movimiento.
    saldo                   BIGINT       NOT NULL DEFAULT 0,
    -- Solo huchas (RF-18, RF-20)
    meta                    BIGINT       CHECK (meta > 0),
    fecha_objetivo          DATE,
    bloqueada_hasta         DATE,
    bonificacion_bloqueo_pb INTEGER      CHECK (bonificacion_bloqueo_pb BETWEEN 0 AND 10000),
    icono                   VARCHAR(40),
    creada_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cerrada_en              TIMESTAMPTZ,
    CONSTRAINT saldo_no_negativo CHECK (tipo = 'BANCO' OR saldo >= 0),       -- RNF-08
    CONSTRAINT banco_sin_titular CHECK ((tipo = 'BANCO') = (titular_id IS NULL)),
    CONSTRAINT campos_de_hucha CHECK (tipo = 'HUCHA' OR
        (meta IS NULL AND fecha_objetivo IS NULL AND bloqueada_hasta IS NULL))
);
CREATE INDEX cuenta_familia_idx ON cuenta(familia_id);
CREATE INDEX cuenta_titular_idx ON cuenta(titular_id);
CREATE UNIQUE INDEX cuenta_un_banco_idx ON cuenta(familia_id) WHERE tipo = 'BANCO';
CREATE UNIQUE INDEX cuenta_un_monedero_idx ON cuenta(titular_id) WHERE tipo = 'MONEDERO';

-- Un movimiento lleva una cantidad positiva de una cuenta a otra.
CREATE TABLE movimiento (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    cuenta_origen_id        BIGINT       NOT NULL REFERENCES cuenta(id),
    cuenta_destino_id       BIGINT       NOT NULL REFERENCES cuenta(id),
    cantidad                BIGINT       NOT NULL CHECK (cantidad > 0),
    tipo                    VARCHAR(24)  NOT NULL CHECK (tipo IN (
                                'RECOMPENSA_TAREA', 'PAGA', 'COMPRA', 'DEVOLUCION_COMPRA',
                                'AHORRO', 'RETIRADA_HUCHA', 'INTERES', 'BONIFICACION_BLOQUEO',
                                'CANJE_EUROS', 'TRANSFERENCIA', 'AJUSTE_MANUAL', 'CORRECCION')),
    categoria_id            BIGINT       REFERENCES categoria(id),
    concepto                VARCHAR(140) NOT NULL,
    autor_id                BIGINT       REFERENCES miembro(id),  -- NULL = el sistema
    -- Qué operación lo originó (tarea, compra, liquidación...), para enlazar
    -- el historial con su contexto sin claves foráneas a cada módulo.
    origen_tipo             VARCHAR(30),
    origen_id               BIGINT,
    corrige_a_id            BIGINT       REFERENCES movimiento(id),
    -- Evita cobrar dos veces si el móvil repite la petición (cabecera
    -- Idempotency-Key de la API).
    clave_idempotencia      VARCHAR(64),
    creado_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT cuentas_distintas CHECK (cuenta_origen_id <> cuenta_destino_id),
    CONSTRAINT correccion_enlazada CHECK ((tipo = 'CORRECCION') = (corrige_a_id IS NOT NULL)),
    UNIQUE (familia_id, clave_idempotencia)
);
CREATE INDEX movimiento_origen_idx  ON movimiento(cuenta_origen_id, creado_en DESC);
CREATE INDEX movimiento_destino_idx ON movimiento(cuenta_destino_id, creado_en DESC);
CREATE INDEX movimiento_origen_op_idx ON movimiento(origen_tipo, origen_id);

-- RNF-09: el libro solo admite inserciones.
CREATE FUNCTION movimiento_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Los movimientos no se pueden modificar ni borrar; registra una corrección'
        USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER movimiento_sin_cambios
    BEFORE UPDATE OR DELETE ON movimiento
    FOR EACH ROW EXECUTE FUNCTION movimiento_inmutable();

-- Al insertar un movimiento se actualizan los dos saldos en la misma
-- transacción. El UPDATE bloquea las filas de las cuentas, así que dos compras
-- simultáneas se ejecutan una detrás de otra, y si la segunda deja el saldo en
-- negativo la restricción saldo_no_negativo la rechaza (RNF-08).
CREATE FUNCTION movimiento_aplicar_saldos() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    familias_ok INTEGER;
BEGIN
    SELECT count(*) INTO familias_ok FROM cuenta
     WHERE id IN (NEW.cuenta_origen_id, NEW.cuenta_destino_id)
       AND familia_id = NEW.familia_id
       AND cerrada_en IS NULL;
    IF familias_ok <> 2 THEN
        RAISE EXCEPTION 'Las dos cuentas deben estar abiertas y ser de la familia %', NEW.familia_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    -- Siempre en orden de id para que dos movimientos cruzados no se bloqueen
    -- mutuamente (interbloqueo).
    UPDATE cuenta
       SET saldo = saldo + CASE WHEN id = NEW.cuenta_destino_id THEN NEW.cantidad
                                ELSE -NEW.cantidad END
     WHERE id IN (SELECT c.id FROM cuenta c
                   WHERE c.id IN (NEW.cuenta_origen_id, NEW.cuenta_destino_id)
                   ORDER BY c.id
                   FOR UPDATE);
    RETURN NEW;
END $$;

CREATE TRIGGER movimiento_saldos
    AFTER INSERT ON movimiento
    FOR EACH ROW EXECUTE FUNCTION movimiento_aplicar_saldos();

-- Comprobación de cuadre: el saldo cacheado coincide con el libro.
-- Se usa en las pruebas y en una tarea programada de auditoría.
CREATE VIEW cuenta_cuadre AS
SELECT c.id, c.saldo AS saldo_cacheado,
       COALESCE((SELECT sum(cantidad) FROM movimiento WHERE cuenta_destino_id = c.id), 0)
     - COALESCE((SELECT sum(cantidad) FROM movimiento WHERE cuenta_origen_id  = c.id), 0)
       AS saldo_libro
  FROM cuenta c;

-- -----------------------------------------------------------------------------
-- 4. Reglas automáticas: paga e interés (RF-11, RF-19)
-- -----------------------------------------------------------------------------

CREATE TABLE regla_paga (
    hijo_id                 BIGINT       PRIMARY KEY REFERENCES miembro(id) ON DELETE CASCADE,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    cantidad                BIGINT       NOT NULL CHECK (cantidad > 0),
    periodicidad            VARCHAR(10)  NOT NULL CHECK (periodicidad IN ('SEMANAL', 'MENSUAL')),
    -- SEMANAL: 1 (lunes) a 7 (domingo). MENSUAL: 1 a 28.
    dia                     SMALLINT     NOT NULL CHECK (dia BETWEEN 1 AND 28),
    -- Parte de la paga que va directa a una hucha (RF-11).
    porcentaje_ahorro       SMALLINT     NOT NULL DEFAULT 0 CHECK (porcentaje_ahorro BETWEEN 0 AND 100),
    hucha_destino_id        BIGINT       REFERENCES cuenta(id),
    activa                  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ahorro_con_hucha CHECK (porcentaje_ahorro = 0 OR hucha_destino_id IS NOT NULL)
);

-- Una regla de interés por familia. Tipo en puntos básicos por periodo
-- (200 pb = 2 % semanal). Interés = floor(base * tipo_pb / 10000).
CREATE TABLE regla_interes (
    familia_id              BIGINT       PRIMARY KEY REFERENCES familia(id) ON DELETE CASCADE,
    tipo_pb                 INTEGER      NOT NULL CHECK (tipo_pb BETWEEN 1 AND 10000),
    modalidad               VARCHAR(10)  NOT NULL CHECK (modalidad IN ('SIMPLE', 'COMPUESTO')),
    periodicidad            VARCHAR(10)  NOT NULL CHECK (periodicidad IN ('SEMANAL', 'MENSUAL')),
    activa                  BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Registro de cada ejecución periódica. La restricción UNIQUE impide pagar dos
-- veces la misma paga o el mismo interés si el servidor se reinicia.
CREATE TABLE liquidacion (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    tipo                    VARCHAR(20)  NOT NULL CHECK (tipo IN (
                                'PAGA', 'INTERES', 'CIERRE_PRESUPUESTO', 'DESBLOQUEO_HUCHA')),
    referencia_id           BIGINT       NOT NULL,   -- hijo, hucha o presupuesto
    periodo                 DATE         NOT NULL,   -- inicio de la semana o del mes
    ejecutada_en            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tipo, referencia_id, periodo)
);

-- -----------------------------------------------------------------------------
-- 5. Tareas (RF-06..08, RF-37)
-- -----------------------------------------------------------------------------

CREATE TABLE plantilla_tarea (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    titulo                  VARCHAR(80)  NOT NULL,
    grupo                   VARCHAR(30)  NOT NULL,   -- limpieza, recoger, cocina, recados, jardín
    recompensa_sugerida     BIGINT       NOT NULL CHECK (recompensa_sugerida > 0),
    icono                   VARCHAR(40)  NOT NULL DEFAULT 'tarea'
);

CREATE TABLE tarea (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    titulo                  VARCHAR(80)  NOT NULL,
    descripcion             VARCHAR(500),
    recompensa              BIGINT       NOT NULL CHECK (recompensa > 0),
    periodicidad            VARCHAR(10)  NOT NULL CHECK (periodicidad IN ('UNICA', 'DIARIA', 'SEMANAL')),
    -- Para SEMANAL: días como máscara de bits (lunes = 1, martes = 2, ... domingo = 64).
    dias_semana             SMALLINT     CHECK (dias_semana BETWEEN 1 AND 127),
    fecha_limite            DATE,                    -- para UNICA
    icono                   VARCHAR(40)  NOT NULL DEFAULT 'tarea',
    creada_por              BIGINT       NOT NULL REFERENCES miembro(id),
    creada_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archivada_en            TIMESTAMPTZ,
    CONSTRAINT semanal_con_dias CHECK ((periodicidad = 'SEMANAL') = (dias_semana IS NOT NULL))
);
CREATE INDEX tarea_familia_idx ON tarea(familia_id);

CREATE TABLE tarea_hijo (
    tarea_id                BIGINT       NOT NULL REFERENCES tarea(id) ON DELETE CASCADE,
    hijo_id                 BIGINT       NOT NULL REFERENCES miembro(id) ON DELETE CASCADE,
    PRIMARY KEY (tarea_id, hijo_id)
);

-- Cada vez que un hijo tiene que hacer una tarea (una por día en las
-- periódicas). Se crean de forma perezosa al consultar las tareas del día.
CREATE TABLE asignacion_tarea (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    tarea_id                BIGINT       NOT NULL REFERENCES tarea(id) ON DELETE CASCADE,
    hijo_id                 BIGINT       NOT NULL REFERENCES miembro(id) ON DELETE CASCADE,
    fecha                   DATE         NOT NULL,
    estado                  VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN (
                                'PENDIENTE', 'HECHA', 'APROBADA', 'RECHAZADA', 'CADUCADA')),
    hecha_en                TIMESTAMPTZ,
    revisada_por            BIGINT       REFERENCES miembro(id),
    revisada_en             TIMESTAMPTZ,
    comentario              VARCHAR(280),
    movimiento_id           BIGINT       REFERENCES movimiento(id),
    version                 INTEGER      NOT NULL DEFAULT 0,   -- bloqueo optimista (dos padres a la vez)
    UNIQUE (tarea_id, hijo_id, fecha),
    CONSTRAINT aprobada_con_pago CHECK ((estado = 'APROBADA') = (movimiento_id IS NOT NULL))
);
CREATE INDEX asignacion_hijo_idx ON asignacion_tarea(hijo_id, fecha DESC);
CREATE INDEX asignacion_por_revisar_idx ON asignacion_tarea(familia_id) WHERE estado = 'HECHA';

-- -----------------------------------------------------------------------------
-- 6. Tienda, compras y dinero real (RF-14..16, RF-35, RF-36)
-- -----------------------------------------------------------------------------

CREATE TABLE plantilla_recompensa (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre                  VARCHAR(80)  NOT NULL,
    tipo                    VARCHAR(12)  NOT NULL CHECK (tipo IN ('REGALO', 'DINERO_REAL', 'ACTIVIDAD')),
    precio_sugerido         BIGINT       NOT NULL CHECK (precio_sugerido > 0),
    icono                   VARCHAR(40)  NOT NULL DEFAULT 'recompensa'
);

CREATE TABLE recompensa (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    nombre                  VARCHAR(80)  NOT NULL,
    descripcion             VARCHAR(500),
    tipo                    VARCHAR(12)  NOT NULL CHECK (tipo IN ('REGALO', 'DINERO_REAL', 'ACTIVIDAD')),
    categoria_id            BIGINT       NOT NULL REFERENCES categoria(id),
    precio                  BIGINT       NOT NULL CHECK (precio > 0),  -- en monedas
    existencias             INTEGER      CHECK (existencias >= 0),    -- NULL = ilimitadas
    icono                   VARCHAR(40)  NOT NULL DEFAULT 'recompensa',
    creada_por              BIGINT       NOT NULL REFERENCES miembro(id),
    creada_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archivada_en            TIMESTAMPTZ
);
CREATE INDEX recompensa_familia_idx ON recompensa(familia_id);

-- Historial de precios: lo usan la inflación (RF-17) y, más adelante, la oferta
-- y la demanda (RF-39), y permite dibujar cómo ha subido un precio.
CREATE TABLE precio_recompensa (
    recompensa_id           BIGINT       NOT NULL REFERENCES recompensa(id) ON DELETE CASCADE,
    desde                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    precio                  BIGINT       NOT NULL CHECK (precio > 0),
    motivo                  VARCHAR(20)  NOT NULL CHECK (motivo IN ('ALTA', 'EDICION', 'INFLACION', 'DEMANDA')),
    PRIMARY KEY (recompensa_id, desde)
);

CREATE TABLE compra (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    recompensa_id           BIGINT       NOT NULL REFERENCES recompensa(id),
    hijo_id                 BIGINT       NOT NULL REFERENCES miembro(id),
    precio_pagado           BIGINT       NOT NULL CHECK (precio_pagado > 0),
    estado                  VARCHAR(12)  NOT NULL DEFAULT 'PEDIDA' CHECK (estado IN (
                                'PEDIDA', 'ENTREGADA', 'CANCELADA')),
    movimiento_id           BIGINT       NOT NULL REFERENCES movimiento(id),
    devolucion_id           BIGINT       REFERENCES movimiento(id),
    pedida_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resuelta_por            BIGINT       REFERENCES miembro(id),
    resuelta_en             TIMESTAMPTZ,
    CONSTRAINT cancelada_con_devolucion CHECK ((estado = 'CANCELADA') = (devolucion_id IS NOT NULL))
);
CREATE INDEX compra_hijo_idx ON compra(hijo_id, pedida_en DESC);

-- "Debes X € a [hijo]" (RF-36). La app no mueve dinero real: solo lo recuerda.
CREATE TABLE pago_pendiente (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    hijo_id                 BIGINT       NOT NULL REFERENCES miembro(id),
    importe_centimos        BIGINT       NOT NULL CHECK (importe_centimos > 0),
    origen                  VARCHAR(10)  NOT NULL CHECK (origen IN ('CANJE', 'COMPRA')),
    compra_id               BIGINT       REFERENCES compra(id),
    movimiento_id           BIGINT       NOT NULL REFERENCES movimiento(id),
    estado                  VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'PAGADO')),
    creado_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    pagado_por              BIGINT       REFERENCES miembro(id),
    pagado_en               TIMESTAMPTZ,
    CONSTRAINT compra_enlazada CHECK ((origen = 'COMPRA') = (compra_id IS NOT NULL))
);
CREATE INDEX pago_pendiente_familia_idx ON pago_pendiente(familia_id) WHERE estado = 'PENDIENTE';

-- Inflación de la tienda (RF-17): porcentaje mensual en puntos básicos.
CREATE TABLE regla_inflacion (
    familia_id              BIGINT       PRIMARY KEY REFERENCES familia(id) ON DELETE CASCADE,
    tipo_mensual_pb         INTEGER      NOT NULL CHECK (tipo_mensual_pb BETWEEN 1 AND 2000),
    activa                  BOOLEAN      NOT NULL DEFAULT TRUE
);

-- -----------------------------------------------------------------------------
-- 7. Presupuesto semanal (RF-21)
-- -----------------------------------------------------------------------------

CREATE TABLE presupuesto (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    hijo_id                 BIGINT       NOT NULL REFERENCES miembro(id) ON DELETE CASCADE,
    semana                  DATE         NOT NULL,   -- lunes de la semana
    cumplido                BOOLEAN,                 -- se rellena al cerrar la semana
    UNIQUE (hijo_id, semana),
    CONSTRAINT semana_en_lunes CHECK (extract(isodow FROM semana) = 1)
);

CREATE TABLE partida_presupuesto (
    presupuesto_id          BIGINT       NOT NULL REFERENCES presupuesto(id) ON DELETE CASCADE,
    categoria_id            BIGINT       NOT NULL REFERENCES categoria(id),
    limite                  BIGINT       NOT NULL CHECK (limite >= 0),
    -- Para no repetir el aviso del 80 % y del 100 % (RF-21, RF-28).
    aviso_80_en             TIMESTAMPTZ,
    aviso_100_en            TIMESTAMPTZ,
    PRIMARY KEY (presupuesto_id, categoria_id)
);

-- -----------------------------------------------------------------------------
-- 8. Experiencia, niveles y logros (RF-22, RF-23)
-- -----------------------------------------------------------------------------

-- Libro de experiencia, también de solo inserción: explica por qué se tiene
-- cada punto y permite recalcular el nivel si cambian las reglas.
CREATE TABLE evento_experiencia (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    miembro_id              BIGINT       NOT NULL REFERENCES miembro(id) ON DELETE CASCADE,
    puntos                  INTEGER      NOT NULL CHECK (puntos > 0),
    motivo                  VARCHAR(40)  NOT NULL,  -- 'TAREA_APROBADA', 'META_CUMPLIDA', 'SEMANA_EN_PRESUPUESTO'...
    origen_tipo             VARCHAR(30),
    origen_id               BIGINT,
    creado_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (miembro_id, motivo, origen_tipo, origen_id)  -- no dar dos veces la misma XP
);

CREATE TRIGGER evento_experiencia_sin_cambios
    BEFORE UPDATE OR DELETE ON evento_experiencia
    FOR EACH ROW EXECUTE FUNCTION movimiento_inmutable();

CREATE TABLE logro (
    codigo                  VARCHAR(40)  PRIMARY KEY,     -- 'PRIMERA_META', 'CUATRO_SEMANAS_PRESUPUESTO'
    nombre                  VARCHAR(80)  NOT NULL,
    descripcion             VARCHAR(200) NOT NULL,
    concepto                VARCHAR(80)  NOT NULL,        -- concepto financiero que premia
    area_pisa               VARCHAR(2)   NOT NULL CHECK (area_pisa IN ('DT', 'PG', 'RR', 'PF')),
    puntos                  INTEGER      NOT NULL DEFAULT 0 CHECK (puntos >= 0),
    icono                   VARCHAR(40)  NOT NULL DEFAULT 'logro',
    para_adultos            BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE logro_miembro (
    miembro_id              BIGINT       NOT NULL REFERENCES miembro(id) ON DELETE CASCADE,
    logro_codigo            VARCHAR(40)  NOT NULL REFERENCES logro(codigo),
    conseguido_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (miembro_id, logro_codigo)
);

-- -----------------------------------------------------------------------------
-- 9. Cuestionario de conocimientos antes y después (RF-44, evaluación)
-- -----------------------------------------------------------------------------

CREATE TABLE cuestionario (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo                  VARCHAR(40)  NOT NULL UNIQUE,  -- 'CONOCIMIENTOS_8_12', 'CONOCIMIENTOS_ADULTOS'
    titulo                  VARCHAR(80)  NOT NULL,
    destinatario            VARCHAR(10)  NOT NULL CHECK (destinatario IN ('HIJO', 'ADULTO'))
);

CREATE TABLE pregunta (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuestionario_id         BIGINT       NOT NULL REFERENCES cuestionario(id) ON DELETE CASCADE,
    orden                   SMALLINT     NOT NULL,
    enunciado               VARCHAR(400) NOT NULL,
    area_pisa               VARCHAR(2)   NOT NULL CHECK (area_pisa IN ('DT', 'PG', 'RR', 'PF')),
    opciones                JSONB        NOT NULL,   -- ["opción A", "opción B", ...]
    opcion_correcta         SMALLINT     NOT NULL,
    UNIQUE (cuestionario_id, orden)
);

CREATE TABLE intento_cuestionario (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuestionario_id         BIGINT       NOT NULL REFERENCES cuestionario(id),
    miembro_id              BIGINT       NOT NULL REFERENCES miembro(id) ON DELETE CASCADE,
    momento                 VARCHAR(10)  NOT NULL CHECK (momento IN ('INICIAL', 'FINAL')),
    aciertos                SMALLINT     NOT NULL CHECK (aciertos >= 0),
    total                   SMALLINT     NOT NULL CHECK (total > 0),
    respondido_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (cuestionario_id, miembro_id, momento)
);

CREATE TABLE respuesta_cuestionario (
    intento_id              BIGINT       NOT NULL REFERENCES intento_cuestionario(id) ON DELETE CASCADE,
    pregunta_id             BIGINT       NOT NULL REFERENCES pregunta(id),
    opcion_elegida          SMALLINT     NOT NULL,
    PRIMARY KEY (intento_id, pregunta_id)
);

-- -----------------------------------------------------------------------------
-- 10. Datos iniciales
-- -----------------------------------------------------------------------------

INSERT INTO etapa (numero, nombre, edad_minima, nivel_minimo) VALUES
    (1, 'Mi primer dinero',          8,  1),
    (2, 'Aprendo a planificar',      9,  5),
    (3, 'Mi dinero crece',          11, 10),
    (4, 'Riesgo y recompensa',      13, 16),
    (5, 'Hacia la vida adulta',     16, 22);

INSERT INTO mecanica (codigo, nombre, concepto, area_pisa, etapa, nivel_minimo, implementada) VALUES
    ('TAREAS',            'Tareas con recompensa',        'Ingreso por trabajo',      'DT', 1, 1,  TRUE),
    ('PAGA',              'Paga periódica',               'Ingreso regular',          'DT', 1, 1,  TRUE),
    ('MONEDERO',          'Monedero e historial',         'Registro de gastos',       'DT', 1, 1,  TRUE),
    ('TIENDA',            'Tienda familiar',              'Coste de oportunidad',     'DT', 1, 1,  TRUE),
    ('HUCHA_META',        'Hucha con meta',               'Ahorro con objetivo',      'PG', 1, 2,  TRUE),
    ('HUCHA_SOLIDARIA',   'Hucha solidaria',              'Donación',                 'PF', 1, 3,  FALSE),
    ('INTERES_SIMPLE',    'Interés simple',               'Interés',                  'RR', 2, 5,  TRUE),
    ('HUCHA_BLOQUEADA',   'Hucha bloqueada',              'Gratificación diferida',   'PG', 2, 6,  TRUE),
    ('PRESUPUESTO',       'Presupuesto semanal',          'Presupuesto',              'PG', 2, 6,  TRUE),
    ('LISTA_DESEOS',      'Lista de deseos con espera',   'Compra impulsiva',         'DT', 2, 7,  FALSE),
    ('COMPARAR_PRECIOS',  'Comparar precios',             'Precio unitario',          'DT', 2, 7,  FALSE),
    ('OFERTA_DEMANDA',    'Oferta y demanda',             'Precio y escasez',         'PF', 2, 8,  FALSE),
    ('TRANSFERENCIAS',    'Transferencias entre hermanos','Pagos',                    'DT', 2, 5,  TRUE),
    ('INTERES_COMPUESTO', 'Interés compuesto',            'Interés compuesto',        'RR', 3, 10, TRUE),
    ('INFLACION',         'Inflación',                    'Poder adquisitivo',        'PF', 3, 11, TRUE),
    ('PRESTAMOS',         'Préstamos entre hermanos',     'Crédito y negociación',    'RR', 3, 12, FALSE),
    ('ADELANTO_PAGA',     'Adelanto de paga',             'Deuda',                    'RR', 3, 12, FALSE),
    ('ESTADO_FAMILIAR',   'El Estado familiar',           'Impuestos y bienes comunes','PF', 3, 13, FALSE),
    ('SERVICIOS',         'Mis servicios',                'Emprendimiento',           'DT', 3, 13, FALSE),
    ('SUSCRIPCIONES',     'Suscripciones',                'Gasto recurrente',         'PG', 3, 14, FALSE);

INSERT INTO categoria (familia_id, nombre, icono) VALUES
    (NULL, 'Chuches y caprichos', 'caramelo'),
    (NULL, 'Juguetes y juegos',   'mando'),
    (NULL, 'Ocio y actividades',  'entrada'),
    (NULL, 'Tecnología',          'movil'),
    (NULL, 'Ropa',                'camiseta'),
    (NULL, 'Regalos para otros',  'regalo'),
    (NULL, 'Dinero real',         'euro'),
    (NULL, 'Otros',               'categoria');

INSERT INTO plantilla_tarea (titulo, grupo, recompensa_sugerida, icono) VALUES
    ('Hacer la cama',            'recoger',   5,  'cama'),
    ('Recoger la habitación',    'recoger',   10, 'caja'),
    ('Poner el lavavajillas',    'cocina',    10, 'platos'),
    ('Poner y quitar la mesa',   'cocina',    5,  'mesa'),
    ('Ayudar a cocinar',         'cocina',    15, 'sarten'),
    ('Limpiar el baño',          'limpieza',  20, 'cubo'),
    ('Pasar la aspiradora',      'limpieza',  15, 'aspiradora'),
    ('Comprar el pan',           'recados',   5,  'pan'),
    ('Sacar la basura',          'recados',   5,  'basura'),
    ('Cortar el césped',         'jardin',    25, 'planta');

INSERT INTO plantilla_recompensa (nombre, tipo, precio_sugerido, icono) VALUES
    ('Helado',                          'REGALO',      20,  'helado'),
    ('Sobre de cromos',                 'REGALO',      15,  'cromos'),
    ('30 minutos extra de consola',     'ACTIVIDAD',   25,  'mando'),
    ('Elegir la cena del viernes',      'ACTIVIDAD',   30,  'pizza'),
    ('Ir al cine',                      'ACTIVIDAD',   120, 'entrada'),
    ('5 € en mano',                     'DINERO_REAL', 50,  'euro');

INSERT INTO logro (codigo, nombre, descripcion, concepto, area_pisa, puntos, icono) VALUES
    ('PRIMERA_TAREA',          'Primer sueldo',          'Cobra tu primera tarea',                           'Ingreso por trabajo',    'DT', 10, 'moneda'),
    ('PRIMERA_COMPRA',         'Primera decisión',       'Compra tu primera recompensa en la tienda',        'Coste de oportunidad',   'DT', 10, 'bolsa'),
    ('PRIMERA_HUCHA',          'Hucha estrenada',        'Crea tu primera hucha con una meta',               'Ahorro con objetivo',    'PG', 10, 'hucha'),
    ('PRIMERA_META',           'Objetivo cumplido',      'Llena una hucha hasta su meta',                    'Ahorro con objetivo',    'PG', 50, 'trofeo'),
    ('AHORRADOR_CONSTANTE',    'Ahorrador constante',    'Ahorra algo 10 semanas seguidas',                  'Hábito de ahorro',       'PG', 100,'calendario'),
    ('PRIMER_INTERES',         'Mi dinero trabaja',      'Recibe tu primer interés',                         'Interés',                'RR', 20, 'grafica'),
    ('INTERES_10',             'Diez de regalo',         'Tus huchas han generado 10 monedas de interés',    'Interés',                'RR', 50, 'grafica'),
    ('ESPERA_CUMPLIDA',        'Paciencia de oro',       'Mantén una hucha bloqueada hasta su fecha',        'Gratificación diferida', 'PG', 50, 'candado'),
    ('SEMANA_EN_PRESUPUESTO',  'Cuentas claras',         'Termina una semana dentro del presupuesto',        'Presupuesto',            'PG', 30, 'lista'),
    ('CUATRO_SEMANAS_PRESUPUESTO','Planificador',        'Cuatro semanas seguidas dentro del presupuesto',   'Presupuesto',            'PG', 100,'lista');
