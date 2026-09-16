package com.shinhan.corebank.limit.application.service;

import com.shinhan.corebank.limit.api.TransferLimitRegistration;
import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * limit/api 의 가입 연계 계약만 맡는다. 고객이 직접 일으키는 변경(TransferLimitCommandUseCase)과
 * 한 클래스에 두지 않는 것은, 스프링의 빈 교체가 타입이 아니라 이름 단위이기 때문이다 -
 * 겸업하면 이 계약을 mock 으로 바꾸는 순간 TransferLimitCommandUseCase 주입처까지 함께 깨진다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TransferLimitRegistrationService implements TransferLimitRegistration {

    private final TransferLimitCommandPort transferLimitCommandPort;

    /**
     * 기본 전파(REQUIRED)라 회원가입 트랜잭션에 참여한다. 이미 있는지 먼저 확인하지 않는다 -
     * saveIfAbsent 가 한 문장이라 DB 가 단독으로 판정한다.
     *
     * <p>save 가 아니라 saveIfAbsent 인 것이 핵심이다. save 는 덮어쓰므로, 고객이 한도를 올린
     * 뒤 이 메서드가 다시 불리면 기본값으로 되돌아간다.
     */
    @Override
    public void registerDefault(Long customerId) {
        transferLimitCommandPort.saveIfAbsent(TransferLimit.create(customerId));
    }
}
