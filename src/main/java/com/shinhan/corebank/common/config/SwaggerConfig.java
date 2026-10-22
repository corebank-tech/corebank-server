package com.shinhan.corebank.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
                        new Server().url("http://localhost:8080/api/v1").description("로컬 WAS A"),
                        new Server().url("http://localhost:8081/api/v1").description("로컬 WAS B — JDBC 세션 공유 검증")));
    }

    // Security 필터가 처리하는 로그아웃도 Swagger에서 실행할 수 있게 명세만 등록한다.
    @Bean
    public OpenApiCustomizer sessionLogoutDocumentation() {
        return api -> api.path(
                "/auth/logout",
                new PathItem()
                        .post(new Operation()
                                .operationId("logout")
                                .addTagsItem("인증")
                                .summary("로그아웃")
                                .description("현재 JSESSIONID의 공용 JDBC 세션을 삭제한다. 이후 다른 WAS에서도 같은 세션은 인증되지 않는다. "
                                        + "먼저 로그인하고 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다.")
                                .addParametersItem(new Parameter()
                                        .name("X-XSRF-TOKEN")
                                        .in("header")
                                        .required(true)
                                        .description("로그인 응답의 XSRF-TOKEN 쿠키 값")
                                        .schema(new StringSchema()))
                                .responses(new ApiResponses()
                                        .addApiResponse("200", new ApiResponse().description("로그아웃 성공, code=0000"))
                                        .addApiResponse("401", new ApiResponse().description("CMN0101 인증 세션 없음 또는 만료"))
                                        .addApiResponse("403", new ApiResponse().description("CMN0102 CSRF 검증 실패")))));
    }
}
