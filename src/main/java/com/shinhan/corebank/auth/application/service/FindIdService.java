package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.application.port.in.FindIdCommand;
import com.shinhan.corebank.auth.application.port.in.FindIdResult;
import com.shinhan.corebank.auth.application.port.in.FindIdUseCase;
import com.shinhan.corebank.auth.application.port.out.FindIdAccountVerificationPort;
import com.shinhan.corebank.auth.application.port.out.FindIdAccountVerificationResult;
import com.shinhan.corebank.auth.application.port.out.FindIdCustomerCandidate;
import com.shinhan.corebank.auth.application.port.out.FindIdCustomerPort;
import com.shinhan.corebank.auth.domain.exception.AuthErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 고객정보와 계좌 본인확인을 순서대로 수행해 전체 로그인 아이디를 반환한다.
@Service
@RequiredArgsConstructor
public class FindIdService implements FindIdUseCase {

    private static final Pattern ACCOUNT_NUMBER_PATTERN =
            Pattern.compile("^\\d{12}$");
    private static final Pattern ACCOUNT_PASSWORD_PATTERN =
            Pattern.compile("^\\d{4}$");

    private final FindIdCustomerPort customerPort;
    private final FindIdAccountVerificationPort accountVerificationPort;

    @Override
    public FindIdResult findId(FindIdCommand command) {
        validateRequiredFields(command);
        LocalDate birthDate = parseAndValidate(command);

        List<FindIdCustomerCandidate> candidates =
                customerPort.findAllByIdentity(
                        command.customerName(),
                        birthDate
                );
        if (candidates.isEmpty()) {
            throw new BusinessException(AuthErrorCode.USER_NOT_FOUND);
        }

        Set<Long> candidateIds = candidates.stream()
                .map(FindIdCustomerCandidate::customerId)
                .collect(Collectors.toUnmodifiableSet());
        FindIdAccountVerificationResult verification =
                accountVerificationPort.verify(
                        candidateIds,
                        command.accountNumber(),
                        command.accountPassword()
                );

        return switch (verification.status()) {
            case INFORMATION_MISMATCH, PASSWORD_MISMATCH ->
                    throw new BusinessException(
                            AuthErrorCode.IDENTITY_INFORMATION_MISMATCH
                    );
            case LOCKED -> throw new BusinessException(
                    AuthErrorCode.ACCOUNT_LOCKED
            );
            case VERIFIED -> new FindIdResult(findUserId(
                    candidates,
                    verification.customerId()
            ));
        };
    }

    private void validateRequiredFields(FindIdCommand command) {
        if (command == null
                || isBlank(command.customerName())
                || isBlank(command.birthDate())
                || isBlank(command.accountNumber())
                || isBlank(command.accountPassword())) {
            throw new BusinessException(
                    CommonErrorCode.REQUIRED_FIELD_MISSING
            );
        }
    }

    private LocalDate parseAndValidate(FindIdCommand command) {
        if (command.customerName().length() > 50
                || !ACCOUNT_NUMBER_PATTERN.matcher(
                        command.accountNumber()
                ).matches()
                || !ACCOUNT_PASSWORD_PATTERN.matcher(
                        command.accountPassword()
                ).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        try {
            return LocalDate.parse(command.birthDate());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private String findUserId(
            List<FindIdCustomerCandidate> candidates,
            Long verifiedCustomerId
    ) {
        return candidates.stream()
                .filter(candidate -> candidate.customerId()
                        .equals(verifiedCustomerId))
                .map(FindIdCustomerCandidate::userId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "계좌 검증 고객이 아이디 찾기 후보에 없습니다."
                ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
