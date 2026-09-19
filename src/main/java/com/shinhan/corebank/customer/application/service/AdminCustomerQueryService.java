package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerDetail;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerSearchQuery;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerSummary;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminQueryPort;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminView;
import com.shinhan.corebank.customer.application.port.out.CustomerSearchCondition;
import com.shinhan.corebank.customer.domain.exception.CustomerErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 고객 검색·상세(#449). 조건 없는 검색(= 회원 목록)은 받지 않고, 응답 개인정보는 서버에서 가린다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCustomerQueryService implements AdminCustomerQueryUseCase {

    private static final int MIN_NAME_LENGTH = 2;
    private static final long MAX_ALL_QUERY_SIZE = 100;
    private static final String FULL_MASK = "****";

    private final CustomerAdminQueryPort customerAdminQueryPort;
    private final CustomerInfoMasker customerInfoMasker;
    private final Clock clock;

    @Override
    public Page<AdminCustomerSummary> search(AdminCustomerSearchQuery query, Pageable pageable) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");
        CustomerSearchCondition condition = toCondition(query);

        // 전체 조회는 본문을 읽기 전에 건수부터 세어 상한을 넘으면 거부한다.
        if (pageable.isUnpaged() && customerAdminQueryPort.count(condition) > MAX_ALL_QUERY_SIZE) {
            throw new BusinessException(CommonErrorCode.ALL_QUERY_TOO_LARGE, "전체조회 결과가 100건을 넘습니다. 검색 조건을 좁혀 주세요.");
        }

        return customerAdminQueryPort.search(condition, pageable).map(this::toSummary);
    }

    @Override
    public AdminCustomerDetail getDetail(Long customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        CustomerAdminView view = customerAdminQueryPort
                .findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        return new AdminCustomerDetail(
                view.customerId(),
                mask(() -> customerInfoMasker.maskUserId(view.userId())),
                mask(() -> customerInfoMasker.maskUserName(view.userName())),
                mask(() -> customerInfoMasker.maskBirthDate(view.birthDate())),
                mask(() -> customerInfoMasker.maskEmail(view.email())),
                mask(() -> customerInfoMasker.maskPhoneNumber(view.phoneNumber())),
                view.loginFailureCount(),
                view.accountLocked(),
                toOffset(view.lastLoginAt()),
                toOffset(view.joinedAt()));
    }

    private CustomerSearchCondition toCondition(AdminCustomerSearchQuery query) {
        String userId = normalize(query.userId());
        String userName = normalize(query.userName());
        String email = normalize(query.email());

        if (userId == null && userName == null && email == null && query.accountLocked() == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING, "검색 조건을 하나 이상 입력해 주세요.");
        }
        if (userName != null && userName.length() < MIN_NAME_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "성명은 두 글자 이상 입력해 주세요.");
        }
        return new CustomerSearchCondition(userId, userName, email, query.accountLocked());
    }

    private AdminCustomerSummary toSummary(CustomerAdminView view) {
        return new AdminCustomerSummary(
                view.customerId(),
                mask(() -> customerInfoMasker.maskUserId(view.userId())),
                mask(() -> customerInfoMasker.maskUserName(view.userName())),
                mask(() -> customerInfoMasker.maskEmail(view.email())),
                view.loginFailureCount(),
                view.accountLocked(),
                toOffset(view.lastLoginAt()),
                toOffset(view.joinedAt()));
    }

    // SQL로 직접 넣은 행처럼 가입 규칙을 거치지 않은 값이 있어도 목록 전체가 500이 되지 않게 그 칸만 전부 가린다.
    private String mask(Supplier<String> masking) {
        try {
            return masking.get();
        } catch (IllegalArgumentException e) {
            return FULL_MASK;
        }
    }

    private OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(clock.getZone()).toOffsetDateTime();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
