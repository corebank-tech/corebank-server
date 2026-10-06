import { login } from './lib/session.js';
import { executeTransfer } from './lib/transfer.js';
import { customer } from './lib/ph60Accounts.js';
import { getRequiredBaseUrl } from './lib/env.js';

// 부하 계열: 계좌를 분산해 락 경합을 없앤 상태에서 처리량 한계를 찾는다.
// 동시 사용자 수가 아니라 초당 이체 시도 수(iteration/s)를 올린다 — 사용자 수를 올리면 서버가 느려질 때
// 사용자가 대기에 묶여 실제 도착률이 따라 오르지 않아 한계점이 보이지 않는다.
// 근거: docs/phase2/harness.md §3-1
// RATE 를 주면 그 도착률을 고정해 한 수준만 잰다(constant-arrival-rate).
// 주지 않으면 램프업으로 훑는다(ramping-arrival-rate).
// 수준별 「어디까지 견뎠나 · 어디서부터 이슈가 나나」는 고정 모드로만 답할 수 있다 —
// 램프업 1회는 k6 종료 요약이 전 구간을 합산해 변곡점이 사라진다.
// 근거: docs/personal/convention/measurement-report.md
const FIXED_RATE = __ENV.RATE ? Number(__ENV.RATE) : null;
const FIXED_DURATION = __ENV.DURATION || '3m';
const START_RATE = Number(__ENV.START_RATE || 5);
const PEAK_RATE = Number(__ENV.PEAK_RATE || 60);
const STEP_DURATION = __ENV.STEP_DURATION || '45s';
const MAX_VUS = Number(__ENV.MAX_VUS || 200);
// 측정 중에는 로그인하지 않는다. ramping-arrival-rate 는 반복마다 풀에서 빈 VU 를 아무거나
// 집어오므로, VU 안에서 세션을 캐시하면 사실상 매 반복이 새 로그인이 된다(실측으로 확인).
// setup() 에서 세션을 미리 만들어 넘기면 로그인이 측정 구간 밖으로 빠진다.
// 세션 수가 VU 수보다 적으면 여러 VU 가 같은 고객을 집어 계좌 행 락이 섞인다.
// 분산 시나리오의 전제가 깨지므로 VU 수만큼 둔다.
const SESSION_POOL = Number(__ENV.SESSION_POOL || MAX_VUS);

const fixedScenario = {
  level: {
    executor: 'constant-arrival-rate',
    rate: FIXED_RATE,
    timeUnit: '1s',
    duration: FIXED_DURATION,
    preAllocatedVUs: Math.min(MAX_VUS, 50),
    maxVUs: MAX_VUS,
  },
};

const rampScenario = {
  ramp: {
    executor: 'ramping-arrival-rate',
    startRate: START_RATE,
    timeUnit: '1s',
    preAllocatedVUs: Math.min(MAX_VUS, 50),
    maxVUs: MAX_VUS,
    stages: [
      { target: Math.round(PEAK_RATE * 0.25), duration: STEP_DURATION },
      { target: Math.round(PEAK_RATE * 0.5), duration: STEP_DURATION },
      { target: Math.round(PEAK_RATE * 0.75), duration: STEP_DURATION },
      { target: PEAK_RATE, duration: STEP_DURATION },
    ],
  },
};

export const options = {
  discardResponseBodies: false,
  scenarios: FIXED_RATE ? fixedScenario : rampScenario,
};

const BASE_URL = getRequiredBaseUrl();
const ACCOUNT_PASSWORD = __ENV.ACCOUNT_PASSWORD || '1234';

// 고객을 분산한다. 같은 고객을 돌리면 계좌 행 락이 걸려 분산 시나리오가 아니게 되고,
// 1일 이체한도 5천만에도 금방 닿는다.
export function setup() {
  const sessions = [];
  for (let i = 1; i <= SESSION_POOL; i++) {
    const me = customer(i);
    sessions.push({ session: login(BASE_URL, me.userId, me.password), me });
  }
  console.log(`세션 ${sessions.length}개 준비 완료 (측정 구간 밖)`);
  return { sessions };
}

export default function (data) {
  const picked = data.sessions[__VU % data.sessions.length];
  const me = picked.me;

  // 금액을 고객마다 달리해 시드(1,000원 단위)와 구분하고, 원장에서 어느 고객이 만든 건지도 읽는다.
  const amount = 19876 + me.seq;
  // 받는 쪽은 출금 고객 집합 밖에서 고른다. 바로 옆 순번을 쓰면 그 계좌가 다른 VU 의
  // 출금 계좌라 입금 쪽에서도 같은 행을 잠근다.
  const peer = customer(SESSION_POOL + me.seq);

  executeTransfer(
    BASE_URL,
    picked.session,
    {
      withdrawalAccountId: me.demandAccountId,
      depositAccountNumber: peer.demandAccountNumber,
      amount,
    },
    ACCOUNT_PASSWORD
  );
}
