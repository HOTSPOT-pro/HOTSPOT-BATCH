package hotspot.batch.jobs.usage_aggregation.repository;

import java.time.LocalDate;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import lombok.RequiredArgsConstructor;

/**
 * weekly_report 테이블의 insert/update를 담당하는 JDBC repository
 */
@Repository
@RequiredArgsConstructor
public class WeeklyReportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Step1에서 생성한 WeeklyReport seed 데이터를 bulk SQL로 삽입
     * 100만 건 규모 처리를 위해 Java 레이어를 거치지 않고 DB 내부에서 직접 수행
     */
    public int insertSeedReports(String receiveDay, LocalDate baseDate, LocalDate weekStartDate, LocalDate weekEndDate) {
        String sql = """
                insert into weekly_report (
                    sub_id,
                    name,
                    week_start_date,
                    week_end_date,
                    report_status
                )
                select 
                    sub_id, 
                    name,
                    :weekStartDate, 
                    :weekEndDate, 
                    :reportStatus
                from report_target
                where is_active = true
                  and receive_day = :receiveDay
                  and (last_report_date is null or last_report_date < :baseDate)
                on conflict (sub_id, week_start_date) do nothing
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("weekStartDate", weekStartDate)
                .addValue("weekEndDate", weekEndDate)
                .addValue("reportStatus", ReportStatus.PENDING.name())
                .addValue("receiveDay", receiveDay)
                .addValue("baseDate", baseDate);

        return jdbcTemplate.update(sql, params);
    }

    /**
     * Step2 파티셔닝을 위한 PENDING 상태의 report_id 최소값 조회
     */
    public Long findMinIdByStatus(ReportStatus status) {
        String sql = "select min(report_id) from weekly_report where report_status = :status";
        MapSqlParameterSource params = new MapSqlParameterSource("status", status.name());
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    /**
     * Step2 파티셔닝을 위한 PENDING 상태의 report_id 최대값 조회
     */
    public Long findMaxIdByStatus(ReportStatus status) {
        String sql = "select max(report_id) from weekly_report where report_status = :status";
        MapSqlParameterSource params = new MapSqlParameterSource("status", status.name());
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }
}
