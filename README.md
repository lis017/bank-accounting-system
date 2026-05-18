# bank-accounting-assignment

카카오뱅크 금융회계 백엔드 과제 대비용 Spring Boot 프로젝트입니다.
핵심 도메인 흐름은 다음과 같습니다.

> 예산 등록 → 집행 요청 생성 → 승인 → 집행 실행 → 회계전표 자동 생성 → 일마감 배치 → 실패 재처리

---

## 1. 실행 방법

### 1-1. MySQL 컨테이너 기동

```bash
docker compose up -d
```

본 프로젝트는 과제/로컬 실행 편의를 위해 Docker 기반 MySQL을 사용합니다.  
아래 계정 정보는 로컬 개발 환경 전용이며, 실제 운영 환경에서는 환경 변수 또는 Secret Manager를 통해 관리해야 합니다.

기본접속정보

- host: `localhost:3306`
- database: `bank_accounting`
- username: `${DB_USERNAME}` 기본값: `bank`
- password: `${DB_PASSWORD}` 기본값: `bankpw`

### 1-2. 애플리케이션 실행

```bash
./gradlew bootRun
```

또는 IDE에서 `BankAccountingApplication`을 실행합니다.

### 1-3. 테스트 실행

```bash
./gradlew test
```

테스트는 H2 인메모리 DB를 사용하므로 Docker가 없어도 실행됩니다(`src/test/resources/application.yml`).

> **Windows + 한글 경로 주의**
> Gradle Test Executor는 한글 경로에서 classpath 인자 파일의 인코딩을 잘못 처리해
> `ClassNotFoundException`을 일으킬 수 있습니다(JVM 시작 시 native 인코딩 의존성 문제).
> 이때는 프로젝트를 `C:\Temp\bank-accounting-assignment`처럼 ASCII 전용 경로로 옮긴 뒤 실행하거나,
> IntelliJ IDEA의 테스트 러너로 실행하면 정상 동작합니다(IDE는 다른 방식으로 classpath를 구성합니다).
> 컴파일/메인 애플리케이션 실행(`./gradlew bootRun`)은 한글 경로에서도 정상 동작합니다.

테스트 결과 (검증 완료, 9개 모두 통과):

- `JournalEntryBalanceTest`: 2건 — 차/대변 합계 검증
- `ExpenseExecutionIntegrationTest`: 6건 — 예산초과 / 미승인 실행 / 전표 자동생성 / 멱등성 동일·충돌 / 재처리 성공
- `DailyClosingJobTest`: 1건 — dailyClosingJob 실행 후 CLOSED 전이

---

## 2. API 테스트 순서

순차적으로 호출하면 전체 흐름을 검증할 수 있습니다.

### Step 1. 부서 등록

```http
POST /api/v1/departments
{ "departmentCode": "DEPT-001", "departmentName": "회계팀" }
```

### Step 2. 계정과목 등록 (비용 계정 + 대변용 PAYABLE 계정 둘 다 필요)

```http
POST /api/v1/account-codes
{ "code": "EXP-001", "codeName": "사무용품비", "accountType": "EXPENSE" }

POST /api/v1/account-codes
{ "code": "PAYABLE",  "codeName": "미지급금",   "accountType": "LIABILITY" }
```

> PAYABLE 계정은 회계전표 자동 생성 시 대변 라인 계정으로 사용됩니다. 반드시 먼저 등록되어야 합니다.

### Step 3. 예산 등록

```http
POST /api/v1/budgets
{
  "departmentId": 1, "accountCodeId": 1, "fiscalYear": 2026,
  "allocatedAmount": 1000000,
  "validFrom": "2026-01-01", "validTo": "2026-12-31"
}
```

### Step 4. 잔액 확인

```http
GET /api/v1/budgets/1/balance
```

### Step 5. 집행 요청 생성

```http
POST /api/v1/expense-requests
{
  "budgetId": 1, "departmentId": 1, "accountCodeId": 1,
  "amount": 50000, "memo": "비품 구매",
  "targetDate": "2026-05-15"
}
```

### Step 6. 승인

```http
POST /api/v1/expense-requests/1/approve
```

### Step 7. 집행 실행 (멱등성 키 필수)

```http
POST /api/v1/expense-requests/1/execute
Header: Idempotency-Key: 11111111-2222-3333-4444-555555555555
{ "executionNote": "정상 집행" }
```

→ 응답: `status=EXECUTED`, `journalEntryId` 포함.

### Step 8. 전표 조회

```http
GET /api/v1/journal-entries?targetDate=2026-05-15
```

### Step 9. 일마감 배치

```http
POST /api/v1/batches/daily-closing?targetDate=2026-05-15
```

→ 해당 일자의 EXECUTED 건이 CLOSED 로 전이됩니다.

### Step 10. (실패 시) 재처리

- 외부 ERP 실패를 흉내내려면 `amount=999999999`로 집행하면 Mock ERP가 강제 실패를 반환합니다.
- 이후 다음 API로 재시도:

```http
POST /api/v1/expense-requests/{id}/retry
```

---

## 3. 면접 예상 질문과 모범 답변

각 답변은 실제 클래스/메서드와 연결되어 있습니다.

### Q1. 이 코드블럭은 왜 이렇게 작성했나요? (`ExpenseExecutionService.execute`)

핵심은 **트랜잭션 경계 분할**입니다. `execute` 메서드 자체에는 `@Transactional`을 붙이지 않고, 그 안에서 다음 네 단계로 분리했습니다.

1. `idempotencyService.acquire(...)` — `REQUIRES_NEW`로 멱등성 키만 먼저 commit.
2. `expensePreparationService.prepareExecution(...)` — APPROVED 검증 + 예산 차감을 단일 트랜잭션으로.
3. `erpClient.requestPayment(...)` — **트랜잭션 밖**에서 외부 호출.
4. `markExecutedAndCreateJournal(...)` 또는 `markExecutionFailed(...)` — `@Transactional`로 상태 전이 + 전표 저장.

이렇게 분리한 이유:

- 외부 HTTP 호출을 트랜잭션 안에 두면 DB 커넥션이 외부 응답 시간 동안 점유되어 커넥션 풀이 고갈됩니다.
- 멱등성 키는 본 처리와 무관하게 즉시 영속화돼야 동시 재요청을 막을 수 있습니다.

`**prepareExecution`을 왜 별도 클래스(`ExpensePreparationService`)로 분리했나요?**

Spring `@Transactional`은 CGLIB 프록시로 동작합니다. `ExpenseExecutionService.execute()`가 `this.prepareExecution()`처럼 같은 클래스 내부 메서드를 직접 호출하면(self-invocation), 프록시를 우회하여 `@Transactional`이 적용되지 않습니다. 그 결과 상태 검증과 예산 차감이 서로 다른 트랜잭션에서 실행되어 원자성이 깨질 수 있습니다. `ExpensePreparationService`로 분리하면 `execute()`가 Spring 프록시를 통해 호출하게 되어 `@Transactional`이 정상 동작합니다.

### Q2. 이 매개변수(`Idempotency-Key`)를 바꾸면 어떤 문제가 생기나요?

- **다른 키 + 같은 요청**: 같은 요청이라도 새 키이므로 다시 처리됩니다. 중복 결제/전표가 발생할 수 있습니다.
- **같은 키 + 다른 요청**: `IdempotencyService.acquire`에서 `requestHash`가 다르면 `IDEMPOTENCY_CONFLICT`(409)로 차단됩니다.
- `Idempotency-Key` 헤더를 아예 보내지 않으면 컨트롤러가 `null`을 서비스에 전달하고, 서비스 첫 줄 검증에서 `INVALID_REQUEST`(400)를 반환합니다. (`@RequestHeader(required=false)`로 받아 서비스에서 명시적으로 검증합니다. `required=true`였다면 `MissingRequestHeaderException`이 GlobalExceptionHandler에서 처리되지 않아 500으로 떨어질 위험이 있었습니다.)

### Q3. Chunk size를 바꾸면 어떻게 되나요? (`DailyClosingJobConfig.CHUNK_SIZE`)

- **너무 크게(예: 10,000)**: 한 트랜잭션이 길어지고 메모리 사용량이 증가합니다. 장시간 락 점유로 다른 트랜잭션 대기가 길어지며, Chunk 도중 실패 시 롤백 비용이 큽니다.
- **너무 작게(예: 1)**: 트랜잭션 시작/커밋 오버헤드가 매 건마다 발생해 처리량이 급락합니다.
- 본 프로젝트는 100으로 두었습니다. 카뱅 일마감 규모(수만~~수십만 건)에서 100~~1000이 일반적이며, 메모리/락 비용의 절충점입니다.

### Q4-1. 멱등성 키가 PROCESSING 상태로 고착되면 어떻게 되나요?

`execute()` 흐름에서 멱등성 키를 먼저 PROCESSING으로 저장한 직후 `prepareExecution()`(상태 검증, 예산 차감)이 실패하면, 아무런 조치를 하지 않으면 키가 PROCESSING에서 벗어나지 못합니다. 그 결과 같은 키로 다시 요청할 때마다 "이미 처리 중입니다(409)"가 반환되어 영원히 재시도할 수 없게 됩니다.

**방법 A(채택): prepareExecution 실패 즉시 FAILED 마킹**

`ExpenseExecutionService.execute()`에서 `prepareExecution` 호출을 `try-catch`로 감싸 실패하면 `idempotencyService.markFailed()`를 호출한 뒤 원 예외를 재던집니다. 이렇게 하면:

- "키를 먼저 잡아 중복 실행을 차단한다"는 핵심 의도를 유지합니다.
- 예산 부족/미승인 같은 **도메인 오류**는 FAILED로 남아 운영 추적이 가능합니다.
- 클라이언트는 문제를 수정한 뒤 **새 Idempotency-Key를 발급**해서 재시도해야 합니다(Stripe 등 표준 멱등성 정책과 동일).

**방법 B(미채택): 사전 검증을 먼저, 멱등성 키는 나중에**

검증 통과 후 키를 저장하면 고착 문제 자체가 없지만, 검증과 키 저장 사이의 짧은 순간에 동일 요청이 들어오면 중복 실행될 수 있습니다. 금융 집행처럼 중복이 치명적인 도메인에서는 방법 A가 더 안전합니다.

### Q4. 멱등성 키 저장에 실패하면 어떻게 되나요?

`IdempotencyService.acquire`는 `REQUIRES_NEW` 트랜잭션으로 saveAndFlush 합니다.

- DB unique 제약 위반(이미 존재) → 기존 키를 조회해 hash 비교 후 분기.
- DB 다운/네트워크 장애 → 예외 전파 → 호출자(execute)가 본 처리를 시작조차 하지 않음.

이렇게 설계한 이유: 키 저장이 실패했는데 본 처리가 진행되면 같은 요청을 중복 처리할 위험이 있기 때문입니다. 그래서 **키 저장이 성공해야만 다음 단계로 진행**합니다.

### Q5. 전표 생성 중간에 실패하면 어떻게 되나요?

`ExpenseRequestService.markExecutedAndCreateJournal`은 `@Transactional`로 묶여 있습니다.

- 상태 전이(EXECUTED) → 이력 저장 → 전표 생성 → 차/대변 검증, 이 모든 작업이 한 트랜잭션입니다.
- 전표 차/대변 검증(`JournalEntry.validateBalanced`)에서 예외가 나면 트랜잭션 전체가 롤백되어 상태도 EXECUTED로 바뀌지 않습니다.
- "집행은 EXECUTED인데 전표가 없는" 정합성 깨짐이 발생하지 않습니다.
- 단, 외부 ERP는 이미 성공했을 수 있으므로 이 경우 `EXECUTION_FAILED`로 마킹하고 재처리(retry) 흐름을 유도합니다.

### Q6. 배치 재시작은 어떻게 동작하나요?

Spring Batch 메타테이블(`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION` 등)에 모든 실행 이력이 기록됩니다.

- 같은 `JobParameters`로는 1번만 실행됩니다.
- 따라서 본 프로젝트의 `BatchController`는 `triggeredAt`(현재 시각)을 파라미터에 추가해 동일 일자라도 재실행이 가능하게 합니다.
- 한편 Step 단위 재시작이 필요하면 `ExitStatus`와 `Restartable=true`(기본값)를 활용해 중단된 지점부터 이어 실행할 수 있습니다.

### Q7. 낙관적 락 충돌이 나면 어떻게 처리하나요?

`BudgetBalance`는 `@Version`을 사용한 낙관적 락을 적용합니다.

- 동시 차감 시 두 트랜잭션 중 하나만 성공하고, 다른 하나는 `ObjectOptimisticLockingFailureException`을 받습니다.
- `GlobalExceptionHandler.handleOptimisticLock`이 이를 409 `BUDGET_LOCK_CONFLICT`로 변환합니다.
- 클라이언트는 짧은 backoff 후 재시도하면 정상 처리됩니다.

**왜 비관적 락이 아니라 낙관적 락을 선택했나?**

- 비관적 락(`SELECT FOR UPDATE`)은 데드락/락 대기 시간이 길어지기 쉽고, MySQL↔Oracle 동작 차이가 큽니다.
- 예산 차감은 짧고 빈번한 트랜잭션이라 낙관적 락 + 클라이언트 재시도 패턴이 더 안전합니다.

### Q8. 왜 MySQL을 사용했고 Oracle 전환 시 무엇을 고려해야 하나요?

**MySQL 선택 이유:**

- 카뱅 일부 시스템이 MySQL 기반이고, 본 과제 규모에서는 OSS인 MySQL이 개발 환경 구성에 가장 단순합니다.
- Docker Compose 기반으로 즉시 띄울 수 있어 onboarding 비용이 낮습니다.

**Oracle 전환 시 고려할 점:**

- **시퀀스 vs AUTO_INCREMENT**: Oracle은 `SEQUENCE`를 권장. JPA `@GeneratedValue(strategy = IDENTITY)`를 사용했지만, Oracle 전환 시 `SEQUENCE`로 변경 + JPA 설정도 조정 필요.
- **데이터 타입**: `VARCHAR` 크기 의미가 다름(MySQL은 char 수, Oracle은 byte 수가 기본). 한글 컬럼 길이는 Oracle에서 보수적으로 잡아야 합니다.
- **페이지네이션**: MySQL `LIMIT/OFFSET` ↔ Oracle `ROWNUM`/`OFFSET ... FETCH`. 본 프로젝트는 JPA/JPQL 위주로 작성해 SQL 호환성 부담을 최소화했습니다.
- **트랜잭션 격리수준 기본값**: MySQL InnoDB는 REPEATABLE_READ, Oracle은 READ_COMMITTED. 동시성 동작이 다를 수 있어 회귀 테스트 필수.

### Q9. 왜 Redis/Redisson을 사용하지 않았나요?

본 과제 핵심은 **금융회계 데이터 정합성**입니다.

- Redis 분산 락을 쓰면 락 획득에 실패한 사이에 Redis 장애가 발생할 경우 정합성을 보장하기 어렵습니다.
- 멱등성 키도 Redis에만 저장하면 캐시 휘발성 때문에 동일 키 재요청을 막지 못하는 사고가 생길 수 있습니다.
- 본 프로젝트는 **DB unique 제약 + @Version 낙관적 락**으로 단일 인스턴스에서도 충분한 정합성을 확보했습니다.
- 추후 멀티 인스턴스 환경에서 성능이 부족해지면 그때 Redis를 캐시 레이어로 추가하면 됩니다(데이터 진실성의 원천은 여전히 DB).

### Q10. 우대사항에 MSA가 있는데 왜 단일 애플리케이션으로 구현했나요?

- MSA는 도메인 간 결합도 분리가 명확할 때 효과적입니다. 본 과제의 7개 도메인은 모두 **하나의 트랜잭션 경계** 안에서 협력해야 정합성이 보장됩니다(예: 예산 차감 + 전표 생성 + 멱등성).
- 잘못 분리하면 분산 트랜잭션 또는 Saga 같은 보상 로직이 필요해지고, 1원 오차도 허용하지 않는 회계 도메인에서는 위험이 큽니다.
- 본 프로젝트는 **모듈 경계는 패키지(budget/expense/accounting/...)로 분리**해 두어, 향후 트래픽/조직 규모가 커지면 모듈별로 MSA로 분리하기 쉬운 구조로 만들었습니다.

### Q11. 왜 Idempotency-Key를 Header로 받았나요?

- 멱등성 키는 **요청 자체의 메타데이터**이지 비즈니스 페이로드가 아닙니다. Body에 두면 같은 요청 페이로드를 정의하는 범위가 모호해집니다.
- HTTP 표준(Stripe, AWS 등)도 Idempotency-Key를 Header로 받습니다.
- 또한 클라이언트가 같은 키로 재시도할 때 body는 변형 없이 그대로 보내고, 키만 헤더로 동일 유지하면 됩니다.

### Q12. 왜 금액에 double/float을 사용하지 않았나요?

`double/float`은 IEEE 754 이진 부동소수점이므로 `0.1 + 0.2 != 0.3` 같은 오차가 발생합니다. 1원 오차도 허용되지 않는 회계 도메인에서는 절대 사용하면 안 됩니다.

본 프로젝트는 `**Long`(원 단위)**을 선택했습니다.

- KRW는 소수점이 없는 통화이므로 Long으로 충분합니다.
- BigDecimal보다 사칙연산이 빠르고 코드가 단순합니다.
- 다중 통화 확장이 필요해지면 `BigDecimal + scale` 정책으로 전환하면 됩니다.

### Q13. 왜 전표의 차변/대변 합계 검증이 중요한가요?

- 복식부기의 절대 원칙: **자산 + 비용 = 부채 + 자본 + 수익**이 항상 성립해야 합니다.
- 전표 단위로 차변 합계 ≠ 대변 합계면 재무제표 합계가 깨지고, 감사에서 즉시 발각됩니다.
- `JournalEntry.validateBalanced()`는 저장 직전에 호출되며, 실패 시 예외로 트랜잭션을 롤백해 불균형 전표가 절대 DB에 들어가지 않게 합니다.

### Q14. 외부 API 호출을 DB 트랜잭션 안에 오래 두면 어떤 문제가 생기나요?

- DB 커넥션이 외부 응답 시간 동안 점유됩니다. 외부 시스템이 느려지면 커넥션 풀이 고갈되어 **전체 서비스가 마비**됩니다.
- 행 단위 락이 길어져 같은 자원을 만지는 다른 트랜잭션이 모두 대기합니다.
- 본 프로젝트의 `ExpenseExecutionService.execute`는 `erpClient.requestPayment` 호출을 `**@Transactional` 메서드 밖**에서 수행해 이 문제를 회피합니다.

### Q15. 상태전이를 enum으로 관리한 이유는 무엇인가요?

- 상태 값이 코드 곳곳에 문자열로 흩어지면 오타나 잘못된 전이가 컴파일 시점에 잡히지 않습니다.
- `ExpenseRequestStatus`는 enum 안에 **허용된 다음 상태 집합**을 직접 정의(`ALLOWED_NEXT`)하고, `validateTransitionTo`로 호출 1회만에 검증합니다.
- 새 상태가 추가될 때 `ALLOWED_NEXT`에 정의해야만 컴파일/런타임이 통과하므로 누락 위험이 적습니다.

### Q16. dailyClosingJob을 API가 아니라 Batch로 처리한 이유는 무엇인가요?

- 일마감은 **대량의 데이터를 일정 간격으로 처리**하며, 부분 실패 후 재시작이 필수입니다. Spring Batch 메타테이블이 실행 이력/재시작 지점을 자동 관리해줍니다.
- API로 처리하면 timeout, 트랜잭션 길이, 메모리 사용량 제어가 어렵습니다.
- Chunk 단위 트랜잭션 분할로 한 청크가 실패해도 다른 청크는 영향받지 않습니다.

### Q17. RepositoryItemReader와 JdbcPagingItemReader 중 무엇을 선택했고 이유는 무엇인가요?

`RepositoryItemReader`를 선택했습니다.

- 본 과제 규모에서 충분한 성능을 내고, JPA Repository 메서드를 그대로 활용해 도메인 일관성이 유지됩니다.
- 엔티티 매핑/lazy 로딩 같은 JPA 기능을 자연스럽게 사용할 수 있습니다.

`JdbcPagingItemReader`가 유리한 경우:

- 처리 건수가 수백만~수억 건으로 매우 큰 경우. JPA 영속성 컨텍스트 비용이 부담될 때.
- 도메인 객체가 아니라 DTO를 바로 SELECT 해 매핑 비용을 줄이고 싶을 때.

### Q18. 배치 실패 후 재시작 시 중복 처리를 어떻게 막나요?

- Spring Batch는 `BATCH_STEP_EXECUTION`에 마지막 처리 위치를 저장하고, 재시작 시 그 다음 페이지부터 처리합니다.
- 본 프로젝트의 Step은 EXECUTED 상태만 읽어 CLOSED 로 전이하므로, 한 번 CLOSED 된 건은 다시 읽히지 않습니다(상태 기반 멱등성).
- 따라서 같은 `JobParameters`로 재시작해도 중복 처리되지 않습니다.

### Q19. 로그는 어떤 기준으로 남겼고 민감정보는 어떻게 보호했나요?

- **요청 단위 추적**: `RequestIdFilter`가 MDC에 `requestId`를 넣고, 모든 로그 패턴에 자동 포함됩니다(`application.yml`).
- **상태 변화 중심**: 예산 차감 시도/성공/실패, 집행 상태 전이, ERP 호출 시작/성공/실패, 전표 생성/검증, 멱등성 키 저장/충돌, 배치 시작/종료 등.
- **민감정보 보호**:
  - 요청 body 전체를 로그로 남기지 않습니다.
  - 멱등성 키는 `LogMasker.maskIdempotencyKey`로 앞 6자만 노출하고 나머지는 `*`**로 가립니다.

---

## 4. 본 버전에서 의도적으로 구현하지 않은 부분 (TODO)

이번 과제 대비 1차 목표는 **예산 집행 → 전표 → 일마감**의 깊이 있는 구현입니다. 다음은 의도적으로 TODO로만 남겨두었습니다.

- `failedExecutionRetryJob`: 실패 건 일괄 재처리 배치 잡 (현재는 retry API로 1건씩 처리)
- `monthlyDepreciationJob`: 월 감가상각 배치 잡
- 동시 집행 요청 시 예산 음수 방지 (낙관적 락 충돌의 다중 동시성 테스트)
- dailyClosingJob 재실행 시 중복 처리 방지 테스트
- 외부 API 타임아웃 시나리오 테스트
- retry 재실패 시 retryCount 증가 검증 테스트
- 예산 차감 후 영구 거절 시 잔액 복원 보정 트랜잭션
- ERP 응답 비동기 처리(이벤트 발행 + 별도 컨슈머)

