package es.upm.tfg.seguridad;

import java.io.IOException;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import es.upm.tfg.comun.CodigoError;
import es.upm.tfg.comun.ManejadorErrores;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seguridad de la API.
 *
 * <ul>
 * <li>Sin sesión de servidor: cada petición trae {@code Authorization: Bearer <JWT>} y Spring
 * Security la valida como servidor de recursos OAuth2.</li>
 * <li>El rol del token ({@code ADULTO_ADMIN}, {@code ADULTO}, {@code HIJO}) se convierte en la
 * autoridad {@code ROLE_<rol>} que comprueban las anotaciones {@code @PreAuthorize}.</li>
 * <li>Solo el registro ({@code POST /familias}), crear sesión ({@code POST /sesiones}), la salud y
 * la documentación son públicos.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, JsonMapper json) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API sin cookies: el token va en una cabecera
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/familias", "/api/v1/sesiones").permitAll()
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                        "/swagger-ui.html", "/error").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(rs -> rs
                .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorRoles()))
                .authenticationEntryPoint((req, res, e) ->
                        escribirProblema(res, json, CodigoError.NO_AUTENTICADO, "Inicia sesión de nuevo"))
                .accessDeniedHandler((req, res, e) ->
                        escribirProblema(res, json, CodigoError.PROHIBIDO, "Tu rol no permite esta operación")))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                        escribirProblema(res, json, CodigoError.NO_AUTENTICADO, "Inicia sesión de nuevo")));
        return http.build();
    }

    private static JwtAuthenticationConverter convertidorRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(UsuarioActual.CLAIM_ROL);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(roles);
        return convertidor;
    }

    private static void escribirProblema(HttpServletResponse res, JsonMapper json, CodigoError codigo,
            String detalle) throws IOException {
        res.setStatus(codigo.estado().value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getWriter(), ManejadorErrores.problema(codigo, detalle));
    }

    @Bean
    SecretKey claveJwt(PropiedadesSeguridad propiedades) {
        byte[] bytes = Base64.getDecoder().decode(propiedades.jwtSecreto());
        if (bytes.length < 32) {
            throw new IllegalStateException("app.seguridad.jwt-secreto debe tener al menos 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey clave) {
        return NimbusJwtEncoder.withSecretKey(clave).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey clave) {
        return NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** BCrypt para contraseñas y PIN, con prefijo {bcrypt} para poder cambiar de algoritmo. */
    @Bean
    PasswordEncoder codificadorContrasenas() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
