package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.writer;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.postgresql.util.PGobject;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;

/**
 * 분석이 완료된 WeeklyReport 데이터를 DB에 일괄 업데이트하는 Writer
 * 최신 DDL 기준: total_score, score_level 컬럼은 제외하고 score_data(JSONB)에 통합 저장함
 */
@Component("usageMetricsJdbcWriter")
public class UsageMetricsWriter extends JdbcBatchItemWriter<WeeklyReport> {

    private final DataSource dataSource;
    private final JsonConverter jsonConverter;

    public UsageMetricsWriter(@Qualifier("batchDataSource") DataSource dataSource, JsonConverter jsonConverter) {
        this.dataSource = dataSource;
        this.jsonConverter = jsonConverter;

        // 최신 DDL 반영: total_score, score_level 컬럼 제거
        String sql = """
                UPDATE weekly_report SET
                    total_usage = ?,
                    score_data = ?,
                    tags = ?,
                    summary_data = ?,
                    usage_list_data = ?,
                    report_status = ?,
                    modified_time = NOW()
                WHERE weekly_report_id = ?
                """;

        this.setDataSource(dataSource);
        this.setSql(sql);
        this.setItemPreparedStatementSetter(this::setParameters);
        
        try {
            this.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize UsageMetricsWriter", e);
        }
    }

    /**
     * DB 컬럼과 DTO 필드 간의 매핑 로직
     */
    private void setParameters(WeeklyReport item, PreparedStatement ps) throws SQLException {
        // 1. total_usage
        ps.setLong(1, item.totalUsage());
        
        // 2. score_data (JSONB)
        ps.setObject(2, createPgObject(jsonConverter.toJson(item.scoreResult())));
        
        // 3. tags (VARCHAR[])
        ps.setArray(3, createSqlArray(item.tags()));
        
        // 4. summary_data (JSONB)
        ps.setObject(4, createPgObject(jsonConverter.toJson(item.summaryData())));
        
        // 5. usage_list_data (JSONB)
        ps.setObject(5, createPgObject(jsonConverter.toJson(item.usageListData())));
        
        // 6. report_status (VARCHAR)
        ps.setString(6, ReportStatus.AGGREGATED.name());
        
        // 7. weekly_report_id (WHERE 조건)
        ps.setLong(7, item.weeklyReportId());
    }

    /**
     * JSON 데이터를 PostgreSQL JSONB 타입으로 변환함
     */
    private PGobject createPgObject(String json) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        pgObject.setValue(json);
        return pgObject;
    }

    /**
     * String 리스트를 PostgreSQL의 VARCHAR[] 타입으로 변환함
     */
    private Array createSqlArray(List<String> tags) throws SQLException {
        if (tags == null || tags.isEmpty()) {
            return dataSource.getConnection().createArrayOf("varchar", new String[0]);
        }
        return dataSource.getConnection().createArrayOf("varchar", tags.toArray());
    }
}
