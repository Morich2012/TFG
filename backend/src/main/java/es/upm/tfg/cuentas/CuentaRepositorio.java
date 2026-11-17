package es.upm.tfg.cuentas;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CuentaRepositorio extends JpaRepository<Cuenta, Long> {

    List<Cuenta> findByFamiliaId(Long familiaId);
}
