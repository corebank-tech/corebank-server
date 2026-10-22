package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.util.AccountNumberPolicy;
import com.shinhan.corebank.transfer.application.port.in.InterbankPayeeInquiryResult;
import com.shinhan.corebank.transfer.application.port.in.InterbankPayeeInquiryUseCase;
import com.shinhan.corebank.transfer.application.port.out.InterbankBankPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankGatewayPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 트랜잭션을 걸지 않는다 — 상대 은행 응답을 기다리는 동안 DB 커넥션을 쥐지 않게 한다.
@Service
@RequiredArgsConstructor
public class InterbankPayeeInquiryService implements InterbankPayeeInquiryUseCase {

    private final InterbankBankPort interbankBankPort;
    private final InterbankGatewayPort interbankGatewayPort;

    @Override
    public InterbankPayeeInquiryResult inquire(String bankCode, String accountNumber) {
        if (bankCode == null || accountNumber == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (!AccountNumberPolicy.INTERBANK_ACCOUNT_NUMBER_PATTERN
                .matcher(accountNumber)
                .matches()) {
            throw new BusinessException(TransferErrorCode.INVALID_INTERBANK_ACCOUNT_NUMBER);
        }
        if (!interbankBankPort.isSupported(bankCode)) {
            throw new BusinessException(TransferErrorCode.UNSUPPORTED_BANK);
        }
        InterbankReply reply = interbankGatewayPort.inquirePayee(bankCode, accountNumber);
        // 무응답·상대 장애·서킷 열림은 FE가 같은 안내로 [다음]을 막으므로 하나로 묶는다(TRF0308과 같은 방식).
        return switch (reply.outcome()) {
            case APPROVED -> new InterbankPayeeInquiryResult(reply.payeeName());
            case ACCOUNT_NOT_FOUND -> throw new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND);
            case ACCOUNT_UNAVAILABLE -> throw new BusinessException(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
            case NOT_PROCESSED, UNKNOWN -> throw new BusinessException(TransferErrorCode.INTERBANK_PAYEE_UNVERIFIED);
        };
    }
}
