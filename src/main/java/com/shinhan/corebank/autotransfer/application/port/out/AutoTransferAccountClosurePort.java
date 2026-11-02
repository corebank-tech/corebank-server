package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import java.util.List;

public interface AutoTransferAccountClosurePort {

    List<AutoTransfer> findNormalReferencingAccountForUpdate(Long accountId, String accountNumber);

    boolean hasProcessingExecutionReferencingAccount(Long accountId, String accountNumber);
}
