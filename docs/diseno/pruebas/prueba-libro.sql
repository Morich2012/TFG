-- Prueba del libro contable sobre esquema.sql.
-- Uso: psql -v ON_ERROR_STOP=1 -f esquema.sql && psql -f pruebas/prueba-libro.sql
-- Cada bloque DO comprueba una regla y lanza un error si no se cumple.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO familia (nombre) VALUES ('Familia de prueba');
INSERT INTO miembro (familia_id, rol, nombre, email, hash_contrasena)
     VALUES (1, 'ADULTO_ADMIN', 'Mamá', 'mama@example.org', 'x');
INSERT INTO miembro (familia_id, rol, nombre, anio_nacimiento) VALUES (1, 'HIJO', 'Lucía', 2017);
INSERT INTO cuenta (familia_id, tipo, nombre) VALUES (1, 'BANCO', 'Banco familiar');            -- id 1
INSERT INTO cuenta (familia_id, tipo, titular_id, nombre) VALUES (1, 'MONEDERO', 2, 'Monedero'); -- id 2
INSERT INTO cuenta (familia_id, tipo, titular_id, nombre, meta) VALUES (1, 'HUCHA', 2, 'Bici', 500); -- id 3

-- 1. Una tarea aprobada ingresa 30 monedas: el banco queda en -30 y el monedero en 30.
INSERT INTO movimiento (familia_id, cuenta_origen_id, cuenta_destino_id, cantidad, tipo, concepto, autor_id)
     VALUES (1, 1, 2, 30, 'RECOMPENSA_TAREA', 'Hacer la cama', 1);
DO $$ BEGIN
  IF (SELECT saldo FROM cuenta WHERE id = 2) <> 30 OR (SELECT saldo FROM cuenta WHERE id = 1) <> -30 THEN
    RAISE EXCEPTION 'FALLO 1: saldos tras la recompensa'; END IF; END $$;

-- 2. Ahorrar 20 en la hucha.
INSERT INTO movimiento (familia_id, cuenta_origen_id, cuenta_destino_id, cantidad, tipo, concepto, autor_id)
     VALUES (1, 2, 3, 20, 'AHORRO', 'A la hucha', 2);

-- 3. Gastar más de lo que hay se rechaza (RNF-08).
DO $$ BEGIN
  BEGIN
    INSERT INTO movimiento (familia_id, cuenta_origen_id, cuenta_destino_id, cantidad, tipo, concepto)
         VALUES (1, 2, 1, 11, 'COMPRA', 'Helado');
    RAISE EXCEPTION 'FALLO 3: se permitió saldo negativo';
  EXCEPTION WHEN check_violation THEN NULL; END; END $$;

-- 4. Un movimiento no se puede modificar ni borrar (RNF-09).
DO $$ BEGIN
  BEGIN
    UPDATE movimiento SET cantidad = 1000 WHERE id = 1;
    RAISE EXCEPTION 'FALLO 4: se modificó un movimiento';
  EXCEPTION WHEN integrity_constraint_violation THEN NULL; END;
  BEGIN
    DELETE FROM movimiento WHERE id = 1;
    RAISE EXCEPTION 'FALLO 4: se borró un movimiento';
  EXCEPTION WHEN integrity_constraint_violation THEN NULL; END; END $$;

-- 5. No se puede mover dinero a una cuenta de otra familia (RNF-03).
INSERT INTO familia (nombre) VALUES ('Otra familia');
INSERT INTO cuenta (familia_id, tipo, nombre) VALUES (2, 'BANCO', 'Banco de otra');             -- id 4
DO $$ BEGIN
  BEGIN
    INSERT INTO movimiento (familia_id, cuenta_origen_id, cuenta_destino_id, cantidad, tipo, concepto)
         VALUES (1, 2, 4, 5, 'TRANSFERENCIA', 'Fuga');
    RAISE EXCEPTION 'FALLO 5: movimiento entre familias';
  EXCEPTION WHEN integrity_constraint_violation THEN NULL; END; END $$;

-- 6. Una corrección es un movimiento contrario enlazado al original.
INSERT INTO movimiento (familia_id, cuenta_origen_id, cuenta_destino_id, cantidad, tipo, concepto, autor_id, corrige_a_id)
     VALUES (1, 2, 1, 10, 'CORRECCION', 'Anula parte de la recompensa', 1, 1);

-- 7. La misma clave de idempotencia no cobra dos veces.
INSERT INTO movimiento (familia_id, cuenta_origen_id, cuenta_destino_id, cantidad, tipo, concepto, clave_idempotencia)
     VALUES (1, 1, 2, 5, 'PAGA', 'Paga', 'clave-1');
DO $$ BEGIN
  BEGIN
    INSERT INTO movimiento (familia_id, cuenta_origen_id, cuenta_destino_id, cantidad, tipo, concepto, clave_idempotencia)
         VALUES (1, 1, 2, 5, 'PAGA', 'Paga repetida', 'clave-1');
    RAISE EXCEPTION 'FALLO 7: se repitió una operación';
  EXCEPTION WHEN unique_violation THEN NULL; END; END $$;

-- 8. El saldo cacheado cuadra con el libro y la suma de todas las cuentas es 0.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM cuenta_cuadre WHERE saldo_cacheado <> saldo_libro) THEN
    RAISE EXCEPTION 'FALLO 8: descuadre'; END IF;
  IF (SELECT sum(saldo) FROM cuenta WHERE familia_id = 1) <> 0 THEN
    RAISE EXCEPTION 'FALLO 8: la familia no suma 0'; END IF;
  IF (SELECT saldo FROM cuenta WHERE id = 2) <> 5 OR (SELECT saldo FROM cuenta WHERE id = 3) <> 20 THEN
    RAISE EXCEPTION 'FALLO 8: saldos finales'; END IF; END $$;

-- 9. Una tarea solo puede estar APROBADA si tiene su movimiento de pago.
INSERT INTO tarea (familia_id, titulo, recompensa, periodicidad, creada_por) VALUES (1, 'Cama', 5, 'UNICA', 1);
DO $$ BEGIN
  BEGIN
    INSERT INTO asignacion_tarea (familia_id, tarea_id, hijo_id, fecha, estado) VALUES (1, 1, 2, current_date, 'APROBADA');
    RAISE EXCEPTION 'FALLO 9: aprobada sin pago';
  EXCEPTION WHEN check_violation THEN NULL; END; END $$;

\echo 'TODAS LAS PRUEBAS DEL LIBRO PASAN'
ROLLBACK;
