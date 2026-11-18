-- =============================================================================
-- V1 · Núcleo: familias, miembros y acceso; etapas y mecánicas; libro contable
--
-- Copiado sin cambios de las secciones 1 a 3 y de los datos iniciales de
-- etapas, mecánicas y categorías de docs/diseno/esquema.sql. Las tablas de los
-- demás módulos (paga e interés, tareas, tienda, presupuesto, logros y
-- cuestionarios) llegarán en V2, V3... con la iteración que las use.
--
-- Una migración aplicada no se edita nunca: los cambios van en una nueva.
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
-- Datos iniciales del núcleo
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
