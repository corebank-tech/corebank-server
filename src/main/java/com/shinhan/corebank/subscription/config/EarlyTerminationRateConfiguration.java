package com.shinhan.corebank.subscription.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 설정 바인딩만 담당한다. domain 정책 구성은 application 계층에서 수행한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EarlyTerminationRateProperties.class)
public class EarlyTerminationRateConfiguration {}
