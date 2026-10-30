import { login } from './lib/session.js';
import { issueAccountPasswordToken, issueOtpToken, postTransfer } from './lib/transfer.js';
import { customer } from './lib/ph60Accounts.js';
import { getRequiredBaseUrl } from './lib/env.js';

// 정합성 계열: 계좌 하나에 동시 요청을 몰아 잔액이 어긋나는지 본다.
// 빠른지가 아니라 틀리는지를 보므로 pass/fail 판정이고, 규모는 100 고정이다.
// 근거: docs/phase2/harness.md §3-2
const CONCURRENCY = Number(__ENV.CONCURRENCY || 100);
const AMOUNT = Number(__ENV.AMOUNT || 19876);
const BASE_URL = getRequiredBaseUrl();
const ACCOUNT_PASSWORD = __ENV.ACCOUNT_PASSWORD || '1234';

// per-vu-iterations 라야 VU 당 정확히 1건이 보장된다. shared-iterations 는 먼저 끝난 VU 가
// 남은 반복을 더 가져가는데(k6 공식문서: 반복이 고르게 분배되지 않는다), 토큰을 VU 번호로
// 집어오므로 그 VU 는 이미 쓴 1회용 토큰을 다시 보내 거절되고 다른 VU 토큰은 남는다.
// 그러면 실제 동시 이체가 100건보다 적어져 「동일 계좌 100건 동시」 전제가 깨진다.
export const options = {
  scenarios: {
    hotspot: {
      executor: 'per-vu-iterations',
      vus: CONCURRENCY,
      iterations: 1,
      maxDuration: '2m',
    },
  },
};

// 토큰은 전부 1회용이라 건당 한 쌍이 필요하다. 측정 구간 안에서 발급하면
// 계좌비밀번호 검증이 이체와 같은 account 행을 잠가, 지연의 원인을 구분할 수 없다.
export function setup() {
  const me = customer(1);
  const peer = customer(2);
  const session = login(BASE_URL, me.userId, me.password);

  const cmd = {
    withdrawalAccountId: me.demandAccountId,
    depositAccountNumber: peer.demandAccountNumber,
    amount: AMOUNT,
  };

  const tokens = [];
  for (let i = 0; i < CONCURRENCY; i++) {
    tokens.push({
      apw: issueAccountPasswordToken(BASE_URL, session, me.demandAccountId, ACCOUNT_PASSWORD),
      otp: issueOtpToken(BASE_URL, session, cmd),
    });
  }

  console.log(`토큰 ${tokens.length}쌍 선발급 완료 · 계좌 ${me.demandAccountId} · 건당 ${AMOUNT}원`);
  return { session, cmd, tokens };
}

export default function (data) {
  const t = data.tokens[__VU - 1];
  if (!t) return;
  postTransfer(BASE_URL, data.session, data.cmd, t.apw, t.otp);
}
