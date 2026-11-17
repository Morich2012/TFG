package es.upm.tfg.comun;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj de la aplicación en hora de Madrid; las pruebas pueden sustituirlo por uno fijo. */
@Configuration
class ConfiguracionReloj {

    @Bean
    Clock reloj() {
        return Clock.system(ZoneId.of("Europe/Madrid"));
    }
}
