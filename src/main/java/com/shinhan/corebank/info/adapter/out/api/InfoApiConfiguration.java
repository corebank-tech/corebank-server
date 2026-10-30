package com.shinhan.corebank.info.adapter.out.api;

import com.shinhan.corebank.info.application.port.out.InfoMartPort;
import com.shinhan.corebank.info.config.InfoApiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(InfoApiProperties.class)
public class InfoApiConfiguration {

    @Bean
    InfoMartPort infoMartPort(InfoApiProperties properties, ObjectMapper objectMapper) {
        return properties.configured() ? new InfoApiClient(properties, objectMapper) : new NoLoadHistoryInfoMart();
    }
}
