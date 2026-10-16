package com.shinhan.corebank.gl.api;

/**
 * 전표 기표 계약 (PH-24). 다른 도메인은 GL 에 이 인터페이스로만 기표한다.
 *
 * <p>호출자 트랜잭션 안에서만 돈다({@code MANDATORY}). 트랜잭션 없이 부르면 즉시 실패한다 — 전표가 원장과
 * 다른 커밋에 들어가면 "원장은 있는데 전표가 없는" 상태가 정상이 되기 때문이다.
 *
 * <p>실패는 예외로 던진다. 삼키지 않는다. 같은 {@code (txType, referenceKey)} 로 두 번 부르면 예외다.
 */
public interface JournalPostingUseCase {

    void post(JournalRequest request);
}
