# 포인트 시스템 API

회원이 포인트를 적립·사용·취소하고 잔액을 조회하는 백엔드 REST API입니다.

---

## 실행 방법

### 요구사항

- JDK 21 이상
- Kotlin 1.9.25

### 빌드 및 실행

```bash
cd point-system
./gradlew bootRun
```

서버가 `http://localhost:8080`에서 시작됩니다.  
H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:pointdb`, 사용자: `sa`, 비밀번호 없음)

**시드 데이터**: 앱 시작 시 `user-1`, `user-2`, `user-3` 회원이 자동 생성됩니다.

### 테스트

```bash
./gradlew test
```

---

## API 명세

### 포인트 적립

```
POST /api/v1/users/{userId}/points/earn
Content-Type: application/json

{ "amount": 3000 }
```

```json
// 201 Created
{
  "balanceId": 1,
  "userId": "user-1",
  "amount": 3000,
  "expiresAt": "2027-01-16T00:00:00",
  "totalBalance": 3000
}
```

### 포인트 사용

```
POST /api/v1/users/{userId}/points/use
Content-Type: application/json

{ "amount": 1500 }
```

```json
// 200 OK
{
  "usageId": 1,
  "userId": "user-1",
  "amount": 1500,
  "breakdown": [
    { "balanceId": 1, "amount": 1500, "expiresAt": "2027-01-16T00:00:00" }
  ]
}
```

### 포인트 사용 취소

```
POST /api/v1/points/usages/{usageId}/cancel
```

```json
// 200 OK
{
  "usageId": 1,
  "restoredAmount": 1500,
  "notRestoredAmount": 0,
  "message": "전액 복원되었습니다."
}
```

### 포인트 적립 취소 (관리자)

```
POST /api/v1/admin/points/balances/{balanceId}/cancel
Content-Type: application/json

{ "fraudulent": false }
```

`fraudulent`: 버그/악용성 비정상 적립 여부 (기본값 `false`)

```json
// 200 OK
{
  "balanceId": 1,
  "reclaimedAmount": 1500,
  "adjustmentAmount": 0,
  "message": "적립 취소: 1500 포인트 회수 (사용분 1500 포인트는 미회수)"
}
```

### 잔액 조회

```
GET /api/v1/users/{userId}/points/balance
```

```json
// 200 OK
{ "userId": "user-1", "totalBalance": 1500 }
```

### 적립 건 목록 조회 (보조)

```
GET /api/v1/users/{userId}/points/balances
```

---

### 에러 응답 형식

```json
{ "code": "INSUFFICIENT_POINT", "message": "포인트가 부족합니다. 보유: 500, 요청: 1000", "timestamp": "..." }
```

| 코드 | HTTP | 설명 |
|---|---|---|
| `MEMBER_NOT_FOUND` | 404 | 존재하지 않는 회원 |
| `BALANCE_NOT_FOUND` | 404 | 존재하지 않는 적립 건 |
| `USAGE_NOT_FOUND` | 404 | 존재하지 않는 사용 내역 |
| `INSUFFICIENT_POINT` | 422 | 잔액 부족 |
| `EXCEED_DAILY_LIMIT` | 422 | 1일 적립 한도 초과 |
| `EXCEED_MAX_BALANCE` | 422 | 최대 보유 한도 초과 |
| `ALREADY_CANCELLED` | 409 | 이미 취소된 항목 |
| `INVALID_INPUT` | 400 | 입력 검증 실패 |

---

## 포인트 정책

| 항목 | 값 |
|---|---|
| 1일 최대 적립 | 10,000 포인트 |
| 유저 최대 보유 | 1,000,000 포인트 |
| 만료 기간 | 적립일로부터 365일 후 자정 |

**만료 기준 예시**: 2026-01-01 적립 → `expireDate` = 2026-12-31 → `expiresAt` = 2027-01-01 00:00:00

---

## 설계 의도

### 계층 구조

```
Controller → Service → Repository
                ↓
           Domain (Entity + Enum)
```

- `Controller`: 입력 검증, HTTP 매핑, 예외 변환 담당. 비즈니스 로직 없음.
- `Service`: 트랜잭션 경계. 모든 비즈니스 규칙(한도 검증, 차감 순서, 취소 정책) 보유.
- `Repository`: 쿼리만 담당. JPA 파생/JPQL 활용.
- `Domain`: 상태 변경 메서드(`deduct`, `restore`, `cancel`)를 엔티티에 위치시켜 도메인 로직이 서비스 레이어 밖으로 나가지 않도록 함.

### 핵심 도메인 모델링 결정: `is_used` → `remainingAmount + status`

원래 설계의 `is_used(boolean)`을 `remainingAmount(int) + status(enum)`으로 대체했습니다.

**이유**: 1건의 적립이 **여러 사용 건에 걸쳐 부분 차감**됩니다. boolean으로는 "2000 포인트 적립 건에서 1500 사용하고 500 남음"을 표현할 수 없습니다.

---

## 정책 결정 근거

### 요구사항 3: 사용 취소 시 이미 만료된 적립 건

**정책**: 복원하지 않음 (환급 없음)

**근거**: 만료 포인트는 이미 회원이 사용 의무가 있던 자산이며, 만료됐다는 것은 유효 기간이 지나 회사가 반환 의무를 더 이상 지지 않는 상태입니다. 사용 취소는 "포인트를 돌려받는" 것인데, 원 적립 건이 이미 만료됐다면 되돌아갈 포인트가 존재하지 않습니다. `notRestoredAmount` 필드로 복원되지 않은 금액을 응답에 명시합니다.

### 요구사항 4: 관리자 적립 취소 시 이미 사용된 적립 건

**정책 A (일반 오적립, `fraudulent=false`)**: 미사용 잔액만 즉시 소멸. 이미 사용된 분은 회수하지 않음. 음수 잔액 미발생.

**근거**: 운영상 실수(잘못 입력한 금액 등)로 인한 취소는 회원에게 책임이 없습니다. 이미 사용한 포인트를 회수하면 회원이 부당하게 부채를 지게 됩니다.

**정책 B (악용성 비정상 적립, `fraudulent=true`)**: 미사용 잔액 소멸 + 사용된 분도 강제 회수 → **음수 조정 잔액 생성** (총잔액 음수 허용).

**근거**: 1일 한도·최대 보유 한도를 버그로 우회하여 비정상 취득한 포인트는 회사 실수가 아닌 회원의 악용입니다. 이미 소비된 부분까지 부채로 처리해 다음 적립 시 상계합니다. `fraudulent` 플래그를 관리자가 명시적으로 전달하여 의도치 않은 음수 처리를 방지합니다.

---

## 트레이드오프

### 동시성: 비관적 락 vs 낙관적 락 vs 네임드 락

**선택**: 비관적 락 (`@Lock(PESSIMISTIC_WRITE)` on `Member`)

회원 행에 쓰기 락을 걸면 동일 유저의 포인트 적립/사용/취소가 완전히 직렬화됩니다. 구현이 단순하고 `@Transactional` 범위 내에서 자동으로 해제됩니다.

- **낙관적 락**: 충돌 시 `OptimisticLockException` → 재시도 로직 필요. 포인트처럼 충돌이 빈번한 도메인에서는 재시도 오버헤드가 오히려 큽니다.
- **네임드 락** (`GET_LOCK`): DB 수준의 분산 락. 현재 단일 인스턴스에서 오버스펙입니다.
- **결론**: 요구사항(단일 인스턴스, H2)에 맞게 비관적 락이 가장 간단하고 안전합니다.

### 만료 처리: Lazy 평가 vs 스케줄러

**선택**: Lazy 평가 — 조회/사용 시 `expiresAt > now` 조건으로 판정

스케줄러로 주기적으로 만료 상태를 갱신하는 방법도 있지만, 현재 요구사항에서 "만료됐음"을 별도로 표시해야 하는 화면이나 보고서가 없습니다. 쿼리 조건 한 줄로 같은 결과를 얻을 수 있습니다.

### QueryDSL 미사용

글로벌 가이드에 QueryDSL이 기재되어 있으나 이 과제의 쿼리는 모두 단순한 JPQL로 충분합니다. QueryDSL을 도입하면 Q클래스 생성, APT 플러그인 설정 등 부가 설정이 추가됩니다. "요구사항에 딱 맞게, 깔끔하게" 원칙에 따라 의도적으로 미사용했습니다.

### 확장 고려사항

현재 단일 인스턴스·H2 기반이므로 비관적 락으로 충분하지만, 다중 인스턴스 환경으로 확장 시 Redis 기반 분산 락 또는 DB 네임드 락으로의 전환이 필요합니다.
