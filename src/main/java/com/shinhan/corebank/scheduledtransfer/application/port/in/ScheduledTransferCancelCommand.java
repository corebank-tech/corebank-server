package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.Builder;

import java.util.List;
import java.util.Objects;

@Builder
public record ScheduledTransferCancelCommand(Long customerId, List<Long> scheduledTransferIds,
                                             String accountPasswordAuthToken, String otpAuthToken, String requestIp) {

    // 목록 한 페이지에서 고를 수 있는 최대 건수와 맞춘다 (api_conventions.md §6-5 페이지 크기 최대 50)
    public static final int MAX_CANCEL_COUNT = 50;

    public ScheduledTransferCancelCommand {
        if (customerId == null || accountPasswordAuthToken == null || otpAuthToken == null || requestIp == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        // 공백 문자열 검증
        if (accountPasswordAuthToken.isBlank() || otpAuthToken.isBlank() || requestIp.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (scheduledTransferIds == null || scheduledTransferIds.isEmpty() || scheduledTransferIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        // OTP 거래정보는 배열 순서까지 그대로 대조된다 — JacksonOtpTransactionDataCanonicalizer.normalize()가
        // Map의 key는 정렬하지만 List 원소는 정렬하지 않기 때문이다. 발급(FE)·검증(BE) 양쪽이 같은 배열을
        // 만들도록 "오름차순 정렬 + 중복 제거"를 계약으로 고정한다.
        scheduledTransferIds = scheduledTransferIds.stream().distinct().sorted().toList();
        if (scheduledTransferIds.size() > MAX_CANCEL_COUNT) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "한 번에 취소할 수 있는 예약이체는 최대 " + MAX_CANCEL_COUNT + "건입니다.");
        }
    }
}
