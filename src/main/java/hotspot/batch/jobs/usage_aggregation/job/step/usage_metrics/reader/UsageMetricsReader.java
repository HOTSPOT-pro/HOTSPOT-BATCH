package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.reader;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
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
 * [최종 개선] Modular Partitioning + DB 함수 기반 인덱스 대응 버전
 */
@Component
@StepScope
public class UsageMetricsReader implements ItemStreamReader<UsageMetricsAggregationInput> {

    private static final Logger log = LoggerFactory.getLogger(UsageMetricsReader.class);

    private final JdbcPagingItemReader<ReportBasicInfo> delegate;
    private final LastWeekUsageService lastWeekUsageService;
    private final ReportUsageAppRedisRepository reportUsageAppRedisRepository;
    private final ReportUsageHourlyRedisRepository reportUsageHourlyRedisRepository;
    private final TaskExecutor taskExecutor;
    
    private final Queue<UsageMetricsAggregationInput> buffer = new LinkedList<>();

    public UsageMetricsReader(
            DataSource dataSource,
            LastWeekUsageService lastWeekUsageService,
            ReportUsageAppRedisRepository reportUsageAppRedisRepository,
            ReportUsageHourlyRedisRepository reportUsageHourlyRedisRepository,
            @Qualifier("usageMetricsPreFetchExecutor") TaskExecutor taskExecutor,
            @Value("#{stepExecutionContext['gridSize']}") Integer gridSize,
            @Value("#{stepExecutionContext['remainder']}") Integer remainder) {

        this.lastWeekUsageService = lastWeekUsageService;
        this.reportUsageAppRedisRepository = reportUsageAppRedisRepository;
        this.reportUsageHourlyRedisRepository = reportUsageHourlyRedisRepository;
        this.taskExecutor = taskExecutor;

        // 1. 파라미터 설정 (gridSize=8, remainder=0~7)
        Map<String, Object> parameters = Map.of(
                "status", ReportStatus.PENDING.name(),
                "gridSize", gridSize,
                "remainder", remainder);

        // 2. QueryProvider 설정 (함수 기반 인덱스를 타는 MOD 쿼리)
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause(
            "weekly_report_id, family_id, sub_id, name, week_start_date, week_end_date"
        );
        queryProvider.setFromClause("from weekly_report");
        // 생성하신 CREATE INDEX idx_weekly_report_mod8 ON weekly_report (MOD(weekly_report_id, 8))를 활용함
        queryProvider.setWhereClause("where report_status = :status and MOD(weekly_report_id, :gridSize) = :remainder");
        queryProvider.setSortKeys(Map.of("weekly_report_id", Order.ASCENDING));

        try {
            this.delegate = new JdbcPagingItemReaderBuilder<ReportBasicInfo>()
                    .name("usageMetricsReaderDelegate")
                    .dataSource(dataSource)
                    .queryProvider(queryProvider)
                    .parameterValues(parameters)
                    .pageSize(BatchConstants.CHUNK_SIZE)
                    .fetchSize(BatchConstants.CHUNK_SIZE)
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
        
        long totalStart = System.currentTimeMillis();

        CompletableFuture<Map<Long, List<DailyAppUsage>>> appUsageFuture = CompletableFuture.supplyAsync(
            () -> reportUsageAppRedisRepository.findBulkWeeklyAppUsage(subIds, first.weekStartDate(), first.weekEndDate()), 
            taskExecutor
        );

        CompletableFuture<Map<Long, List<DailyHourlyUsage>>> hourlyUsageFuture = CompletableFuture.supplyAsync(
            () -> reportUsageHourlyRedisRepository.findBulkWeeklyHourlyUsage(subIds, first.weekStartDate(), first.weekEndDate()), 
            taskExecutor
        );

        CompletableFuture<Map<Long, WeeklyReportSnapshot>> lastWeekFuture = CompletableFuture.supplyAsync(
            () -> {
                Map<Long, LocalDate> lastReportDateMap = rawInfos.stream()
                    .collect(Collectors.toMap(ReportBasicInfo::subId, info -> info.weekStartDate().minusDays(7)));
                return lastWeekUsageService.getBulkSnapshotList(lastReportDateMap);
            }, 
            taskExecutor
        );

        CompletableFuture.allOf(appUsageFuture, hourlyUsageFuture, lastWeekFuture).join();

        Map<Long, List<DailyAppUsage>> appUsageMap = appUsageFuture.get();
        Map<Long, List<DailyHourlyUsage>> hourlyUsageMap = hourlyUsageFuture.get();
        Map<Long, WeeklyReportSnapshot> lastWeekMap = lastWeekFuture.get();

        log.info("[Perf-Reader-Final] MOD Partition I/O: {} ms for {} items (Parallel)", 
                 (System.currentTimeMillis() - totalStart), rawInfos.size());

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
