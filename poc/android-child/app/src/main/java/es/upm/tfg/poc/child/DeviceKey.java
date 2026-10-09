package es.upm.tfg.poc.child;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

/**
 * La clave del hijo vive en el Android Keystore (chip seguro): la app nunca ve la clave privada,
 * solo puede pedir firmas, y cada firma exige la huella (BiometricPrompt con CryptoObject).
 * Curva P-256 (secp256r1): Keystore no tiene la secp256k1 de Ethereum; el contrato la verifica
 * con el precompilado EIP-7951.
 */
final class DeviceKey {

    static final String ALIAS = "tfg-hucha-hijo";

    private DeviceKey() {
    }

    private static KeyStore keyStore() throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            return ks;
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    static boolean exists() throws GeneralSecurityException {
        return keyStore().containsAlias(ALIAS);
    }

    /** Crea la clave. Intenta StrongBox (chip dedicado) y, si el móvil no lo tiene, usa el TEE. */
    static void create() throws GeneralSecurityException {
        try {
            generate(true);
        } catch (StrongBoxUnavailableException e) {
            generate(false);
        }
    }

    private static void generate(boolean strongBox) throws GeneralSecurityException {
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                // Cada firma necesita la huella (0 = no vale una autenticación anterior).
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                // Si alguien añade una huella nueva al móvil, la clave deja de valer.
                .setInvalidatedByBiometricEnrollment(true)
                .setIsStrongBoxBacked(strongBox)
                .build();
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        generator.initialize(spec);
        generator.generateKeyPair();
    }

    static void delete() throws GeneralSecurityException {
        keyStore().deleteEntry(ALIAS);
    }

    /** (x, y) de la clave pública: lo que el padre registra en el contrato. */
    static byte[][] publicKeyXY() throws GeneralSecurityException {
        ECPublicKey pub = (ECPublicKey) keyStore().getCertificate(ALIAS).getPublicKey();
        BigInteger x = pub.getW().getAffineX();
        BigInteger y = pub.getW().getAffineY();
        return new byte[][] { SpendMessage.to32(x), SpendMessage.to32(y) };
    }

    /** Firma preparada; solo podrá usarse tras poner el dedo (se pasa a BiometricPrompt). */
    static Signature signatureForPrompt() throws GeneralSecurityException {
        PrivateKey key = (PrivateKey) keyStore().getKey(ALIAS, null);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key);
        return signature;
    }

    /** Dónde está la clave de verdad: evidencia para la memoria. */
    static String securityLevel() throws GeneralSecurityException {
        PrivateKey key = (PrivateKey) keyStore().getKey(ALIAS, null);
        KeyInfo info = KeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore").getKeySpec(key, KeyInfo.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            switch (info.getSecurityLevel()) {
                case KeyProperties.SECURITY_LEVEL_STRONGBOX:
                    return "StrongBox (chip seguro dedicado)";
                case KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
                    return "TEE (entorno seguro del procesador)";
                case KeyProperties.SECURITY_LEVEL_SOFTWARE:
                    return "software (sin hardware seguro)";
                default:
                    return "desconocido";
            }
        }
        @SuppressWarnings("deprecation")
        boolean hardware = info.isInsideSecureHardware();
        return hardware ? "hardware seguro" : "software (sin hardware seguro)";
    }
}
