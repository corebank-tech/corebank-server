package com.shinhan.corebank.batch.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CobCheckpointJpaRepository extends JpaRepository<CobCheckpointJpaEntity, Long> {

    // 이미 있으면 아무것도 바꾸지 않아 다시 불러도 SUCCESS가 PENDING으로 돌아가지 않는다
    @Modifying
    @Query(
            value = "INSERT INTO cob_checkpoint (cob_date, step_name, status, updated_at) "
                    + "VALUES (:cobDate, :stepName, 'PENDING', :now) ON DUPLICATE KEY UPDATE cob_date = cob_date",
            nativeQuery = true)
    void insertPendingIfAbsent(
            @Param("cobDate") LocalDate cobDate, @Param("stepName") String stepName, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE CobCheckpointJpaEntity c SET c.status = 'SUCCESS', c.updatedAt = :now "
            + "WHERE c.cobDate = :cobDate AND c.stepName = :stepName")
    int markSuccess(
            @Param("cobDate") LocalDate cobDate, @Param("stepName") String stepName, @Param("now") LocalDateTime now);

    List<CobCheckpointJpaEntity> findByStatusAndStepNameInOrderByCobDateAscStepNameAsc(
            String status, Collection<String> stepNames);
}
