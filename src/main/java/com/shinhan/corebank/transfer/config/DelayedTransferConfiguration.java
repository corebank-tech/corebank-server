package com.shinhan.corebank.transfer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DelayedTransferProperties.class)
public class DelayedTransferConfiguration {}
