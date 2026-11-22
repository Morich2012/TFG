package es.upm.tfg.seguridad;

import java.util.Map;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import es.upm.tfg.comun.ErrorApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Comprueba en un único sitio, antes de llegar a cualquier controlador, que el
 * {@code {familiaId}} de la ruta es la familia del token. Si no lo es responde 404, no 403,
 * para no revelar que esa familia existe (RNF-03). Así ninguna ruta nueva bajo
 * {@code /familias/{familiaId}} puede olvidarse de hacerlo.
 */
@Component
public class ComprobadorFamilia implements HandlerInterceptor, WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(this).addPathPatterns("/api/v1/familias/*", "/api/v1/familias/*/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object handler) {
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) peticion
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String familiaRuta = variables == null ? null : variables.get("familiaId");
        if (familiaRuta == null) {
            return true;
        }
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            return true; // sin token: Spring Security ya responde 401 en las rutas protegidas
        }
        long familiaToken = UsuarioActual.de(token.getToken()).familiaId();
        if (!familiaRuta.equals(Long.toString(familiaToken))) {
            throw ErrorApi.noEncontrado();
        }
        return true;
    }
}
