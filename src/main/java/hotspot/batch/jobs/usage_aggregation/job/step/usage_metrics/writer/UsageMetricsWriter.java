package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.writer;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.postgresql.util.PGobject;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;

/**
 * 분석이 완료된 WeeklyReport 데이터를 DB에 일괄 업데이트하는 Writer
 */
@Configuration
public class UsageMetricsWriter {

    private final DataSource dataSource;
    private final JsonConverter jsonConverter;

    public UsageMetricsWriter(DataSource dataSource, JsonConverter jsonConverter) {
        this.dataSource = dataSource;
        this.jsonConverter = jsonConverter;
    }

    /**
     * JDBC 기반의 Bulk Update Writer를 생성함
     * PostgreSQL의 JSONB 및 String Array 타입을 처리하기 위해 커스텀 PreparedStatementSetter를 사용함
     */
    @Bean
    public JdbcBatchItemWriter<WeeklyReport> usageMetricsJdbcWriter() {
        String sql = """
                UPDATE weekly_report SET
                    total_usage = ?,
                    total_score = ?,
                    score_level = ?,
                    score_data = ?,
                    tags = ?,
                    summary_data = ?,
                    usage_list_data = ?,
                    report_status = ?,
                    modified_time = NOW()
                WHERE report_id = ?
                """;

        return new JdbcBatchItemWriterBuilder<WeeklyReport>()
                .dataSource(dataSource)
                .sql(sql)
                .itemPreparedStatementSetter(this::setParameters)
                .build();
    }

    /**
     * DB 컬럼과 DTO 필드 간의 매핑 로직
     */
    private void setParameters(WeeklyReport item, PreparedStatement ps) throws SQLException {
        ps.setLong(1, item.totalUsage());
        ps.setInt(2, item.scoreResult().totalScore());
        ps.setString(3, item.scoreResult().scoreLevel());
        
        // JSONB 타입 처리 (PGobject 활용)
        ps.setObject(4, createPgObject(jsonConverter.toJson(item.scoreResult())));
        
        // String Array 타입 처리
        ps.setArray(5, createSqlArray(item.tags()));
        
        ps.setObject(6, createPgObject(jsonConverter.toJson(item.summaryData())));
        ps.setObject(7, createPgObject(jsonConverter.toJson(item.usageListData())));
        
        ps.setString(8, ReportStatus.AGGREGATED.name());
        ps.setLong(9, item.reportId());
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
