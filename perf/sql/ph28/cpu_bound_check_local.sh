#!/usr/bin/env bash
# 가설: 개선 전 시산표 집계는 코어 하나(스레드 하나)의 CPU에 묶여 있다.
# 확인 1: 쿼리 1회 동안 mysqld 스레드별 CPU 시간 / 경과 시간. 한 스레드가 100% 근처면 코어 하나가 꽉 찬 것
# 확인 2: 버퍼 풀 128MB(운영과 같음)와 2GB(데이터 전부 메모리)에서 시간 비교. 거의 같으면 CPU, 크게 줄면 디스크가 병목
# 대상: PH-60b를 적재한 로컬 MySQL 8.4.10 컨테이너. 끝나면 버퍼 풀을 128MB로 되돌린다. 결과: docs/phase2/ph28_baseline_results.md §2
set -uo pipefail
C=${CONTAINER:-ph28b-perf-mysql}
REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
SQL="SET @from_date='2026-09-01'; SET @to_date='2026-10-05'; $(sed -n '/^EXPLAIN ANALYZE/,/^ORDER BY s.account_code;/p' "$REPO/perf/sql/ph28_trial_balance_baseline.sql" | sed '1s/^EXPLAIN ANALYZE//')"
q() { docker exec -i "$C" sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" minicore -N -B' 2>/dev/null; }
# 스레드별 utime+stime(틱, 100 = 1초)과 VM 전체 iowait 틱
snap() { docker exec -u root "$C" sh -c 'for t in /proc/1/task/*; do echo "$(basename $t) $(awk "{print \$14+\$15}" $t/stat)"; done; awk "/^cpu /{print \"iowait\", \$6}" /proc/stat'; }
status() { echo "SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_data_read','Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests');" | q | awk '{printf "%s=%s ", $1, $2} END {print ""}'; }
ms() { perl -MTime::HiRes=time -e 'printf "%.0f\n", time*1000'; }

one_run() {
  local label=$1 before after s0 t0 t1 s1
  before=$(snap); s0=$(status); t0=$(ms)
  # 쿼리가 실패한 회차를 측정값으로 내지 않는다
  if ! echo "$SQL" | q > /dev/null; then echo "$label: 쿼리 실패. 측정을 멈춘다"; return 1; fi
  t1=$(ms); after=$(snap); s1=$(status)
  python3 - "$label" "$((t1 - t0))" "$before" "$after" "$s0" "$s1" <<'EOF'
import sys
label, el = sys.argv[1], int(sys.argv[2]) / 1000
def parse(x): return {l.split()[0]: int(l.split()[1]) for l in x.strip().splitlines()}
b, a = parse(sys.argv[3]), parse(sys.argv[4])
d = sorted(((a[k] - b.get(k, 0)) / 100, k) for k in a if k != 'iowait')[::-1]
top, total = d[0][0], sum(x for x, _ in d)
st = lambda s: dict(kv.split('=') for kv in s.split())
s0, s1 = st(sys.argv[5]), st(sys.argv[6])
mb = (int(s1['Innodb_data_read']) - int(s0['Innodb_data_read'])) / 1048576
pr = int(s1['Innodb_buffer_pool_reads']) - int(s0['Innodb_buffer_pool_reads'])
print(f"{label}: 경과 {el:.2f}s | 가장 바쁜 스레드 CPU {top:.2f}s ({top / el * 100:.0f}% of 1코어) | mysqld 전체 CPU {total:.2f}s | 2위 스레드 {d[1][0]:.2f}s | 디스크 읽기 {mb:.0f}MB, 페이지 {pr} | VM iowait {(a['iowait'] - b['iowait']) / 100:.2f}s")
EOF
}

# 버퍼 풀을 바꾸고 실제로 그 크기가 된 것을 확인한다. 끝나지 않거나 크기가 다르면 실패
resize() {
  local target=$1 st size i
  echo "SET GLOBAL innodb_buffer_pool_size = $target;" | q || { echo "크기 변경 요청 실패($target)"; return 1; }
  for i in $(seq 1 120); do
    st=$(echo "SHOW STATUS LIKE 'Innodb_buffer_pool_resize_status_code';" | q | cut -f2)
    size=$(echo "SELECT @@innodb_buffer_pool_size;" | q)
    # 상태 코드 0 = 진행 중인 크기 변경 없음
    [[ "$st" == 0 && "$size" == "$target" ]] && { echo "버퍼 풀 $((size / 1048576))MB"; return 0; }
    sleep 1
  done
  echo "버퍼 풀 크기 변경이 끝나지 않음(목표 $target, 현재 $size, 상태 $st)"; return 1
}

# 중간에 끊기거나 실패해도 128MB로 되돌린다. 같은 컨테이너의 index_compare_local.sh 가 128MB를 전제로 한다
restore() { resize 134217728 || echo "!! 버퍼 풀을 128MB로 되돌리지 못했다. 직접 확인할 것"; }
trap restore EXIT

echo "== 버퍼 풀 128MB"
resize 134217728 || exit 1
for r in warm 1 2 3; do one_run "128MB $r" || exit 1; done

echo "== 버퍼 풀 2GB로 확장"
resize 2147483648 || exit 1
for r in load1 load2 1 2 3; do one_run "2GB $r" || exit 1; done

echo "== 버퍼 풀 128MB로 되돌림(종료 처리)"
