package hotspot.batch.jobs.usage_aggregation.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.WeeklyReportSeedCommand;

/**
 * weekly_report 테이블의 insert/upsert를 담당하는 JDBC repository
 */
@Repository
public class WeeklyReportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WeeklyReportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Step1에서 생성한 WeeklyReport seed 데이터를 batch upsert
     * 동일한 (sub_id, week_start_date) 조합이 있으면 중복 생성 X
     * 같은 주 재생성 방지는 report_target.last_report_date 대신 unique key로 처리한다.
     */
    public void upsertWeeklyReports(List<? extends WeeklyReportSeedCommand> commands) {
        String sql = """
                insert into weekly_report (
                    sub_id,
                    week_start_date,
                    week_end_date,
                    status
                ) values (
                    :subId,
                    :weekStartDate,
                    :weekEndDate,
                    :status
                )
                on conflict (sub_id, week_start_date) do nothing
                """;

        SqlParameterSource[] batchParams = commands.stream()
                .map(command -> new MapSqlParameterSource()
                        .addValue("subId", command.subId())
                        .addValue("weekStartDate", command.weekStartDate())
                        .addValue("weekEndDate", command.weekEndDate())
                        .addValue("status", command.status().name()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, batchParams);
    }
}
