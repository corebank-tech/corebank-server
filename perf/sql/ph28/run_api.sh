#!/usr/bin/env bash
# PH-28 시산표 API 응답 시간 측정. 기존 단일 EC2에서 실행한다(결과: docs/phase2/ph28_baseline_results.md)
# EC2 안에서 운영 컨테이너(localhost:80)로 호출해 인터넷 구간 변동을 뺀다.
# 기간마다 다시 로그인하고(세션 만료 대비), 예열 1회 + N회. 응답 JSON은 개선 후 결과 동일성 비교용으로 보관한다.
#
#   ./run_ph28_api.sh probe   # 로그인 + 잘못된 기간(400) 1회. DB 집계를 돌리지 않는다
#   ./run_ph28_api.sh run     # 본 측정. tmux 안에서 실행한다 (아이디·비밀번호는 화면에서 입력)
#   ./run_ph28_api.sh summary <log>
set -uo pipefail
umask 077   # 응답 JSON과 로그를 본인만 읽게 한다

MODE=${1:-}
N=${N:-5}
DIR=${PH28_DIR:-/home/ubuntu/ph28}
BASE=http://localhost/api/v1
PERIODS=("2026-10-01 2026-10-01" "2026-09-01 2026-09-30" "2026-09-01 2026-10-05")
SID=""

now() { date '+%F %T'; }

login() {
  local hdr code
  hdr="$OUT/.login_hdr"
  code=$(jq -n --arg u "$ADMIN_ID" --arg p "$ADMIN_PW" '{userId: $u, password: $p}' \
    | curl -s -o "$OUT/login_resp.json" -D "$hdr" -w '%{http_code}' \
        -H 'Content-Type: application/json' --data-binary @- "$BASE/admin/auth/login")
  SID=$(grep -i '^set-cookie: JSESSIONID=' "$hdr" | sed -E 's/^[Ss]et-[Cc]ookie: JSESSIONID=([^;]+).*/\1/' | tr -d '\r')
  rm -f "$hdr"
  if [[ "$code" != 200 || -z "$SID" ]]; then
    echo "login failed http=$code $(jq -c '{code, message}' "$OUT/login_resp.json" 2>/dev/null)"
    return 1
  fi
  echo "-- login ok $(now)"
}

# 출력 열: 구분 회차 시작일 시작시각 종료일 종료시각 http time_ms code rows balanced
call() {
  local kind=$1 i=$2 from=$3 to=$4 file start end w http t code rows bal
  file="$OUT/${from}_${to}_$(printf %02d "$i").json"
  start=$(now)
  w=$(curl -s -o "$file" -w '%{http_code} %{time_total}' -H "Cookie: JSESSIONID=$SID" \
      "$BASE/admin/trial-balance?fromDate=$from&toDate=$to")
  end=$(now)
  http=${w%% *}
  t=$(awk -v s="${w##* }" 'BEGIN { printf "%.1f", s * 1000 }')
  code=$(jq -r '.code // "-"' "$file" 2>/dev/null)
  rows=$(jq -r '.data.rows | length' "$file" 2>/dev/null)
  bal=$(jq -r '.data.balanced' "$file" 2>/dev/null)
  echo "$kind $i $start $end $http $t $code $rows $bal"
  [[ "$http" == 200 && "$code" == 0000 ]] || { echo "call failed at $kind $i ($from~$to)"; return 1; }
  return 0
}

measure() {
  local from=$1 to=$2 i
  echo "-- api $from ~ $to"
  login || return 1
  call first 0 "$from" "$to" || return 1
  for i in $(seq 1 "$N"); do
    call warm "$i" "$from" "$to" || return 1
  done
  return 0
}

probe() {
  local http
  login || return 1
  http=$(curl -s -o "$OUT/probe.json" -w '%{http_code}' -H "Cookie: JSESSIONID=$SID" \
      "$BASE/admin/trial-balance?fromDate=2026-10-05&toDate=2026-10-01")
  echo "probe http=$http $(jq -c '{code, message}' "$OUT/probe.json")"
  [[ "$http" == 400 ]]   # 기간 역전 CMN0003 이면 인증·권한·경로가 모두 맞다
}

summary() {
  awk '
    /^-- api / { p = $3 "~" $5; order[++np] = p; next }
    /^first / { first[p] = $8; next }
    /^warm /  { n[p]++; v[p, n[p]] = $8; rows[p] = $10; bal[p] = $11 }
    END {
      printf "%-23s %3s %10s %10s %10s %10s %6s %8s\n", "period", "n", "avg_ms", "median_ms", "max_ms", "first_ms", "rows", "balanced"
      for (k = 1; k <= np; k++) {
        p = order[k]; m = n[p]; sum = 0
        for (i = 1; i <= m; i++) { s[i] = v[p, i]; sum += v[p, i] }
        for (i = 2; i <= m; i++) { x = s[i]; j = i - 1; while (j >= 1 && s[j] + 0 > x + 0) { s[j+1] = s[j]; j-- } s[j+1] = x }
        med = (m % 2) ? s[(m + 1) / 2] : (s[m / 2] + s[m / 2 + 1]) / 2
        printf "%-23s %3d %10.1f %10.1f %10.1f %10.1f %6s %8s\n", p, m, sum / m, med, s[m], first[p], rows[p], bal[p]
      }
    }' "$1"
}

if [[ "$MODE" == summary ]]; then summary "${2:?log 경로}"; exit $?; fi
[[ "$MODE" == probe || "$MODE" == run ]] || { echo "usage: $0 probe|run|summary <log>"; exit 2; }

read -rp "관리자 아이디: " ADMIN_ID
read -rsp "비밀번호: " ADMIN_PW; echo
TS=$(date +%Y%m%d_%H%M%S)
OUT=$DIR/logs/api_${MODE}_${TS}
mkdir -p "$OUT"
# 중간에 끊겨도 세션 쿠키가 담긴 헤더와 로그인 응답을 남기지 않는다
trap 'rm -f "$OUT/.login_hdr" "$OUT/login_resp.json"' EXIT
LOG=$OUT/api.log
echo "log: $LOG"

main() {
  echo "== PH-28 API $MODE 시작 $(now) N=$N"
  echo "image: $(docker ps --filter name=^corebank-server$ --format '{{.Image}}')"
  case "$MODE" in
    probe) probe ;;
    run) measure 2026-10-01 2026-10-01 && measure 2026-09-01 2026-09-30 && measure 2026-09-01 2026-10-05 ;;
  esac
  local rc=$?
  rm -f "$OUT/login_resp.json"
  echo "== PH-28 API $MODE 종료 $(now) rc=$rc"
  return $rc
}
main 2>&1 | tee -a "$LOG"
exit "${PIPESTATUS[0]}"
