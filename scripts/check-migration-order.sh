#!/usr/bin/env bash
# 새로 추가한 Flyway V__ 마이그레이션이 base 최신 버전보다 앞이면 실패시킨다 (#814)
# 사용법: scripts/check-migration-order.sh <base 커밋> [head 커밋, 기본 HEAD]
#   로컬: scripts/check-migration-order.sh origin/dev
set -euo pipefail

BASE=${1:?"사용법: $0 <base 커밋> [head 커밋]"}
HEAD_REF=${2:-HEAD}
DIR=src/main/resources/db/migration

# 커밋을 못 찾으면 아래 비교가 빈 결과로 조용히 통과하므로 먼저 막는다 (얕은 checkout 등)
for ref in "$BASE" "$HEAD_REF"; do
  if ! git rev-parse -q --verify "$ref^{commit}" > /dev/null; then
    echo "::error::커밋 $ref 을(를) 찾을 수 없습니다. checkout fetch-depth가 0인지 확인하세요."
    exit 2
  fi
done

# V202610301000__x.sql → 202610301000, V20260728_1600__x.sql → 20260728.1600 (Flyway는 _를 .으로 읽는다)
# 끝의 .0은 Flyway가 없는 것으로 비교하므로 뗀다 (1000.0 = 1000)
version_of() {
  basename "$1" | sed -E -e 's/^V//' -e 's/__.*//' -e 's/_/./g' -e 's/(\.0+)+$//'
}

# 둘 중 큰 버전을 고른다. 마디별 숫자 비교라 Flyway 순서와 같다
max_version() {
  printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n 1
}

# base에 이미 있는 V__ 중 가장 큰 버전
base_max=""
for f in $(git ls-tree -r --name-only "$BASE" -- "$DIR" | grep '/V[^/]*\.sql$' || true); do
  v=$(version_of "$f")
  base_max=$(max_version "${base_max:-0}" "$v")
done

# head가 새로 추가한 V__. 이름만 바꾼 파일도 새 이름으로 검사하려고 --no-renames
added=$(git diff --no-renames --name-only --diff-filter=A "$BASE...$HEAD_REF" -- "$DIR" | grep '/V[^/]*\.sql$' || true)

if [ -z "$added" ]; then
  echo "검사 통과: 새 V__ 마이그레이션 없음"
  exit 0
fi
if [ -z "$base_max" ]; then
  echo "검사 통과: base에 V__ 마이그레이션이 없어 비교 대상 없음"
  exit 0
fi

failed=0
for f in $added; do
  v=$(version_of "$f")
  # 같은 번호도 Flyway가 중복으로 거절하므로 base 최신과 같거나 앞이면 실패
  if [ "$(max_version "$v" "$base_max")" = "$base_max" ]; then
    echo "::error file=$f::$(basename "$f")의 버전 $v 이(가) base 최신 V$base_max 보다 앞이거나 같습니다. 파일 이름을 base 최신보다 뒤 번호로 바꾸세요."
    failed=1
  else
    echo "통과: $(basename "$f") (V$v > base 최신 V$base_max)"
  fi
done

exit "$failed"
