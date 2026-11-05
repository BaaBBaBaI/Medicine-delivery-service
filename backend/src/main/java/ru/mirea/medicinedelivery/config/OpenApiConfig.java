package ru.mirea.medicinedelivery.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Конфигурирует документацию Swagger / OpenAPI 3 для сервиса доставки лекарств. */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI medicineDeliveryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Medicine Delivery Service REST API")
                        .version("1.0.0")
                        .description("Единый защищённый REST API информационной системы «Заказ доставки лекарств» для Web, JavaFX и Android"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
