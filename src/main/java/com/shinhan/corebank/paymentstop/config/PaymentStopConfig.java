package com.shinhan.corebank.paymentstop.config;

import com.shinhan.corebank.paymentstop.domain.gate.GateSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GateSettingsProperties.class)
public class PaymentStopConfig {

    @Bean
    public GateSettings gateSettings(GateSettingsProperties properties) {
        return properties.toSettings();
    }
}
