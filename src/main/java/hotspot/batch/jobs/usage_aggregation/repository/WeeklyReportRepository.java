package hotspot.batch.jobs.usage_aggregation.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
     * 특정 유저들의 특정 날짜 기준 리포트 데이터를 벌크로 조회함
     * 지난주 스냅샷 조회를 위해 사용되며, 비교에 필요한 최소한의 컬럼만 선택함
     */
    public List<Map<String, Object>> findLastWeekSnapshotsForComparison(List<Long> subIds, LocalDate startDate) {
        String sql = """
                select sub_id, total_usage, total_score, summary_data, usage_list_data, score_data
                from weekly_report
                where sub_id in (:subIds)
                  and week_start_date = :startDate
                  and report_status = :reportStatus::report_status_enum
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("subIds", subIds)
                .addValue("startDate", startDate)
                .addValue("reportStatus", ReportStatus.AGGREGATED.name());

        return jdbcTemplate.queryForList(sql, params);
    }

    /**
     * Step2 파티셔닝을 위한 PENDING 상태의 weekly_report_id 최소값 조회
     */
    public Long findMinIdByStatus(ReportStatus status) {
        String sql = "select min(weekly_report_id) from weekly_report where report_status = :status::report_status_enum";
        MapSqlParameterSource params = new MapSqlParameterSource("status", status.name());
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    /**
     * Step2 파티셔닝을 위한 PENDING 상태의 weekly_report_id 최대값 조회
     */
    public Long findMaxIdByStatus(ReportStatus status) {
        String sql = "select max(weekly_report_id) from weekly_report where report_status = :status::report_status_enum";
        MapSqlParameterSource params = new MapSqlParameterSource("status", status.name());
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }
}
