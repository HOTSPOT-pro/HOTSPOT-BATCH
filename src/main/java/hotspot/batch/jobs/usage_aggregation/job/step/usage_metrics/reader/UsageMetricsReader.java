package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.reader;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

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
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyAppUsage;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyHourlyUsage;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ReportBasicInfo;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReportSnapshot;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.LastWeekUsageService;
import hotspot.batch.jobs.usage_aggregation.repository.ReportUsageAppRedisRepository;
import hotspot.batch.jobs.usage_aggregation.repository.ReportUsageHourlyRedisRepository;

/**
 * Step2: "Bulk Pre-fetching" 기능이 있는 Reader
 */
@Component
@StepScope
public class UsageMetricsReader implements ItemStreamReader<UsageMetricsAggregationInput> {

    private final JdbcPagingItemReader<ReportBasicInfo> delegate;
    private final LastWeekUsageService lastWeekUsageService;
    private final ReportUsageAppRedisRepository reportUsageAppRedisRepository;
    private final ReportUsageHourlyRedisRepository reportUsageHourlyRedisRepository;
    
    private final Queue<UsageMetricsAggregationInput> buffer = new LinkedList<>();

    public UsageMetricsReader(
            DataSource dataSource,
            LastWeekUsageService lastWeekUsageService,
            ReportUsageAppRedisRepository reportUsageAppRedisRepository,
            ReportUsageHourlyRedisRepository reportUsageHourlyRedisRepository,
            @Value("#{stepExecutionContext['startId']}") Long startId,
            @Value("#{stepExecutionContext['endId']}") Long endId) {

        this.lastWeekUsageService = lastWeekUsageService;
        this.reportUsageAppRedisRepository = reportUsageAppRedisRepository;
        this.reportUsageHourlyRedisRepository = reportUsageHourlyRedisRepository;

        Map<String, Object> parameters = Map.of(
                "status", ReportStatus.PENDING.name(),
                "startId", startId,
                "endId", endId);

        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause(
            "weekly_report_id as weeklyReportId, family_id as familyId, sub_id as subId, name, " +
            "week_start_date as weekStartDate, week_end_date as weekEndDate"
        );
        queryProvider.setFromClause("from weekly_report");
        queryProvider.setWhereClause("where report_status = :status and weekly_report_id between :startId and :endId");
        queryProvider.setSortKeys(Map.of("weekly_report_id", Order.ASCENDING));

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

    private void fillBuffer() throws Exception {
        List<ReportBasicInfo> rawInfos = new ArrayList<>();
        
        for (int i = 0; i < BatchConstants.CHUNK_SIZE; i++) {
            ReportBasicInfo info = delegate.read();
            if (info == null) break;
            rawInfos.add(info);
        }

        if (rawInfos.isEmpty()) return;

        List<Long> subIds = rawInfos.stream().map(ReportBasicInfo::subId).toList();
        ReportBasicInfo first = rawInfos.get(0);
        
        // 지난주 리포트의 시작일은 이번 주 시작일의 7일 전임
        Map<Long, LocalDate> lastReportDateMap = rawInfos.stream()
            .collect(Collectors.toMap(ReportBasicInfo::subId, info -> info.weekStartDate().minusDays(7)));

        // 1. 이번 주 일별/앱별 사용량 벌크 조회 (ZSet)
        Map<Long, List<DailyAppUsage>> appUsageMap = reportUsageAppRedisRepository.findBulkWeeklyAppUsage(
                subIds, first.weekStartDate(), first.weekEndDate());

        // 2. 이번 주 일별/시간대별 사용량 벌크 조회 (Hash)
        Map<Long, List<DailyHourlyUsage>> hourlyUsageMap = reportUsageHourlyRedisRepository.findBulkWeeklyHourlyUsage(
                subIds, first.weekStartDate(), first.weekEndDate());

        // 3. 지난주 리포트 벌크 조회
        Map<Long, WeeklyReportSnapshot> lastWeekMap = lastWeekUsageService.getBulkSnapshotList(lastReportDateMap);

        // 4. 데이터 조합하여 버퍼 적재
        for (ReportBasicInfo info : rawInfos) {
            buffer.add(new UsageMetricsAggregationInput(
                info,
                appUsageMap.getOrDefault(info.subId(), Collections.emptyList()),
                hourlyUsageMap.getOrDefault(info.subId(), Collections.emptyList()),
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
