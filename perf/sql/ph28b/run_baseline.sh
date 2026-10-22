#!/usr/bin/env bash
# PH-28b 60b 오탐 기준선. 깨끗한 60b 위에서 거래일마다 GL 정합성 점검과 시산표를 한 번씩 호출해 결과를 TSV로 남긴다.
#
# 읽기만 한다. 결함을 주입하지 않는다. 탐지율은 GlIntegrityCheckFaultDetectionTest 행렬이 정본이다.
# 앱은 local 프로필로 측정 DB에 붙여 띄운다. GL_READ 권한이 있는 관리자로 관리자 로그인을 한다(admin_auth.md §6).
#
# 사용: BASE_URL=http://localhost:18080/api/v1 ADMIN_ID=adminreader ADMIN_PW='Admin1234!' \
#       ./run_baseline.sh 2026-09-01 2026-10-05 > baseline.tsv
set -euo pipefail

FROM=${1:?시작 거래일}
TO=${2:?종료 거래일}
BASE_URL=${BASE_URL:-http://localhost:18080/api/v1}
JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT

curl -sf -c "$JAR" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"${ADMIN_ID:?}\",\"password\":\"${ADMIN_PW:?}\"}" "$BASE_URL/admin/auth/login" > /dev/null

printf 'tradeDate\tchecked\texcluded\tunbalanced\tmismatch\torphan\torphanFinal\tclean\ttrialBalanced\tcheckMs\n'
day=$FROM
while [[ "$day" < "$TO" || "$day" == "$TO" ]]; do
  started=$(perl -MTime::HiRes=time -e 'printf "%d", time*1000')
  check=$(curl -sf -b "$JAR" "$BASE_URL/admin/gl/integrity-check?tradeDate=$day")
  ended=$(perl -MTime::HiRes=time -e 'printf "%d", time*1000')
  trial=$(curl -sf -b "$JAR" "$BASE_URL/admin/trial-balance?fromDate=$day&toDate=$day")
  jq -r --argjson ms $((ended - started)) --argjson balanced "$(jq '.data.balanced' <<<"$trial")" '
    .data | [ .tradeDate, .checkedTransactionCount,
              (.excludedTransactionCounts | to_entries | map("\(.key)=\(.value)") | join(",")),
              .unbalancedVoucherCount, .ledgerVoucherMismatchCount, .orphanVoucherCount,
              .orphanVoucherCountFinal, .clean, $balanced, $ms ] | @tsv' <<<"$check"
  # macOS와 리눅스의 date 옵션이 달라 이미 쓰는 perl로 다음 날을 구한다
  day=$(perl -MTime::Piece -MTime::Seconds -e 'print((Time::Piece->strptime($ARGV[0], "%Y-%m-%d") + ONE_DAY)->ymd)' "$day")
done
