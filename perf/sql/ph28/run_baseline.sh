#!/usr/bin/env bash
# PH-28 시산표 베이스라인 실행기. 기존 단일 EC2에서 실행한다(결과: docs/phase2/ph28_baseline_results.md)
# 절차 정본: docs/phase2/ph28_baseline_results.md §1. measure()의 반복·판정 규칙은 그대로다.
# 덧붙인 것: 회차별 시작·종료 시각(초), EXPLAIN ANALYZE 전문 저장, 테이블 전체 행 수, 통계 계산, 남은 쿼리 확인,
# 전 구간 일반 SELECT 3회 교차 측정(첫 회차가 60초를 넘으면 건너뜀).
#
#   ./run_ph28.sh check          # 측정 전·중단 뒤: 지금 도는 app_user 쿼리 목록. 운영 부하 거의 없음
#   ./run_ph28.sh dry            # EXPLAIN(실행 안 함)으로 SQL 추출·접속만 확인
#   ./run_ph28.sh run            # 본 측정. tmux 안에서 실행한다
#   ./run_ph28.sh summary <log>  # 로그에서 기간별 평균·p95·p99 계산 (DB 접속 없음)
set -uo pipefail

MODE=${1:-}
DIR=${PH28_DIR:-/home/ubuntu/ph28}
SQL_FILE=$DIR/perf/sql/ph28_trial_balance_baseline.sql
PERIODS=("2026-10-01 2026-10-01" "2026-09-01 2026-09-30" "2026-09-01 2026-10-05")
FIRST_MS=""
COMPOSE=${COMPOSE:-/home/ubuntu/corebank/docker-compose.yml}
MYSQL_HOST=$(grep -E '^\s*- DB_HOST=' "$COMPOSE" | sed -E 's/^\s*- DB_HOST=//')
export MYSQL_HOST
export MYSQL_USER=app_user
export MYSQL_DB=minicore

now() { date '+%F %T'; }
q() { mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" "$MYSQL_DB" "$@"; }
query_sql() { sed -n '/^EXPLAIN ANALYZE/,/^ORDER BY s.account_code;/p' "$SQL_FILE"; }

load_pwd() {
  MYSQL_PWD=$(grep -E '^\s*- DB_PASSWORD=' "$COMPOSE" | sed -E 's/^\s*- DB_PASSWORD=//')
  export MYSQL_PWD
  [[ -n "$MYSQL_PWD" ]] || { echo "DB_PASSWORD를 읽지 못했다"; return 1; }
}

header() {
  echo "== PH-28 $MODE 시작 $(now)"
  echo "image: $(docker ps --filter name=^corebank-server$ --format '{{.Image}}')"
  echo "sql sha256: $(sha256sum "$SQL_FILE" | cut -d' ' -f1)"
  q -t -e "SELECT NOW() db_now, VERSION() v, @@innodb_buffer_pool_size/1024/1024 bp_mb"
}

# 앱 서버도 app_user를 쓴다. 중단한 측정 쿼리를 끊을 때는 info가 EXPLAIN ANALYZE인 id만 KILL QUERY 한다.
check() {
  q -t -e "SELECT id, time AS sec, state, LEFT(REPLACE(info, '\n', ' '), 70) AS query
           FROM information_schema.processlist
           WHERE user = 'app_user' AND command <> 'Sleep' AND id <> CONNECTION_ID()"
  echo "측정 쿼리가 남아 있으면: mysql ... -e 'KILL QUERY <id>'  (info가 EXPLAIN ANALYZE인 행만)"
}

dry() {
  local p
  for p in "${PERIODS[@]}"; do
    set -- $p
    echo "-- dry $1 ~ $2"
    q -N -r -e "SET @from_date='$1'; SET @to_date='$2'; $(query_sql | sed '1s/^EXPLAIN ANALYZE/EXPLAIN FORMAT=TREE/')" || return 1
  done
}

# 이 쿼리는 기간과 무관하게 테이블 전체를 훑는다(fk_gl_journal_entry_account 인덱스 스캔 후 필터).
# 그래서 시간은 기간 행 수가 아니라 테이블 전체 행 수에 달려 있다. 재측정 비교용으로 앞뒤에 센다.
table_rows() {
  echo "-- table_rows $1 start $(now)"
  q -t -e "SELECT COUNT(*) AS gl_journal_entry_total_rows FROM gl_journal_entry" || return 1
  echo "-- table_rows $1 end $(now)"
}

# 문서 §1 measure()와 같은 판정. 출력 열: 구분 회차 시작일 시작시각 종료일 종료시각 actual_ms 결과행
measure() {
  local from=$1 to=$2
  local sql="SET @from_date='$from'; SET @to_date='$to'; $(query_sql)"
  local i out timing rows start end
  mkdir -p "$PLAN_DIR"
  echo "-- measure $from ~ $to"
  for i in $(seq 0 20); do
    start=$(now)
    if ! out=$(q -N -r -e "$sql"); then
      echo "mysql failed at iteration $i ($from~$to) $(now)"
      return 1
    fi
    end=$(now)
    printf '%s\n' "$out" > "$PLAN_DIR/${from}_${to}_$(printf %02d "$i").txt"
    timing=$(printf '%s\n' "$out" | sed -nE '1s/.*actual time=[0-9.]+\.\.([0-9.]+).*/\1/p')
    rows=$(printf '%s\n' "$out" | sed -nE '1s/.*actual time=[0-9.]+\.\.[0-9.]+ rows=([0-9]+).*/\1/p')
    if [[ ! "$timing" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
      echo "invalid actual time at iteration $i: $timing"
      return 1
    fi
    echo "$([[ $i -eq 0 ]] && echo first || echo warm) $i $start $end $timing $rows"
    if [[ $i -eq 0 && -z "$FIRST_MS" ]]; then FIRST_MS=$timing; fi
  done
  return 0
}

# 교차 측정(문서 §1 밖, 10/26 추가): EXPLAIN ANALYZE의 행별 계측 비용을 가늠하려고 같은 쿼리를 계측 없이 잰다.
# 클라이언트 벽시계라 접속·TLS 시간이 들어가므로 SELECT 1의 시간을 따로 재서 남긴다.
# 출력 열: plain 회차 시작일 시작시각 종료일 종료시각 wall_ms 결과행 / conn 회차 wall_ms
plain() {
  local from=2026-09-01 to=2026-10-05
  local sql="SET @from_date='$from'; SET @to_date='$to'; $(query_sql | sed '1s/^EXPLAIN ANALYZE//')"
  local i t0 t1 out start end rows
  if awk -v f="$FIRST_MS" 'BEGIN { exit !(f > 60000) }'; then
    echo "-- plain 건너뜀: 첫 회차 ${FIRST_MS}ms > 60000ms (A안으로 복귀)"
    return 0
  fi
  echo "-- conn"
  for i in 1 2 3; do
    t0=$(date +%s%N); q -N -e "SELECT 1" > /dev/null || return 1; t1=$(date +%s%N)
    echo "conn $i $(( (t1 - t0) / 1000000 ))"
  done
  echo "-- plain $from ~ $to"
  for i in 1 2 3; do
    start=$(now); t0=$(date +%s%N)
    if ! out=$(q -N -B -e "$sql"); then
      echo "mysql failed at plain $i $(now)"
      return 1
    fi
    t1=$(date +%s%N); end=$(now)
    printf '%s\n' "$out" > "$PLAN_DIR/plain_${from}_${to}_${i}.tsv"
    rows=$(printf '%s\n' "$out" | grep -c .)
    echo "plain $i $start $end $(( (t1 - t0) / 1000000 )) $rows"
  done
}

data_check() {
  local p
  q -t -e "SHOW INDEX FROM gl_journal_entry" || return 1
  for p in "${PERIODS[@]}"; do
    set -- $p
    echo "-- data $1 ~ $2 start $(now)"
    q -t -e "SET @from_date='$1'; SET @to_date='$2';
      SELECT COUNT(*) AS journal_entry_rows,
             SUM(CASE WHEN dr_cr = 'DEBIT'  THEN amount ELSE 0 END) AS debit_checksum,
             SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END) AS credit_checksum
      FROM gl_journal_entry WHERE trade_date BETWEEN @from_date AND @to_date;" || return 1
    echo "-- data end $(now)"
  done
}

# 문서 §1: 웜업 뒤 20회만 쓴다. p95 = 오름차순 19번째, p99 = 최댓값. 앞 10회·뒤 10회 평균은 회차 사이 추세 확인용
summary() {
  local log=$1
  awk '
    /^-- measure / { period = $3 "~" $5; order[++np] = period; next }
    /^first / { first[period] = $7; next }
    /^warm /  { n[period]++; v[period, n[period]] = $7; rows[period] = $8; next }
    /^plain [0-9]/ { pv[++pn] = $7; pr[pn] = $8; next }
    /^conn [0-9]/  { cv[++cn] = $3; next }
    END {
      printf "%-23s %3s %10s %10s %10s %10s %10s %10s %6s\n", "period", "n", "avg_ms", "p95_ms", "p99_ms", "first_ms", "avg_1-10", "avg_11-20", "rows"
      for (k = 1; k <= np; k++) {
        p = order[k]; m = n[p]
        for (i = 1; i <= m; i++) s[i] = v[p, i]
        for (i = 2; i <= m; i++) { x = s[i]; j = i - 1; while (j >= 1 && s[j] + 0 > x + 0) { s[j+1] = s[j]; j-- } s[j+1] = x }
        sum = 0; a = 0; b = 0
        for (i = 1; i <= m; i++) { sum += v[p, i]; if (i <= 10) a += v[p, i]; else b += v[p, i] }
        printf "%-23s %3d %10.1f %10.1f %10.1f %10.1f %10.1f %10.1f %6s\n", p, m, sum / m, s[19], s[m], first[p], a / 10, b / 10, rows[p]
        if (m != 20) print "  !! 표본이 20개가 아니다. 이 기간은 다시 잰다"
        last_avg = sum / m
      }
      if (pn > 0) {
        ps = 0; for (i = 1; i <= pn; i++) ps += pv[i]
        cs = 0; for (i = 1; i <= cn; i++) cs += cv[i]
        printf "plain(전 구간, 계측 없음) n=%d avg_wall_ms=%.1f  conn n=%d avg_ms=%.1f  plain-conn=%.1f  vs 전 구간 warm avg=%.1f\n", pn, ps / pn, cn, (cn ? cs / cn : 0), ps / pn - (cn ? cs / cn : 0), last_avg
        for (i = 1; i <= pn; i++) printf "  plain %d: %s ms, rows %s\n", i, pv[i], pr[i]
      }
    }' "$log"
}

main() {
  case "$MODE" in
    check) load_pwd && check ;;
    dry) load_pwd && header && dry ;;
    run) load_pwd && header && check && table_rows before \
           && measure 2026-10-01 2026-10-01 && measure 2026-09-01 2026-09-30 && measure 2026-09-01 2026-10-05 \
           && plain && table_rows after && data_check ;;
    # 중간에 멈춘 run을 이어 잰다(한 달, 전 구간, 교차 측정, 마무리). PH28_FIRST_MS는 앞 run의 첫 회차 값
    rest) load_pwd && header && check && FIRST_MS=${PH28_FIRST_MS:?첫 run의 first ms} \
           && measure 2026-09-01 2026-09-30 && measure 2026-09-01 2026-10-05 \
           && plain && table_rows after && data_check ;;
    *) echo "usage: $0 check|dry|run|rest|summary <log>"; return 2 ;;
  esac
  local rc=$?
  echo "== PH-28 $MODE 종료 $(now) rc=$rc"
  return $rc
}

if [[ "$MODE" == summary ]]; then
  summary "${2:?log 경로}"
  exit $?
fi

TS=$(date +%Y%m%d_%H%M%S)
LOG=$DIR/logs/ph28_${MODE}_${TS}.log
PLAN_DIR=$DIR/logs/plans_${TS}
mkdir -p "$DIR/logs"
echo "log: $LOG"
main 2>&1 | tee -a "$LOG"
exit "${PIPESTATUS[0]}"
