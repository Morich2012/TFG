package es.upm.tfg.comun;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens de refresco y códigos de un solo uso. En la base de datos solo se guarda su
 * SHA-256: si alguien leyera la tabla no podría usarlos. Basta un hash rápido porque son
 * aleatorios y largos (no son contraseñas elegidas por personas).
 */
public final class Secretos {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    /** Sin 0/O, 1/I/L para que el código se pueda teclear sin confusiones. */
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private Secretos() {
    }

    /** Token opaco de 256 bits en Base64 URL. */
    public static String tokenAleatorio() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Código legible con formato {@code XXXX-XXXX}. */
    public static String codigoLegible() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(ALFABETO_CODIGO.charAt(ALEATORIO.nextInt(ALFABETO_CODIGO.length())));
        }
        return sb.toString();
    }

    /** Hash de un código tecleado: ignora mayúsculas, espacios y guiones. */
    public static String hashCodigo(String codigo) {
        return sha256(codigo.replaceAll("[\\s-]", "").toUpperCase());
    }

    public static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
