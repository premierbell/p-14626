# 백엔드 배포 가이드

이 가이드는 AWS + Terraform + Docker + GitHub Actions를 사용하여 Spring Boot 백엔드를 배포하는 방법을 설명합니다.

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

### 4단계: GitHub Actions로 자동 배포

코드를 main 브랜치에 푸시하면 자동으로 배포가 진행됩니다:

```bash
git add .
git commit -m "Initial deployment setup"
git push origin main
```

GitHub Actions에서 다음 작업이 자동으로 수행됩니다:
1. 태그 및 릴리즈 생성
2. Docker 이미지 빌드
3. GitHub Container Registry에 푸시
4. EC2에 배포 명령 전송

### 5단계: 배포 확인

```bash
# 애플리케이션 접속
http://EC2_PUBLIC_IP:8080

# API 문서 확인 (Swagger)
http://EC2_PUBLIC_IP:8080/swagger-ui.html
```

## Nginx Proxy Manager 설정 (선택사항)

도메인이 있다면 HTTPS를 설정할 수 있습니다:

1. Nginx Proxy Manager 접속
   - URL: `http://EC2_PUBLIC_IP:81`
   - 초기 계정: admin@example.com / changeme

2. Proxy Host 추가
   - Domain Names: 본인의 도메인
   - Forward Hostname/IP: app1 (Docker 컨테이너 이름)
   - Forward Port: 8080
   - SSL 탭에서 Let's Encrypt 인증서 발급

## 트러블슈팅

### GitHub Actions 실패 시
1. Secrets가 모두 등록되어 있는지 확인
2. EC2_INSTANCE_ID가 올바른지 확인
3. EC2에서 `docker login ghcr.io`가 완료되었는지 확인

### 애플리케이션 접속 불가 시
```bash
# EC2에 접속하여 확인
docker logs app1

# 컨테이너 재시작
docker restart app1
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
