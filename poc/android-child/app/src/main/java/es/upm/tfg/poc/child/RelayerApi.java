package es.upm.tfg.poc.child;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Cliente mínimo del backend (poc/backend, /ledger). Llamadas bloqueantes: usar fuera del hilo de la UI. */
final class RelayerApi {

    /** Error del backend con su código (InvalidSignature, InsufficientBalance...) y un mensaje para el niño. */
    static final class ApiException extends IOException {
        final String code;

        ApiException(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private final String baseUrl;

    RelayerApi(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    JSONObject child(String childId) throws IOException {
        return request("GET", "/ledger/children/" + childId, null);
    }

    JSONObject registerDevice(String childId, String x, String y) throws IOException {
        return request("POST", "/ledger/children/" + childId + "/device", json("x", x, "y", y));
    }

    JSONObject reward(String childId, String taskId, long amount) throws IOException {
        return request("POST", "/ledger/children/" + childId + "/rewards", json("task", taskId, "amount", amount));
    }

    JSONObject spend(String childId, String conceptId, long amount, String r, String s) throws IOException {
        return request("POST", "/ledger/children/" + childId + "/spends",
                json("conceptId", conceptId, "amount", amount, "r", r, "s", s));
    }

    JSONObject tx(String hash) throws IOException {
        return request("GET", "/ledger/tx/" + hash, null);
    }

    private JSONObject request(String method, String path, JSONObject body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(5000);
        // registrar y recompensar esperan a que se mine el bloque (~12 s en Sepolia)
        c.setReadTimeout(120_000);
        c.setRequestProperty("Accept", "application/json");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = c.getResponseCode();
        String text = read(status < 400 ? c.getInputStream() : c.getErrorStream());
        c.disconnect();
        try {
            JSONObject json = new JSONObject(text.isEmpty() ? "{}" : text);
            if (status >= 400) {
                throw new ApiException(json.optString("error", "HTTP " + status),
                        json.optString("message", "Error " + status + " del backend"));
            }
            return json;
        } catch (JSONException e) {
            throw new IOException("Respuesta inesperada (HTTP " + status + "): " + text, e);
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = is.read(buf)) != -1; ) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static JSONObject json(Object... keyValues) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i < keyValues.length; i += 2) {
                o.put((String) keyValues[i], keyValues[i + 1]);
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return o;
    }
}
