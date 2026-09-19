package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerDetail;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerSearchQuery;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerSummary;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminQueryPort;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminView;
import com.shinhan.corebank.customer.application.port.out.CustomerSearchCondition;
import com.shinhan.corebank.customer.domain.exception.CustomerErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 고객 계정 운영 조회 서비스")
class AdminCustomerQueryServiceTest {

    @Mock
    CustomerAdminQueryPort customerAdminQueryPort;

    private AdminCustomerQueryService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new AdminCustomerQueryService(customerAdminQueryPort, new CustomerInfoMasker(), clock);
    }

    @Test
    @DisplayName("조건이 하나도 없으면 전체 목록이 되므로 CMN0002로 거부한다")
    void rejectsSearchWithoutCondition() {
        assertThatThrownBy(
                        () -> service.search(new AdminCustomerSearchQuery(" ", "", null, null), PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    @DisplayName("잠기지 않은 계정(accountLocked=false)만으로는 회원 목록이 되므로 CMN0002로 거부한다")
    void rejectsUnlockedOnlyCondition() {
        assertThatThrownBy(() ->
                        service.search(new AdminCustomerSearchQuery(null, null, null, false), PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        verify(customerAdminQueryPort, never()).search(any(), any());
    }

    @Test
    @DisplayName("아이디 조건은 두 글자 이상이어야 한다")
    void rejectsOneCharacterUserId() {
        assertThatThrownBy(() ->
                        service.search(new AdminCustomerSearchQuery("a", null, null, null), PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("성명 조건은 두 글자 이상이어야 한다")
    void rejectsOneCharacterName() {
        assertThatThrownBy(() ->
                        service.search(new AdminCustomerSearchQuery(null, "김", null, null), PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("잠금 여부만으로도 검색할 수 있고, 입력 앞뒤 공백은 지운다")
    void searchesWithTrimmedConditions() {
        given(customerAdminQueryPort.search(
                        new CustomerSearchCondition("adm449", null, "a@b.test", true), PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        Page<AdminCustomerSummary> page = service.search(
                new AdminCustomerSearchQuery(" adm449 ", " ", " a@b.test ", true), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("전체 조회는 본문을 읽기 전에 건수를 세서 100건을 넘으면 CMN0006으로 거부한다")
    void rejectsLargeAllQueryBeforeReadingRows() {
        given(customerAdminQueryPort.count(any())).willReturn(101L);

        assertThatThrownBy(
                        () -> service.search(new AdminCustomerSearchQuery(null, null, null, true), Pageable.unpaged()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.ALL_QUERY_TOO_LARGE);
        verify(customerAdminQueryPort, never()).search(any(), any());
    }

    @Test
    @DisplayName("목록은 개인정보를 가리고 일시에 +09:00을 붙이며 생년월일·연락처를 담지 않는다")
    void masksSummaryRows() {
        given(customerAdminQueryPort.search(any(), any()))
                .willReturn(new PageImpl<>(List.of(view(7L, "adm449beta")), PageRequest.of(0, 10), 1));

        AdminCustomerSummary row = service.search(
                        new AdminCustomerSearchQuery("adm449", null, null, null), PageRequest.of(0, 10))
                .getContent()
                .get(0);

        assertThat(row.userId()).isEqualTo("adm4******");
        assertThat(row.userName()).isEqualTo("홍*동");
        assertThat(row.email()).isEqualTo("bet*@adm449.test");
        assertThat(row.joinedAt().toString()).isEqualTo("2026-09-02T09:00+09:00");
        assertThat(row.lastLoginAt()).isNull();
        assertThat(List.of(AdminCustomerSummary.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .doesNotContain("birthDate", "phoneNumber");
    }

    @Test
    @DisplayName("마스킹 규칙에 맞지 않는 값이 있어도 그 칸만 전부 가리고 목록은 내려준다")
    void fallsBackToFullMaskOnMalformedValue() {
        given(customerAdminQueryPort.search(any(), any()))
                .willReturn(new PageImpl<>(List.of(view(8L, "abc")), PageRequest.of(0, 10), 1));

        AdminCustomerSummary row = service.search(
                        new AdminCustomerSearchQuery(null, null, "beta@adm449.test", null), PageRequest.of(0, 10))
                .getContent()
                .get(0);

        assertThat(row.userId()).isEqualTo("****");
        assertThat(row.userName()).isEqualTo("홍*동");
    }

    @Test
    @DisplayName("상세는 생년월일·연락처까지 가려서 내려주고, 없는 고객은 ATH0201이다")
    void returnsMaskedDetailOrNotFound() {
        given(customerAdminQueryPort.findById(7L)).willReturn(Optional.of(view(7L, "adm449beta")));
        given(customerAdminQueryPort.findById(9L)).willReturn(Optional.empty());

        AdminCustomerDetail detail = service.getDetail(7L);

        assertThat(detail.birthDate()).isEqualTo("1990-**-**");
        assertThat(detail.phoneNumber()).isEqualTo("010****5678");
        assertThat(detail.loginFailureCount()).isEqualTo(5);
        assertThat(detail.accountLocked()).isTrue();
        assertThatThrownBy(() -> service.getDetail(9L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_NOT_FOUND);
    }

    private CustomerAdminView view(Long customerId, String userId) {
        return new CustomerAdminView(
                customerId,
                userId,
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "beta@adm449.test",
                "01012345678",
                5,
                true,
                null,
                LocalDateTime.of(2026, 9, 2, 9, 0));
    }
}
