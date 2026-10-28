import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { reqParams, idempotencyKey, body } from './http.js';
import { issueAccountPasswordToken, issueOtpToken } from './transfer.js';

// 당행 transfer_duration 과 섞지 않는다. TIMEOUT 은 응답 대기(3초)를 다 채우고 와서 당행 p95 를 끌어올린다.
export const interbankDuration = new Trend('interbank_duration', true);

// TIMEOUT 은 처리 불명이지 실패가 아니라서 성공률 하나로 접지 않고 상태별로 센다.
export const interbankSuccess = new Counter('interbank_success');
export const interbankTimeout = new Counter('interbank_timeout');
export const interbankRejected = new Counter('interbank_rejected');
export const interbankError = new Counter('interbank_error');

// 서킷이 열리면 채번 전에 끊겨 거래번호 없는 TRF0308 이 온다(InterbankTransferService.verifyBeforeNumbering).
const NOT_PROCESSED_CODE = 'TRF0308';

function classify(res, data) {
  if (res.status !== 200 || !data) return 'error';
  if (data.status === 'SUCCESS') return 'success';
  if (data.status === 'TIMEOUT') return 'timeout';
  if (data.errorCode === NOT_PROCESSED_CODE && !data.transactionNumber) return 'rejected';
  return 'error';
}

const COUNTERS = {
  success: interbankSuccess,
  timeout: interbankTimeout,
  rejected: interbankRejected,
  error: interbankError,
};

export function postInterbankTransfer(baseUrl, session, cmd, accountPasswordToken, otpAuthToken, memo) {
  const label = memo || 'ph36';
  const res = http.post(
    `${baseUrl}/transfers/interbank`,
    JSON.stringify({
      withdrawalAccountId: cmd.withdrawalAccountId,
      payeeBankCode: cmd.payeeBankCode,
      depositAccountNumber: cmd.depositAccountNumber,
      amount: cmd.amount,
      myPassbookMemo: label,
      recipientPassbookMemo: label,
    }),
    reqParams(session, {
      'Idempotency-Key': idempotencyKey(),
      'Account-Password-Auth-Token': accountPasswordToken,
      'Otp-Auth-Token': otpAuthToken,
    }, 'interbank')
  );

  interbankDuration.add(res.timings.duration);

  const payload = body(res);
  const data = payload && payload.data;
  const outcome = classify(res, data);
  COUNTERS[outcome].add(1);

  return {
    outcome,
    status: data ? data.status : null,
    errorCode: data ? data.errorCode : null,
    transactionNumber: data ? data.transactionNumber : null,
    raw: res,
  };
}

export function executeInterbankTransfer(baseUrl, session, cmd, accountPassword) {
  const apw = issueAccountPasswordToken(baseUrl, session, cmd.withdrawalAccountId, accountPassword);
  // 서버가 이 4개 키로만 대조한다(TransferOtpVerificationAdapter). 키가 하나라도 더 붙으면 OTP0102.
  const otp = issueOtpToken(baseUrl, session, {
    withdrawalAccountId: cmd.withdrawalAccountId,
    payeeBankCode: cmd.payeeBankCode,
    depositAccountNumber: cmd.depositAccountNumber,
    amount: cmd.amount,
  });
  return postInterbankTransfer(baseUrl, session, cmd, apw, otp);
}
