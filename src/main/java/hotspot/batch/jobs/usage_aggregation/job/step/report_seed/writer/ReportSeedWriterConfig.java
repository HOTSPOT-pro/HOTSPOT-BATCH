package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.writer;

import javax.sql.DataSource;

import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;

/**
 * Job1 - step1 - writer
 * 배치 DB의 weekly_report 테이블에 시드 데이터를 JDBC Batch 기능을 통해 벌크로 삽입하는 Writer
 * 1,000건(Chunk Size)의 데이터를 하나의 INSERT 명령어로 묶어서 전송함
 */

@Configuration
public class ReportSeedWriterConfig {
    @Bean
    public JdbcBatchItemWriter<WeeklyReport> reportSeedWriter(
            @Qualifier("batchDataSource") DataSource batchDataSource) {

        String sql = """
                INSERT INTO weekly_report (
                    family_id, sub_id, name, week_start_date, week_end_date, report_status
                ) VALUES (
                    :familyId, :subId, :name, :weekStartDate, :weekEndDate, :reportStatus
                ) ON CONFLICT (sub_id, week_start_date) DO NOTHING
                """;

        return new JdbcBatchItemWriterBuilder<WeeklyReport>()
                .dataSource(batchDataSource)
                .sql(sql)
                .assertUpdates(false) // 핵심 추가: DO NOTHING으로 인한 업데이트 카운트 0 통과 허용
                // Java Record의 필드 접근자(method())와 SQL 파라미터를 벌크로 매핑함
                .itemSqlParameterSourceProvider(item -> {
                    MapSqlParameterSource params = new MapSqlParameterSource();
                    params.addValue("familyId", item.familyId());
                    params.addValue("subId", item.subId());
                    params.addValue("name", item.name());
                    params.addValue("weekStartDate", item.weekStartDate());
                    params.addValue("weekEndDate", item.weekEndDate());
                    params.addValue("reportStatus", item.reportStatus());
                    return params;
                })
                .build();
    }
}