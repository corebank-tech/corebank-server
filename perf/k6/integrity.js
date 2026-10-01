import { login, issueAccountPasswordToken, issueOtpToken, postTransfer } from './lib/session.js';
import { customer } from './lib/seed.js';
import { getRequiredBaseUrl } from './lib/env.js';

// 정합성 계열: 계좌 하나에 동시 요청을 몰아 잔액이 어긋나는지 본다.
// 빠른지가 아니라 틀리는지를 보므로 pass/fail 판정이고, 규모는 100 고정이다.
// 근거: docs/phase2/harness.md §3-2
const CONCURRENCY = Number(__ENV.CONCURRENCY || 100);
const AMOUNT = Number(__ENV.AMOUNT || 19876);
const BASE_URL = getRequiredBaseUrl();
const ACCOUNT_PASSWORD = __ENV.ACCOUNT_PASSWORD || '1234';

export const options = {
  scenarios: {
    hotspot: {
      executor: 'shared-iterations',
      vus: CONCURRENCY,
      iterations: CONCURRENCY,
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
