#!/usr/bin/env bash
#
# 실행 중인 애플리케이션에서 OpenAPI 문서를 받아 docs/openapi.generated.json 으로 저장한다.
#
# 운영 프로파일은 /v3/api-docs 를 닫는다. 그래서 프런트엔드가 볼 명세를 저장소에 파일로 둔다.
# 엔드포인트나 DTO 를 바꾼 뒤에는 이 스크립트를 다시 돌려 파일을 갱신한다.
#
# 이 파일은 구현에서 뽑아낸 결과다. 설계 계약 문서(docs/openapi.yaml)와는 역할이 다르며,
# 둘이 어긋나면 구현이나 계약 중 하나를 고쳐야 한다는 신호로 읽는다.
#
# 사용법
#   1. 로컬에 애플리케이션을 띄운다(기본 프로파일이나 local 프로파일이어야 문서가 열린다)
#        docker compose up -d mysql-db redis
#        COMPOSE_FILE=docker-compose.yml ./scripts/apply-db.sh --schema
#        docker compose --profile app up -d --build
#   2. 이 스크립트를 실행한다
#        ./scripts/export-openapi.sh
#
# 다른 주소에서 받으려면 BASE_URL 을 준다.
#   BASE_URL=http://127.0.0.1:18080 ./scripts/export-openapi.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
OUTPUT="${OUTPUT:-docs/openapi.generated.json}"

if [[ ! -f settings.gradle ]]; then
  echo "저장소 루트에서 실행해 주세요." >&2
  exit 1
fi

TMP="$(mktemp)"
trap 'rm -f "${TMP}"' EXIT

echo "받는 중: ${BASE_URL}/v3/api-docs"
if ! curl -fsS --max-time 30 "${BASE_URL}/v3/api-docs" -o "${TMP}"; then
  echo >&2
  echo "문서를 받지 못했습니다. 확인할 것:" >&2
  echo "  - 애플리케이션이 ${BASE_URL} 에서 실행 중인지" >&2
  echo "  - 프로파일이 prod 가 아닌지 (prod 는 springdoc 을 끕니다)" >&2
  exit 1
fi

# 사람이 읽고 diff 로 변경을 확인할 수 있게 들여쓰기를 넣어 저장한다.
# json.tool 은 키 순서를 유지하므로 경로 순서가 흐트러지지 않는다.
# --no-ensure-ascii 가 없으면 한글 설명이 \uXXXX 로 박혀 리뷰가 어려워진다.
python3 -m json.tool --indent 2 --no-ensure-ascii "${TMP}" "${OUTPUT}"
printf '\n' >> "${OUTPUT}"

PATH_COUNT="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["paths"]))' "${OUTPUT}")"
echo "저장 완료: ${OUTPUT} (경로 ${PATH_COUNT}개)"
