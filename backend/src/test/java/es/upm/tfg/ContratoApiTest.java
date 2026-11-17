package es.upm.tfg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.jayway.jsonpath.JsonPath;

/**
 * Comprueba que el código no se separa del contrato de diseño (contract-first): cada operación que
 * publica el backend (springdoc, /v3/api-docs) existe en docs/diseno/api/openapi.yaml con la misma
 * ruta, el mismo método y el mismo operationId. El contrato tiene más operaciones de las que hay
 * hechas; se irán implementando por iteraciones.
 */
class ContratoApiTest extends PruebaIntegracion {

    private static final Path CONTRATO = Path.of("..", "docs", "diseno", "api", "openapi.yaml");
    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");

    @Test
    void cadaOperacionImplementadaEstaEnElContrato() throws Exception {
        Map<String, String> contrato = operaciones(leerContrato(), "");
        String generado = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, String> implementadas = operaciones(JsonPath.read(generado, "$"), "/api/v1");

        assertThat(implementadas).isNotEmpty();
        assertThat(contrato).containsAllEntriesOf(implementadas);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> leerContrato() throws Exception {
        try (InputStream in = Files.newInputStream(CONTRATO)) {
            return new Yaml().load(in);
        }
    }

    /** "POST /auth/registro" -> "registrarAdulto". */
    @SuppressWarnings("unchecked")
    private static Map<String, String> operaciones(Map<String, Object> openApi, String prefijo) {
        Map<String, String> resultado = new TreeMap<>();
        Map<String, Map<String, Object>> rutas = (Map<String, Map<String, Object>>) openApi.get("paths");
        rutas.forEach((ruta, metodos) -> metodos.forEach((metodo, op) -> {
            if (METODOS.contains(metodo) && ruta.startsWith(prefijo)) {
                String clave = metodo.toUpperCase() + " " + ruta.substring(prefijo.length());
                resultado.put(clave, (String) ((Map<String, Object>) op).get("operationId"));
            }
        }));
        return resultado;
    }
}
