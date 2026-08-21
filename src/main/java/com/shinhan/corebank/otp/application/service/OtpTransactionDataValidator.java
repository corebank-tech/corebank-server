package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 거래정보가 비어 있거나 민감한 인증정보와 null 선택 필드를 포함하는지 검사한다.
@Component
public class OtpTransactionDataValidator {

    public void validate(Map<String, Object> transactionData) {
        if (transactionData == null || transactionData.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        validateMap(transactionData);
    }

    private void validateMap(Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            validateFieldName(key);
            validateDateField(key, entry.getValue());
            validateValue(entry.getValue());
        }
    }

    private void validateFieldName(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        if (normalized.contains("password")
                || normalized.equals("otpcode")
                || normalized.endsWith("authtoken")) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private void validateDateField(String fieldName, Object value) {
        if (!fieldName.endsWith("Date")) {
            return;
        }
        if (!(value instanceof String date)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private void validateValue(Object value) {
        if (value == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (value instanceof Map<?, ?> nestedMap) {
            validateMap(nestedMap);
        } else if (value instanceof List<?> list) {
            list.forEach(this::validateValue);
        }
    }
}
