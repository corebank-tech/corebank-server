import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { body } from './lib/http.js';
import { customer } from './lib/ph60Accounts.js';
import { getRequiredBaseUrl } from './lib/env.js';

// PH-08 무차별 로그인 1만 건 주입 테스트(#900).
// 가입 계정과 없는 아이디에 같은 틀린 비밀번호를 보내, 응답 순서와 응답 시간으로 둘을 구분할 수 있는지 잰다.
// 대상 하나에 5회(잠금까지) + 1회(잠긴 뒤)를 순서대로 보낸다. 기본값은 대상 1,000쌍이라 5회 구간이 1만 건이다.
//
// 실행 전후에 할 일:
//   · 가입 계정 대역(REGISTERED_START부터 TARGET_PAIRS개)이 잠겨 있지 않아야 한다. 이미 잠겨 있으면 1회째부터 ATH0102 라 비교가 깨진다.
//   · 측정이 끝나면 그 대역을 SQL 로 풀어 둔다. 대역은 다른 k6 측정(load · interbankFault, 풀 200명 안팎)과 겹치지 않는 뒤쪽을 기본으로 쓴다.
//   · 없는 아이디는 RUN_TAG 를 붙여 매번 새로 만든다. 지난 실행의 카운터가 30일 남아 있기 때문이다.
//
//   k6 run -e BASE_URL="https://<기존 ALB DNS>/api/v1" \
//     --summary-trend-stats="avg,min,med,p(90),p(95),p(99),max" \
//     --summary-export=ph08_login_enumeration.json perf/k6/loginEnumeration.js

const BASE_URL = getRequiredBaseUrl();
const TARGET_PAIRS = Number(__ENV.TARGET_PAIRS || 1000);
const REGISTERED_START = Number(__ENV.REGISTERED_START || 9001);
const CONTROL_START = Number(__ENV.CONTROL_START || 8901);
const CONTROL_COUNT = Number(__ENV.CONTROL_COUNT || 100);
const VUS = Number(__ENV.VUS || 4);
const WRONG_PASSWORD = 'ph08-wrong-pw';
const CORRECT_PASSWORD = '1234';
const LOCK_THRESHOLD = 5;
const ATTEMPTS_PER_TARGET = LOCK_THRESHOLD + 1;
// 아이디 규칙 ^[A-Za-z0-9_-]{1,20}$ 안에 들도록 4자로 자른다
const RUN_TAG = (__ENV.RUN_TAG || Date.now().toString(36)).slice(-4);

if (REGISTERED_START + TARGET_PAIRS - 1 > 10000) {
  throw new Error(`가입 계정 대역 ${REGISTERED_START}~${REGISTERED_START + TARGET_PAIRS - 1} 이 시드 1만 명을 넘는다`);
}
if (CONTROL_START + CONTROL_COUNT > REGISTERED_START) {
  throw new Error('대조군 대역이 공격 대역과 겹친다 — CONTROL_START · CONTROL_COUNT 를 줄인다');
}

export const options = {
  scenarios: {
    attack: {
      executor: 'shared-iterations',
      vus: VUS,
      // 짝수 반복은 가입 계정, 홀수 반복은 없는 아이디라 두 쪽이 같은 시간대에 섞여 돈다
      iterations: TARGET_PAIRS * 2,
      maxDuration: '30m',
      exec: 'attack',
    },
    control: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: CONTROL_COUNT,
      maxDuration: '10m',
      exec: 'control',
      // 기본값은 공격과 같은 시간대에 정상 사용자가 막히지 않는지 본다. 공격 뒤 확인은 다음 실행의 대조군이 맡는다
      startTime: __ENV.CONTROL_START_TIME || '0s',
    },
  },
  thresholds: {
    // 요약 JSON 에 항목이 반드시 남도록 건다. 판정은 리포트에서 한다
    sequence_identical: ['rate>=0'],
  },
};

// 실패 경로 쌍별 응답 시간 — 이름 끝이 registered / unregistered 로 짝을 이룬다
const wrongPasswordRegistered = new Trend('wrong_password_registered', true);
const wrongPasswordUnregistered = new Trend('wrong_password_unregistered', true);
const lockingRegistered = new Trend('locking_attempt_registered', true);
const lockingUnregistered = new Trend('locking_attempt_unregistered', true);
const lockedRegistered = new Trend('locked_registered', true);
const lockedUnregistered = new Trend('locked_unregistered', true);

const blockedAtThreshold = new Rate('blocked_at_threshold');
const sequenceIdentical = new Rate('sequence_identical');
const controlLoginSuccess = new Rate('control_login_success');
const unexpectedStatus = new Counter('unexpected_status');

const EXPECTED_SEQUENCE = buildExpectedSequence();

// 가입 계정의 실제 응답과 같은 모양을 기대값으로 둔다. 5회째부터는 잠금 응답이다
function buildExpectedSequence() {
  const sequence = [];
  for (let attempt = 1; attempt <= ATTEMPTS_PER_TARGET; attempt++) {
    sequence.push(attempt < LOCK_THRESHOLD ? `ATH0101:${attempt}:${LOCK_THRESHOLD - attempt}` : 'ATH0102');
  }
  return sequence;
}

function unregisteredUserId(pairIndex) {
  // 예: ph08n_k3x9_00001 (16자)
  return `ph08n_${RUN_TAG}_${String(pairIndex + 1).padStart(5, '0')}`;
}

function loginAttempt(userId, password) {
  return http.post(`${BASE_URL}/auth/login`, JSON.stringify({ userId, password }), {
    jar: new http.CookieJar(),
    headers: { 'Content-Type': 'application/json' },
    tags: { step: 'login' },
  });
}

// 응답 코드와 남은 횟수까지 묶어 한 줄로 만든다. 두 쪽이 이 문자열까지 같아야 구분할 수 없다
function responseSignature(res) {
  const payload = body(res);
  if (!payload) {
    return `HTTP${res.status}`;
  }
  if (payload.code === 'ATH0101' && payload.data) {
    return `ATH0101:${payload.data.errorCount}:${payload.data.remainingAttempts}`;
  }
  return payload.code;
}

function recordDuration(isRegistered, attempt, duration) {
  if (attempt < LOCK_THRESHOLD) {
    (isRegistered ? wrongPasswordRegistered : wrongPasswordUnregistered).add(duration);
  } else if (attempt === LOCK_THRESHOLD) {
    (isRegistered ? lockingRegistered : lockingUnregistered).add(duration);
  } else {
    (isRegistered ? lockedRegistered : lockedUnregistered).add(duration);
  }
}

export function attack() {
  const iteration = exec.scenario.iterationInTest;
  const pairIndex = Math.floor(iteration / 2);
  const isRegistered = iteration % 2 === 0;
  const userId = isRegistered ? customer(REGISTERED_START + pairIndex).userId : unregisteredUserId(pairIndex);

  const signatures = [];
  for (let attempt = 1; attempt <= ATTEMPTS_PER_TARGET; attempt++) {
    const res = loginAttempt(userId, WRONG_PASSWORD);
    if (res.status !== 401 && res.status !== 403) {
      unexpectedStatus.add(1, { status: String(res.status) });
    }
    recordDuration(isRegistered, attempt, res.timings.duration);
    signatures.push(responseSignature(res));
  }

  blockedAtThreshold.add(signatures[LOCK_THRESHOLD - 1] === 'ATH0102', { kind: isRegistered ? 'registered' : 'unregistered' });
  const isIdentical = signatures.join('|') === EXPECTED_SEQUENCE.join('|');
  sequenceIdentical.add(isIdentical, { kind: isRegistered ? 'registered' : 'unregistered' });
  if (!isIdentical) {
    console.warn(`sequence mismatch kind=${isRegistered ? 'registered' : 'unregistered'} userId=${userId} got=${signatures.join('|')}`);
  }
}

// 공격 대역 밖의 가입 계정이 올바른 비밀번호로 로그인되는지 본다. 막히면 오탐이다
export function control() {
  const seq = CONTROL_START + exec.scenario.iterationInTest;
  const res = loginAttempt(customer(seq).userId, CORRECT_PASSWORD);
  const payload = body(res);
  controlLoginSuccess.add(res.status === 200 && payload !== null && payload.code === '0000');
}

export function setup() {
  console.log(
    `ph08 run_tag=${RUN_TAG} pairs=${TARGET_PAIRS} registered=ph60_user_${String(REGISTERED_START).padStart(5, '0')}~` +
      `${String(REGISTERED_START + TARGET_PAIRS - 1).padStart(5, '0')} control=${CONTROL_START}~${CONTROL_START + CONTROL_COUNT - 1} vus=${VUS}`
  );
}
