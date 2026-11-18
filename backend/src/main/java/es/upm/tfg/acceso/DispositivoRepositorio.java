package es.upm.tfg.acceso;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface DispositivoRepositorio extends JpaRepository<Dispositivo, Long> {

    /** Bloquea la fila para que dos refrescos simultáneos con el mismo token no ganen los dos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Dispositivo> findByHashTokenRefresco(String hash);

    List<Dispositivo> findByMiembroIdAndRevocadoEnIsNullOrderById(Long miembroId);
}
