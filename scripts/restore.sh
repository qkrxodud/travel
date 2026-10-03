#!/usr/bin/env bash
# 나의 영토 MySQL 복원 — backup.sh 가 만든 덤프로 DB 를 통째로 되돌린다(현재 데이터는 사라진다).
#   scripts/restore.sh backups/territory-20261003-120000.sql.gz          # 확인 질문
#   scripts/restore.sh backups/territory-20261003-120000.sql.gz --yes    # 묻지 않음
# 순서: app 중지 → 덤프 적용(DROP/CREATE DATABASE 포함) → app 기동(Flyway 가 스키마 버전 확인 + ddl validate).
set -euo pipefail
cd "$(dirname "$0")/.."

DUMP="${1:-}"
[ -n "$DUMP" ] && [ -f "$DUMP" ] || { echo "사용법: $0 <backups/*.sql.gz> [--yes]" >&2; exit 2; }
PROJECT="${COMPOSE_PROJECT:-territory}"
FILES="${COMPOSE_FILES:--f compose.yaml}"
# shellcheck disable=SC2206
DC=(docker compose -p "$PROJECT" $FILES)

if [ "${2:-}" != "--yes" ]; then
  read -r -p "프로젝트 '$PROJECT' 의 DB 를 $DUMP 로 덮어씁니다. 계속할까요? [y/N] " ans
  [ "$ans" = "y" ] || [ "$ans" = "Y" ] || { echo "취소"; exit 1; }
fi

"${DC[@]}" stop app
"${DC[@]}" up -d --wait mysql
# 덤프는 --databases 로 만들어 CREATE DATABASE/USE 를 포함한다 — 남은 테이블이 섞이지 않게 먼저 비운다.
"${DC[@]}" exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON \`$MYSQL_DATABASE\`.* TO \"$MYSQL_USER\"@\"%\";"' 2> >(grep -v "Using a password" >&2)
gzip -dc "$DUMP" | "${DC[@]}" exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4' 2> >(grep -v "Using a password" >&2)
"${DC[@]}" up -d --wait app
echo "복원 완료: $DUMP → $PROJECT"
