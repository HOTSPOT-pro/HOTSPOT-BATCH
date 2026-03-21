package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.reader;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

@Component
@StepScope
public class UsageMetricsReader implements ItemStreamReader<UsageMetricsAggregationInput> {

    private static final Logger log = LoggerFactory.getLogger(UsageMetricsReader.class);

    private final JdbcPagingItemReader<ReportBasicInfo> delegate;
    private final LastWeekUsageService lastWeekUsageService;
    private final ReportUsageAppRedisRepository reportUsageAppRedisRepository;
    private final ReportUsageHourlyRedisRepository reportUsageHourlyRedisRepository;
    
    // I/O 가속을 위한 전용 스레드 풀 (GRID_SIZE=4, 각 파티션당 8개 이상의 Future가 생성되므로 32개로 확장)
    private static final ExecutorService ioExecutor = Executors.newFixedThreadPool(32);
    
    private final Queue<UsageMetricsAggregationInput> buffer = new LinkedList<>();
    private final Integer remainder;
    private int totalProcessed = 0;

    public UsageMetricsReader(
            DataSource dataSource,
            LastWeekUsageService lastWeekUsageService,
            ReportUsageAppRedisRepository reportUsageAppRedisRepository,
            ReportUsageHourlyRedisRepository reportUsageHourlyRedisRepository,
            hotspot.batch.jobs.usage_aggregation.job.UsageAggregationDateCalculator dateCalculator,
            @Value("#{jobParameters['targetDate']}") String targetDate,
            @Value("#{stepExecutionContext['gridSize']}") Integer gridSize,
            @Value("#{stepExecutionContext['remainder']}") Integer remainder) {

        this.lastWeekUsageService = lastWeekUsageService;
        this.reportUsageAppRedisRepository = reportUsageAppRedisRepository;
        this.reportUsageHourlyRedisRepository = reportUsageHourlyRedisRepository;
        this.remainder = remainder;

        // Step 1과 동일한 날짜 보정 로직 적용
        java.time.LocalDate baseDate = dateCalculator.getBaseDate(targetDate);
        java.time.LocalDate calculatedTargetDate = dateCalculator.getWeekStartDate(baseDate);
        
        log.info("[Part-{}] Initializing Reader for calculated date: {} (Original: {})", remainder, calculatedTargetDate, targetDate);

        Map<String, Object> parameters = Map.of(
                "statuses", List.of(ReportStatus.PENDING.name(), ReportStatus.AGGREGATED.name()),
                "targetDate", calculatedTargetDate,
                "gridSize", gridSize,
                "remainder", remainder);

        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("weekly_report_id, family_id, sub_id, name, week_start_date, week_end_date");
        queryProvider.setFromClause("from weekly_report");
        queryProvider.setWhereClause("where report_status in (:statuses) and week_start_date = :targetDate and MOD(weekly_report_id, :gridSize) = :remainder");
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

        long start = System.currentTimeMillis();
        List<Long> subIds = rawInfos.stream().map(ReportBasicInfo::subId).toList();
        LocalDate startDay = rawInfos.get(0).weekStartDate();
        LocalDate endDay = rawInfos.get(0).weekEndDate();

        // 1. Redis 조회를 병렬 그룹으로 분할 (Parallel Pipelining)
        // 200건을 50건씩 4개 그룹으로 분할
        int groupSize = 50;
        List<List<Long>> subIdGroups = new ArrayList<>();
        for (int i = 0; i < subIds.size(); i += groupSize) {
            subIdGroups.add(subIds.subList(i, Math.min(i + groupSize, subIds.size())));
        }

        // Redis 비동기 호출 리스트
        List<CompletableFuture<Map<Long, List<DailyAppUsage>>>> appFutures = subIdGroups.stream()
                .map(group -> CompletableFuture.supplyAsync(() -> 
                        reportUsageAppRedisRepository.findBulkWeeklyAppUsage(group, startDay, endDay), ioExecutor))
                .toList();

        List<CompletableFuture<Map<Long, List<DailyHourlyUsage>>>> hourlyFutures = subIdGroups.stream()
                .map(group -> CompletableFuture.supplyAsync(() -> 
                        reportUsageHourlyRedisRepository.findBulkWeeklyHourlyUsage(group, startDay, endDay), ioExecutor))
                .toList();

        // 2. DB 조회 비동기 호출 (Snapshot)
        Map<Long, LocalDate> lastReportDateMap = rawInfos.stream().collect(Collectors.toMap(ReportBasicInfo::subId, info -> info.weekStartDate().minusDays(7)));
        LocalDate targetLastWeekDate = lastReportDateMap.values().iterator().next();
        log.info("[Part-{}] Searching last week reports for date: {}", remainder, targetLastWeekDate);
        
        CompletableFuture<Map<Long, WeeklyReportSnapshot>> lastWeekFuture = CompletableFuture.supplyAsync(() -> 
                lastWeekUsageService.getBulkSnapshotList(lastReportDateMap), ioExecutor);

        // 모든 Future 대기
        CompletableFuture.allOf(appFutures.toArray(new CompletableFuture[0])).join();
        CompletableFuture.allOf(hourlyFutures.toArray(new CompletableFuture[0])).join();
        lastWeekFuture.join();

        // 결과 병합
        Map<Long, List<DailyAppUsage>> appUsageMap = new HashMap<>();
        for (var f : appFutures) appUsageMap.putAll(f.get());
        
        Map<Long, List<DailyHourlyUsage>> hourlyUsageMap = new HashMap<>();
        for (var f : hourlyFutures) hourlyUsageMap.putAll(f.get());
        
        Map<Long, WeeklyReportSnapshot> lastWeekMap = lastWeekFuture.get();

        totalProcessed += rawInfos.size();
        log.info("[Part-{}] Reader Accelerated I/O -> Total: {}ms | Processed: {}", 
                 remainder, (System.currentTimeMillis() - start), totalProcessed);

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
    public void open(ExecutionContext executionContext) throws ItemStreamException { delegate.open(executionContext); }
    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException { delegate.update(executionContext); }
    @Override
    public void close() throws ItemStreamException { delegate.close(); buffer.clear(); }
}
