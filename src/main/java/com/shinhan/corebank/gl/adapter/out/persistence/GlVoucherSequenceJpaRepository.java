package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.gl.api.GlTxType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GlVoucherSequenceJpaRepository extends JpaRepository<GlVoucherSequenceJpaEntity, GlVoucherSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
        SELECT s
        FROM GlVoucherSequenceJpaEntity s
        WHERE s.tradeDate = :tradeDate AND s.txType = :txType
        """)
    Optional<GlVoucherSequenceJpaEntity> findForUpdate(
            @Param("tradeDate") LocalDate tradeDate, @Param("txType") GlTxType txType);
}
