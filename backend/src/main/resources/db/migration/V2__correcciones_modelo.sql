-- =============================================================================
-- V2: correcciones del modelo de datos tras la revisión del 25 de noviembre
-- (documento «Análisis del modelo de datos TFG», propuestas 1 a 29).
--
-- Solo toca las tablas que creó V1. Las propuestas sobre tablas de módulos
-- posteriores (tareas, tienda, presupuesto, experiencia, cuestionarios) ya
-- están en docs/diseno/esquema.sql y entrarán con sus migraciones.
-- =============================================================================

-- [15] Conjuntos de valores repetidos.
CREATE DOMAIN area_pisa AS VARCHAR(2)
    CHECK (VALUE IN ('DT', 'PG', 'RR', 'PF'));
CREATE DOMAIN periodicidad AS VARCHAR(10)
    CHECK (VALUE IN ('SEMANAL', 'MENSUAL'));

-- [14] Zona horaria de la familia para calcular días, semanas y meses.
ALTER TABLE familia ADD COLUMN zona_horaria VARCHAR(40) NOT NULL DEFAULT 'Europe/Madrid';

-- [3] [5] [6] [13] miembro
ALTER TABLE miembro
    ADD CONSTRAINT miembro_email_check CHECK (email = lower(email)),
    ADD CONSTRAINT adulto_sin_datos_de_hijo CHECK (
        rol = 'HIJO' OR (anio_nacimiento IS NULL AND hash_pin IS NULL AND etapa_fijada IS NULL)),
    ADD CONSTRAINT miembro_familia_uq UNIQUE (familia_id, id),
    DROP CONSTRAINT miembro_etapa_fijada_check,
    ADD CONSTRAINT miembro_etapa_fijada_fk FOREIGN KEY (etapa_fijada) REFERENCES etapa(numero);

-- [3] [6] [8] codigo_acceso: el hijo a vincular es de la misma familia, y una
-- invitación no lleva hijo.
ALTER TABLE codigo_acceso
    DROP CONSTRAINT codigo_acceso_miembro_id_fkey,
    DROP CONSTRAINT vinculacion_con_hijo,
    ADD CONSTRAINT vinculacion_con_hijo CHECK ((tipo = 'VINCULACION') = (miembro_id IS NOT NULL)),
    ADD CONSTRAINT codigo_acceso_miembro_fk FOREIGN KEY (familia_id, miembro_id)
        REFERENCES miembro(familia_id, id) ON DELETE CASCADE;
CREATE INDEX codigo_acceso_familia_idx ON codigo_acceso(familia_id);
CREATE INDEX codigo_acceso_miembro_idx ON codigo_acceso(miembro_id);

-- [15] mecanica usa el dominio.
ALTER TABLE mecanica DROP CONSTRAINT mecanica_area_pisa_check;
ALTER TABLE mecanica ALTER COLUMN area_pisa TYPE area_pisa;

-- [4] [18] categoria: tampoco se repiten las comunes.
ALTER TABLE categoria
    DROP CONSTRAINT categoria_familia_id_nombre_key,
    ADD CONSTRAINT categoria_nombre_uq UNIQUE NULLS NOT DISTINCT (familia_id, nombre);

-- [2] [6] cuenta
ALTER TABLE cuenta
    DROP CONSTRAINT campos_de_hucha,
    ADD CONSTRAINT campos_de_hucha CHECK (tipo = 'HUCHA' OR
        (meta IS NULL AND fecha_objetivo IS NULL AND bloqueada_hasta IS NULL
         AND bonificacion_bloqueo_pb IS NULL)),
    DROP CONSTRAINT cuenta_titular_id_fkey,
    ADD CONSTRAINT cuenta_titular_fk FOREIGN KEY (familia_id, titular_id)
        REFERENCES miembro(familia_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT cuenta_familia_uq UNIQUE (familia_id, id);

-- [8] [18] [27] movimiento
ALTER TABLE movimiento
    ADD CONSTRAINT movimiento_corrige_a_id_key UNIQUE (corrige_a_id);
ALTER TABLE movimiento
    RENAME CONSTRAINT movimiento_familia_id_clave_idempotencia_key TO movimiento_idempotencia_uq;
CREATE INDEX movimiento_categoria_idx ON movimiento(categoria_id);

-- [1] El libro deja borrar solo dentro de borrar_familia().
CREATE OR REPLACE FUNCTION movimiento_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('tfg.borrando_familia', true) = 'si' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Los movimientos no se pueden modificar ni borrar; registra una corrección'
        USING ERRCODE = 'integrity_constraint_violation';
END $$;

-- [26] [27] Sentido por tipo de movimiento y correcciones completas.
CREATE OR REPLACE FUNCTION movimiento_aplicar_saldos() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    origen   cuenta%ROWTYPE;
    destino  cuenta%ROWTYPE;
    original movimiento%ROWTYPE;
    sentido  TEXT;
    permitido BOOLEAN;
BEGIN
    SELECT * INTO origen  FROM cuenta WHERE id = NEW.cuenta_origen_id;
    SELECT * INTO destino FROM cuenta WHERE id = NEW.cuenta_destino_id;
    IF origen.familia_id <> NEW.familia_id OR destino.familia_id <> NEW.familia_id
       OR origen.cerrada_en IS NOT NULL OR destino.cerrada_en IS NOT NULL THEN
        RAISE EXCEPTION 'Las dos cuentas deben estar abiertas y ser de la familia %', NEW.familia_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    -- Cada tipo de movimiento va en un sentido fijo entre tipos de cuenta.
    sentido := origen.tipo || '>' || destino.tipo;
    permitido := CASE NEW.tipo
        WHEN 'RECOMPENSA_TAREA'     THEN sentido = 'BANCO>MONEDERO'
        WHEN 'PAGA'                 THEN sentido IN ('BANCO>MONEDERO', 'BANCO>HUCHA')
        WHEN 'COMPRA'               THEN sentido = 'MONEDERO>BANCO'
        WHEN 'DEVOLUCION_COMPRA'    THEN sentido = 'BANCO>MONEDERO'
        WHEN 'AHORRO'               THEN sentido = 'MONEDERO>HUCHA'
        WHEN 'RETIRADA_HUCHA'       THEN sentido = 'HUCHA>MONEDERO'
        WHEN 'INTERES'              THEN sentido = 'BANCO>HUCHA'
        WHEN 'BONIFICACION_BLOQUEO' THEN sentido = 'BANCO>HUCHA'
        WHEN 'CANJE_EUROS'          THEN sentido = 'MONEDERO>BANCO'
        WHEN 'TRANSFERENCIA'        THEN sentido = 'MONEDERO>MONEDERO'
        WHEN 'AJUSTE_MANUAL'        THEN 'BANCO' IN (origen.tipo, destino.tipo)
        ELSE TRUE                   -- CORRECCION: se comprueba abajo
    END;
    IF NOT permitido THEN
        RAISE EXCEPTION 'Un movimiento % no puede ir de % a %', NEW.tipo, origen.tipo, destino.tipo
            USING ERRCODE = 'check_violation';
    END IF;
    -- Ahorrar y retirar solo entre cuentas del mismo titular.
    IF NEW.tipo IN ('AHORRO', 'RETIRADA_HUCHA') AND origen.titular_id <> destino.titular_id THEN
        RAISE EXCEPTION 'Solo se puede ahorrar o retirar entre cuentas propias'
            USING ERRCODE = 'check_violation';
    END IF;
    -- Una corrección deshace el original entero: misma cantidad, cuentas al revés.
    IF NEW.tipo = 'CORRECCION' THEN
        SELECT * INTO original FROM movimiento WHERE id = NEW.corrige_a_id;
        IF original.tipo = 'CORRECCION' OR original.familia_id <> NEW.familia_id
           OR NEW.cantidad <> original.cantidad
           OR NEW.cuenta_origen_id <> original.cuenta_destino_id
           OR NEW.cuenta_destino_id <> original.cuenta_origen_id THEN
            RAISE EXCEPTION 'Una corrección debe anular el movimiento % entero y al revés', NEW.corrige_a_id
                USING ERRCODE = 'check_violation';
        END IF;
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

-- [25] El saldo solo cambia con un movimiento.
CREATE FUNCTION cuenta_saldo_protegido() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.saldo IS DISTINCT FROM OLD.saldo AND pg_trigger_depth() < 2 THEN
        RAISE EXCEPTION 'El saldo solo cambia con un movimiento'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER cuenta_saldo_protegido
    BEFORE UPDATE OF saldo ON cuenta
    FOR EACH ROW EXECUTE FUNCTION cuenta_saldo_protegido();

-- [1] Supresión completa de una familia (RGPD art. 17).
CREATE FUNCTION borrar_familia(p_familia_id BIGINT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    PERFORM set_config('tfg.borrando_familia', 'si', true);   -- solo esta transacción
    DELETE FROM familia WHERE id = p_familia_id;
    PERFORM set_config('tfg.borrando_familia', '', true);
END $$;
