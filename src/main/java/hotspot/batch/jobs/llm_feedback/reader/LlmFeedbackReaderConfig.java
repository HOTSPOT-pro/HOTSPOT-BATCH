package hotspot.batch.jobs.llm_feedback.reader;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageListData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.PagingQueryProvider;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;

/**
 * LLM 피드백 처리를 위한 WeeklyReport 데이터를 읽어오는 Reader 설정
 */
@Configuration
@RequiredArgsConstructor
public class LlmFeedbackReaderConfig {

    private final DataSource dataSource;
    private final JsonConverter jsonConverter;

    @Bean
    @StepScope
    public JdbcPagingItemReader<LlmFeedbackWeeklyReport> llmFeedbackReader(
            @Value("#{jobParameters[targetDate]}") String targetDate) throws Exception {
        
        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("targetDate", targetDate);
        parameterValues.put("status", ReportStatus.AGGREGATED.name());

        return new JdbcPagingItemReaderBuilder<LlmFeedbackWeeklyReport>()
                .name("llmFeedbackReader")
                .dataSource(dataSource)
                .queryProvider(createPagingQueryProvider())
                .parameterValues(parameterValues)
                .pageSize(BatchConstants.LLM_CHUNK_SIZE)
                .rowMapper(llmFeedbackRowMapper())
                .build();
    }

    @Bean
    public PagingQueryProvider createPagingQueryProvider() {
        SqlPagingQueryProviderFactoryBean queryProvider = new SqlPagingQueryProviderFactoryBean();
        queryProvider.setDataSource(dataSource);
        queryProvider.setSelectClause("SELECT weekly_report_id, family_id, sub_id, name, week_start_date, week_end_date, total_usage, score_result, tags, summary_data, usage_list_data, report_status");
        queryProvider.setFromClause("FROM weekly_report");
        queryProvider.setWhereClause("WHERE report_status = :status AND week_start_date = :targetDate::date");
        queryProvider.setSortKeys(Map.of("weekly_report_id", Order.ASCENDING));
        
        try {
            return queryProvider.getObject();
        } catch (Exception e) {
            throw new RuntimeException("PagingQueryProvider 생성 실패", e);
        }
    }

    /**
     * PostgreSQL의 복잡한 타입을 Java Record 필드에 매핑하는 RowMapper
     */
    private RowMapper<LlmFeedbackWeeklyReport> llmFeedbackRowMapper() {
        return (rs, rowNum) -> LlmFeedbackWeeklyReport.builder()
                .weeklyReportId(rs.getLong("weekly_report_id"))
                .familyId(rs.getLong("family_id"))
                .subId(rs.getLong("sub_id"))
                .name(rs.getString("name"))
                .weekStartDate(rs.getObject("week_start_date", LocalDate.class))
                .weekEndDate(rs.getObject("week_end_date", LocalDate.class))
                .totalUsage(rs.getLong("total_usage"))
                // JSONB -> Object 변환
                .scoreResult(jsonConverter.fromJson(rs.getString("score_result"), ScoreResult.class))
                // varchar[] -> List<String> 변환
                .tags(parseSqlArray(rs, "tags"))
                // JSONB -> Object 변환
                .summaryData(jsonConverter.fromJson(rs.getString("summary_data"), SummaryData.class))
                .usageListData(jsonConverter.fromJson(rs.getString("usage_list_data"), UsageListData.class))
                .reportStatus(rs.getString("report_status"))
                .build();
    }

    private List<String> parseSqlArray(ResultSet rs, String columnName) throws SQLException {
        java.sql.Array sqlArray = rs.getArray(columnName);
        if (sqlArray == null) return List.of();
        String[] array = (String[]) sqlArray.getArray();
        return Arrays.asList(array);
    }
}
