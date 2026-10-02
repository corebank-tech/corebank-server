// PH-60 시드의 생성 공식을 그대로 옮긴 것이다. 고객당 계좌 3개가 요구불·정기예금·적금 순으로
// 깔리고 요구불이 첫 번째라, 계좌 ID 는 고객 순번에 3을 곱해 얻는다.
// 근거: Phase2MinimumSeedService (ACCOUNTS_PER_CUSTOMER=3, accountNumber(), demandAccountIndex())
const ACCOUNTS_PER_CUSTOMER = 3;
const ACCOUNT_ID_START = 60000001;
const CUSTOMER_COUNT = 10000;

function pad(value, width) {
  let s = String(value);
  while (s.length < width) s = '0' + s;
  return s;
}

// seq 는 1부터 시작하는 고객 순번이다.
export function customer(seq) {
  const index = ((seq - 1) % CUSTOMER_COUNT) + 1;
  return {
    seq: index,
    userId: `ph60_user_${pad(index, 5)}`,
    password: '1234',
    demandAccountId: ACCOUNT_ID_START + (index - 1) * ACCOUNTS_PER_CUSTOMER,
    demandAccountNumber: `86010${pad(index, 7)}`,
  };
}

export const customerCount = CUSTOMER_COUNT;
