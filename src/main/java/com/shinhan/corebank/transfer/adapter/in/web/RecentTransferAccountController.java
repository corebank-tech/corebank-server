package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.application.port.in.RecentTransferAccountQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers/recent-accounts")
@RequiredArgsConstructor
@Tag(name = "최근 이체계좌", description = "즉시이체 화면에서 고를 최근 이체계좌 목록 API")
public class RecentTransferAccountController {

    private final RecentTransferAccountQueryUseCase queryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping
    @Operation(
            operationId = "getRecentTransferAccounts",
            summary = "최근 이체계좌 목록조회",
            description =
                    """
            최근 6개월 안에 성공한 당행 이체(즉시·예약·자동)의 입금계좌를 중복 없이 최신순으로 최대 5건 조회한다. \
            내 계좌로 보낸 이체와 타행 이체는 제외한다. 이체 이력이 없으면 빈 배열을 반환한다.""")
    public ApiResponse<List<RecentTransferAccountResponse>> list() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return ApiResponse.success(queryUseCase.queryRecent(customerId).stream()
                .map(RecentTransferAccountResponse::from)
                .toList());
    }
}
