package com.shinhan.corebank.transfer.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FavoriteAccountErrorCode implements ErrorCode {

    ALIAS_LENGTH_EXCEEDED("FAV0001", 400, "별칭 길이 제한을 초과했습니다."),
    FAVORITE_ACCOUNT_NOT_FOUND("FAV0201", 404, "등록된 계좌를 찾을 수 없습니다."),
    FAVORITE_ACCOUNT_ALREADY_EXISTS("FAV0301", 409, "이미 등록된 계좌입니다."),
    MAX_FAVORITE_ACCOUNTS_EXCEEDED("FAV0302", 409, "자주 쓰는 계좌는 최대 20건까지 등록할 수 있습니다.");

    private final String code;
    private final int status;
    private final String message;
}
