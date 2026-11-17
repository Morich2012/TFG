package es.upm.tfg.familia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MiembroRepositorio extends JpaRepository<Miembro, Long> {

    Optional<Miembro> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Busca siempre dentro de la familia del token: un id de otra familia no aparece (RNF-03). */
    Optional<Miembro> findByIdAndFamiliaIdAndDadoDeBajaEnIsNull(Long id, Long familiaId);

    List<Miembro> findByFamiliaIdAndDadoDeBajaEnIsNullOrderById(Long familiaId);
}
