package es.upm.tfg.acceso;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface CodigoAccesoRepositorio extends JpaRepository<CodigoAcceso, Long> {

    /** Bloquea la fila: si dos móviles canjean el mismo código a la vez, solo uno lo consigue. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoAcceso> findByHashCodigo(String hash);

    /** Anula los códigos pendientes de un miembro (al darlo de baja o al generar uno nuevo). */
    @Modifying
    @Query("UPDATE CodigoAcceso c SET c.usadoEn = :ahora WHERE c.miembroId = :miembroId AND c.usadoEn IS NULL")
    int anularPendientes(Long miembroId, Instant ahora);
}
