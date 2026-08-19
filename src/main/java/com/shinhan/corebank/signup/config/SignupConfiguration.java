package com.shinhan.corebank.signup.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SignupTokenProperties.class)
public class SignupConfiguration {
}
