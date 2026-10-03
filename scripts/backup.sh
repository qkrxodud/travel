#!/usr/bin/env bash
# 나의 영토 MySQL 백업 — 운영 compose(territory)의 mysql 컨테이너에서 mysqldump → backups/territory-YYYYmmdd-HHMMSS.sql.gz
#   scripts/backup.sh                 # 기본 프로젝트 territory
#   COMPOSE_PROJECT=territory-e2e COMPOSE_FILES="-f compose.yaml -f compose.e2e.yaml" scripts/backup.sh
# 비밀번호는 컨테이너 안의 MYSQL_ROOT_PASSWORD 환경변수를 쓴다(호스트 명령줄·로그에 남기지 않음).
# 카드 PNG(share-cards 볼륨)는 캐시라 백업하지 않는다(지워져도 다시 그린다).
set -euo pipefail
cd "$(dirname "$0")/.."

PROJECT="${COMPOSE_PROJECT:-territory}"
FILES="${COMPOSE_FILES:--f compose.yaml}"
KEEP="${BACKUP_KEEP:-14}"   # 최근 N 개만 남긴다(0 이면 지우지 않음)
mkdir -p backups
OUT="backups/${PROJECT}-$(date +%Y%m%d-%H%M%S).sql.gz"

# shellcheck disable=SC2086
docker compose -p "$PROJECT" $FILES exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --routines --triggers --events \
     --set-gtid-purged=OFF --default-character-set=utf8mb4 --databases "$MYSQL_DATABASE"' 2> >(grep -v "Using a password" >&2) \
  | gzip -9 > "$OUT.tmp"

# 덤프가 끝까지 쓰였는지 확인(mysqldump 는 마지막 줄에 "Dump completed" 를 남긴다)
if ! gzip -dc "$OUT.tmp" | tail -n 1 | grep -q "Dump completed"; then
  rm -f "$OUT.tmp"
  echo "백업 실패: 덤프가 완료되지 않았습니다" >&2
  exit 1
fi
mv "$OUT.tmp" "$OUT"
chmod 600 "$OUT"
echo "백업 완료: $OUT ($(du -h "$OUT" | cut -f1))"

if [ "$KEEP" -gt 0 ]; then
  ls -1t backups/"${PROJECT}"-*.sql.gz 2>/dev/null | tail -n +"$((KEEP + 1))" | xargs -r rm -f
fi
