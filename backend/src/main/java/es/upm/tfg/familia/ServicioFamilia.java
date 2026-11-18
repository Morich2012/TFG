package es.upm.tfg.familia;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.upm.tfg.comun.ErrorApi;
import es.upm.tfg.comun.Secretos;
import es.upm.tfg.cuentas.ServicioCuentas;
import es.upm.tfg.seguridad.UsuarioActual;

/** Familia y miembros (RF-01, RF-03, RF-05, RF-35). */
@Service
@Transactional
public class ServicioFamilia {

    private final FamiliaRepositorio familias;
    private final MiembroRepositorio miembros;
    private final ServicioCuentas cuentas;
    private final ServicioEtapas etapas;
    private final PasswordEncoder codificador;
    private final ApplicationEventPublisher eventos;

    public ServicioFamilia(FamiliaRepositorio familias, MiembroRepositorio miembros, ServicioCuentas cuentas,
            ServicioEtapas etapas, PasswordEncoder codificador, ApplicationEventPublisher eventos) {
        this.familias = familias;
        this.miembros = miembros;
        this.cuentas = cuentas;
        this.etapas = etapas;
        this.codificador = codificador;
        this.eventos = eventos;
    }

    /** Crea la familia con su cuenta BANCO (la que emite las monedas). */
    public Familia crearFamilia(String nombre) {
        Familia familia = familias.save(new Familia(nombre.strip()));
        cuentas.abrirBanco(familia.getId());
        return familia;
    }

    @Transactional(readOnly = true)
    public FamiliaDto obtener(UsuarioActual u) {
        return FamiliaDto.de(familia(u));
    }

    public FamiliaDto editar(UsuarioActual u, FamiliaCambios cambios) {
        if (cambios.vacio()) {
            throw ErrorApi.datosInvalidos("No hay ningún cambio");
        }
        Familia familia = familia(u);
        if (cambios.nombre() != null) {
            familia.setNombre(cambios.nombre().strip());
        }
        if (cambios.nombreMoneda() != null) {
            familia.setNombreMoneda(cambios.nombreMoneda().strip());
        }
        if (cambios.monedasPorEuro() != null) {
            // No altera saldos: solo afecta a canjes y precios futuros.
            familia.setMonedasPorEuro(cambios.monedasPorEuro());
        }
        return FamiliaDto.de(familia);
    }

    @Transactional(readOnly = true)
    public List<MiembroDto> listar(UsuarioActual u) {
        return miembros.findByFamiliaIdAndDadoDeBajaEnIsNullOrderById(u.familiaId()).stream()
                .map(this::dto)
                .toList();
    }

    /** Alta de un hijo sin correo, con su monedero (RF-03). */
    public MiembroDto altaHijo(UsuarioActual u, AltaHijoPeticion p) {
        Miembro hijo = miembros.save(Miembro.hijo(u.familiaId(), p.nombre().strip(), p.avatar(),
                p.anioNacimiento() == null ? null : p.anioNacimiento().shortValue(),
                p.pin() == null ? null : codificador.encode(p.pin())));
        cuentas.abrirMonedero(u.familiaId(), hijo.getId(), "Monedero de " + hijo.getNombre());
        return dto(hijo);
    }

    /**
     * Un adulto edita a los hijos y a sí mismo; solo el administrador edita a otros adultos.
     */
    public MiembroDto editarMiembro(UsuarioActual u, long miembroId, MiembroCambios c) {
        if (c.vacio()) {
            throw ErrorApi.datosInvalidos("No hay ningún cambio");
        }
        Miembro m = miembroDeLaFamilia(u, miembroId);
        if (m.getRol().esAdulto() && m.getId() != u.miembroId() && u.rol() != Rol.ADULTO_ADMIN) {
            throw ErrorApi.prohibido("Solo el administrador puede editar a otro adulto");
        }
        if (m.getRol().esAdulto() && (c.anioNacimiento() != null || c.pin() != null)) {
            throw ErrorApi.datosInvalidos("El año de nacimiento y el PIN solo son para los hijos");
        }
        if (c.nombre() != null) {
            m.setNombre(c.nombre().strip());
        }
        if (c.avatar() != null) {
            m.setAvatar(c.avatar());
        }
        if (c.anioNacimiento() != null) {
            m.setAnioNacimiento(c.anioNacimiento().shortValue());
        }
        if (c.pin() != null) {
            m.setHashPin(codificador.encode(c.pin()));
        }
        return dto(m);
    }

    /** Baja y borrado de datos personales (RF-05, RNF-06). El administrador no se da de baja. */
    public void baja(UsuarioActual u, long miembroId) {
        Miembro m = miembroDeLaFamilia(u, miembroId);
        if (m.getRol() == Rol.ADULTO_ADMIN) {
            throw ErrorApi.prohibido("No se puede dar de baja al administrador de la familia");
        }
        m.anonimizar(codificador.encode(Secretos.tokenAleatorio()));
        eventos.publishEvent(new MiembroDadoDeBaja(m.getId(), m.getFamiliaId()));
    }

    /** Miembro activo de la familia del token o 404 (nunca 403, para no revelar que existe). */
    @Transactional(readOnly = true)
    public Miembro miembroDeLaFamilia(UsuarioActual u, long miembroId) {
        return miembros.findByIdAndFamiliaIdAndDadoDeBajaEnIsNull(miembroId, u.familiaId())
                .orElseThrow(ErrorApi::noEncontrado);
    }

    @Transactional(readOnly = true)
    public MiembroDto dto(Miembro m) {
        return MiembroDto.de(m, etapas.etapaDe(m));
    }

    private Familia familia(UsuarioActual u) {
        return familias.findById(u.familiaId()).orElseThrow(ErrorApi::noEncontrado);
    }
}
