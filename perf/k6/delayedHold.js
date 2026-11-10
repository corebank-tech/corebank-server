import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { login } from './lib/session.js';
import { reqParams, idempotencyKey, body } from './lib/http.js';
import { issueAccountPasswordToken, issueOtpToken } from './lib/transfer.js';
import { customer } from './lib/ph60Accounts.js';
import { getRequiredBaseUrl } from './lib/env.js';

// PH-36b 지연이체 정상 지급률용 보류 건 만들기. 부하 측정이 아니다.
// 고객마다 지연이체를 켜고(기준금액 THRESHOLD, 지연 DELAY_HOURS) 기준금액 이상을 당행 이체해 보류시킨다.
// 서버 스위치 APP_DELAYED_TRANSFER_ENABLED=true 상태에서만 보류된다. 꺼져 있으면 바로 기표되니 held 카운터로 확인한다.
// 고객 대역: 출금 8001~, 수취 9001~ — interbankFault·load(1~200대)와 겹치지 않는 대역이다.
//
//   BASE="https://<기존 ALB DNS>/api/v1"
//   k6 run --insecure-skip-tls-verify -e BASE_URL="$BASE" perf/k6/delayedHold.js
//   # 실패분은 안 쓴 고객으로 동시 1개로 다시 건다. PH-36b는 FROM_SEQ=8051 COUNT=13 이었다
//   k6 run --insecure-skip-tls-verify -e BASE_URL="$BASE" -e FROM_SEQ=8051 -e COUNT=13 -e VUS=1 perf/k6/delayedHold.js
//
// 판정 SQL은 HELD 로그의 holdId 범위로 거른다(ph36b_results.md 4-2).

const BASE_URL = getRequiredBaseUrl();
const COUNT = Number(__ENV.COUNT || 50);
const FROM_SEQ = Number(__ENV.FROM_SEQ || 8001);
const THRESHOLD = Number(__ENV.THRESHOLD || 10000);
const DELAY_HOURS = Number(__ENV.DELAY_HOURS || 3);
const ACCOUNT_PASSWORD = __ENV.ACCOUNT_PASSWORD || '1234';

const held = new Counter('held');
const notHeld = new Counter('not_held');
const failed = new Counter('failed');

export const options = {
  scenarios: {
    hold: { executor: 'shared-iterations', vus: Number(__ENV.VUS || 5), iterations: COUNT, maxDuration: '10m' },
  },
};

export default function () {
  const i = exec.scenario.iterationInTest;
  const me = customer(FROM_SEQ + i);
  const peer = customer(FROM_SEQ + 1000 + i);
  // 기준금액 이상, 1,000 배수를 피해 다른 측정 이체와 구분한다
  const amount = THRESHOLD + 9876 + i;

  try {
    const session = login(BASE_URL, me.userId, me.password);

    const setting = http.put(
      `${BASE_URL}/transfers/delayed-transfer-setting`,
      JSON.stringify({ enabled: true, thresholdAmount: THRESHOLD, delayHours: DELAY_HOURS }),
      reqParams(session, { 'Idempotency-Key': idempotencyKey() }, 'delayed_setting')
    );
    const settingBody = body(setting);
    if (setting.status !== 200 || !settingBody || settingBody.code !== '0000') {
      throw new Error(`setting failed: ${setting.status} ${setting.body}`);
    }

    const cmd = { withdrawalAccountId: me.demandAccountId, depositAccountNumber: peer.demandAccountNumber, amount };
    const apw = issueAccountPasswordToken(BASE_URL, session, cmd.withdrawalAccountId, ACCOUNT_PASSWORD);
    const otp = issueOtpToken(BASE_URL, session, cmd);
    const res = http.post(
      `${BASE_URL}/transfers`,
      JSON.stringify({ ...cmd, myPassbookMemo: 'ph36b-hold', recipientPassbookMemo: 'ph36b-hold' }),
      reqParams(session, {
        'Idempotency-Key': idempotencyKey(),
        'Account-Password-Auth-Token': apw,
        'Otp-Auth-Token': otp,
      }, 'transfer')
    );
    const data = (body(res) || {}).data;
    if (res.status === 200 && data && data.transferHold) {
      held.add(1);
      console.log(`HELD seq=${me.seq} amount=${amount} holdId=${data.transferHold.transferHoldId} releaseAt=${data.transferHold.releaseAt}`);
    } else {
      notHeld.add(1);
      console.log(`NOT_HELD seq=${me.seq} http=${res.status} body=${res.body}`);
    }
  } catch (e) {
    failed.add(1);
    console.log(`FAILED seq=${me.seq} ${e.message}`);
  }
}
