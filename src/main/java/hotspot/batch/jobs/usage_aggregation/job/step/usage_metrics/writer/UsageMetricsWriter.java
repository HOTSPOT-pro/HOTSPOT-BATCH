package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.writer;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;

@Component("usageMetricsJdbcWriter")
public class UsageMetricsWriter extends JdbcBatchItemWriter<WeeklyReport> {

    private static final Logger log = LoggerFactory.getLogger(UsageMetricsWriter.class);
    private StepExecution stepExecution;
    private final AtomicInteger totalWriteCount = new AtomicInteger(0);

    public UsageMetricsWriter(@Qualifier("batchDataSource") DataSource dataSource) {
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
        this.setAssertUpdates(false);
    }

    @BeforeStep
    public void saveStepExecution(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public void write(Chunk<? extends WeeklyReport> chunk) throws Exception {
        long start = System.currentTimeMillis();
        super.write(chunk);
        long duration = System.currentTimeMillis() - start;
        
        int currentTotal = totalWriteCount.addAndGet(chunk.size());
        
        log.info("[Part-Writer] DB Update -> {}ms | Items: {} (Cumulative: {}) | Step: {}", 
                 duration, chunk.size(), currentTotal, stepExecution.getStepName());
    }

    private void setParameters(WeeklyReport item, PreparedStatement ps) throws SQLException {
        ps.setLong(1, item.totalUsage());
        ps.setObject(2, createPgObject(item.scoreJson()));
        ps.setArray(3, createSqlArray(item.tags(), ps));
        ps.setObject(4, createPgObject(item.summaryJson()));
        ps.setObject(5, createPgObject(item.usageListJson()));
        ps.setString(6, ReportStatus.AGGREGATED.name());
        ps.setLong(7, item.weeklyReportId());
    }

    private PGobject createPgObject(String json) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        pgObject.setValue(json != null ? json : "{}");
        return pgObject;
    }

    private Array createSqlArray(List<String> tags, PreparedStatement ps) throws SQLException {
        if (tags == null || tags.isEmpty()) {
            return ps.getConnection().createArrayOf("varchar", new String[0]);
        }
        return ps.getConnection().createArrayOf("varchar", tags.toArray());
    }
}
