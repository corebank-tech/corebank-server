package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeJpaRepository extends JpaRepository<NoticeJpaEntity, Long> {

    List<NoticeJpaEntity> findByObjectionIdOrderByNoticeIdAsc(Long objectionId);
}
