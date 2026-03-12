package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.reader;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.stereotype.Component;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ReportBasicInfo;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReportSnapshot;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.LastWeekUsageService;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ThisWeekUsageService;

/**
 * Step2: "Bulk Pre-fetching" 기능이 있는 Reader.
 * 청크 단위로 데이터를 먼저 읽은 뒤, 이번 주/지난주 데이터를 일괄 조회 & 조합하여 반환한다.
 */
@Component
@StepScope
public class UsageMetricsReader implements ItemStreamReader<UsageMetricsAggregationInput> {

    private final JdbcPagingItemReader<ReportBasicInfo> delegate;
    private final ThisWeekUsageService thisWeekUsageService;
    private final LastWeekUsageService lastWeekUsageService;
    
    private final Queue<UsageMetricsAggregationInput> buffer = new LinkedList<>();

    public UsageMetricsReader(
            DataSource dataSource,
            ThisWeekUsageService thisWeekUsageService,
            LastWeekUsageService lastWeekUsageService,
            @Value("#{stepExecutionContext['startId']}") Long startId,
            @Value("#{stepExecutionContext['endId']}") Long endId) {

        this.thisWeekUsageService = thisWeekUsageService;
        this.lastWeekUsageService = lastWeekUsageService;

        Map<String, Object> parameters = Map.of(
                "status", ReportStatus.PENDING.name(),
                "startId", startId,
                "endId", endId);

        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("report_id, sub_id, week_start_date, week_end_date");
        queryProvider.setFromClause("from weekly_report");
        queryProvider.setWhereClause("where report_status = :status and report_id between :startId and :endId");
        queryProvider.setSortKeys(Map.of("report_id", Order.ASCENDING));

        try {
            this.delegate = new JdbcPagingItemReaderBuilder<ReportBasicInfo>()
                    .name("usageMetricsReaderDelegate")
                    .dataSource(dataSource)
                    .queryProvider(queryProvider)
                    .parameterValues(parameters)
                    .pageSize(BatchConstants.CHUNK_SIZE)
                    .rowMapper(new DataClassRowMapper<>(ReportBasicInfo.class))
                    .saveState(false)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build usageMetricsReader delegate", e);
        }
    }

    @Override
    public UsageMetricsAggregationInput read() throws Exception {
        if (buffer.isEmpty()) {
            fillBuffer();
        }
        return buffer.poll();
    }

    /**
     * 버퍼를 채우는 내부 로직
     * delegate.read()의 예외 처리를 위해 throws Exception 추가
     */
    private void fillBuffer() throws Exception {
        List<ReportBasicInfo> rawInfos = new ArrayList<>();
        
        for (int i = 0; i < BatchConstants.CHUNK_SIZE; i++) {
            ReportBasicInfo info = delegate.read();
            if (info == null) break;
            rawInfos.add(info);
        }

        if (rawInfos.isEmpty()) return;

        List<Long> subIds = rawInfos.stream().map(ReportBasicInfo::subId).toList();

        Map<Long, UsageData> thisWeekMap = thisWeekUsageService.getBulkUsageList(subIds);
        Map<Long, WeeklyReportSnapshot> lastWeekMap = lastWeekUsageService.getBulkSnapshotList(subIds);

        for (ReportBasicInfo info : rawInfos) {
            buffer.add(new UsageMetricsAggregationInput(
                info,
                thisWeekMap.get(info.subId()),
                lastWeekMap.get(info.subId())
            ));
        }
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        delegate.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        delegate.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        delegate.close();
        buffer.clear();
    }
}
