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

1. `family_remove_schedule`에서 `SCHEDULED && schedule_date <= 오늘` 조회
2. 대상 `family_sub` 제거
3. 대상 회선의 `policy_sub`, `blocked_service_sub` 비활성화(`is_active=false`)
4. `family.family_num`, `family.family_data_amount` 재계산
5. 남은 `family_sub.data_limit` 갱신
6. 성공 시 `COMPLETED`, 실패 시 `FAILED`

## 참고 문서

- 상세 개발 가이드: [`docs/BATCH_ARCHITECTURE_GUIDE.md`](docs/BATCH_ARCHITECTURE_GUIDE.md)

