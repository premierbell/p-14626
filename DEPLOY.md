# 백엔드 무중단 배포 가이드

이 가이드는 AWS + Terraform + Docker + GitHub Actions + HAProxy를 사용하여 Spring Boot 백엔드를 Blue-Green 무중단 배포하는 방법을 설명합니다.

## 무중단 배포 아키텍처

- **HAProxy**: 로드밸런서로 8090 포트에서 트래픽 수신
- **Blue-Green 배포**: app1_1과 app1_2 두 컨테이너를 번갈아 배포
- **헬스체크**: `/actuator/health` 엔드포인트로 서버 상태 확인
- **다운타임**: 0초 (HAProxy가 자동으로 정상 서버로만 트래픽 전달)

## 사전 준비사항

### 1. AWS 계정 설정
- AWS 계정 생성 및 MFA 설정
- IAM admin 계정 생성
- AWS CLI 설치 및 액세스 키 등록

```bash
# AWS CLI 설치 확인
aws --version

# AWS 액세스 키 등록
aws configure
# Access Key ID 입력
# Secret Access Key 입력
# Region: ap-northeast-2
# Output format: (엔터)
```

### 2. Terraform 설치
```bash
# Terraform 설치 확인
terraform --version
```

Windows: https://developer.hashicorp.com/terraform/install
Mac: `brew install terraform`

### 3. GitHub 설정
리포지터리를 생성하고 다음 Secrets를 등록합니다:

**Settings > Secrets and variables > Actions > New repository secret**

필수 Secrets:
- `AWS_REGION`: `ap-northeast-2`
- `AWS_ACCESS_KEY_ID`: AWS IAM 액세스 키
- `AWS_SECRET_ACCESS_KEY`: AWS IAM 시크릿 키
- `EC2_INSTANCE_ID`: EC2 인스턴스 ID (Terraform 실행 후 입력)
- `APPLICATION_SECRET`: application-secret.yml 내용

#### APPLICATION_SECRET 설정
`backend/src/main/resources/application-secret.yml.example` 파일을 참고하여 다음 내용을 작성:

```yaml
custom:
  jwt:
    secretPattern: your-jwt-secret-pattern-minimum-32-characters
```

## 배포 단계

### 1단계: Terraform으로 인프라 구축

```bash
cd infra

# Terraform 초기화
terraform init

# 실행 계획 확인
terraform plan

# 인프라 생성
terraform apply
# yes 입력
```

실행이 완료되면 다음 정보가 출력됩니다:
- `ec2_public_ip`: EC2 인스턴스의 공용 IP 주소
- `ec2_instance_id`: EC2 인스턴스 ID (GitHub Secrets에 등록 필요)

**중요**: `ec2_instance_id`를 GitHub Secrets의 `EC2_INSTANCE_ID`에 등록하세요.

### 2단계: EC2 초기 설정

EC2 인스턴스가 생성되면 약 5-10분 후 다음 서비스들이 자동으로 설치됩니다:
- Docker
- Nginx Proxy Manager (포트 80, 443, 81)
- MySQL (포트 3306, 데이터베이스: app_prod)
- Redis (포트 6379)
- **HAProxy (포트 8090) - 무중단 배포용 로드밸런서**

AWS 콘솔에서 EC2에 접속하여 확인:
```bash
# Docker 확인
docker ps

# MySQL 확인
docker exec -it mysql_1 mysql -uroot -plldj123414
show databases;  # app_prod 확인
select user, host from mysql.user;  # appuser, applocal 확인

# Redis 확인
docker exec -it redis_1 redis-cli
auth lldj123414
keys *

# HAProxy 확인
docker ps | grep ha_proxy_1
```

### 3단계: Docker 로그인 설정

EC2에서 GitHub Container Registry에 로그인해야 합니다:

1. GitHub에서 Personal Access Token (PAT) 생성
   - Settings > Developer settings > Personal access tokens > Tokens (classic)
   - `read:packages` 권한 체크
   - 토큰 생성 및 복사

2. EC2에서 Docker 로그인
```bash
docker login ghcr.io -u YOUR_GITHUB_USERNAME
# Password: 위에서 생성한 PAT 입력
```

### 4단계: GitHub Actions로 자동 Blue-Green 배포

코드를 main 브랜치에 푸시하면 자동으로 무중단 배포가 진행됩니다:

```bash
git add .
git commit -m "Initial deployment setup"
git push origin main
```

GitHub Actions에서 다음 작업이 자동으로 수행됩니다:
1. 태그 및 릴리즈 생성
2. Docker 이미지 빌드
3. GitHub Container Registry에 푸시
4. **Blue-Green 무중단 배포**
   - 현재 실행 중인 컨테이너 확인 (app1_1 또는 app1_2)
   - 새 버전 컨테이너 시작 (다른 컨테이너)
   - 헬스체크 대기 (최대 60초)
   - HAProxy가 새 컨테이너를 감지할 때까지 대기
   - 기존 컨테이너 중지
   - **다운타임: 0초**

### 5단계: 배포 확인

```bash
# HAProxy를 통한 애플리케이션 접속 (권장)
http://EC2_PUBLIC_IP:8090

# 헬스체크 확인
http://EC2_PUBLIC_IP:8090/actuator/health

# API 문서 확인 (Swagger)
http://EC2_PUBLIC_IP:8090/swagger-ui.html

# 직접 컨테이너 접속 (테스트용)
http://EC2_PUBLIC_IP:8080  # app1_1
http://EC2_PUBLIC_IP:8081  # app1_2
```

EC2에서 배포 상태 확인:
```bash
# 현재 실행 중인 애플리케이션 컨테이너 확인
docker ps | grep app1

# HAProxy 로그 확인
docker logs ha_proxy_1

# 특정 컨테이너 로그 확인
docker logs app1_1
docker logs app1_2
```

## Nginx Proxy Manager 설정 (선택사항)

도메인이 있다면 HTTPS를 설정할 수 있습니다:

1. Nginx Proxy Manager 접속
   - URL: `http://EC2_PUBLIC_IP:81`
   - 초기 계정: admin@example.com / changeme

2. Proxy Host 추가
   - Domain Names: 본인의 도메인
   - **Forward Hostname/IP: ha_proxy_1** (HAProxy 컨테이너 이름)
   - **Forward Port: 8090** (HAProxy 포트)
   - SSL 탭에서 Let's Encrypt 인증서 발급

**중요**: Nginx Proxy Manager는 HAProxy로 트래픽을 전달해야 Blue-Green 배포가 작동합니다.

## 무중단 배포 작동 원리

### Blue-Green 배포 흐름

1. **초기 상태**: app1_1 실행 중 (Blue)
2. **새 버전 배포 시작**: GitHub Actions 트리거
3. **Green 시작**: app1_2 컨테이너 시작
4. **헬스체크**: `/actuator/health` 엔드포인트가 UP 응답할 때까지 대기
5. **HAProxy 감지**: HAProxy가 2초마다 헬스체크하여 app1_2를 자동으로 감지
6. **트래픽 분산**: HAProxy가 app1_1과 app1_2로 트래픽 분산 (잠깐)
7. **Blue 중지**: app1_1 컨테이너 중지
8. **완료**: app1_2만 실행 중 (Green이 새로운 Blue가 됨)

### 다음 배포 시
- app1_2가 Blue 역할
- app1_1이 Green 역할로 새 버전 배포
- 위 프로세스 반복

## 트러블슈팅

### GitHub Actions 실패 시
1. Secrets가 모두 등록되어 있는지 확인
2. EC2_INSTANCE_ID가 올바른지 확인
3. EC2에서 `docker login ghcr.io`가 완료되었는지 확인

### 애플리케이션 접속 불가 시
```bash
# EC2에 접속하여 확인
docker logs app1_1
docker logs app1_2

# HAProxy 상태 확인
docker logs ha_proxy_1

# 모든 컨테이너 재시작 (최후의 수단)
docker restart ha_proxy_1
docker restart app1_1 2>/dev/null || true
docker restart app1_2 2>/dev/null || true
```

### HAProxy 헬스체크 실패 시
```bash
# 컨테이너에서 직접 헬스체크 테스트
docker exec app1_1 curl http://localhost:8080/actuator/health
docker exec app1_2 curl http://localhost:8080/actuator/health

# HAProxy 설정 확인
cat /dockerProjects/ha_proxy_1/volumes/usr/local/etc/haproxy/haproxy.cfg
```

### MySQL 연결 실패 시
```bash
# MySQL 상태 확인
docker exec -it mysql_1 mysql -uroot -plldj123414 -e "SHOW DATABASES;"

# 네트워크 확인
docker network inspect common
```

## 리소스 삭제

배포한 인프라를 삭제하려면:

```bash
cd infra
terraform destroy
# yes 입력
```

**주의**: 모든 데이터가 삭제되므로 중요한 데이터는 백업하세요.

## 비용 안내

이 구성의 AWS 예상 비용 (월):
- EC2 t3.micro: 프리티어 포함 시 무료, 이후 약 $10/월
- EBS 12GB: 프리티어 포함 시 무료, 이후 약 $1/월
- 데이터 전송: 사용량에 따라 다름

## 참고 자료

- [AWS 프리티어](https://aws.amazon.com/free/)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [GitHub Actions 문서](https://docs.github.com/actions)
