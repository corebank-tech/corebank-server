package com.shinhan.corebank.autotransfer.application;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferQueryUseCase;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferQueryPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
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

    @Override
    public Page<AutoTransfer> search(Long withdrawalAccountId, AutoTransferStatus status, int page, int size) {
        if(withdrawalAccountId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (!ALLOWED_PAGE_SIZE.contains(size)) {
            throw new BusinessException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
        if (page < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "page는 0 이상이여야 합니다.");
        }
        return autoTransferQueryPort.search(withdrawalAccountId,status, PageRequest.of(page,size));
    }
}
