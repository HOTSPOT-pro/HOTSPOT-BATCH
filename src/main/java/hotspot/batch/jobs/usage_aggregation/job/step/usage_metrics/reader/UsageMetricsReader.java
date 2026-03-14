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
 * Step 1에서 생성된 PENDING 상태의 리포트 데이터를 읽어 분석 단계로 전달함
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

        // 1. 상태값 및 범위 파라미터 설정
        Map<String, Object> parameters = Map.of(
                "status", ReportStatus.PENDING.name(),
                "startId", startId,
                "endId", endId);

        // 2. QueryProvider 설정: report_target JOIN을 제거하고 weekly_report 단일 테이블에서 필요한 정보를 모두 추출
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

    /**
     * Chunk 단위로 데이터를 미리 읽어와 Redis 및 지난주 데이터를 벌크로 채워 넣는 핵심 로직
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
        ReportBasicInfo first = rawInfos.get(0);
        
        // 3. 지난주 리포트 조회를 위한 날짜 매핑 (현재 주차 시작일의 7일 전으로 자동 계산)
        Map<Long, LocalDate> lastReportDateMap = rawInfos.stream()
            .collect(Collectors.toMap(ReportBasicInfo::subId, info -> info.weekStartDate().minusDays(7)));

        // 4. 이번 주 일별/앱별 사용량 벌크 조회 (Redis ZSet)
        Map<Long, List<DailyAppUsage>> appUsageMap = reportUsageAppRedisRepository.findBulkWeeklyAppUsage(
                subIds, first.weekStartDate(), first.weekEndDate());

        // 5. 이번 주 일별/시간대별 사용량 벌크 조회 (Redis Hash)
        Map<Long, List<DailyHourlyUsage>> hourlyUsageMap = reportUsageHourlyRedisRepository.findBulkWeeklyHourlyUsage(
                subIds, first.weekStartDate(), first.weekEndDate());

        // 6. 지난주 리포트 스냅샷 벌크 조회 (DB)
        Map<Long, WeeklyReportSnapshot> lastWeekMap = lastWeekUsageService.getBulkSnapshotList(lastReportDateMap);

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
