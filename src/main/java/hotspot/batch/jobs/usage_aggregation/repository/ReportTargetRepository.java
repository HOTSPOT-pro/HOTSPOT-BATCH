package hotspot.batch.jobs.usage_aggregation.repository;

import java.time.LocalDate;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * report_target 테이블 조회를 담당하는 JDBC repository
 * report_target = 각 회선별 리포트 받고 싶은 요일, 마지막 리포트 생성 날짜 저장
 */
@Repository
public class ReportTargetRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportTargetRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 오늘 리포트를 받아야 하는 대상 중 가장 작은 report_target id를 조회
     * 같은 리포트 주기에 이미 생성이 완료된 대상은 제외
     */
    public Long findMinTargetId(String receiveDay, LocalDate weekStartDate) {
        return jdbcTemplate.queryForObject(
                minMaxSql("min(id)"),
                seedTargetParams(receiveDay, weekStartDate),
                Long.class);
    }

    /**
     * 오늘 리포트를 받아야 하는 대상 중 가장 큰 report_target id를 조회
     * 같은 리포트 주기에 이미 생성이 완료된 대상은 제외
     */
    public Long findMaxTargetId(String receiveDay, LocalDate weekStartDate) {
        return jdbcTemplate.queryForObject(
                minMaxSql("max(id)"),
                seedTargetParams(receiveDay, weekStartDate),
                Long.class);
    }

    /**
     * 최소/최대 id 조회에 공통으로 사용하는 조건 SQL
     */
    private String minMaxSql(String selectClause) {
        return """
                select %s
                from report_target
                where is_active = true
                  and receive_day = :receiveDay
                  and (last_report_date is null or last_report_date < :weekStartDate)
                """.formatted(selectClause);
    }

    /**
     * Step1 대상 조회에 공통으로 사용하는 파라미터를 구성
     */
    private MapSqlParameterSource seedTargetParams(String receiveDay, LocalDate weekStartDate) {
        return new MapSqlParameterSource()
                .addValue("receiveDay", receiveDay)
                .addValue("weekStartDate", weekStartDate);
    }
}
