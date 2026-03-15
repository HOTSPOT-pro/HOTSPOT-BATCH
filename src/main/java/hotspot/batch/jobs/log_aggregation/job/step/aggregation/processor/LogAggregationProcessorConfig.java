package hotspot.batch.jobs.log_aggregation.job.step.aggregation.processor;

import hotspot.batch.jobs.log_aggregation.repository.LogAggregationRepository.UsageAppliedEventLogRow;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogAggregationProcessorConfig {

    @Bean
    public ItemProcessor<UsageAppliedEventLogRow, UsageAppliedEventLogRow> usageAppliedEventLogProcessor() {
        return item -> item;
    }
}
