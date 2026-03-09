# HOTSPOT-BATCH 구조/개발 가이드

## 1. 현재 배치 구조 요약

현재 프로젝트는 다음 원칙으로 구성되어 있다.

- 실행 진입점은 CLI 기반 (`job.name` 파라미터)
- `family_remove`는 Tasklet 기반 (소량/월배치)
- `usage_report`, `crypto_key`는 Chunk 기반 스켈레톤
- 공통 관심사(파라미터 검증, 결과 로깅, chunk 진행 로그)는 `common`에 배치

패키지 구조:

```text
hotspot.batch
├─ BatchApplication
├─ common
│  ├─ config
│  │  ├─ BatchTimeConfig
│  │  └─ JobParameterValidator
│  ├─ listener
│  │  ├─ JobResultListener
│  │  └─ TimeBasedChunkListener
│  └─ runner
│     └─ BatchJobRunner
└─ jobs
   ├─ family_remove
   │  ├─ job
   │  │  └─ FamilyRemoveJobConfig
   │  └─ tasklet
   │     └─ FamilyRemoveTasklet
   ├─ usage_report
   │  └─ job
   │     └─ UsageReportJobConfig
   └─ crypto_key
      └─ job
         └─ CryptoKeyRotationJobConfig
```

---

## 2. 실행 방식

`BatchJobRunner`가 애플리케이션 시작 시 인자를 읽어 잡을 실행한다.

- `--job.name=familyRemoveJob`
- `--job.name=usageReportJob`
- `--job.name=cryptoKeyRotationJob`
- 보조 파라미터: `--targetDate=yyyy-MM-dd`, `--yearMonth=yyyyMM`

파라미터는 `JobParameterValidator`에서 검증한다.

---

## 3. 클래스 설명

### 공통(`common`)

- `BatchTimeConfig`  
  KST `Clock` 빈 제공. 날짜 계산을 시스템 로컬타임 대신 명시적으로 통일하기 위해 사용.

- `JobParameterValidator`  
  `targetDate`, `yearMonth` 형식 검증.

- `JobResultListener`  
  Job 시작/성공/실패/소요시간 로깅.

- `TimeBasedChunkListener`  
  Chunk 잡 진행 상황 주기 로그 (`chunkSize`).

- `BatchJobRunner`  
  실행 대상 Job 결정 + JobOperator 호출 + 실패 시 예외 처리.

### 잡별(`jobs`)

- `FamilyRemoveJobConfig`  
  `familyRemoveJob` 정의. Tasklet step 1개 구성.

- `FamilyRemoveTasklet`  
  가족 삭제 배치 실구현 위치(현재는 skeleton 로그만 수행).

- `UsageReportJobConfig`  
  `usageReportJob` 정의. Chunk step 1개 스켈레톤(Reader/Processor/Writer 더미).

- `CryptoKeyRotationJobConfig`  
  `cryptoKeyRotationJob` 정의. Chunk step 1개 스켈레톤(Reader/Processor/Writer 더미).

---

## 4. Job별 해야 할 일 (구현 TODO)

## 4.1 family_remove (우선 구현)

배경: `family_remove_schedule`를 기준으로 월 1회 제거 처리.

필수 구현:

1. 스케줄 조회
   - 조건: `status = 'PENDING' AND schedule_date <= :today`
2. 대상자 가족 매핑 제거
   - `family_sub`에서 `target_sub_id` 매핑 삭제
3. 가족 집계 갱신
   - `family.family_num` 재계산
   - `family.family_data_amount` 재계산
4. 상태 처리
   - 현재 더미 스키마에는 `PENDING`만 명시됨
   - 운영 상태 확장(`DONE/FAILED` 등) 필요 시 DB 스키마 변경 먼저 합의
5. 트랜잭션/재실행 설계
   - 동일 대상 중복 실행 방지
   - 실패 시 재실행 정책 합의

권장 분리:

- `repository` 구현체(JdbcTemplate)
- Tasklet은 흐름 제어만, SQL은 repository로 이동

## 4.2 usage_report (대량 대상)

배경: 최대 100만 사용자 가능.

필수 구현:

1. Reader
   - 발행 대상 조회(페이징/정렬 키 고정)
2. Processor
   - 주간 사용량 계산/가공
3. Writer
   - 발행 이력 저장, 중복 방지 업서트
4. 파라미터
   - `targetDate` 기반 주간 범위 계산

권장:

- 초기 chunk size 500~2000에서 부하 테스트 후 조정
- Reader/Processor/Writer를 별도 클래스로 분리

## 4.3 crypto_key (대량 재암호화)

배경: 키 로테이션 + 점진 재암호화.

권장 구현 구조:

1. `activate` step/tasklet
   - 새 DEK 버전 생성 및 active 전환
2. `reencrypt` chunk step
   - 구버전 데이터 범위 처리 후 `dek_version` 갱신
3. `retire` step/tasklet
   - 사용 종료 버전 비활성화

현재 스켈레톤은 chunk 1 step이므로, 추후 3-step 또는 3-job로 분리 권장.

---

## 5. JobConfig에 구현을 둘지, 분리할지 기준

현재는 스켈레톤 속도 때문에 `JobConfig` 내부에 더미 Reader/Processor/Writer를 두었다.

아래 조건 중 하나라도 만족하면 분리:

1. SQL이 1개라도 복잡해진다 (조인/조건 다수)
2. Processor 비즈니스 로직이 10~15줄 이상으로 증가한다
3. 재사용 가능성이 있다 (다른 step/job에서 같은 로직 사용)
4. 단위 테스트가 필요하다

분리 권장 형태:

```text
jobs/{job_name}
├─ job/{JobName}JobConfig.java
├─ reader/{JobName}Reader.java
├─ processor/{JobName}Processor.java
└─ writer/{JobName}Writer.java
```

---

## 6. 운영/인프라 권장사항

1. 스케줄 실행은 외부 오케스트레이터(AWS EventBridge + ECS RunTask) 권장
2. 배치 앱은 one-shot 실행 후 종료
3. 실패 알람(CloudWatch + SNS) 연결
4. 재실행 시 같은 파라미터로 idempotent 보장

---

## 7. 바로 다음 작업 우선순위

1. `family_remove` DB repository 구현 + Tasklet 실동작화
2. `usage_report` Reader/Processor/Writer 분리 클래스 생성
3. `crypto_key`를 `activate/reencrypt/retire` 단계로 분리 설계

