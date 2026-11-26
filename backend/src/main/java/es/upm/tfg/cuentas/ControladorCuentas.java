package es.upm.tfg.cuentas;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.upm.tfg.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Cuentas, historial de movimientos, ajustes y correcciones (etiqueta Cuentas). Las respuestas 201
 * no llevan Location porque un movimiento no tiene recurso propio: se consulta en el historial.
 */
@RestController
@RequestMapping("/api/v1/familias/{familiaId}")
@Tag(name = "Cuentas")
public class ControladorCuentas {

    private final ConsultasCuentas consultas;
    private final ServicioCuentas servicio;

    public ControladorCuentas(ConsultasCuentas consultas, ServicioCuentas servicio) {
        this.consultas = consultas;
        this.servicio = servicio;
    }

    @GetMapping("/cuentas")
    public List<CuentaDto> listarCuentas(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @RequestParam(required = false) Long titularId, @RequestParam(required = false) TipoCuenta tipo) {
        return consultas.listar(UsuarioActual.de(jwt, familiaId), titularId, tipo);
    }

    @GetMapping("/cuentas/{cuentaId}")
    public CuentaDto obtenerCuenta(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @PathVariable long cuentaId) {
        return consultas.obtener(UsuarioActual.de(jwt, familiaId), cuentaId);
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos")
    public PaginaMovimientos listarMovimientos(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @PathVariable long cuentaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return consultas.movimientos(UsuarioActual.de(jwt, familiaId), cuentaId, desde, hasta, tipo, pagina, tamano);
    }

    @PostMapping("/ajustes")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public ResponseEntity<MovimientoDto> ajusteManual(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 64) String clave,
            @Valid @RequestBody AjustePeticion peticion) {
        MovimientoDto m = servicio.ajustar(UsuarioActual.de(jwt, familiaId), peticion, clave);
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @PutMapping("/movimientos/{movimientoId}/correccion")
    @PreAuthorize("hasAnyRole('ADULTO_ADMIN', 'ADULTO')")
    public ResponseEntity<MovimientoDto> corregirMovimiento(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long familiaId, @PathVariable long movimientoId,
            @Valid @RequestBody CorreccionPeticion peticion) {
        MovimientoDto m = servicio.corregir(UsuarioActual.de(jwt, familiaId), movimientoId, peticion);
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @GetMapping("/categorias")
    @Tag(name = "Tienda")
    public List<CategoriaDto> listarCategorias(@AuthenticationPrincipal Jwt jwt, @PathVariable long familiaId) {
        return consultas.categorias(UsuarioActual.de(jwt, familiaId));
    }
}
