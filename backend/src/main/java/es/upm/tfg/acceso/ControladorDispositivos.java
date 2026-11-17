package es.upm.tfg.acceso;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import es.upm.tfg.acceso.Respuestas.CodigoAccesoDto;
import es.upm.tfg.acceso.Respuestas.DispositivoDto;
import es.upm.tfg.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Códigos de vinculación e invitación y gestión de dispositivos (etiqueta Familia, solo adultos). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Familia")
@PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
public class ControladorDispositivos {

    private final ServicioAcceso servicio;

    public ControladorDispositivos(ServicioAcceso servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/familia/miembros/{miembroId}/codigo-vinculacion")
    @ResponseStatus(HttpStatus.CREATED)
    public CodigoAccesoDto generarCodigoVinculacion(@AuthenticationPrincipal Jwt jwt, @PathVariable long miembroId) {
        return servicio.generarCodigoVinculacion(UsuarioActual.de(jwt), miembroId);
    }

    @GetMapping("/familia/miembros/{miembroId}/dispositivos")
    public List<DispositivoDto> listarDispositivos(@AuthenticationPrincipal Jwt jwt, @PathVariable long miembroId) {
        return servicio.listarDispositivos(UsuarioActual.de(jwt), miembroId);
    }

    @DeleteMapping("/dispositivos/{dispositivoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocarDispositivo(@AuthenticationPrincipal Jwt jwt, @PathVariable long dispositivoId) {
        servicio.revocarDispositivo(UsuarioActual.de(jwt), dispositivoId);
    }

    @PostMapping("/familia/invitaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADULTO_ADMIN')")
    public CodigoAccesoDto invitarAdulto(@AuthenticationPrincipal Jwt jwt) {
        return servicio.invitarAdulto(UsuarioActual.de(jwt));
    }
}
