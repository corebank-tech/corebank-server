import http from 'k6/http';

// 세션·이체 양쪽이 쓰는 요청 조립 규칙이다. 쿠키 인증과 멱등키 형식이 이 harness 전체에
// 걸리므로 한 곳에 둔다 — 어느 한 파일만 어긋나면 403·CMN0001 이 그 호출에서만 난다.

// k6 의 쿠키 저장소는 VU 마다 따로라 세션을 공유할 수 없다. 저장소를 안 쓰고 쿠키를 헤더로
// 직접 실어 보내면, setup() 에서 만든 세션 하나를 어느 VU 가 집어도 그대로 쓴다.
// jar 는 요청마다 새로 만든다 — 하나를 재사용하면 응답의 Set-Cookie 가 거기 쌓여서
// 여러 고객의 쿠키가 섞이고, 헤더로 보낸 세션과 어긋나 CMN0101·CMN0102 가 번갈아 난다.
export function reqParams(session, extra, step) {
  return {
    jar: new http.CookieJar(),
    tags: { step },
    headers: Object.assign(
      {
        'Content-Type': 'application/json',
        // 쿠키 인증이라 상태를 바꾸는 요청은 XSRF-TOKEN 값을 헤더로도 보내야 한다.
        // 빠뜨리면 서비스까지 가지 못하고 403 CMN0102 로 끊긴다.
        'X-XSRF-TOKEN': session.csrf,
        Cookie: session.cookie,
      },
      extra || {}
    ),
  };
}

// 이체는 Idempotency-Key 를 UUID v4 로만 받는다(CMN0001). 계좌비밀번호 검증은 형식을
// 검사하지 않아서, 임의 문자열을 쓰면 이체에서만 뒤늦게 막힌다.
export function idempotencyKey() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function body(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}
