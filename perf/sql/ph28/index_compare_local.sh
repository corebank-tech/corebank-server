#!/usr/bin/env bash
# GL-IDX 인덱스 후보 로컬 비교(결과: docs/phase2/ph28_baseline_results.md §3)
# 대상: PH-60b를 적재한 로컬 MySQL 8.4.10 컨테이너(버퍼 풀 128MB). 사용: OUT=결과폴더 [N=5] [ONLY="c2 c3"] ./index_compare_local.sh
# 후보마다 인덱스 하나만 있는 상태로 잰다. 측정 SQL은 perf/sql/ph28_trial_balance_baseline.sql 의 EXPLAIN ANALYZE 문장 그대로다.
set -uo pipefail

REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
CONTAINER=${CONTAINER:-ph28b-perf-mysql}
SQL_FILE=$REPO/perf/sql/ph28_trial_balance_baseline.sql
OUT=${OUT:?결과 폴더}
N=${N:-5}
PERIODS=("2026-10-01 2026-10-01" "2026-09-01 2026-09-30" "2026-09-01 2026-10-05")
CANDIDATES=(
  "base|"
  "c1|ix_gl_je_c1 (account_code, trade_date)"
  "c2|ix_gl_je_c2 (trade_date, account_code, dr_cr, amount)"
  "c3|ix_gl_je_c3 (account_code, trade_date, dr_cr, amount)"
)
mkdir -p "$OUT"

q() { docker exec -i "$CONTAINER" sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" minicore -N -r' 2>/dev/null; }
now() { date '+%F %T'; }
ms() { perl -MTime::HiRes=time -e 'printf "%.0f\n", time*1000'; }
query_sql() { sed -n '/^EXPLAIN ANALYZE/,/^ORDER BY s.account_code;/p' "$SQL_FILE"; }

run_candidate() {
  local tag=$1 def=$2 name p from to i out t rows t0 t1
  echo "== $tag ${def:-'(추가 인덱스 없음)'} $(now)"
  if [[ -n "$def" ]]; then
    name=${def%% *}
    t0=$(ms); echo "CREATE INDEX $def ALGORITHM=INPLACE LOCK=NONE;" | sed "s/^CREATE INDEX $name /CREATE INDEX $name ON gl_journal_entry /" | q || return 1; t1=$(ms)
    echo "create_ms $tag $((t1 - t0))"
    echo "ANALYZE TABLE gl_journal_entry;" | q > /dev/null
    echo "SELECT ROUND(stat_value * @@innodb_page_size / 1048576, 1) FROM mysql.innodb_index_stats WHERE database_name='minicore' AND table_name='gl_journal_entry' AND index_name='$name' AND stat_name='size';" | q | sed "s/^/index_mb $tag /"
  fi
  for p in "${PERIODS[@]}"; do
    set -- $p; from=$1; to=$2
    echo "SET @from_date='$from'; SET @to_date='$to'; $(query_sql | sed '1s/^EXPLAIN ANALYZE/EXPLAIN FORMAT=TREE/')" | q > "$OUT/${tag}_${from}_${to}_plan.txt"
    echo "-- $tag $from ~ $to access: $(grep -oE '(Index range scan|Index scan|Covering index[^(]*|Table scan) on je[^(]*' "$OUT/${tag}_${from}_${to}_plan.txt" | head -1)"
    for i in $(seq 0 "$N"); do
      out=$(echo "SET @from_date='$from'; SET @to_date='$to'; $(query_sql)" | q) || { echo "failed $tag $i"; return 1; }
      t=$(printf '%s\n' "$out" | sed -nE '1s/.*actual time=[0-9.]+\.\.([0-9.]+).*/\1/p')
      rows=$(printf '%s\n' "$out" | sed -nE '1s/.*actual time=[0-9.]+\.\.[0-9.]+ rows=([0-9]+).*/\1/p')
      [[ $i -eq 0 ]] && printf '%s\n' "$out" > "$OUT/${tag}_${from}_${to}_analyze.txt"
      echo "$([[ $i -eq 0 ]] && echo first || echo warm) $tag $from~$to $i $t $rows"
    done
    echo "SET @from_date='$from'; SET @to_date='$to'; $(query_sql | sed '1s/^EXPLAIN ANALYZE//')" | q > "$OUT/${tag}_${from}_${to}_result.tsv"
  done
  if [[ -n "$def" ]]; then
    # account_code 로 시작하는 후보는 FK 가 자동으로 만든 fk_gl_journal_entry_account 를 MySQL 이 조용히 지운다.
    # 그 상태로는 후보를 지울 수 없으니(1553) FK 인덱스를 먼저 되살린다
    echo "fk_index_after_create $tag $(echo "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='minicore' AND table_name='gl_journal_entry' AND index_name='fk_gl_journal_entry_account';" | q)"
    if [[ "$(echo "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='minicore' AND table_name='gl_journal_entry' AND index_name='fk_gl_journal_entry_account';" | q)" == 0 ]]; then
      echo "CREATE INDEX fk_gl_journal_entry_account ON gl_journal_entry (account_code);" | q || return 1
    fi
    echo "DROP INDEX $name ON gl_journal_entry;" | q || return 1
  fi
  return 0
}

for c in "${CANDIDATES[@]}"; do
  [[ -n "${ONLY:-}" && " $ONLY " != *" ${c%%|*} "* ]] && continue
  run_candidate "${c%%|*}" "${c#*|}" || { echo "중단 $(now)"; exit 1; }
done
echo "-- 결과 동일성 (base 대비)"
for c in ${ONLY:-c1 c2 c3}; do
  for p in "${PERIODS[@]}"; do
    set -- $p
    if cmp -s "$OUT/base_$1_$2_result.tsv" "$OUT/${c}_$1_$2_result.tsv"; then echo "same $c $1~$2"; else echo "DIFF $c $1~$2"; fi
  done
done
echo "SHOW INDEX FROM gl_journal_entry;" | q | awk '{print "final_index", $3}' | sort -u
echo "== 끝 $(now)"
