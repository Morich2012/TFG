package es.upm.tfg.acceso;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import es.upm.tfg.acceso.Peticiones.RegistroPeticion;
import es.upm.tfg.acceso.Peticiones.SesionCambios;
import es.upm.tfg.acceso.Peticiones.SesionPeticion;
import es.upm.tfg.acceso.Respuestas.Sesion;
import es.upm.tfg.acceso.Respuestas.SesionActual;
import es.upm.tfg.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Etiqueta Acceso de la OpenAPI. Registrarse es crear una familia ({@code POST /familias}) y
 * entrar es crear una sesión ({@code POST /sesiones}); las dos son públicas.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Acceso")
public class ControladorAcceso {

    private final ServicioAcceso servicio;

    public ControladorAcceso(ServicioAcceso servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/familias")
    public ResponseEntity<Sesion> registrarFamilia(@Valid @RequestBody RegistroPeticion peticion) {
        Sesion sesion = servicio.registrar(peticion);
        return ResponseEntity.created(URI.create("/api/v1/familias/" + sesion.familiaId())).body(sesion);
    }

    @PostMapping("/sesiones")
    @ResponseStatus(HttpStatus.CREATED)
    public Sesion crearSesion(@Valid @RequestBody SesionPeticion peticion) {
        return servicio.crearSesion(peticion);
    }

    @GetMapping("/sesiones/actual")
    public SesionActual obtenerSesionActual(@AuthenticationPrincipal Jwt jwt) {
        return servicio.sesionActual(UsuarioActual.de(jwt));
    }

    @PatchMapping("/sesiones/actual")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void editarSesionActual(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SesionCambios cambios) {
        servicio.guardarTokenPush(UsuarioActual.de(jwt), cambios.tokenPush());
    }

    @DeleteMapping("/sesiones/actual")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrarSesion(@AuthenticationPrincipal Jwt jwt) {
        servicio.cerrarSesion(UsuarioActual.de(jwt));
    }
}
