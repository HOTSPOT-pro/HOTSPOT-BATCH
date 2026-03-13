# HOTSPOT-BATCH

HOTSPOT 서비스의 배치 전용 서버입니다.

## 현재 구성

- 실행 방식: CLI 파라미터 기반 (`BatchJobRunner`)
- 시간대 기준: KST (`Asia/Seoul`)
- 공통 기능:
  - Job 파라미터 검증
  - Job 결과 로깅
  - Chunk 진행 로그

## Job 목록

1. `familyRemoveJob`
- 목적: `family_remove_schedule`의 도래 건(`SCHEDULED`) 처리
- 방식: Tasklet
- 현재 상태: 구현 진행 완료(삭제/집계/상태 갱신)

2. `usageAggregationJob`
- 목적: 주간 리포트 seed 생성 + 사용량 지표 집계
- 방식: Tasklet + Chunk(2-step 구조)
- 현재 상태: Step1(seed) 구현, Step2(metrics) skeleton

3. `cryptoKeyRotationJob`
- 목적: DEK 로테이션(향후 activate/reencrypt/retire 분리 예정)
- 방식: Chunk skeleton
- 현재 상태: skeleton

4. `redisDualWriteJob` (예정)
- 목적: Redis 이중화/동기화
- 방식: 미정
- 현재 상태: 미구현

## 실행 방법

예시:

```bash
./gradlew bootRun --args="--job.name=familyRemoveJob --yearMonth=202604"
```

지원 파라미터:

- `job.name` (필수)
- `targetDate` (`yyyy-MM-dd`, 선택)
- `yearMonth` (`yyyyMM`, 선택)

## familyRemoveJob 처리 흐름

1. `family_remove_schedule`에서 `SCHEDULED && schedule_date <= 실행일` 조회
2. 대상 `family_sub` 제거
3. 대상 회선의 `policy_sub`, `blocked_service_sub` 비활성화(`is_active=false`)
4. `family.family_num`, `family.family_data_amount` 재계산
5. 남은 `family_sub.data_limit` 갱신
6. 성공 시 `COMPLETED`, 실패 시 `FAILED`

---

## 📊 주간 AI 분석 리포트 처리 파이프라인 설계

> **Spring Batch**의 **Partitioning**과 **비동기 처리**를 활용하여 대규모 데이터를 집계하고 LLM(Large Language Model) 피드백을 생성하는 아키텍처입니다.

---

## 🎫 Job 1: 사용량 집계 및 지표 연산
사용자의 활동 로그를 수집하여 분석을 위한 기초 지표를 산출하는 단계입니다.

<br/>

### 📍 Step 1: 대상자 선정 및 WeeklyReport Seed 생성
* **전략**: 파티셔닝 오버헤드를 줄이기 위한 **단일 Bulk SQL Tasklet** 방식 채택 (100만 건 기준 최적화)
* **논리 흐름**:
  1. **Bulk INSERT (Write)**: `report_target`에서 대상자를 조회하여 `weekly_report` 테이블에 즉시 삽입
  2. **멱등성(Idempotency) 확보**: `ON CONFLICT (sub_id, week_start_date) DO NOTHING` 제약 조건을 통해 중복 생성 방지
  3. **상태값**: 생성된 모든 데이터는 초기값인 `PENDING` 상태로 시작

<br/>

### 📍 Step 2: 지표 분석 및 리포트 완성
* **Reader (Bulk Pre-fetching Reader)**:
  * **Paging**: `JdbcPagingItemReader`를 통해 `PENDING` 대상 페이징 조회
  * **Partitioning**: `report_id` 범위를 기반으로 Worker Step 분할 (**Grid Size: 8**)
  * **N+1 방지**: Redis Pipeline(`MGET/HGETALL`)으로 Chunk(1,000명)의 7일치 사용량을 네트워크 1회 통신으로 예비 로드

<br/>

* **Processor (3-Layer Pipeline Architecture)**:
  1. `UsageAggregationService`: Raw 데이터를 요약 지표(`SummaryData`) 및 차트 리스트로 가공
  2. `ComparisonCalculationService`: 이번 주 집계와 지난주 스냅샷을 대조하여 증감 수치($\%$ 등) 계산
  3. `ReportInsightService`: 가공된 수치를 기반으로 인사이트 태그 및 5대 지표 정밀 점수(0~100점) 도출

<br/>

* **Writer (JDBC Bulk Update Writer)**:
  * **PostgreSQL 특화**: `PGobject`를 활용해 `JSONB` 컬럼 매핑 및 `createArrayOf`를 통한 `ARRAY` 타입 저장
  * **Status 전이**: `PENDING` → `AGGREGATED` 상태 업데이트

<br/>

---

## 🎫 Job 2: AI 분석 리포트 생성 (설계 중)
집계된 데이터를 바탕으로 외부 LLM API와 연동하여 개인화 피드백을 생성합니다.

<br/>

### 📍 Step 1: 외부 LLM API 호출 및 업데이트
* **Reader**: `status = AGGREGATED` 인 대상을 추출하여 처리 대상 선정

<br/>

* **Processor (Async + Pipeline)**:
  * **`AsyncItemProcessor`**: `WebClient`를 활용한 비동기 논블로킹 호출로 I/O 효율 극대화
  * **흐름**: `Prompt 생성` → `LLM API 호출(JSON 응답)` → `Entity 변환` 순차 실행

<br/>

* **Writer**: 가공된 데이터를 `WeeklyReport` 테이블의 `ai_feedback` 컬럼에 업데이트하고 `status = COMPLETED` 로 기록

<br/>

### 💡 LLM 연동 전략 비교

| 비교 항목 | 비동기 호출 (`Async` + `WebClient`) | LLM Batch API |
| :--- | :--- | :--- |
| **추천 케이스** | 빠른 리포트 생성 및 실시간성 중요 시 | 대량의 데이터 처리 및 비용 절감 우선 시 |
| **장점** | 즉각적인 처리, 세밀한 상태 관리 | **비용 대폭 절감**, API Rate Limit 관리 용이 |
| **단점** | API Limit 및 네트워크 지연 고려 필요 | 결과 수령까지 최대 24시간 소요 가능 |

> [!TIP]
> **Chunk Size 권장**: LLM API 호출 환경에서는 **10 ~ 30** 사이의 사이즈를 추천합니다. 너무 크면 API 타임아웃 시 롤백 부담이 크고, 너무 작으면 DB I/O 오버헤드가 증가합니다.

---

### 🛠 Tech Stack
- **Framework**: Spring Batch, Spring Data JPA
- **Database**: PostgreSQL (JSONB, Array), Redis
- **Communication**: WebClient (Asynchronous I/O)
- **AI**: LLM API (Prompt Engineering)

## 참고 문서

- 상세 개발 가이드: [`docs/BATCH_ARCHITECTURE_GUIDE.md`](docs/BATCH_ARCHITECTURE_GUIDE.md)

