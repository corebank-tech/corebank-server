import http from 'k6/http';
import { Trend, Rate } from 'k6/metrics';

// 공식 지표는 이체 호출 구간만 집계한다. 토큰 발급 3건은 다른 트랙이 개선할 대상이 아니라서
// 합산하면 개선폭이 희석된다. 근거는 docs/phase2/harness.md §2-1.
export const transferDuration = new Trend('transfer_duration', true);
export const transferSuccess = new Rate('transfer_success');

// 세션이 만료되기 전에 미리 갱신한다. 로그인 응답의 sessionExpiresAt 이 10분 뒤로 오는데,
// 만료 직전에 걸친 요청이 CMN0101 로 떨어지는 것을 막으려고 여유를 둔다.
const SESSION_RENEW_MARGIN_MS = 60 * 1000;

// k6 의 쿠키 저장소는 VU 마다 따로라 세션을 공유할 수 없다. 저장소를 안 쓰고 쿠키를 헤더로
// 직접 실어 보내면, setup() 에서 만든 세션 하나를 어느 VU 가 집어도 그대로 쓴다.
// jar 는 요청마다 새로 만든다 — 하나를 재사용하면 응답의 Set-Cookie 가 거기 쌓여서
// 여러 고객의 쿠키가 섞이고, 헤더로 보낸 세션과 어긋나 CMN0101·CMN0102 가 번갈아 난다.
function reqParams(session, extra, step) {
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
function idempotencyKey() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function body(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

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

// 1회용이고 300초 산다. 핫스팟 시나리오는 이 호출을 측정 구간 밖에서 미리 돌린다 —
// 계좌비밀번호 실패 횟수가 account 행에 있어서, 이체가 잠그는 바로 그 행을 여기서도 건드린다.
export function issueAccountPasswordToken(baseUrl, session, accountId, accountPassword) {
  const res = http.post(
    `${baseUrl}/accounts/${accountId}/password/verify`,
    JSON.stringify({ accountPassword }),
    reqParams(session, { 'Idempotency-Key': idempotencyKey() }, 'account_password')
  );

  const payload = body(res);
  if (res.status !== 200 || !payload || payload.code !== '0000') {
    throw new Error(`account password verify failed: ${res.status} ${res.body}`);
  }
  return payload.data.accountPasswordAuthToken;
}

// OTP 는 거래 내용에 묶인다. 발급 때 보낸 키와 이체 검증이 만드는 맵이 한 글자라도 다르면
// OTP0102 가 나는데, 비교 대상은 TransferOtpVerificationAdapter 가 만든다 —
// withdrawalAccountId 이지 Swagger 예시의 accountId 가 아니다.
// cmd 를 그대로 넘겨 두 본문이 어긋날 여지를 없앤다.
export function issueOtpToken(baseUrl, session, cmd) {
  const issued = http.post(
    `${baseUrl}/otp/issue`,
    JSON.stringify({ transactionType: 'IMMEDIATE_TRANSFER', transactionData: cmd }),
    reqParams(session, null, 'otp_issue')
  );

  const issuedBody = body(issued);
  if (issued.status !== 200 || !issuedBody || issuedBody.code !== '0000') {
    throw new Error(`otp issue failed: ${issued.status} ${issued.body}`);
  }

  // 1차 범위가 Mock OTP 라 응답에 6자리가 그대로 온다(otp.expose-code).
  // 이 설정이 꺼지면 harness 가 동작하지 않는다.
  const verified = http.post(
    `${baseUrl}/otp/verify`,
    JSON.stringify({
      otpRequestId: issuedBody.data.otpRequestId,
      otpCode: issuedBody.data.otpCode,
    }),
    reqParams(session, null, 'otp_verify')
  );

  const verifiedBody = body(verified);
  if (verified.status !== 200 || !verifiedBody || verifiedBody.code !== '0000') {
    throw new Error(`otp verify failed: ${verified.status} ${verified.body}`);
  }
  return verifiedBody.data.otpAuthToken;
}

// 200 이어도 data.status 가 ERROR 면 이체는 실패한 것이다. 성공률은 그 값으로 센다.
export function postTransfer(baseUrl, session, cmd, accountPasswordToken, otpAuthToken, memo) {
  const label = memo || 'ph30';
  const res = http.post(
    `${baseUrl}/transfers`,
    JSON.stringify({
      withdrawalAccountId: cmd.withdrawalAccountId,
      depositAccountNumber: cmd.depositAccountNumber,
      amount: cmd.amount,
      myPassbookMemo: label,
      recipientPassbookMemo: label,
    }),
    reqParams(session, {
      'Idempotency-Key': idempotencyKey(),
      'Account-Password-Auth-Token': accountPasswordToken,
      'Otp-Auth-Token': otpAuthToken,
    }, 'transfer')
  );

  transferDuration.add(res.timings.duration);

  const payload = body(res);
  const ok = res.status === 200 && payload && payload.data && payload.data.status === 'SUCCESS';
  transferSuccess.add(ok);

  return {
    ok,
    status: payload && payload.data ? payload.data.status : null,
    errorCode: payload && payload.data ? payload.data.errorCode : null,
    raw: res,
  };
}

// 토큰 발급부터 이체까지 한 번에 돈다. 분산 시나리오가 쓴다.
export function executeTransfer(baseUrl, session, cmd, accountPassword) {
  const apw = issueAccountPasswordToken(baseUrl, session, cmd.withdrawalAccountId, accountPassword);
  const otp = issueOtpToken(baseUrl, session, cmd);
  return postTransfer(baseUrl, session, cmd, apw, otp);
}
