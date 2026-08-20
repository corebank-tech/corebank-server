package com.shinhan.corebank.otp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// OTP 설정 프로퍼티를 Spring Bean으로 등록한다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OtpProperties.class)
public class OtpConfiguration {
}
