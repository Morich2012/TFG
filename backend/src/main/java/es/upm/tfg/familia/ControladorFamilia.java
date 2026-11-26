package es.upm.tfg.familia;

import java.net.URI;
import java.util.List;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import es.upm.tfg.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Recurso familia y su subcolección de miembros: {@code /familias/{familiaId}} y
 * {@code /familias/{familiaId}/miembros/{miembroId}} (etiqueta Familia).
 */
@RestController
@RequestMapping("/api/v1/familias/{familiaId}")
@Tag(name = "Familia")
public class ControladorFamilia {

    private final ServicioFamilia servicio;
    private final ServicioResumen resumen;

    public ControladorFamilia(ServicioFamilia servicio, ServicioResumen resumen) {
        this.servicio = servicio;
        this.resumen = resumen;
    }

    @GetMapping
    public FamiliaDto obtenerFamilia(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId) {
        return servicio.obtener(UsuarioActual.de(jwt, familiaId));
    }

    @PatchMapping
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public FamiliaDto editarFamilia(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @Valid @RequestBody FamiliaCambios cambios) {
        return servicio.editar(UsuarioActual.de(jwt, familiaId), cambios);
    }

    @GetMapping("/miembros")
    public List<MiembroDto> listarMiembros(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId) {
        return servicio.listar(UsuarioActual.de(jwt, familiaId));
    }

    @PostMapping("/miembros")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public ResponseEntity<MiembroDto> altaHijo(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @Valid @RequestBody AltaHijoPeticion peticion) {
        MiembroDto hijo = servicio.altaHijo(UsuarioActual.de(jwt, familiaId), peticion);
        URI ubicacion = URI.create("/api/v1/familias/" + familiaId + "/miembros/" + hijo.id());
        return ResponseEntity.created(ubicacion).body(hijo);
    }

    @GetMapping("/miembros/{miembroId}")
    public MiembroDto obtenerMiembro(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @PathVariable long miembroId) {
        return servicio.dto(servicio.miembroDeLaFamilia(UsuarioActual.de(jwt, familiaId), miembroId));
    }

    @PatchMapping("/miembros/{miembroId}")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public MiembroDto editarMiembro(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @PathVariable long miembroId, @Valid @RequestBody MiembroCambios cambios) {
        return servicio.editarMiembro(UsuarioActual.de(jwt, familiaId), miembroId, cambios);
    }

    @DeleteMapping("/miembros/{miembroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADULTO_ADMIN')")
    public void bajaMiembro(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @PathVariable long miembroId) {
        servicio.baja(UsuarioActual.de(jwt, familiaId), miembroId);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public ResumenFamilia resumenFamilia(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId) {
        return resumen.resumen(UsuarioActual.de(jwt, familiaId));
    }
}
