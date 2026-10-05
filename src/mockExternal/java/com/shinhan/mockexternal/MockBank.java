package com.shinhan.mockexternal;

import com.shinhan.corebank.transfer.adapter.out.external.ExternalMessage;
import com.shinhan.corebank.transfer.adapter.out.external.ResponseCode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 모의 대외기관의 장부. 처리한 이체의 일련번호와 결과를 기억해 중복(2001)과 조회거래(INQ001)에 답한다.
 *
 * 이체 결과는 수취 계좌번호 앞 4자리로 정한다. 호출하는 쪽이 원하는 응답을 골라 받게 하려는 것이다.
 * 메모리에만 두므로 서버를 다시 띄우면 장부가 비워진다.
 */
final class MockBank {

    static final String PAYEE_NAME = "김민수";

    private static final Map<String, ResponseCode> RESULT_BY_ACCOUNT_PREFIX = Map.of(
            "1001", ResponseCode.ACCOUNT_NOT_FOUND,
            "1002", ResponseCode.ACCOUNT_UNAVAILABLE,
            "9999", ResponseCode.SYSTEM_ERROR);

    private final Map<String, ResponseCode> processed = new ConcurrentHashMap<>();

    ExternalMessage handle(ExternalMessage request, LocalDateTime now) {
        return switch (request.transactionCode()) {
            case TRANSFER -> transfer(request, now);
            case INQUIRY -> inquire(request, now);
        };
    }

    private ExternalMessage transfer(ExternalMessage request, LocalDateTime now) {
        ResponseCode result = decide(request.accountNumber());
        // 시스템 오류는 처리하지 못한 것이라 기록을 남기지 않는다. 조회하면 3001(원거래 미수신)이 된다.
        if (result == ResponseCode.SYSTEM_ERROR) {
            return request.toResponse(result, null, now);
        }
        if (processed.putIfAbsent(request.serialNumber(), result) != null) {
            return request.toResponse(ResponseCode.DUPLICATE, null, now);
        }
        return request.toResponse(result, payeeName(result), now);
    }

    private ExternalMessage inquire(ExternalMessage request, LocalDateTime now) {
        ResponseCode original = processed.get(request.serialNumber());
        if (original == null) {
            return request.toResponse(ResponseCode.ORIGINAL_NOT_RECEIVED, null, now);
        }
        return request.toResponse(original, payeeName(original), now);
    }

    private static ResponseCode decide(String accountNumber) {
        String prefix = accountNumber.length() < 4 ? accountNumber : accountNumber.substring(0, 4);
        return RESULT_BY_ACCOUNT_PREFIX.getOrDefault(prefix, ResponseCode.APPROVED);
    }

    private static String payeeName(ResponseCode result) {
        return result == ResponseCode.APPROVED ? PAYEE_NAME : null;
    }
}
