// 측정 대상 주소에 기본값을 두지 않는다. 이 스크립트들은 실제로 이체를 실행하므로,
// 기본값이 있으면 명령을 복사한 사람이 의도 없이 그 서버의 잔액·원장을 바꾼다.
// 세 시나리오가 같은 규칙을 쓰도록 여기 한 곳에 둔다.
// 근거: docs/phase2/harness.md §6
export function getRequiredBaseUrl() {
  const baseUrl = __ENV.BASE_URL;
  if (!baseUrl) {
    throw new Error('BASE_URL 이 필요하다 — 측정 대상 API 주소를 -e BASE_URL=... 로 지정한다');
  }
  return baseUrl;
}
