#!/usr/bin/env bash
#
# 스키마와 시드를 이미 돌아가는 MySQL 컨테이너에 적용한다.
#
# docker-compose.prod.yml 은 db/ 파일을 MySQL 초기화 스크립트로 마운트해 두었다. 그것은 볼륨이
# 비어 있는 첫 기동에만 실행된다. 두 번째 배포부터는 볼륨에 데이터가 있어 실행되지 않으므로
# 마이그레이션과 시드를 이 스크립트로 넣는다.
#
# 사용법
#   ./scripts/apply-db.sh              스키마를 건드리지 않고 마이그레이션과 시드만 적용
#   ./scripts/apply-db.sh --schema     비어 있는 DB 에 스키마까지 적용
#
# 시드와 마이그레이션은 재실행해도 안전하게 쓰여 있다. 스키마는 CREATE TABLE 이라 이미 테이블이
# 있으면 실패한다. 그래서 --schema 를 기본으로 두지 않는다.

set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
SERVICE="${MYSQL_SERVICE:-mysql-db}"
DATABASE="${MYSQL_DATABASE:-flourishing}"

WITH_SCHEMA=false
if [[ "${1:-}" == "--schema" ]]; then
  WITH_SCHEMA=true
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "compose 파일을 찾지 못했습니다: ${COMPOSE_FILE}" >&2
  echo "저장소 루트에서 실행해 주세요." >&2
  exit 1
fi

# 비밀번호는 .env 에서 읽는다. 명령줄에 적으면 셸 히스토리와 프로세스 목록에 남는다.
if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  set -a && source .env && set +a
fi

if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "DB_PASSWORD 가 비어 있습니다. .env 를 확인해 주세요." >&2
  exit 1
fi

run_sql_file() {
  local file="$1"

  if [[ ! -f "${file}" ]]; then
    echo "건너뜁니다(파일 없음): ${file}"
    return 0
  fi

  echo "적용: ${file}"
  # -i 로 표준입력을 붙이고 -T 로 TTY 를 끈다. TTY 가 붙으면 파이프한 SQL 이 들어가지 않는다.
  docker compose -f "${COMPOSE_FILE}" exec -T \
    -e MYSQL_PWD="${DB_PASSWORD}" \
    "${SERVICE}" \
    mysql --default-character-set=utf8mb4 -u root "${DATABASE}" < "${file}"
}

if [[ "${WITH_SCHEMA}" == true ]]; then
  run_sql_file db/schema.sql
fi

# 마이그레이션을 파일 이름 순서로 적용한다. 각 파일이 재실행에 안전하게 쓰여 있다.
if [[ -d db/migration ]]; then
  for file in $(find db/migration -maxdepth 1 -name '*.sql' | sort); do
    run_sql_file "${file}"
  done
fi

# 시드는 규칙과 문구다. ON DUPLICATE KEY UPDATE 로 최신 문구에 맞춘다.
if [[ -d db/seed ]]; then
  for file in $(find db/seed -maxdepth 1 -name '*.sql' | sort); do
    run_sql_file "${file}"
  done
fi

echo
echo "확인"
docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD="${DB_PASSWORD}" "${SERVICE}" \
  mysql --default-character-set=utf8mb4 -u root "${DATABASE}" -e "
    SELECT
      (SELECT COUNT(*) FROM care_rules) AS care_rules,
      (SELECT COUNT(*) FROM care_ingredients WHERE active = TRUE) AS ingredients,
      (SELECT COUNT(*) FROM guide_sections) AS guide_sections,
      (SELECT COUNT(*) FROM rule_sets WHERE status = 'ACTIVE') AS active_rule_sets;
  "

echo
echo "care_rules 26 / ingredients 8 / guide_sections 6 / active_rule_sets 1 이면 정상입니다."
echo "active_rule_sets 가 0 이면 피부 보고 제출이 503 으로 막힙니다."
