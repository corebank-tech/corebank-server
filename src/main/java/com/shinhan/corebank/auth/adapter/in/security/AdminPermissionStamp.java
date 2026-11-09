package com.shinhan.corebank.auth.adapter.in.security;

import java.io.Serial;
import java.io.Serializable;

// 관리자가 로그인할 때 읽은 권한 회수 횟수. 세션의 인증 객체 details 에 실려 회수 이후의 낡은 세션을 가려낸다.
public record AdminPermissionStamp(long version) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
