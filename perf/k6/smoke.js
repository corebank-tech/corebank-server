import { login, executeTransfer } from './lib/session.js';
import { getRequiredBaseUrl } from './lib/env.js';

// session.js 가 수동으로 통과시킨 4단계를 그대로 재현하는지만 본다. 수치는 믿지 않는다.
export const options = { vus: 1, iterations: 1 };

const BASE_URL = getRequiredBaseUrl();
const ACCOUNT_PASSWORD = __ENV.ACCOUNT_PASSWORD || '1234';

// 시드 이체는 10,000~99,000 사이 1,000원 단위라(Phase2MinimumSeedService.transferAmount)
// 1,000 으로 안 나뉘는 금액을 쓰면 원장에서 harness 가 만든 건만 골라낼 수 있다.
const AMOUNT = Number(__ENV.AMOUNT || 19876);

export default function () {
  const session = login(BASE_URL, 'ph60_user_00001', '1234');
  console.log(`login ok · customerId=${session.customerId}`);

  const result = executeTransfer(
    BASE_URL,
    session,
    { withdrawalAccountId: 60000001, depositAccountNumber: '860100000002', amount: AMOUNT },
    ACCOUNT_PASSWORD
  );

  console.log(`transfer amount=${AMOUNT} status=${result.status} errorCode=${result.errorCode}`);
  if (!result.ok) {
    throw new Error(`transfer not SUCCESS: ${result.raw.body}`);
  }
}
