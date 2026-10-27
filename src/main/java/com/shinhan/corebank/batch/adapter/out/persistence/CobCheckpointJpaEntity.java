package com.shinhan.corebank.batch.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** COB 재시작 체크포인트 (#663). 쓰기는 JpaRepository 의 쿼리로만 하고, 엔티티는 조회와 스키마 검증용이다. */
@Entity
@Table(name = "cob_checkpoint")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CobCheckpointJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cob_checkpoint_id")
    private Long cobCheckpointId;

    @Column(name = "cob_date", nullable = false)
    private LocalDate cobDate;

    @Column(name = "step_name", nullable = false, length = 100)
    private String stepName;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
