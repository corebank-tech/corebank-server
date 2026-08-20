package com.shinhan.corebank.autotransfer.application;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferListItem;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferQueryUseCase;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferQueryPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoTransferQueryService implements AutoTransferQueryUseCase {
    private static final Set<Integer> ALLOWED_PAGE_SIZE = Set.of(5,10,20,30,50);

    private final AutoTransferQueryPort autoTransferQueryPort;
    private final AccountStatusPort accountStatusPort;

    @Override
    public Page<AutoTransferListItem> search(Long customerId, Long withdrawalAccountId, AutoTransferStatus status, int page, int size) {
        if(customerId == null || withdrawalAccountId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (!ALLOWED_PAGE_SIZE.contains(size)) {
            throw new BusinessException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
        if (page < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "page는 0 이상이여야 합니다.");
        }
        // withdrawalAccountId가 필수 파라미터라 페이지 안 모든 행이 같은 계좌 - 별칭은 요청당 1회만 조회
        String fromAlias = accountStatusPort.findAccountAlias(withdrawalAccountId).orElse(null);
        return autoTransferQueryPort.search(customerId, withdrawalAccountId, status, PageRequest.of(page,size))
                .map(autoTransfer -> new AutoTransferListItem(autoTransfer, fromAlias));
    }
}
