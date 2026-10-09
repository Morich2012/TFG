package es.upm.tfg.poc.relayer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

/**
 * La única clave privada que tiene el backend: la del relayer. Solo sirve para pagar el gas
 * (y, en esta PoC, para actuar como "padre" del contrato al registrar el móvil y dar recompensas).
 * No controla el dinero del hijo: sus gastos solo valen si los firma su móvil.
 *
 * Se genera la primera vez y se guarda fuera del repositorio, así nadie tiene que exportar
 * claves de MetaMask ni copiarlas a mano. Basta con enviarle un poco de SepoliaETH a su dirección.
 */
final class RelayerKey {

    private RelayerKey() {
    }

    static Credentials loadOrCreate(Path dataDir) {
        Path file = dataDir.resolve("relayer.key");
        try {
            if (Files.exists(file)) {
                return Credentials.create(Files.readString(file, StandardCharsets.UTF_8).trim());
            }
            Files.createDirectories(dataDir);
            String privateKey = Numeric.toHexStringWithPrefixZeroPadded(Keys.createEcKeyPair().getPrivateKey(), 64);
            Files.writeString(file, privateKey + "\n", StandardCharsets.UTF_8);
            restrictToOwner(file);
            return Credentials.create(privateKey);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer o crear " + file, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo generar la clave del relayer", e);
        }
    }

    private static void restrictToOwner(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException windows) {
            // En Windows no hay permisos POSIX; el fichero queda en la carpeta del usuario.
        }
    }
}
