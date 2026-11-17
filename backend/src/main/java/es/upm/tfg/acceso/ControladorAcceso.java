package es.upm.tfg.acceso;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import es.upm.tfg.acceso.Peticiones.LoginPeticion;
import es.upm.tfg.acceso.Peticiones.RefrescarPeticion;
import es.upm.tfg.acceso.Peticiones.RegistroPeticion;
import es.upm.tfg.acceso.Peticiones.TokenPushPeticion;
import es.upm.tfg.acceso.Peticiones.UnirsePeticion;
import es.upm.tfg.acceso.Peticiones.VincularPeticion;
import es.upm.tfg.acceso.Respuestas.Sesion;
import es.upm.tfg.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Rutas de la etiqueta Acceso de la OpenAPI. Las de {@code /auth} son públicas salvo cerrar sesión. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Acceso")
public class ControladorAcceso {

    private final ServicioAcceso servicio;

    public ControladorAcceso(ServicioAcceso servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/auth/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public Sesion registrarAdulto(@Valid @RequestBody RegistroPeticion peticion) {
        return servicio.registrar(peticion);
    }

    @PostMapping("/auth/login")
    public Sesion iniciarSesion(@Valid @RequestBody LoginPeticion peticion) {
        return servicio.iniciarSesion(peticion);
    }

    @PostMapping("/auth/vincular")
    @ResponseStatus(HttpStatus.CREATED)
    public Sesion vincularDispositivo(@Valid @RequestBody VincularPeticion peticion) {
        return servicio.vincular(peticion);
    }

    @PostMapping("/auth/unirse")
    @ResponseStatus(HttpStatus.CREATED)
    public Sesion unirseAFamilia(@Valid @RequestBody UnirsePeticion peticion) {
        return servicio.unirse(peticion);
    }

    @PostMapping("/auth/refrescar")
    public Sesion refrescarSesion(@Valid @RequestBody RefrescarPeticion peticion) {
        return servicio.refrescar(peticion);
    }

    @PostMapping("/auth/cerrar-sesion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrarSesion(@AuthenticationPrincipal Jwt jwt) {
        servicio.cerrarSesion(UsuarioActual.de(jwt));
    }

    @PutMapping("/dispositivos/actual/token-push")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardarTokenPush(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TokenPushPeticion peticion) {
        servicio.guardarTokenPush(UsuarioActual.de(jwt), peticion.tokenPush());
    }
}
