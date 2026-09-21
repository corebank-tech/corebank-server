package com.shinhan.corebank.account.adapter.out.transferusage;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface AutoTransferUsageJpaRepository extends Repository<AutoTransferUsageJpaEntity, Long> {

    boolean existsByWithdrawalAccountIdAndStatus(Long withdrawalAccountId, String status);

    @Query(
            value =
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM auto_transfer_execution e
                        JOIN auto_transfer a
                          ON a.auto_transfer_id = e.auto_transfer_id
                        WHERE a.withdrawal_account_id = :withdrawalAccountId
                          AND e.status = :status
                    )
                    """,
            nativeQuery = true)
    long existsExecutionByWithdrawalAccountIdAndStatus(
            @Param("withdrawalAccountId") Long withdrawalAccountId, @Param("status") String status);
}
