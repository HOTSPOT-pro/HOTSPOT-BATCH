package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor;


import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Reader로부터 받은 원천 데이터를 가공하여 최종 WeeklyReport를 생성하는 Processor
 */
public interface UsageMetricsProcessor extends ItemProcessor<UsageMetricsAggregationInput, WeeklyReport> {}
