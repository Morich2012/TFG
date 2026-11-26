-- Iteración 2: tareas (RF-06..08, RF-37) y arreglo de borrar_familia.
-- Las tablas son las de la sección 5 de docs/diseno/esquema.sql, copiadas tal cual.
-- Sus FK de autoría (creada_por, revisada_por) son diferibles: se comprueban al
-- confirmar, para que borrar_familia pueda borrar en cascada revision_tarea,
-- que cuelga de familia a dos niveles.

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
    creada_por              BIGINT       NOT NULL REFERENCES miembro(id) DEFERRABLE INITIALLY DEFERRED,
    creada_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archivada_en            TIMESTAMPTZ,
    CONSTRAINT semanal_con_dias CHECK ((periodicidad = 'SEMANAL') = (dias_semana IS NOT NULL)),
    CONSTRAINT tarea_familia_uq UNIQUE (familia_id, id)
);

CREATE TABLE tarea_hijo (
    tarea_id                BIGINT       NOT NULL REFERENCES tarea(id) ON DELETE CASCADE,
    hijo_id                 BIGINT       NOT NULL REFERENCES miembro(id) ON DELETE CASCADE,
    PRIMARY KEY (tarea_id, hijo_id)
);
CREATE INDEX tarea_hijo_hijo_idx ON tarea_hijo(hijo_id);

-- Cada vez que un hijo tiene que hacer una tarea (una por día en las
-- periódicas). Se crean de forma perezosa al consultar las tareas del día.
CREATE TABLE asignacion_tarea (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    familia_id              BIGINT       NOT NULL REFERENCES familia(id) ON DELETE CASCADE,
    tarea_id                BIGINT       NOT NULL,
    hijo_id                 BIGINT       NOT NULL,
    fecha                   DATE         NOT NULL,
    -- Recompensa pactada al crear la asignación: si luego cambia la tarea, se
    -- paga lo pactado.
    recompensa              BIGINT       NOT NULL CHECK (recompensa > 0),
    estado                  VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN (
                                'PENDIENTE', 'HECHA', 'APROBADA', 'RECHAZADA', 'CADUCADA')),
    hecha_en                TIMESTAMPTZ,              -- última entrega
    movimiento_id           BIGINT       UNIQUE REFERENCES movimiento(id),  -- un pago, una tarea
    version                 INTEGER      NOT NULL DEFAULT 0,   -- bloqueo optimista (dos padres a la vez)
    CONSTRAINT asignacion_dia_uq UNIQUE (tarea_id, hijo_id, fecha),
    CONSTRAINT aprobada_con_pago CHECK ((estado = 'APROBADA') = (movimiento_id IS NOT NULL)),
    CONSTRAINT asignacion_tarea_fk FOREIGN KEY (familia_id, tarea_id)
        REFERENCES tarea(familia_id, id) ON DELETE CASCADE,
    CONSTRAINT asignacion_hijo_fk FOREIGN KEY (familia_id, hijo_id)
        REFERENCES miembro(familia_id, id) ON DELETE CASCADE
);
CREATE INDEX asignacion_hijo_idx ON asignacion_tarea(hijo_id, fecha DESC);
CREATE INDEX asignacion_por_revisar_idx ON asignacion_tarea(familia_id) WHERE estado = 'HECHA';

-- Historial de revisiones (POST .../asignaciones/{id}/revisiones): una tarea
-- rechazada y rehecha conserva cada revisión.
CREATE TABLE revision_tarea (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asignacion_id           BIGINT       NOT NULL REFERENCES asignacion_tarea(id) ON DELETE CASCADE,
    resultado               VARCHAR(10)  NOT NULL CHECK (resultado IN ('APROBADA', 'RECHAZADA')),
    comentario              VARCHAR(280),
    revisada_por            BIGINT       NOT NULL REFERENCES miembro(id) DEFERRABLE INITIALLY DEFERRED,
    revisada_en             TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX revision_tarea_asignacion_idx ON revision_tarea(asignacion_id, revisada_en);
-- Solo una revisión aprobada por asignación.
CREATE UNIQUE INDEX revision_tarea_una_aprobada_idx ON revision_tarea(asignacion_id)
    WHERE resultado = 'APROBADA';

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

-- borrar_familia marcaba la transacción con 'si', y cualquier conexión podía
-- poner esa marca con un SET y borrar movimientos de cualquier familia. Ahora la
-- marca es el id de la familia y el disparador solo deja borrar los suyos.
-- RNF-09: el libro solo admite inserciones. La única excepción es borrar una
-- familia entera con borrar_familia() (derecho de supresión, RGPD art. 17),
-- que marca la transacción con el id de esa familia en tfg.borrando_familia:
-- solo se borran movimientos de la familia marcada, nunca los de otra.
CREATE OR REPLACE FUNCTION movimiento_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE'
       AND current_setting('tfg.borrando_familia', true) = OLD.familia_id::text THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Los movimientos no se pueden modificar ni borrar; registra una corrección'
        USING ERRCODE = 'integrity_constraint_violation';
END $$;

-- Supresión completa de una familia (RGPD art. 17). Es la única forma de borrar
-- movimientos: marca la transacción con el id de la familia para que
-- movimiento_inmutable permita borrar los suyos y solo los suyos.
CREATE OR REPLACE FUNCTION borrar_familia(p_familia_id BIGINT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    PERFORM set_config('tfg.borrando_familia', p_familia_id::text, true);   -- solo esta transacción
    DELETE FROM familia WHERE id = p_familia_id;
    PERFORM set_config('tfg.borrando_familia', '', true);
END $$;
