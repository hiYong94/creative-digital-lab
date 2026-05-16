package com.creatived.chat.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("크디랩 실시간 채팅 API")
                        .description("1:1 실시간 채팅 서비스 + Event Sourcing 기반 상태 복원")
                        .version("v1.0.0"));
    }
}
