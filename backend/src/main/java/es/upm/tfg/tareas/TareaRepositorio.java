package es.upm.tfg.tareas;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TareaRepositorio extends JpaRepository<Tarea, Long> {

    Optional<Tarea> findByIdAndFamiliaId(Long id, Long familiaId);

    List<Tarea> findByFamiliaIdOrderById(Long familiaId);

    List<Tarea> findByFamiliaIdAndArchivadaEnIsNullOrderById(Long familiaId);
}
