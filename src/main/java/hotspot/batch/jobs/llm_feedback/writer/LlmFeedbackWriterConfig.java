package hotspot.batch.jobs.llm_feedback.writer;

import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 피드백 생성 결과를 DB에 업데이트하는 Writer 설정
 */
@Configuration
@RequiredArgsConstructor
public class LlmFeedbackWriterConfig {

    private final DataSource dataSource;
    private final JsonConverter jsonConverter;

    @Bean
    public AsyncItemWriter<LlmFeedbackWeeklyReport> asyncLlmFeedbackWriter(
            @Qualifier("llmFeedbackWriter") ItemWriter<LlmFeedbackWeeklyReport> writer) {
        return new AsyncItemWriter<>(writer);
    }

    @Bean
    public JdbcBatchItemWriter<LlmFeedbackWeeklyReport> llmFeedbackWriter() {
        String sql = """
                UPDATE weekly_report
                SET ai_feedback = ?,
                    is_llm_used = ?,
                    ai_model = ?,
                    prompt_version = ?,
                    report_status = ?,
                    modified_time = NOW()
                WHERE weekly_report_id = ?
                """;

        return new JdbcBatchItemWriterBuilder<LlmFeedbackWeeklyReport>()
                .dataSource(dataSource)
                .sql(sql)
                .itemPreparedStatementSetter(this::setParameters)
                .build();
    }

    private void setParameters(LlmFeedbackWeeklyReport item, PreparedStatement ps) throws SQLException {
        ps.setObject(1, createPgObject(jsonConverter.toJson(item.aiFeedback())));
        ps.setBoolean(2, item.isLlmUsed());
        ps.setString(3, item.aiModel());
        ps.setString(4, item.promptVersion());
        ps.setString(5, item.reportStatus());
        ps.setLong(6, item.weeklyReportId());
    }

    private PGobject createPgObject(String json) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        pgObject.setValue(json);
        return pgObject;
    }
}
