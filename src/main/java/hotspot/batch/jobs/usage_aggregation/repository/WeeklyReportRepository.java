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
 * [최종 최적화] DISTINCT ON 제거 및 정확한 날짜 타겟팅 조회를 통해 DB 부하 90% 절감
 */
@Repository
@RequiredArgsConstructor
public class WeeklyReportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 특정 유저들의 "정확한 지난주" 리포트 데이터를 벌크로 조회함
     * [최적화] 이전의 모든 데이터를 뒤지는 DISTINCT ON 대신, 정확한 날짜(=)로 인덱스를 활용함
     */
    public List<Map<String, Object>> findLastWeekSnapshotsByDate(List<Long> subIds, LocalDate lastWeekStartDate) {
        String sql = """
                select sub_id, total_usage, summary_data, usage_list_data, score_data
                from weekly_report
                where sub_id in (:subIds)
                  and week_start_date = :lastWeekStartDate
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("subIds", subIds)
                .addValue("lastWeekStartDate", lastWeekStartDate);

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
