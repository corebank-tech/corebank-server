package com.shinhan.corebank.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CoreBank API Specification")
                        .description("Corebank REST API 명세서")
                        .version("v1.0.0"))
                .servers(List.of(
                        new Server().url("https://api.corebank.cloud/api/v1").description("배포 서버"),
                        new Server().url("http://localhost:8080/api/v1").description("로컬 서버")));
    }
}
