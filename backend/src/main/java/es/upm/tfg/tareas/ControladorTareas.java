package es.upm.tfg.tareas;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import es.upm.tfg.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Tareas, plantillas y asignaciones con sus entregas y revisiones (etiqueta Tareas). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tareas")
public class ControladorTareas {

    private final ServicioTareas tareas;
    private final ServicioAsignaciones asignaciones;

    public ControladorTareas(ServicioTareas tareas, ServicioAsignaciones asignaciones) {
        this.tareas = tareas;
        this.asignaciones = asignaciones;
    }

    @GetMapping("/plantillas-tarea")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public List<PlantillaTareaDto> plantillasTareas() {
        return tareas.plantillas();
    }

    @GetMapping("/familias/{familiaId}/tareas")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public List<TareaDto> listarTareas(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @RequestParam(defaultValue = "false") boolean incluirArchivadas) {
        return tareas.listar(UsuarioActual.de(jwt, familiaId), incluirArchivadas);
    }

    @PostMapping("/familias/{familiaId}/tareas")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public ResponseEntity<TareaDto> crearTarea(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @Valid @RequestBody TareaPeticion peticion) {
        TareaDto t = tareas.crear(UsuarioActual.de(jwt, familiaId), peticion);
        return ResponseEntity.created(URI.create("/api/v1/familias/" + familiaId + "/tareas/" + t.id())).body(t);
    }

    @PatchMapping("/familias/{familiaId}/tareas/{tareaId}")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public TareaDto editarTarea(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @PathVariable long tareaId, @Valid @RequestBody TareaCambios cambios) {
        return tareas.editar(UsuarioActual.de(jwt, familiaId), tareaId, cambios);
    }

    @DeleteMapping("/familias/{familiaId}/tareas/{tareaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public void archivarTarea(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @PathVariable long tareaId) {
        tareas.archivar(UsuarioActual.de(jwt, familiaId), tareaId);
    }

    @GetMapping("/familias/{familiaId}/asignaciones")
    public List<AsignacionDto> listarAsignaciones(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @RequestParam(required = false) Long hijoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) EstadoAsignacion estado) {
        return asignaciones.listar(UsuarioActual.de(jwt, familiaId), hijoId, fecha, estado);
    }

    @PostMapping("/familias/{familiaId}/asignaciones/{asignacionId}/entregas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HIJO')")
    public AsignacionDto marcarHecha(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @PathVariable long asignacionId) {
        return asignaciones.entregar(UsuarioActual.de(jwt, familiaId), asignacionId);
    }

    @PostMapping("/familias/{familiaId}/asignaciones/{asignacionId}/revisiones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public AsignacionDto revisarTarea(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @PathVariable long asignacionId,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 64) String clave,
            @Valid @RequestBody RevisionPeticion peticion) {
        return asignaciones.revisar(UsuarioActual.de(jwt, familiaId), asignacionId, peticion, clave);
    }
}
