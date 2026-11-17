package es.upm.tfg.acceso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.upm.tfg.acceso.Peticiones.LoginPeticion;
import es.upm.tfg.acceso.Peticiones.RefrescarPeticion;
import es.upm.tfg.acceso.Peticiones.RegistroPeticion;
import es.upm.tfg.acceso.Peticiones.UnirsePeticion;
import es.upm.tfg.acceso.Peticiones.VincularPeticion;
import es.upm.tfg.acceso.Respuestas.CodigoAccesoDto;
import es.upm.tfg.acceso.Respuestas.DispositivoDto;
import es.upm.tfg.acceso.Respuestas.Sesion;
import es.upm.tfg.comun.CodigoError;
import es.upm.tfg.comun.ErrorApi;
import es.upm.tfg.comun.Secretos;
import es.upm.tfg.familia.Familia;
import es.upm.tfg.familia.Miembro;
import es.upm.tfg.familia.MiembroDadoDeBaja;
import es.upm.tfg.familia.MiembroRepositorio;
import es.upm.tfg.familia.Rol;
import es.upm.tfg.familia.ServicioFamilia;
import es.upm.tfg.seguridad.PropiedadesSeguridad;
import es.upm.tfg.seguridad.ServicioTokens;
import es.upm.tfg.seguridad.UsuarioActual;

/**
 * Registro, inicio de sesión y vinculación de dispositivos (RF-01, RF-02, RF-04).
 *
 * <p>Cada sesión es un {@link Dispositivo} con un token de refresco opaco (se guarda su hash) y un
 * token de acceso JWT de 15 minutos. El token de refresco se rota en cada uso.
 */
@Service
@Transactional
public class ServicioAcceso {

    private static final String DISPOSITIVO_POR_DEFECTO = "Dispositivo sin nombre";

    private final ServicioFamilia familias;
    private final MiembroRepositorio miembros;
    private final DispositivoRepositorio dispositivos;
    private final CodigoAccesoRepositorio codigos;
    private final ServicioTokens tokens;
    private final PasswordEncoder codificador;
    private final LimitadorIntentosPin limitador;
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;
    /** Se compara con él cuando el correo no existe, para tardar lo mismo y no revelar qué correos hay. */
    private final String hashFicticio;

    public ServicioAcceso(ServicioFamilia familias, MiembroRepositorio miembros, DispositivoRepositorio dispositivos,
            CodigoAccesoRepositorio codigos, ServicioTokens tokens, PasswordEncoder codificador,
            LimitadorIntentosPin limitador, PropiedadesSeguridad propiedades, Clock reloj) {
        this.familias = familias;
        this.miembros = miembros;
        this.dispositivos = dispositivos;
        this.codigos = codigos;
        this.tokens = tokens;
        this.codificador = codificador;
        this.limitador = limitador;
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.hashFicticio = codificador.encode(Secretos.tokenAleatorio());
    }

    /** RF-01: crea la familia, su banco y al adulto como administrador, e inicia sesión. */
    public Sesion registrar(RegistroPeticion p) {
        String email = normalizar(p.email());
        comprobarCorreoLibre(email);
        Familia familia = familias.crearFamilia(p.nombreFamilia());
        Miembro admin = miembros.save(Miembro.adulto(familia.getId(), Rol.ADULTO_ADMIN, p.nombre().strip(), email,
                codificador.encode(p.contrasena())));
        return abrirSesion(admin, DISPOSITIVO_POR_DEFECTO);
    }

    public Sesion iniciarSesion(LoginPeticion p) {
        Miembro m = miembros.findByEmail(normalizar(p.email())).orElse(null);
        boolean correcta = codificador.matches(p.contrasena(), m == null ? hashFicticio : m.getHashContrasena());
        if (m == null || !correcta || !m.estaActivo()) {
            throw new ErrorApi(CodigoError.NO_AUTENTICADO, "Correo o contraseña incorrectos");
        }
        return abrirSesion(m, p.nombreDispositivo());
    }

    /** RF-04: el móvil del hijo canjea el código del QR por una sesión. */
    public Sesion vincular(VincularPeticion p) {
        CodigoAcceso codigo = canjear(p.codigo(), TipoCodigo.VINCULACION);
        Miembro hijo = miembros.findByIdAndFamiliaIdAndDadoDeBajaEnIsNull(codigo.getMiembroId(), codigo.getFamiliaId())
                .orElseThrow(() -> new ErrorApi(CodigoError.CODIGO_CADUCADO));
        return abrirSesion(hijo, p.nombreDispositivo());
    }

    /** RF-02: un segundo adulto se une a la familia con el código de invitación. */
    public Sesion unirse(UnirsePeticion p) {
        String email = normalizar(p.email());
        comprobarCorreoLibre(email);
        CodigoAcceso codigo = canjear(p.codigo(), TipoCodigo.INVITACION);
        Miembro adulto = miembros.save(Miembro.adulto(codigo.getFamiliaId(), Rol.ADULTO, p.nombre().strip(), email,
                codificador.encode(p.contrasena())));
        return abrirSesion(adulto, p.nombreDispositivo());
    }

    /**
     * Cambia el token de refresco por uno nuevo y un JWT nuevo. Si el hijo entra con PIN en vez de
     * huella, la app lo envía y se comprueba con límite de intentos.
     */
    public Sesion refrescar(RefrescarPeticion p) {
        Dispositivo d = dispositivos.findByHashTokenRefresco(Secretos.sha256(p.tokenRefresco()))
                .filter(Dispositivo::estaActivo)
                .orElseThrow(() -> new ErrorApi(CodigoError.NO_AUTENTICADO, "La sesión ha caducado"));
        Miembro m = miembros.findById(d.getMiembroId()).filter(Miembro::estaActivo)
                .orElseThrow(() -> new ErrorApi(CodigoError.NO_AUTENTICADO, "La sesión ha caducado"));
        if (p.pin() != null) {
            comprobarPin(d, m, p.pin());
        }
        String nuevoToken = Secretos.tokenAleatorio();
        d.rotarToken(Secretos.sha256(nuevoToken));
        return sesion(m, d, nuevoToken);
    }

    public void cerrarSesion(UsuarioActual u) {
        dispositivos.findById(u.dispositivoId()).ifPresent(Dispositivo::revocar);
    }

    public void guardarTokenPush(UsuarioActual u, String tokenPush) {
        Dispositivo d = dispositivos.findById(u.dispositivoId()).filter(Dispositivo::estaActivo)
                .orElseThrow(() -> new ErrorApi(CodigoError.NO_AUTENTICADO));
        d.setTokenPush(tokenPush);
    }

    /** Código QR para vincular el móvil de un hijo. Anula los códigos anteriores sin usar. */
    public CodigoAccesoDto generarCodigoVinculacion(UsuarioActual u, long hijoId) {
        Miembro hijo = familias.miembroDeLaFamilia(u, hijoId);
        if (hijo.getRol() != Rol.HIJO) {
            throw new ErrorApi(CodigoError.ESTADO_INVALIDO, "Solo se vinculan dispositivos de hijos");
        }
        codigos.anularPendientes(hijo.getId(), reloj.instant());
        return nuevoCodigo(u, TipoCodigo.VINCULACION, hijo.getId(), propiedades.duracionCodigoVinculacion());
    }

    public CodigoAccesoDto invitarAdulto(UsuarioActual u) {
        return nuevoCodigo(u, TipoCodigo.INVITACION, null, propiedades.duracionCodigoInvitacion());
    }

    @Transactional(readOnly = true)
    public List<DispositivoDto> listarDispositivos(UsuarioActual u, long miembroId) {
        Miembro m = familias.miembroDeLaFamilia(u, miembroId);
        return dispositivos.findByMiembroIdAndRevocadoEnIsNullOrderById(m.getId()).stream()
                .map(DispositivoDto::de)
                .toList();
    }

    /** Desvincula un dispositivo (un móvil perdido). Debe ser de un miembro de la familia. */
    public void revocarDispositivo(UsuarioActual u, long dispositivoId) {
        Dispositivo d = dispositivos.findById(dispositivoId).filter(Dispositivo::estaActivo)
                .orElseThrow(ErrorApi::noEncontrado);
        familias.miembroDeLaFamilia(u, d.getMiembroId()); // 404 si es de otra familia
        d.revocar();
    }

    /** Al dar de baja a un miembro se cierran todas sus sesiones y se anulan sus códigos. */
    @EventListener
    public void alDarDeBaja(MiembroDadoDeBaja evento) {
        dispositivos.findByMiembroIdAndRevocadoEnIsNullOrderById(evento.miembroId()).forEach(Dispositivo::revocar);
        codigos.anularPendientes(evento.miembroId(), reloj.instant());
    }

    // ------------------------------------------------------------------------------------------

    private Sesion abrirSesion(Miembro m, String nombreDispositivo) {
        String nombre = nombreDispositivo == null || nombreDispositivo.isBlank()
                ? DISPOSITIVO_POR_DEFECTO : nombreDispositivo.strip();
        String tokenRefresco = Secretos.tokenAleatorio();
        Dispositivo d = dispositivos.save(new Dispositivo(m.getId(), nombre, Secretos.sha256(tokenRefresco)));
        return sesion(m, d, tokenRefresco);
    }

    private Sesion sesion(Miembro m, Dispositivo d, String tokenRefresco) {
        ServicioTokens.TokenAcceso acceso = tokens.emitir(m, d.getId());
        return new Sesion(acceso.valor(), tokenRefresco, acceso.expiraEn(), familias.dto(m));
    }

    private void comprobarPin(Dispositivo d, Miembro m, String pin) {
        if (limitador.estaBloqueado(d.getId())) {
            throw new ErrorApi(CodigoError.DEMASIADOS_INTENTOS, "Has fallado el PIN demasiadas veces. Espera un poco.");
        }
        if (m.getHashPin() == null || !codificador.matches(pin, m.getHashPin())) {
            limitador.registrarFallo(d.getId());
            throw new ErrorApi(CodigoError.NO_AUTENTICADO, "PIN incorrecto");
        }
        limitador.registrarAcierto(d.getId());
    }

    private CodigoAcceso canjear(String texto, TipoCodigo tipo) {
        Instant ahora = reloj.instant();
        CodigoAcceso codigo = codigos.findByHashCodigo(Secretos.hashCodigo(texto))
                .filter(c -> c.sePuedeUsar(tipo, ahora))
                .orElseThrow(() -> new ErrorApi(CodigoError.CODIGO_CADUCADO, "El código ha caducado o ya se ha usado"));
        codigo.marcarUsado(ahora);
        return codigo;
    }

    private CodigoAccesoDto nuevoCodigo(UsuarioActual u, TipoCodigo tipo, Long miembroId, Duration duracion) {
        String texto = Secretos.codigoLegible();
        Instant expira = reloj.instant().plus(duracion);
        codigos.save(new CodigoAcceso(u.familiaId(), tipo, miembroId, Secretos.hashCodigo(texto), u.miembroId(), expira));
        String accion = tipo == TipoCodigo.VINCULACION ? "vincular" : "unirse";
        return new CodigoAccesoDto(texto, expira, "tfgfamilia://" + accion + "?codigo=" + texto);
    }

    private void comprobarCorreoLibre(String email) {
        if (miembros.existsByEmail(email)) {
            throw new ErrorApi(CodigoError.CORREO_EN_USO, "Ya hay una cuenta con ese correo");
        }
    }

    private static String normalizar(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
