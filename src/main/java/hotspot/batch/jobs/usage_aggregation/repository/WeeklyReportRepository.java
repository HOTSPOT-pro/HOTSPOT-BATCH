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
     * 특정 유저들의 현재 주차 시작일 이전 리포트 중 가장 최신 데이터를 벌크로 조회함
     * 전주 대비 비교 분석을 위한 스냅샷 조회를 위해 사용됨
     */
    public List<Map<String, Object>> findLastWeekSnapshotsForComparison(List<Long> subIds, LocalDate currentStartDate) {
        // PostgreSQL의 DISTINCT ON을 사용하여 유저별로 가장 최신의 이전 리포트 1개씩만 추출
        String sql = """
                select distinct on (sub_id) sub_id, total_usage, summary_data, usage_list_data, score_data
                from weekly_report
                where sub_id in (:subIds)
                  and week_start_date < :currentStartDate
                  and report_status = :reportStatus
                order by sub_id, week_start_date desc
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("subIds", subIds)
                .addValue("currentStartDate", currentStartDate)
                .addValue("reportStatus", ReportStatus.COMPLETED.name());

        return jdbcTemplate.queryForList(sql, params);
    }

    /**
     * Step2 파티셔닝을 위한 PENDING 상태의 weekly_report_id 최소값 조회
     */
    public Long findMinIdByStatus(ReportStatus status) {
        String sql = "select min(weekly_report_id) from weekly_report where report_status = :status";
        MapSqlParameterSource params = new MapSqlParameterSource("status", status.name());
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    /**
     * Step2 파티셔닝을 위한 PENDING 상태의 weekly_report_id 최대값 조회
     */
    public Long findMaxIdByStatus(ReportStatus status) {
        String sql = "select max(weekly_report_id) from weekly_report where report_status = :status";
        MapSqlParameterSource params = new MapSqlParameterSource("status", status.name());
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }
}
