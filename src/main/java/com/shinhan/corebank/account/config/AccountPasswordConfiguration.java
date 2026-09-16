package com.shinhan.corebank.account.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// 계좌비밀번호 인증 설정 프로퍼티를 Spring Bean으로 등록한다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountPasswordProperties.class)
public class AccountPasswordConfiguration {}
