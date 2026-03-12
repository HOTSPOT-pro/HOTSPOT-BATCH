# HOTSPOT-BATCH 아키텍처 가이드 (현재 코드 기준)

## 1. 배치 실행 구조

- 앱 시작 후 `BatchJobRunner`가 `job.name`으로 실행할 Job을 선택한다.
- 실행 파라미터:
  - `targetDate` (`yyyy-MM-dd`)
  - `yearMonth` (`yyyyMM`)
- 파라미터 검증은 `JobParameterValidator`가 수행한다.

핵심 클래스:

- `common/runner/BatchJobRunner`
- `common/config/JobParameterValidator`
- `common/listener/JobResultListener`
- `common/listener/TimeBasedChunkListener`
- `common/config/BatchTimeConfig`

---

## 2. Job 현황

## 2.1 familyRemoveJob

방식: Tasklet

역할:

1. `family_remove_schedule`에서 도래 건 조회 (`SCHEDULED`, `schedule_date <= 실행일`)
2. `family_sub` 매핑 제거
3. `policy_sub`, `blocked_service_sub` 비활성화(`is_active=false`)
4. `family` 집계값 갱신 (`family_num`, `family_data_amount`)
5. `family_sub.data_limit` 갱신
6. 스케줄 상태 반영 (`COMPLETED` / `FAILED`)

구현 파일:

- `jobs/family_remove/tasklet/FamilyRemoveTasklet`
- `jobs/family_remove/repository/FamilyRemoveBatchRepository`
- `jobs/family_remove/repository/FamilyRemoveBatchRepositoryImpl`

## 2.2 usageAggregationJob

방식: 2-step (Tasklet + Chunk)

역할:

1. Step1 (`reportSeedStep`): 주간 리포트 대상 seed 생성
2. Step2 (`usageMetricsStep`): 지표/점수/스냅샷 계산 및 저장

현재 상태:

- Step1 구현됨
- Step2 구성은 존재하지만 reader/processor/writer는 skeleton
- `UsageAggregationJobConfig`에서 Step2 연결은 주석 처리 상태

## 2.3 cryptoKeyRotationJob

방식: Chunk skeleton

현재 상태:

- reader/processor/writer 더미 구현
- 실제 로테이션 로직(activate -> reencrypt -> retire) 미구현

## 2.4 redisDualWriteJob

현재 상태: 미구현 (JobConfig 없음)

---

## 3. 구현 우선순위 권장

1. `familyRemoveJob` 통합 테스트 및 실패 케이스 검증
2. `usageAggregationJob` Step2 실구현 및 Step 연결 활성화
3. `cryptoKeyRotationJob` 단계 분리(activate/reencrypt/retire)
4. `redisDualWriteJob` Job 골격 생성 및 실행 파이프라인 연결

---

## 4. 운영 기준

1. 스케줄링은 EventBridge + ECS one-shot 실행 우선
2. 동일 파라미터 재실행 시 idempotent 보장
3. 실패 시 상태/로그로 재처리 가능해야 함

