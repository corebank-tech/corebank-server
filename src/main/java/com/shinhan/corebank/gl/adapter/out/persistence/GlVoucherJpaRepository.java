package com.shinhan.corebank.gl.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GlVoucherJpaRepository extends JpaRepository<GlVoucherJpaEntity, String> {

    /**
     * 전표번호 범위 안의 가장 큰 번호. 같은 (거래일, 유형)의 번호는 앞 13자가 같으므로 PK 범위 스캔으로 끝난다.
     */
    @Query(
            """
        SELECT MAX(v.voucherNo)
        FROM GlVoucherJpaEntity v
        WHERE v.voucherNo BETWEEN :fromVoucherNo AND :toVoucherNo
        """)
    Optional<String> findMaxVoucherNoBetween(
            @Param("fromVoucherNo") String fromVoucherNo, @Param("toVoucherNo") String toVoucherNo);
}
