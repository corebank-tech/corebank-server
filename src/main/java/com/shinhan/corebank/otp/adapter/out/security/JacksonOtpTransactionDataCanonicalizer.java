package com.shinhan.corebank.otp.adapter.out.security;

import com.shinhan.corebank.otp.application.port.out.OtpTransactionDataCanonicalizerPort;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// JSON 객체 Key와 숫자를 재귀적으로 정규화해 동일 거래의 표현 차이를 제거한다.
@Component
public class JacksonOtpTransactionDataCanonicalizer implements OtpTransactionDataCanonicalizerPort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public JacksonOtpTransactionDataCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String canonicalize(Map<String, Object> transactionData) {
        try {
            return objectMapper.writeValueAsString(normalize(transactionData));
        } catch (JacksonException exception) {
            throw new IllegalStateException("OTP 거래정보 정규화에 실패했습니다.", exception);
        }
    }

    @Override
    public Map<String, Object> parse(String canonicalTransactionData) {
        try {
            return objectMapper.readValue(canonicalTransactionData, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OTP 거래정보 역직렬화에 실패했습니다.", exception);
        }
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nestedValue) -> sorted.put(String.valueOf(key), normalize(nestedValue)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            list.forEach(element -> normalized.add(normalize(element)));
            return normalized;
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigInteger) {
            return new BigInteger(value.toString());
        }
        if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
            return new BigDecimal(value.toString()).stripTrailingZeros();
        }
        return value;
    }
}
