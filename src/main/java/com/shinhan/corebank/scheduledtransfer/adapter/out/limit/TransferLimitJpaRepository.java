package com.shinhan.corebank.scheduledtransfer.adapter.out.limit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("scheduledTransferTransferLimitJpaRepository")
public interface TransferLimitJpaRepository extends JpaRepository<TransferLimitJpaEntity, Long> {
}
