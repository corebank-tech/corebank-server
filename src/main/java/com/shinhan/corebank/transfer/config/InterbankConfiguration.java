package com.shinhan.corebank.transfer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// 타행 송신 설정 프로퍼티를 Spring Bean으로 등록한다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InterbankProperties.class)
public class InterbankConfiguration {}
