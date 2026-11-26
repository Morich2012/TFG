package es.upm.tfg.tareas;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface AsignacionRepositorio extends JpaRepository<Asignacion, Long> {

    /**
     * Bloquea la fila (SELECT ... FOR UPDATE) hasta el final de la transacción: si dos adultos
     * revisan la misma tarea a la vez, el segundo espera y luego ve que ya no está HECHA.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Asignacion> findWithLockByIdAndFamiliaId(Long id, Long familiaId);
}
