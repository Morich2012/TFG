package es.upm.tfg.comun;

/** Error de negocio que el manejador global convierte en un Problema con su código. */
public class ErrorApi extends RuntimeException {

    private final CodigoError codigo;

    public ErrorApi(CodigoError codigo, String detalle) {
        super(detalle);
        this.codigo = codigo;
    }

    public ErrorApi(CodigoError codigo) {
        this(codigo, codigo.titulo());
    }

    public CodigoError codigo() {
        return codigo;
    }

    public static ErrorApi noEncontrado() {
        return new ErrorApi(CodigoError.NO_ENCONTRADO);
    }

    public static ErrorApi prohibido(String detalle) {
        return new ErrorApi(CodigoError.PROHIBIDO, detalle);
    }

    public static ErrorApi datosInvalidos(String detalle) {
        return new ErrorApi(CodigoError.DATOS_INVALIDOS, detalle);
    }

    public static ErrorApi estadoInvalido(String detalle) {
        return new ErrorApi(CodigoError.ESTADO_INVALIDO, detalle);
    }
}
