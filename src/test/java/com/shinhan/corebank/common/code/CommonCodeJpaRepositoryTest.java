package com.shinhan.corebank.common.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * common_code 마이그레이션 시드 데이터와 CommonCodeJpaEntity 매핑이 일치하는지,
 * (code_group, code) 복합키로 그룹 간 code 값 중복이 정상 처리되는지 검증한다.
 */
class CommonCodeJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    CommonCodeJpaRepository repository;

    @Test
    @DisplayName("codeGroup + useYn 으로 조회하면 sortOrder 오름차순으로 반환된다")
    void findByCodeGroupAndUseYn_orderedBySortOrder() {
        List<CommonCodeJpaEntity> result = repository.findByCodeGroupAndUseYnOrderBySortOrderAsc("ACCOUNT_STATUS", "Y");

        assertThat(result)
                .extracting(
                        CommonCodeJpaEntity::getCode,
                        CommonCodeJpaEntity::getCodeName,
                        CommonCodeJpaEntity::getSortOrder)
                .containsExactly(tuple("ACTIVE", "정상", 1), tuple("SUSPENDED", "거래정지", 2), tuple("CLOSED", "해지", 3));
    }

    @Test
    @DisplayName("같은 code 값이 서로 다른 codeGroup 에 속해도 복합키로 구분된다")
    void sameCodeAcrossDifferentGroups_isDistinguishedByCompositeKey() {
        CommonCodeJpaEntity processResultSuccess =
                repository.findByCodeGroupAndUseYnOrderBySortOrderAsc("PROCESS_RESULT_STATUS", "Y").stream()
                        .filter(c -> c.getCode().equals("SUCCESS"))
                        .findFirst()
                        .orElseThrow();
        CommonCodeJpaEntity scheduledTransferSuccess =
                repository.findByCodeGroupAndUseYnOrderBySortOrderAsc("SCHEDULED_TRANSFER_STATUS", "Y").stream()
                        .filter(c -> c.getCode().equals("SUCCESS"))
                        .findFirst()
                        .orElseThrow();

        assertThat(processResultSuccess.getSortOrder()).isEqualTo(1);
        assertThat(scheduledTransferSuccess.getSortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("전체 조회는 codeGroup, sortOrder 순으로 정렬되고 시드 데이터를 모두 포함한다")
    void findAllActive_orderedByGroupThenSortOrder() {
        List<CommonCodeJpaEntity> result = repository.findByUseYnOrderByCodeGroupAscSortOrderAsc("Y");

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(c -> assertThat(c.isActive()).isTrue());
        assertThat(result).extracting(CommonCodeJpaEntity::getCodeGroup).isSorted();
    }

    @Test
    @DisplayName("description 이 있는 코드는 그대로 매핑되고, BaseEntity 의 감사 컬럼도 채워진다")
    void descriptionAndAuditColumns_arePopulated() {
        CommonCodeJpaEntity expired =
                repository.findByCodeGroupAndUseYnOrderBySortOrderAsc("AUTO_TRANSFER_STATUS", "Y").stream()
                        .filter(c -> c.getCode().equals("EXPIRED"))
                        .findFirst()
                        .orElseThrow();

        assertThat(expired.getDescription()).isEqualTo("이체 종료일 경과로 시스템 자동 전환");
        assertThat(expired.getCreatedAt()).isNotNull();
        assertThat(expired.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("CommonCodeId 를 직접 생성해 findById 로 조회할 수 있다")
    void findById_withManuallyConstructedId() {
        Optional<CommonCodeJpaEntity> found = repository.findById(new CommonCodeId("ACCOUNT_STATUS", "ACTIVE"));

        assertThat(found).isPresent();
        assertThat(found.get().getCodeName()).isEqualTo("정상");
    }
}
