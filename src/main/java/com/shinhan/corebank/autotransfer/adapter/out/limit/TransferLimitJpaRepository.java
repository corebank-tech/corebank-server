package com.shinhan.corebank.autotransfer.adapter.out.limit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("autoTransferTransferLimitJpaRepository")
public interface TransferLimitJpaRepository extends JpaRepository<TransferLimitJpaEntity, Long> {
}
