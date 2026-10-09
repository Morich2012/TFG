package es.upm.tfg.poc.child;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.math.BigInteger;
import java.security.Signature;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PoC 2: la app del hijo. Para gastar, el niño solo pone el dedo; no hay wallet, ni frase, ni gas.
 *
 * 1. "Crear la clave del móvil": par P-256 en el Android Keystore, protegido con la huella.
 * 2. "Registrar este móvil": el backend (que en la PoC hace de padre) guarda la clave pública en el contrato.
 * 3. "Recompensa de prueba": el padre da saldo por una tarea.
 * 4. "Gastar con la huella": el móvil firma (chainId, contrato, hijo, concepto, cantidad, nonce) y el
 *    backend lo envía pagando el gas. El contrato comprueba que la firma es de este móvil.
 *
 * La interfaz se crea por código (sin XML) para que todo esté en un único fichero fácil de leer.
 */
public class MainActivity extends Activity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private EditText backendUrl;
    private EditText childName;
    private EditText concept;
    private EditText amount;
    private TextView balance;
    private TextView log;
    private Button spendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("poc", MODE_PRIVATE);
        setContentView(buildUi());
        refresh();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    // ---------------- acciones ----------------

    private void createKey() {
        run("Crear clave", () -> {
            if (DeviceKey.exists()) {
                DeviceKey.delete();
                say("Había una clave anterior: borrada (habrá que registrar el móvil otra vez).");
            }
            DeviceKey.create();
            byte[][] xy = DeviceKey.publicKeyXY();
            say("Clave P-256 creada en " + DeviceKey.securityLevel());
            say("x = " + SpendMessage.hex(xy[0]));
            say("y = " + SpendMessage.hex(xy[1]));
        });
    }

    private void registerDevice() {
        run("Registrar móvil", () -> {
            byte[][] xy = DeviceKey.publicKeyXY();
            JSONObject tx = api().registerDevice(childId(), SpendMessage.hex(xy[0]), SpendMessage.hex(xy[1]));
            sayTx(tx);
            loadBalance();
        });
    }

    private void testReward() {
        run("Recompensa +10", () -> {
            // Cada pulsación es una tarea distinta (el contrato no paga dos veces la misma tarea).
            String task = SpendMessage.hex(SpendMessage.id("tarea:" + System.currentTimeMillis()));
            sayTx(api().reward(childId(), task, 10));
            loadBalance();
        });
    }

    /** Paso 1: pedir al backend el nonce, la red y el contrato; preparar el mensaje y pedir la huella. */
    private void spend() {
        final String what = concept.getText().toString().trim();
        final long howMuch;
        try {
            howMuch = Long.parseLong(amount.getText().toString().trim());
        } catch (NumberFormatException e) {
            say("Escribe una cantidad válida");
            return;
        }
        spendButton.setEnabled(false);
        io.execute(() -> {
            try {
                if (!DeviceKey.exists()) throw new IllegalStateException("Primero crea la clave del móvil");
                JSONObject child = api().child(childId());
                if (!child.getBoolean("registered")) throw new IllegalStateException("Primero registra el móvil");
                byte[] conceptId = SpendMessage.id("concepto:" + what);
                byte[] message = SpendMessage.encode(child.getLong("chainId"), child.getString("contract"),
                        SpendMessage.id("child:" + name()), conceptId, BigInteger.valueOf(howMuch),
                        new BigInteger(child.get("nonce").toString()));
                Signature signature = DeviceKey.signatureForPrompt();
                main.post(() -> askFingerprint(what, howMuch, conceptId, message, signature));
            } catch (Exception e) {
                fail("Gastar", e);
            }
        });
    }

    /** Paso 2: el niño pone el dedo. Solo entonces el Keystore deja usar la clave para esta firma. */
    private void askFingerprint(String what, long howMuch, byte[] conceptId, byte[] message, Signature signature) {
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("¿Gastar " + howMuch + " en " + what + "?")
                .setSubtitle("Pon el dedo para confirmar")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton("Cancelar", getMainExecutor(), (dialog, which) -> {
                    say("Cancelado");
                    spendButton.setEnabled(true);
                })
                .build();
        prompt.authenticate(new BiometricPrompt.CryptoObject(signature), new CancellationSignal(), getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        Signature unlocked = result.getCryptoObject().getSignature();
                        sendSpend(what, howMuch, conceptId, message, unlocked);
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence error) {
                        say("Huella: " + error);
                        spendButton.setEnabled(true);
                    }
                });
    }

    /** Paso 3: firmar, enviar al backend y enseñar "¡Hecho!" sin esperar al bloque; el ✓ llega después. */
    private void sendSpend(String what, long howMuch, byte[] conceptId, byte[] message, Signature unlocked) {
        final long t0 = System.currentTimeMillis();
        io.execute(() -> {
            try {
                unlocked.update(message);
                byte[][] rs = SpendMessage.rsFromDer(unlocked.sign());
                JSONObject sent = api().spend(childId(), SpendMessage.hex(conceptId), howMuch,
                        SpendMessage.hex(rs[0]), SpendMessage.hex(rs[1]));
                long accepted = System.currentTimeMillis() - t0;
                say("¡Hecho! " + howMuch + " en " + what + " (" + accepted + " ms)");
                say("tx " + sent.getString("txHash"));
                main.post(() -> spendButton.setEnabled(true));
                waitConfirmation(sent.getString("txHash"), t0);
            } catch (Exception e) {
                main.post(() -> spendButton.setEnabled(true));
                fail("Gastar", e);
            }
        });
    }

    private void waitConfirmation(String hash, long t0) throws Exception {
        for (int i = 0; i < 90; i++) {
            JSONObject tx = api().tx(hash);
            String status = tx.getString("status");
            if (!"pending".equals(status)) {
                long ms = System.currentTimeMillis() - t0;
                say(("success".equals(status) ? "✓ Confirmado en la blockchain" : "✗ Falló en la blockchain")
                        + " en " + ms + " ms, gas " + tx.opt("gasUsed"));
                if (!tx.isNull("explorer")) say(tx.getString("explorer"));
                loadBalance();
                return;
            }
            Thread.sleep(2000);
        }
        say("Sigue pendiente; mira el enlace en Etherscan");
    }

    private void refresh() {
        io.execute(() -> {
            try {
                say("Clave del móvil: " + (DeviceKey.exists() ? "creada, en " + DeviceKey.securityLevel() : "no creada"));
                loadBalance();
            } catch (Exception e) {
                fail("Actualizar", e);
            }
        });
    }

    private void loadBalance() throws Exception {
        JSONObject c = api().child(childId());
        String text = c.getBoolean("registered") ? c.get("balance").toString() + " monedas" : "móvil sin registrar";
        main.post(() -> balance.setText(text));
    }

    // ---------------- utilidades ----------------

    private interface Action {
        void run() throws Exception;
    }

    private void run(String name, Action action) {
        say(name + "…");
        final long t0 = System.currentTimeMillis();
        io.execute(() -> {
            try {
                action.run();
                say(name + ": OK en " + (System.currentTimeMillis() - t0) + " ms");
            } catch (Exception e) {
                fail(name, e);
            }
        });
    }

    private void fail(String name, Exception e) {
        String code = e instanceof RelayerApi.ApiException ? " [" + ((RelayerApi.ApiException) e).code + "]" : "";
        say(name + ": ERROR" + code + " " + e.getMessage());
        main.post(() -> spendButton.setEnabled(true));
    }

    private void sayTx(JSONObject tx) throws Exception {
        say("tx " + tx.getString("txHash") + " → " + tx.getString("status") + ", gas " + tx.opt("gasUsed"));
    }

    private void say(String line) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date());
        main.post(() -> log.append("[" + time + "] " + line + "\n"));
    }

    private RelayerApi api() {
        String url = backendUrl.getText().toString().trim();
        prefs.edit().putString("url", url).putString("child", name()).apply();
        return new RelayerApi(url);
    }

    private String name() {
        return childName.getText().toString().trim();
    }

    private String childId() {
        return SpendMessage.hex(SpendMessage.id("child:" + name()));
    }

    // ---------------- interfaz ----------------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        balance = new TextView(this);
        balance.setTextSize(32);
        balance.setGravity(Gravity.CENTER);
        balance.setText("…");
        root.addView(label("Mi hucha"));
        root.addView(balance);

        concept = field("Concepto", "cromos", InputType.TYPE_CLASS_TEXT);
        amount = field("Cantidad", "3", InputType.TYPE_CLASS_NUMBER);
        root.addView(concept);
        root.addView(amount);
        spendButton = button("Gastar con la huella", v -> spend());
        root.addView(spendButton);

        root.addView(label("Configuración (padres)"));
        backendUrl = field("Backend", prefs.getString("url", "http://10.0.2.2:8080"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        childName = field("Nombre del hijo", prefs.getString("child", "hijo"), InputType.TYPE_CLASS_TEXT);
        root.addView(backendUrl);
        root.addView(childName);
        root.addView(button("1. Crear la clave del móvil", v -> createKey()));
        root.addView(button("2. Registrar este móvil", v -> registerDevice()));
        root.addView(button("3. Recompensa de prueba +10", v -> testReward()));
        root.addView(button("Actualizar saldo", v -> refresh()));

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(11);
        log.setTextIsSelectable(true);
        root.addView(label("Registro"));
        root.addView(log);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    private EditText field(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(type);
        e.setSingleLine(true);
        return e;
    }

    private Button button(String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
