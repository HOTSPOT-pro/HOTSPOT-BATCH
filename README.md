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
- 방식: Job1 (Chunk - 2step 구조) + Job2 (Chunk - 1step 구조)
- 현재 상태: 구현 진행 완료

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

## 📊 주간 AI 분석 리포트 파이프라인

회선 대상자의 일주일 간 사용량을 수집하고 지표를 계산하고 이를 분석해 **주간 AI 분석 리포트** 를 생성합니다.

### 🚀 전체 흐름

`usageAggregationJob`
  - 리포트 대상 seed 생성, Redis 사용량 집계, 전주 스냅샷 비교, 점수 및 태그 계산
  - 상태 전이: `PENDING -> AGGREGATED`

<br/>

`llmFeedbackJob`
  - 집계 완료 리포트 조회, 프롬프트 생성, LLM 호출, AI 피드백 저장
  - 상태 전이: `AGGREGATED -> COMPLETED`

- 스케줄 실행: [`WeeklyReportJobScheduler`](src/main/java/hotspot/batch/jobs/usage_aggregation/scheduler/WeeklyReportJobScheduler.java)
- 실행 순서: `usageAggregationJob` 선행 실행 후 성공 시 `llmFeedbackJob` 실행

---

### ✨ 왜 이렇게 나눴나

- 집계와 LLM 호출의 장애 범위 분리
- Step1 기반 seed 적재와 멱등성 확보
- `MOD(weekly_report_id, gridSize)` 기반 파티셔닝 구조
- Redis 파이프라인과 비동기 prefetch 기반 N+1 완화
- `week_start_date = ?` 기반 전주 비교 조회 최적화
- `AsyncItemProcessor` + `AsyncItemWriter` + `WebClient` + `RateLimiter/Retry` 기반 외부 API 병목 완화

---

### ⚡ 성능 튜닝 핵심

- Chunk Size
  - 변경값: `1000 -> 200`
  - 효과: Chunk당 DB/Redis 대기 부담 감소 및 처리 회전율 개선

- Grid Size
  - 변경값: `8 -> 4`
  - 효과: 과도한 `Context Switching` 비용 완화 및 병렬 처리 효율 개선

- 전주 스냅샷 조회
  - 변경 구조: `DISTINCT ON` 기반 조회 -> `week_start_date = ?` 정확 조회
  - 효과: 불필요한 스캔 및 정렬 제거, DB 부하 절감

---

### 📘 상세 문서

- 설계 배경, 병목 지점, 성능 개선 전체 분석: [`docs/WEEKLY_AI_REPORT_ARCHITECTURE.md`](docs/WEEKLY_AI_REPORT_ARCHITECTURE.md)

### 🛠 Tech Stack
- **Framework**: Spring Boot, Spring Batch, Spring Batch Integration, WebFlux
- **Database**: PostgreSQL, Redis
- **Communication**: WebClient, Resilience4j
- **AI**: OpenAI 호환 LLM API

## 참고 문서

- 상세 개발 가이드: [`docs/BATCH_ARCHITECTURE_GUIDE.md`](docs/BATCH_ARCHITECTURE_GUIDE.md)
