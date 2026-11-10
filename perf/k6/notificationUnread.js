import http from 'k6/http';
import { Trend, Rate } from 'k6/metrics';
import { login } from './lib/session.js';
import { reqParams, body } from './lib/http.js';
import { customer } from './lib/ph60Accounts.js';
import { getRequiredBaseUrl } from './lib/env.js';

// PH-04 미읽음 건수 조회의 p95·p99 를 잰다. 대상은 load.js 가 이체를 보낸 출금 고객 1~200번이다 —
// 그 고객들에게 이체 알림이 쌓여 있어야 건수 집계가 실제 데이터 위에서 돈다.
const RATE = Number(__ENV.RATE || 10);
const DURATION = __ENV.DURATION || '3m';
const SESSION_POOL = Number(__ENV.SESSION_POOL || 200);

export const unreadCountDuration = new Trend('unread_count_duration', true);
export const unreadCountSuccess = new Rate('unread_count_success');

export const options = {
  scenarios: {
    unread: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
};

const BASE_URL = getRequiredBaseUrl();

// 로그인은 측정 구간 밖에서 끝낸다
export function setup() {
  const sessions = [];
  for (let i = 1; i <= SESSION_POOL; i++) {
    const me = customer(i);
    sessions.push(login(BASE_URL, me.userId, me.password));
  }
  console.log(`세션 ${sessions.length}개 준비 완료 (측정 구간 밖)`);
  return { sessions };
}

export default function (data) {
  const session = data.sessions[(__ITER + __VU) % data.sessions.length];
  const res = http.get(`${BASE_URL}/notifications/unread-count`, reqParams(session, null, 'unread_count'));
  const payload = body(res);
  const isSuccess = res.status === 200 && payload !== null && payload.code === '0000';

  unreadCountDuration.add(res.timings.duration);
  unreadCountSuccess.add(isSuccess);
}
