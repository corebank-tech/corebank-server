package com.shinhan.corebank.signup.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// 회원가입 설정 프로퍼티를 Spring 빈으로 등록한다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SignupTokenProperties.class, EmailVerificationProperties.class})
public class SignupConfiguration {}
