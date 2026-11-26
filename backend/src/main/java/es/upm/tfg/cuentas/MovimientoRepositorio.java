package es.upm.tfg.cuentas;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoRepositorio extends JpaRepository<Movimiento, Long> {

    Optional<Movimiento> findByIdAndFamiliaId(Long id, Long familiaId);

    Optional<Movimiento> findByFamiliaIdAndClaveIdempotencia(Long familiaId, String claveIdempotencia);

    boolean existsByCorrigeAId(Long corrigeAId);
}
