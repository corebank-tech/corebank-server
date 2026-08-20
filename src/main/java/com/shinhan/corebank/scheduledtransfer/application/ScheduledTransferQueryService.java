package com.shinhan.corebank.scheduledtransfer.application;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferQueryUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduledTransferQueryService implements ScheduledTransferQueryUseCase {
    private static final Set<Integer> ALLOWED_PAGE_SIZE = Set.of(5, 10, 20, 30, 50);
    private static final int MAX_RANGE_DAYS = 365;

    private final ScheduledTransferQueryPort scheduledTransferQueryPort;
    private final AccountStatusPort accountStatusPort;

    @Override
    public Page<ScheduledTransferListItem> search(Long customerId, ScheduledTransferStatus status, Long
                                                          withdrawalAccountId,
                                                  LocalDate fromDate, LocalDate toDate, int page, int size) {
        if (customerId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (!ALLOWED_PAGE_SIZE.contains(size)) {
            throw new BusinessException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
        if (page < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "page는 0 이상이어야 합니다.");
        }
        // REQ-SCD-007: fromDate/toDate 둘 다 선택값이라 기본값을 주입하지 않는다 — 둘 다 있을 때만 검증
        if (fromDate != null && toDate != null) {
            if (fromDate.isAfter(toDate)) {
                throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
            }
            if (fromDate.plusDays(MAX_RANGE_DAYS).isBefore(toDate)) {
                throw new BusinessException(CommonErrorCode.DATE_RANGE_EXCEEDED);
            }
        }

        Page<ScheduledTransfer> result = scheduledTransferQueryPort.search(customerId, status, withdrawalAccountId,
                fromDate, toDate, PageRequest.of(page, size));

        // 페이지 내 출금계좌번호·별칭을 한 번에 조회 — 원소마다 개별 조회하면 size만큼 N+1이 발생한다
        List<Long> withdrawalAccountIds = result.getContent().stream()
                .map(ScheduledTransfer::getWithdrawalAccountId)
                .distinct()
                .toList();
        Map<Long, String> withdrawalAccountNumbers = accountStatusPort.findAccountNumbersByIds(withdrawalAccountIds);
        Map<Long, String> withdrawalAccountAliases = accountStatusPort.findAccountAliasesByIds(withdrawalAccountIds);

        return result.map(scheduledTransfer -> toItem(scheduledTransfer, withdrawalAccountNumbers, withdrawalAccountAliases));
    }

    private ScheduledTransferListItem toItem(ScheduledTransfer scheduledTransfer, Map<Long, String> withdrawalAccountNumbers,
                                             Map<Long, String> withdrawalAccountAliases) {
        String withdrawalAccountNumber = withdrawalAccountNumbers.get(scheduledTransfer.getWithdrawalAccountId());

        if (withdrawalAccountNumber == null) {
            throw new IllegalStateException("출금계좌 정보를 확인할 수 없습니다.");
        }
        // 별칭은 미설정일 수 있어 Map에 키 자체가 없을 수 있음 - null 허용
        String fromAlias = withdrawalAccountAliases.get(scheduledTransfer.getWithdrawalAccountId());
        return new ScheduledTransferListItem(
                scheduledTransfer.getScheduledTransferId(),
                scheduledTransfer.getScheduledDate(),
                withdrawalAccountNumber,
                fromAlias,
                scheduledTransfer.getPayeeBankCode(),
                scheduledTransfer.getPayeeAccountNumber(),
                scheduledTransfer.getPayeeName(),
                scheduledTransfer.getAmount(),
                scheduledTransfer.getMyPassbookMemo(),
                scheduledTransfer.getStatus(),
                scheduledTransfer.getStatus() == ScheduledTransferStatus.WAITING,
                scheduledTransfer.getRegisteredAt()
        );
    }
}