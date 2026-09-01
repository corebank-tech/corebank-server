package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.application.port.in.CustomerInfoQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.CustomerInfoResult;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import java.time.Clock;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인 고객의 기본정보를 조회하고 마스킹된 결과로 변환한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerInfoQueryService implements CustomerInfoQueryUseCase {

    private final CustomerPersistencePort customerPersistencePort;
    private final CustomerInfoMasker customerInfoMasker;
    private final Clock clock;

    @Override
    public CustomerInfoResult getCustomerInfo(Long customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");

        Customer customer = customerPersistencePort
                .findById(customerId)
                .orElseThrow(() -> new IllegalStateException("로그인 고객의 기본정보를 찾을 수 없습니다."));

        return new CustomerInfoResult(
                customer.getCustomerId(),
                customerInfoMasker.maskUserName(customer.getUserName()),
                customerInfoMasker.maskUserId(customer.getUserId()),
                customerInfoMasker.maskBirthDate(customer.getBirthDate()),
                customerInfoMasker.maskPhoneNumber(customer.getPhoneNumber()),
                customerInfoMasker.maskEmail(customer.getEmail()),
                customer.getJoinedAt().atZone(clock.getZone()).toOffsetDateTime());
    }
}
