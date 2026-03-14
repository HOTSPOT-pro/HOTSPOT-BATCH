package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto;

import lombok.Builder;

/**
 * 메인 DB에서 읽어온 리포트 생성 대상자 기초 정보
 */
@Builder
public record ReportSeedInput(
    Long familyId,
    Long subId,
    String name
) {}
