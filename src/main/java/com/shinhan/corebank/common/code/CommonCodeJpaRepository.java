package com.shinhan.corebank.common.code;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonCodeJpaRepository extends JpaRepository<CommonCodeJpaEntity, CommonCodeId> {

    /** 특정 그룹 조회. use_yn = 'Y' 만, sort_order 오름차순 */
    List<CommonCodeJpaEntity> findByCodeGroupAndUseYnOrderBySortOrderAsc(String codeGroup, String useYn);

    /** 전체 조회. 프론트가 앱 진입 시 1회 호출해 캐싱 */
    List<CommonCodeJpaEntity> findByUseYnOrderByCodeGroupAscSortOrderAsc(String useYn);
}