package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionSequenceJpaRepository extends JpaRepository<TransactionSequenceJpaEntity, TransactionSequenceId> {

    /**
     * 팀 JPQL 정적 쿼리 컨벤션 및 비관적 락(PESSIMISTIC_WRITE)을 적용하여
     * 일자·채널별 거래번호 일련번호 레코드를 조회하고 X-Lock을 획득합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM TransactionSequenceJpaEntity s
        WHERE s.seqDate = :seqDate AND s.channel = :channel
        """)
    Optional<TransactionSequenceJpaEntity> findBySeqDateAndChannelForUpdate(
            @Param("seqDate") LocalDate seqDate,
            @Param("channel") String channel
    );
}
