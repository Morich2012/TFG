package es.upm.tfg.familia;

import java.util.List;

import org.springframework.http.HttpStatus;
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

/** Rutas {@code /familia} y {@code /familia/miembros} de la OpenAPI (etiqueta Familia). */
@RestController
@RequestMapping("/api/v1/familia")
@Tag(name = "Familia")
public class ControladorFamilia {

    private final ServicioFamilia servicio;

    public ControladorFamilia(ServicioFamilia servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public FamiliaDto obtenerFamilia(@AuthenticationPrincipal Jwt jwt) {
        return servicio.obtener(UsuarioActual.de(jwt));
    }

    @PatchMapping
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public FamiliaDto editarFamilia(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FamiliaCambios cambios) {
        return servicio.editar(UsuarioActual.de(jwt), cambios);
    }

    @GetMapping("/miembros")
    public List<MiembroDto> listarMiembros(@AuthenticationPrincipal Jwt jwt) {
        return servicio.listar(UsuarioActual.de(jwt));
    }

    @PostMapping("/miembros")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public MiembroDto altaHijo(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AltaHijoPeticion peticion) {
        return servicio.altaHijo(UsuarioActual.de(jwt), peticion);
    }

    @PatchMapping("/miembros/{miembroId}")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public MiembroDto editarMiembro(@AuthenticationPrincipal Jwt jwt, @PathVariable long miembroId,
            @Valid @RequestBody MiembroCambios cambios) {
        return servicio.editarMiembro(UsuarioActual.de(jwt), miembroId, cambios);
    }

    @DeleteMapping("/miembros/{miembroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADULTO_ADMIN')")
    public void bajaMiembro(@AuthenticationPrincipal Jwt jwt, @PathVariable long miembroId) {
        servicio.baja(UsuarioActual.de(jwt), miembroId);
    }
}
