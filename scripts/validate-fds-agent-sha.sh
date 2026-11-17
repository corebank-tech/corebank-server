#!/usr/bin/env bash
set -euo pipefail

full_git_sha='^[0-9a-fA-F]{40}$'

# Compose의 필수 변수 검사는 형식을 확인하지 않으므로 실행 전에 전체 Git SHA를 검증한다.
for variable_name in FDS_AGENT_TAG FDS_AGENT_GIT_SHA; do
  value="${!variable_name:-}"
  if [[ ! "$value" =~ $full_git_sha ]]; then
    printf '%s\n' "$variable_name must be a full 40-character Git SHA" >&2
    exit 1
  fi
done

if [[ "$FDS_AGENT_TAG" != "$FDS_AGENT_GIT_SHA" ]]; then
  printf '%s\n' 'FDS_AGENT_TAG and FDS_AGENT_GIT_SHA must match' >&2
  exit 1
fi
