import http from 'k6/http';
import { login } from './lib/session.js';
import { executeTransfer } from './lib/transfer.js';
import { executeInterbankTransfer } from './lib/interbank.js';
import { customer, customerCount } from './lib/ph60Accounts.js';
import { getRequiredBaseUrl } from './lib/env.js';

// 상대 은행이 무응답일 때 당행 이체가 버티는지 본다(PH-33-② 완료 기준, #711).
// 당행 이체를 고정 도착률로 재면서 타행 이체를 낮은 비율로 함께 흘리고, 스위치 NONE · NO_RESPONSE 를 한 번씩 돌려
// 당행 transfer_duration p95·p99 와 처리량을 비교한다. 비교는 이 스크립트끼리만 한다 — load.js 와는 배경 부하가 다르다.
//
// EC2(B 단계)는 9301 이 닫혀 있어 스위치를 컨테이너 안에서 켠다(mock_external_server.md §4). 부하 생성기는 대상과 다른 기기다.
//
//   BASE="https://<측정 대상>/api/v1"
//   STATS="avg,min,med,p(90),p(95),p(99),max"
//   docker exec corebank-mock-external wget -qO- --post-data='' 'http://localhost:9301/faults?mode=NONE'
//   k6 run -e BASE_URL="$BASE" -e RATE=5 -e FAULT_MODE=NONE \
//     --summary-trend-stats="$STATS" --summary-export=fault_none.json perf/k6/interbankFault.js
//   docker exec corebank-mock-external wget -qO- --post-data='' 'http://localhost:9301/faults?mode=NO_RESPONSE'
//   k6 run -e BASE_URL="$BASE" -e RATE=5 -e FAULT_MODE=NO_RESPONSE \
//     --summary-trend-stats="$STATS" --summary-export=fault_no_response.json perf/k6/interbankFault.js
//   docker exec corebank-mock-external wget -qO- --post-data='' 'http://localhost:9301/faults?mode=NONE'
//
// 끝나면 남은 TIMEOUT 을 조회거래가 확정할 때까지(1분 주기) 기다린 뒤 정합성을 본다.
// 로컬은 -e FAULT_ADMIN_URL=http://localhost:9301 을 주면 스위치를 setup() 에서 켜고 teardown() 에서 NONE 으로 되돌린다.

const FAULT_MODES = ['NONE', 'NO_RESPONSE'];
const FAULT_MODE = __ENV.FAULT_MODE;
if (!FAULT_MODES.includes(FAULT_MODE)) {
  throw new Error(`FAULT_MODE 가 필요하다 — ${FAULT_MODES.join(' · ')} 중 하나. 결과 파일이 어느 모드에서 잰 것인지 남긴다`);
}
const FAULT_ADMIN_URL = __ENV.FAULT_ADMIN_URL || null;

// 고정 수준만 잰다. 두 모드를 같은 수준에서 비교해야 해서 램프업 모드는 두지 않는다.
if (!__ENV.RATE) {
  throw new Error('RATE 가 필요하다 — 당행 이체 고정 도착률(/s)');
}
const RATE = Number(__ENV.RATE);
const DURATION = __ENV.DURATION || '3m';
// 고정 5~10/s 수준만 재므로 load.js(램프업 60/s 용)보다 작게 둔다. 세션을 VU 합계만큼 두 벌 만들어야 해서다.
const MAX_VUS = Number(__ENV.MAX_VUS || 50);
const INTERBANK_RATE = Number(__ENV.INTERBANK_RATE || 1);
const INTERBANK_MAX_VUS = Number(__ENV.INTERBANK_MAX_VUS || 20);
const TOTAL_VUS = MAX_VUS + INTERBANK_MAX_VUS;
const SESSION_POOL = Number(__ENV.SESSION_POOL || TOTAL_VUS);
const INTERBANK_POOL = Number(__ENV.INTERBANK_POOL || TOTAL_VUS);
const PAYEE_BANK_CODE = __ENV.PAYEE_BANK_CODE || '004';

// VU 번호는 두 시나리오를 합쳐 매겨진다. 세션이 VU 합계보다 적으면 두 VU 가 같은 고객을 집는다 —
// 당행은 계좌 행 락이 섞이고, 타행은 OTP 발급이 같은 고객의 앞 요청을 만료시켜 반복이 끊긴다.
if (SESSION_POOL < TOTAL_VUS || INTERBANK_POOL < TOTAL_VUS) {
  throw new Error(
    `SESSION_POOL(${SESSION_POOL}) · INTERBANK_POOL(${INTERBANK_POOL}) 은 VU 합계(${TOTAL_VUS}) 이상이어야 한다 — 두 VU 가 같은 고객을 집는다`
  );
}
// 당행 출금 1..P · 당행 수취 P+1..2P · 타행 출금 2P+1..2P+Q. 넘치면 customer() 랩어라운드로 대역이 겹친다.
if (SESSION_POOL * 2 + INTERBANK_POOL > customerCount) {
  throw new Error(
    `고객 ${SESSION_POOL * 2 + INTERBANK_POOL} 명이 필요한데 시드는 ${customerCount} 명이다 — SESSION_POOL · INTERBANK_POOL 을 줄인다`
  );
}

export const options = {
  discardResponseBodies: false,
  scenarios: {
    domestic: {
      executor: 'constant-arrival-rate',
      exec: 'domestic',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.min(MAX_VUS, 50),
      maxVUs: MAX_VUS,
    },
    interbank: {
      executor: 'constant-arrival-rate',
      exec: 'interbank',
      rate: INTERBANK_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.min(INTERBANK_MAX_VUS, 10),
      maxVUs: INTERBANK_MAX_VUS,
    },
  },
};

const BASE_URL = getRequiredBaseUrl();
const ACCOUNT_PASSWORD = __ENV.ACCOUNT_PASSWORD || '1234';

function pad(value, width) {
  let s = String(value);
  while (s.length < width) s = '0' + s;
  return s;
}

// 응답 본문이 바뀐 뒤 값이다("NO_RESPONSE delayMs=0"). 첫 단어로 실제 반영을 확인한다.
function setFault(mode) {
  const res = http.post(`${FAULT_ADMIN_URL}/faults?mode=${mode}`, null, { tags: { step: 'fault_switch' } });
  if (res.status !== 200 || String(res.body).split(' ')[0] !== mode) {
    throw new Error(`fault switch ${mode} failed: ${res.status} ${res.body}`);
  }
}

export function setup() {
  if (FAULT_ADMIN_URL) {
    setFault(FAULT_MODE);
    console.log(`장애 스위치 ${FAULT_MODE} 로 켬 (${FAULT_ADMIN_URL})`);
  } else {
    console.log(`FAULT_ADMIN_URL 없음 — 스위치가 ${FAULT_MODE} 인지 docker exec 로 먼저 확인했어야 한다`);
  }

  const domesticSessions = [];
  for (let i = 1; i <= SESSION_POOL; i++) {
    const me = customer(i);
    domesticSessions.push({ session: login(BASE_URL, me.userId, me.password), me });
  }
  const interbankSessions = [];
  for (let i = 1; i <= INTERBANK_POOL; i++) {
    const me = customer(SESSION_POOL * 2 + i);
    interbankSessions.push({ session: login(BASE_URL, me.userId, me.password), me });
  }
  console.log(`세션 당행 ${domesticSessions.length} · 타행 ${interbankSessions.length} 준비 완료 (측정 구간 밖)`);
  return { domesticSessions, interbankSessions };
}

export function domestic(data) {
  const picked = data.domesticSessions[__VU % data.domesticSessions.length];
  const me = picked.me;
  const peer = customer(SESSION_POOL + me.seq);

  // load.js 와 같은 금액 규칙 — 1,000 배수를 피해 시드 이체와 구분한다.
  executeTransfer(
    BASE_URL,
    picked.session,
    { withdrawalAccountId: me.demandAccountId, depositAccountNumber: peer.demandAccountNumber, amount: 19876 + me.seq },
    ACCOUNT_PASSWORD
  );
}

export function interbank(data) {
  const picked = data.interbankSessions[__VU % data.interbankSessions.length];
  const me = picked.me;

  // 모의 대외기관은 1001·1002 로 시작하지 않는 계좌를 모두 승인한다(MockBank). 당행 금액과 다른 대역을 써 원장에서 구분한다.
  executeInterbankTransfer(
    BASE_URL,
    picked.session,
    {
      withdrawalAccountId: me.demandAccountId,
      payeeBankCode: PAYEE_BANK_CODE,
      depositAccountNumber: `77${pad(me.seq, 10)}`,
      amount: 29876 + me.seq,
    },
    ACCOUNT_PASSWORD
  );
}

export function teardown() {
  if (FAULT_ADMIN_URL) {
    setFault('NONE');
    console.log('장애 스위치 NONE 으로 되돌림 — 남은 TIMEOUT 은 조회거래가 1분 주기로 확정한다');
  }
}
