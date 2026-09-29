package com.shinhan.corebank.customer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminQueryPort;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminView;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.application.port.out.CustomerSearchCondition;
import com.shinhan.corebank.customer.domain.model.Customer;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

// 테스트 고객은 adm449 접두어 아이디와 @adm449.test 이메일로만 만든다(시드·다른 테스트와 겹치지 않게).
@Transactional
@DisplayName("관리자 고객 조회 어댑터 MySQL 통합 테스트")
class CustomerAdminQueryPersistenceAdapterTest extends IntegrationTestSupport {

    private static final String PREFIX = "adm449";

    @Autowired
    private CustomerAdminQueryPort customerAdminQueryPort;

    @Autowired
    private CustomerPersistencePort customerPersistencePort;

    @Autowired
    private EntityManager entityManager;

    private Long oldestId;
    private Long lockedId;
    private Long newestId;

    @BeforeEach
    void setUp() {
        oldestId = save("adm449alpha", "사사구가", "alpha@adm449.test", 0, false, 1);
        lockedId = save("adm449beta", "사사구나", "beta@adm449.test", 5, true, 2);
        newestId = save("adm449gamma", "다른이름", "gamma@adm449.test", 0, false, 3);
        save("adm449a_b", "밑줄고객", "underscore@adm449.test", 0, false, 4);
        save("adm449axb", "밑줄대조", "noescape@adm449.test", 0, false, 4);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("아이디 앞부분 일치로 찾고 가입일 최신순, 같으면 PK 역순으로 정렬한다")
    void searchesByUserIdPrefixOrderedByJoinedAtDesc() {
        Page<CustomerAdminView> page = search(condition(PREFIX, null, null, null), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent())
                .extracting(CustomerAdminView::userId)
                .endsWith("adm449gamma", "adm449beta", "adm449alpha");
        assertThat(page.getContent().get(0).joinedAt())
                .isAfterOrEqualTo(page.getContent().get(1).joinedAt());
    }

    @Test
    @DisplayName("입력의 _ 와 % 는 와일드카드가 아니라 글자로 비교한다")
    void treatsLikeWildcardsAsLiterals() {
        assertThat(search(condition("adm449a_", null, null, null), PageRequest.of(0, 10))
                        .getContent())
                .extracting(CustomerAdminView::userId)
                .containsExactly("adm449a_b");
        assertThat(search(condition("adm449%", null, null, null), PageRequest.of(0, 10))
                        .getContent())
                .isEmpty();
    }

    @Test
    @DisplayName("성명은 앞부분 일치, 이메일은 정확 일치, 잠금 여부는 값 일치로 거른다")
    void filtersByNamePrefixEmailAndLockState() {
        assertThat(search(condition(PREFIX, "사사구", null, null), PageRequest.of(0, 10))
                        .getContent())
                .extracting(CustomerAdminView::customerId)
                .containsExactly(lockedId, oldestId);
        assertThat(search(condition(null, null, "beta@adm449.test", null), PageRequest.of(0, 10))
                        .getContent())
                .extracting(CustomerAdminView::customerId)
                .containsExactly(lockedId);
        assertThat(search(condition(null, null, "beta@adm449", null), PageRequest.of(0, 10))
                        .getContent())
                .isEmpty();
        assertThat(search(condition(PREFIX, null, null, true), PageRequest.of(0, 10))
                        .getContent())
                .extracting(CustomerAdminView::customerId)
                .containsExactly(lockedId);
    }

    @Test
    @DisplayName("페이지 경계와 전체 건수를 조건 기준으로 계산한다")
    void pagesAndCounts() {
        Page<CustomerAdminView> second = search(condition(PREFIX, null, null, null), PageRequest.of(1, 2));

        assertThat(second.getContent()).hasSize(2);
        assertThat(second.getTotalElements()).isEqualTo(5);
        assertThat(second.getTotalPages()).isEqualTo(3);
        assertThat(customerAdminQueryPort.count(condition(PREFIX, null, null, false)))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("전체 조회(unpaged)는 조건에 맞는 전 건을 돌려준다")
    void returnsAllRowsWhenUnpaged() {
        assertThat(search(condition(PREFIX, null, null, null), Pageable.unpaged())
                        .getContent())
                .hasSize(5);
    }

    @Test
    @DisplayName("상세 조회는 비밀번호 해시 없이 관리자 화면에 필요한 칼럼만 읽는다")
    void findsDetailById() {
        CustomerAdminView view = customerAdminQueryPort.findById(lockedId).orElseThrow();

        assertThat(view.userId()).isEqualTo("adm449beta");
        assertThat(view.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(view.phoneNumber()).isEqualTo("01012345678");
        assertThat(view.loginFailureCount()).isEqualTo(5);
        assertThat(view.accountLocked()).isTrue();
        assertThat(List.of(CustomerAdminView.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .doesNotContain("passwordHash");
        assertThat(customerAdminQueryPort.findById(newestId + 1_000_000)).isEmpty();
    }

    private Page<CustomerAdminView> search(CustomerSearchCondition condition, Pageable pageable) {
        return customerAdminQueryPort.search(condition, pageable);
    }

    private CustomerSearchCondition condition(String userId, String userName, String email, Boolean accountLocked) {
        return new CustomerSearchCondition(userId, userName, email, accountLocked);
    }

    private Long save(String userId, String userName, String email, int failures, boolean locked, int joinedDay) {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 9, joinedDay, 9, 0);
        Customer customer = Customer.restore(
                null,
                userId,
                null,
                "$2a$10$adm449PasswordHash",
                userName,
                LocalDate.of(1990, 1, 1),
                email,
                "01012345678",
                failures,
                locked,
                null,
                null,
                null,
                null,
                joinedAt,
                null,
                null);
        return customerPersistencePort.save(customer).getCustomerId();
    }
}
