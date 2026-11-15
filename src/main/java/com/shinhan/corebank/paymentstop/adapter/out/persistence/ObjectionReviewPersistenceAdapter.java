package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.domain.SupplementRequest;
import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheck;
import com.shinhan.corebank.paymentstop.domain.notice.ObjectionNotice;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ObjectionReviewPersistenceAdapter implements ObjectionReviewPort {

    private final SupplementRequestJpaRepository supplementRepository;
    private final ReviewSessionJpaRepository reviewSessionRepository;
    private final ExternalCheckJpaRepository externalCheckRepository;
    private final NoticeJpaRepository noticeRepository;

    @Override
    public void saveSupplementRequest(SupplementRequest request) {
        supplementRepository.save(SupplementRequestJpaEntity.of(request));
    }

    @Override
    public List<SupplementRequest> findSupplementRequests(long objectionId) {
        return supplementRepository.findByObjectionIdOrderByRoundAsc(objectionId).stream()
                .map(SupplementRequestJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Map<Long, SupplementRequest> findLatestSupplementRequests(Collection<Long> objectionIds) {
        if (objectionIds.isEmpty()) {
            return Map.of();
        }
        return supplementRepository.findByObjectionIdIn(objectionIds).stream()
                .map(SupplementRequestJpaEntity::toDomain)
                .collect(Collectors.toMap(
                        SupplementRequest::objectionId,
                        request -> request,
                        BinaryOperator.maxBy(Comparator.comparingInt(SupplementRequest::round))));
    }

    @Override
    public void markSupplementSubmitted(long objectionId, int round, LocalDateTime submittedAt, String note) {
        supplementRepository
                .findByObjectionIdAndRound(objectionId, (byte) round)
                .orElseThrow(
                        () -> new IllegalStateException("보완 요청이 없습니다. objectionId=" + objectionId + ", round=" + round))
                .submit(submittedAt, note);
    }

    @Override
    public long saveReviewSession(long objectionId, long adminId, LocalDateTime startedAt) {
        return reviewSessionRepository
                .save(ReviewSessionJpaEntity.of(objectionId, adminId, startedAt))
                .getReviewSessionId();
    }

    @Override
    public Optional<ExternalCheck> findExternalCheck(long objectionId, ExternalAgency agency) {
        return externalCheckRepository
                .findByObjectionIdAndAgency(objectionId, agency)
                .map(ExternalCheckJpaEntity::toDomain);
    }

    @Override
    public ExternalCheck saveExternalCheck(ExternalCheck check) {
        return externalCheckRepository
                .saveAndFlush(ExternalCheckJpaEntity.of(check))
                .toDomain();
    }

    @Override
    public List<ExternalCheck> findExternalChecks(long objectionId) {
        return externalCheckRepository.findByObjectionIdOrderByExternalCheckIdAsc(objectionId).stream()
                .map(ExternalCheckJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void saveNotices(List<ObjectionNotice> notices) {
        noticeRepository.saveAll(notices.stream().map(NoticeJpaEntity::of).toList());
    }

    @Override
    public List<ObjectionNotice> findNotices(long objectionId) {
        return noticeRepository.findByObjectionIdOrderByNoticeIdAsc(objectionId).stream()
                .map(NoticeJpaEntity::toDomain)
                .toList();
    }
}
