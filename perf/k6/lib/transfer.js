import http from 'k6/http';
import { Trend, Rate } from 'k6/metrics';
import { reqParams, idempotencyKey, body } from './http.js';

// 공식 지표는 이체 호출 구간만 집계한다. 토큰 발급 3건은 다른 트랙이 개선할 대상이 아니라서
// 합산하면 개선폭이 희석된다. 근거는 docs/phase2/harness.md §2-1.
export const transferDuration = new Trend('transfer_duration', true);
export const transferSuccess = new Rate('transfer_success');

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
