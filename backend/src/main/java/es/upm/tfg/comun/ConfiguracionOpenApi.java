package es.upm.tfg.comun;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Datos de la documentación que genera springdoc. El contrato de referencia sigue siendo
 * docs/diseno/api/openapi.yaml; ContratoApiTest comprueba que no se separan.
 */
@Configuration
class ConfiguracionOpenApi {

    @Bean
    OpenAPI documentacionApi() {
        return new OpenAPI()
                .info(new Info().title("API de la economía familiar (TFG)").version("0.1.0"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
