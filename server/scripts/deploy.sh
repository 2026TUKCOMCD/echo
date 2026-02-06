#!/bin/bash
set -e

APP_NAME="echo"
JAR_PATH="build/libs/echo-0.0.1-SNAPSHOT.jar"
LOG_FILE="app.log"
BRANCH="main"
PROFILE="prod"

echo "========== [$APP_NAME] 배포 시작 =========="

# 1. 최신 코드 pull
echo "[1/5] 최신 코드 pull ($BRANCH 브랜치)..."
git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"

# 2. Gradle 빌드 (테스트 스킵)
echo "[2/5] Gradle 빌드 (테스트 스킵)..."
chmod +x ./gradlew
./gradlew clean build -x test

# 3. 기존 프로세스 종료
echo "[3/5] 기존 프로세스 종료..."
PID=$(pgrep -f "$JAR_PATH" || true)
if [ -n "$PID" ]; then
  kill "$PID"
  echo "기존 프로세스(PID=$PID) 종료 완료"
  sleep 2
else
  echo "실행 중인 프로세스 없음"
fi

# 4. 새 JAR 실행
echo "[4/5] 새 JAR 실행 (profile=$PROFILE)..."
nohup java -jar "$JAR_PATH" --spring.profiles.active="$PROFILE" > "$LOG_FILE" 2>&1 &
NEW_PID=$!
echo "새 프로세스 PID=$NEW_PID"

# 5. 실행 확인
echo "[5/5] 실행 확인..."
sleep 5
if ps -p "$NEW_PID" > /dev/null 2>&1; then
  echo "애플리케이션 정상 실행 중 (PID=$NEW_PID)"
  echo "로그 확인: tail -f $LOG_FILE"
else
  echo "애플리케이션 실행 실패! 로그를 확인하세요:"
  tail -20 "$LOG_FILE"
  exit 1
fi

echo "========== [$APP_NAME] 배포 완료 =========="
