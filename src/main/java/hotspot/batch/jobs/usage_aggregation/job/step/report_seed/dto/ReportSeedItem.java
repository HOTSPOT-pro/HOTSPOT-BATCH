package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto;

/**
 * Step1 reader가 읽어온 대상자 정보를 담는 모델
 */
public record ReportSeedItem(
        Long subId,
        Integer partitionNumber) {
}
