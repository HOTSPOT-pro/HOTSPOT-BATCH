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

import java.util.concurrent.CompletableFuture;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Step2: "Bulk Pre-fetching" 기능이 있는 Reader
 * Step 1에서 생성된 PENDING 상태의 리포트 데이터를 읽어 분석 단계로 전달함
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
            @Value("#{stepExecutionContext['startId']}") Long startId,
            @Value("#{stepExecutionContext['endId']}") Long endId) {

        this.lastWeekUsageService = lastWeekUsageService;
        this.reportUsageAppRedisRepository = reportUsageAppRedisRepository;
        this.reportUsageHourlyRedisRepository = reportUsageHourlyRedisRepository;
        this.taskExecutor = taskExecutor;

        // 1. 상태값 및 범위 파라미터 설정
        Map<String, Object> parameters = Map.of(
                "status", ReportStatus.PENDING.name(),
                "startId", startId,
                "endId", endId);

        // 2. QueryProvider 설정: report_target JOIN을 제거하고 weekly_report 단일 테이블에서 필요한 정보를 모두 추출
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause(
            "weekly_report_id, family_id, sub_id, name, week_start_date, week_end_date"
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

    /**
     * Chunk 단위로 데이터를 미리 읽어와 Redis 및 지난주 데이터를 벌크로 채워 넣는 핵심 로직
     * [Phase 2 개선] CompletableFuture를 활용한 비동기 병렬 I/O 처리
     */
    private void fillBuffer() throws Exception {
        List<ReportBasicInfo> rawInfos = new ArrayList<>();
        
        for (int i = 0; i < BatchConstants.CHUNK_SIZE; i++) {
            ReportBasicInfo info = delegate.read();
            if (info == null) break;
            rawInfos.add(info);
        }

        if (rawInfos.isEmpty()) {
            return;
        }

        List<Long> subIds = rawInfos.stream().map(ReportBasicInfo::subId).toList();
        ReportBasicInfo first = rawInfos.get(0);
        
        long totalStart = System.currentTimeMillis();

        // 4. 비동기 작업 정의
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

        // 5. 모든 작업 완료 대기
        CompletableFuture.allOf(appUsageFuture, hourlyUsageFuture, lastWeekFuture).join();

        Map<Long, List<DailyAppUsage>> appUsageMap = appUsageFuture.get();
        Map<Long, List<DailyHourlyUsage>> hourlyUsageMap = hourlyUsageFuture.get();
        Map<Long, WeeklyReportSnapshot> lastWeekMap = lastWeekFuture.get();

        log.info("[Perf-Reader-Optimized] Total fillBuffer I/O time: {} ms for {} items (Parallel)", 
                 (System.currentTimeMillis() - totalStart), rawInfos.size());

        // 7. 모든 데이터를 조합하여 버퍼 적재
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
