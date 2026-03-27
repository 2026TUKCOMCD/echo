#!/bin/bash
set -e

AWS_REGION="ap-northeast-2"
ACCOUNT_ID="868859238182"
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
ECR_REPOSITORY="echo-server"
CONTAINER_NAME="echo-server"
PORT="8080"
ENV_FILE="/home/ec2-user/app/.env"

IMAGE_TAG="${1:-latest}"
IMAGE_URI="${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"

echo "===== [Echo] 배포 시작 - 태그: ${IMAGE_TAG} ====="

# 1. ECR 로그인 (IAM Role 기반, 액세스 키 불필요)
echo "[1/5] ECR 로그인..."
aws ecr get-login-password --region ${AWS_REGION} \
  | docker login --username AWS --password-stdin ${ECR_REGISTRY}

# 2. 새 이미지 Pull (중지 전에 미리 받아 실패 시 기존 컨테이너 유지)
echo "[2/5] 이미지 Pull: ${IMAGE_URI}"
docker pull ${IMAGE_URI}

# 3. 기존 컨테이너 정리
echo "[3/5] 기존 컨테이너 정리..."
docker stop ${CONTAINER_NAME} 2>/dev/null || true
docker rm ${CONTAINER_NAME} 2>/dev/null || true

# 4. 새 컨테이너 실행
echo "[4/5] 새 컨테이너 실행..."
docker run -d \
  --name ${CONTAINER_NAME} \
  -p ${PORT}:${PORT} \
  --env-file ${ENV_FILE} \
  --restart unless-stopped \
  ${IMAGE_URI}

# 5. 헬스체크 (최대 60초 대기)
echo "[5/5] 헬스체크..."
HEALTH_URL="http://localhost:${PORT}/actuator/health"
for i in $(seq 1 12); do
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" ${HEALTH_URL} 2>/dev/null || echo "000")
  if [ "${HTTP_STATUS}" = "200" ]; then
    echo "헬스체크 성공!"
    break
  fi
  if [ "${i}" = "12" ]; then
    echo "헬스체크 실패! 로그:"
    docker logs --tail 50 ${CONTAINER_NAME}
    exit 1
  fi
  echo "  대기 중... (${i}/12, HTTP ${HTTP_STATUS})"
  sleep 5
done

# 6. 미사용 이미지 정리 (디스크 절약)
docker image prune -f

echo "===== [Echo] 배포 완료! ====="
