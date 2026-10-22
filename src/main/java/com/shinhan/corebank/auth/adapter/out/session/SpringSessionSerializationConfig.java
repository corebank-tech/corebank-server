package com.shinhan.corebank.auth.adapter.out.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializationFailedException;
import org.springframework.core.serializer.support.SerializingConverter;

@Configuration
public class SpringSessionSerializationConfig {

    @Bean("springSessionConversionService")
    public ConversionService springSessionConversionService() {
        var deserializer = new DeserializingConverter(getClass().getClassLoader());
        var conversionService = new GenericConversionService();
        conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
        // 이전 배포의 인증 객체를 읽지 못하면 인증정보 없는 세션으로 처리한다.
        conversionService.addConverter(byte[].class, Object.class, bytes -> {
            try {
                return deserializer.convert(bytes);
            } catch (SerializationFailedException exception) {
                return null;
            }
        });
        return conversionService;
    }
}
