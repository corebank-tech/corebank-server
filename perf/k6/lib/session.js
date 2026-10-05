import http from 'k6/http';
import { body } from './http.js';

// 로그인으로 세션을 얻는 것만 담당한다. 이체 체인은 transfer.js 에 있다.

// 세션이 만료되기 전에 미리 갱신한다. 로그인 응답의 sessionExpiresAt 이 10분 뒤로 오는데,
// 만료 직전에 걸친 요청이 CMN0101 로 떨어지는 것을 막으려고 여유를 둔다.
const SESSION_RENEW_MARGIN_MS = 60 * 1000;

export function login(baseUrl, userId, password) {
  const res = http.post(
    `${baseUrl}/auth/login`,
    JSON.stringify({ userId, password }),
    { jar: new http.CookieJar(), headers: { 'Content-Type': 'application/json' }, tags: { step: 'login' } }
  );

  const payload = body(res);
  if (res.status !== 200 || !payload || payload.code !== '0000') {
    throw new Error(`login failed: ${res.status} ${res.body}`);
  }

  // 로그인 응답은 XSRF-TOKEN 을 두 번 내린다 — 이전 토큰을 만료시키는 빈 값이 먼저 오고
  // 새 토큰이 뒤에 온다. 첫 번째를 집으면 빈 문자열이 들어가 이체에서 403 이 난다.
  const lastValue = (name) => {
    const list = res.cookies[name] || [];
    for (let i = list.length - 1; i >= 0; i--) {
      if (list[i].value) return list[i].value;
    }
    return null;
  };

  const jsession = lastValue('JSESSIONID');
  const xsrf = lastValue('XSRF-TOKEN');
  if (!jsession || !xsrf) {
    throw new Error(`session cookies not found after login: ${JSON.stringify(res.cookies)}`);
  }

  return {
    userId,
    password,
    csrf: xsrf,
    cookie: `JSESSIONID=${jsession}; XSRF-TOKEN=${xsrf}`,
    customerId: payload.data.customerId,
    expiresAt: Date.parse(payload.data.sessionExpiresAt),
  };
}

export function ensureSession(baseUrl, session) {
  if (Date.now() < session.expiresAt - SESSION_RENEW_MARGIN_MS) {
    return session;
  }
  return login(baseUrl, session.userId, session.password);
}
