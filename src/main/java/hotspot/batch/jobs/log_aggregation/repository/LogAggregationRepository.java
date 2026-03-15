package hotspot.batch.jobs.log_aggregation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LogAggregationRepository {

    private static final RowMapper<UsageAppliedEventLogRow> USAGE_EVENT_ROW_MAPPER = new UsageAppliedEventLogRowMapper();
    private static final RowMapper<SubUsageMonthlyAggregateRow> MONTHLY_AGGREGATE_ROW_MAPPER =
            new SubUsageMonthlyAggregateRowMapper();

    private final NamedParameterJdbcTemplate batchJdbcTemplate;

    public LogAggregationRepository(@Qualifier("batchJdbcTemplate") NamedParameterJdbcTemplate batchJdbcTemplate) {
        this.batchJdbcTemplate = batchJdbcTemplate;
    }

    public long findLastAppliedSeq(String projectionName) {
        String sql = """
                select last_applied_seq
                  from usage_aggregate_cursor
                 where projection_name = :projectionName
                """;

        List<Long> results = batchJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("projectionName", projectionName),
                (rs, rowNum) -> rs.getLong("last_applied_seq"));

        return results.isEmpty() ? 0L : results.get(0);
    }

    public long findMaxAppliedSeq() {
        String sql = """
                select coalesce(max(applied_seq), 0)
                  from usage_applied_event_log
                """;
        Long maxAppliedSeq = batchJdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return maxAppliedSeq == null ? 0L : maxAppliedSeq;
    }

    public List<UsageAppliedEventLogRow> findUsageAppliedEventLogsAfter(long lastAppliedSeq, int fetchSize) {
        String sql = """
                select applied_seq,
                       event_id,
                       sub_id,
                       family_id,
                       app_id,
                       occurred_at,
                       yyyymm,
                       yyyymmdd,
                       usage_amount,
                       gift_used,
                       plan_used,
                       family_used,
                       gift_detail_json
                  from usage_applied_event_log
                 where applied_seq > :lastAppliedSeq
                 order by applied_seq asc
                 limit :fetchSize
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastAppliedSeq", lastAppliedSeq)
                .addValue("fetchSize", fetchSize);

        return batchJdbcTemplate.query(sql, params, USAGE_EVENT_ROW_MAPPER);
    }

    public SubUsageMonthlyAggregateRow findSubUsageMonthlyAggregate(Long subId, String yyyymm) {
        String sql = """
                select sub_id,
                       yyyymm,
                       family_id,
                       gift_used_total,
                       plan_used_total,
                       family_used_total,
                       total_used,
                       coalesce(daily_usage_json, '{}'::jsonb)::text as daily_usage_json,
                       coalesce(gift_usage_json, '{}'::jsonb)::text as gift_usage_json,
                       last_applied_seq
                  from sub_usage_monthly_aggregate
                 where sub_id = :subId
                   and yyyymm = :yyyymm
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("subId", subId)
                .addValue("yyyymm", yyyymm);

        List<SubUsageMonthlyAggregateRow> rows = batchJdbcTemplate.query(sql, params, MONTHLY_AGGREGATE_ROW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void upsertSubUsageMonthlyAggregate(SubUsageMonthlyAggregateUpsert upsert) {
        String sql = """
                insert into sub_usage_monthly_aggregate (
                    sub_id,
                    yyyymm,
                    family_id,
                    gift_used_total,
                    plan_used_total,
                    family_used_total,
                    total_used,
                    daily_usage_json,
                    gift_usage_json,
                    last_applied_seq,
                    updated_time
                ) values (
                    :subId,
                    :yyyymm,
                    :familyId,
                    :giftUsedTotal,
                    :planUsedTotal,
                    :familyUsedTotal,
                    :totalUsed,
                    cast(:dailyUsageJson as jsonb),
                    cast(:giftUsageJson as jsonb),
                    :lastAppliedSeq,
                    now()
                )
                on conflict (sub_id, yyyymm) do update
                   set family_id = excluded.family_id,
                       gift_used_total = excluded.gift_used_total,
                       plan_used_total = excluded.plan_used_total,
                       family_used_total = excluded.family_used_total,
                       total_used = excluded.total_used,
                       daily_usage_json = excluded.daily_usage_json,
                       gift_usage_json = excluded.gift_usage_json,
                       last_applied_seq = excluded.last_applied_seq,
                       updated_time = now()
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("subId", upsert.subId())
                .addValue("yyyymm", upsert.yyyymm())
                .addValue("familyId", upsert.familyId())
                .addValue("giftUsedTotal", upsert.giftUsedTotal())
                .addValue("planUsedTotal", upsert.planUsedTotal())
                .addValue("familyUsedTotal", upsert.familyUsedTotal())
                .addValue("totalUsed", upsert.totalUsed())
                .addValue("dailyUsageJson", upsert.dailyUsageJson())
                .addValue("giftUsageJson", upsert.giftUsageJson())
                .addValue("lastAppliedSeq", upsert.lastAppliedSeq());

        batchJdbcTemplate.update(sql, params);
    }

    public void upsertUsageAggregateCursor(String projectionName, long lastAppliedSeq) {
        String sql = """
                insert into usage_aggregate_cursor (
                    projection_name,
                    last_applied_seq,
                    updated_time
                ) values (
                    :projectionName,
                    :lastAppliedSeq,
                    now()
                )
                on conflict (projection_name) do update
                   set last_applied_seq = excluded.last_applied_seq,
                       updated_time = now()
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectionName", projectionName)
                .addValue("lastAppliedSeq", lastAppliedSeq);

        batchJdbcTemplate.update(sql, params);
    }

    public record UsageAppliedEventLogRow(
            long appliedSeq,
            String eventId,
            Long subId,
            Long familyId,
            Long appId,
            String yyyymm,
            String yyyymmdd,
            long usageAmount,
            long giftUsed,
            long planUsed,
            long familyUsed,
            String giftDetailJson) {
    }

    public record SubUsageMonthlyAggregateRow(
            Long subId,
            String yyyymm,
            Long familyId,
            long giftUsedTotal,
            long planUsedTotal,
            long familyUsedTotal,
            long totalUsed,
            String dailyUsageJson,
            String giftUsageJson,
            long lastAppliedSeq) {
    }

    public record SubUsageMonthlyAggregateUpsert(
            Long subId,
            String yyyymm,
            Long familyId,
            long giftUsedTotal,
            long planUsedTotal,
            long familyUsedTotal,
            long totalUsed,
            String dailyUsageJson,
            String giftUsageJson,
            long lastAppliedSeq) {
    }

    private static class UsageAppliedEventLogRowMapper implements RowMapper<UsageAppliedEventLogRow> {
        @Override
        public UsageAppliedEventLogRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UsageAppliedEventLogRow(
                    rs.getLong("applied_seq"),
                    rs.getString("event_id"),
                    rs.getLong("sub_id"),
                    rs.getLong("family_id"),
                    rs.getLong("app_id"),
                    rs.getString("yyyymm"),
                    rs.getString("yyyymmdd"),
                    rs.getLong("usage_amount"),
                    rs.getLong("gift_used"),
                    rs.getLong("plan_used"),
                    rs.getLong("family_used"),
                    rs.getString("gift_detail_json"));
        }
    }

    private static class SubUsageMonthlyAggregateRowMapper implements RowMapper<SubUsageMonthlyAggregateRow> {
        @Override
        public SubUsageMonthlyAggregateRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SubUsageMonthlyAggregateRow(
                    rs.getLong("sub_id"),
                    rs.getString("yyyymm"),
                    rs.getLong("family_id"),
                    rs.getLong("gift_used_total"),
                    rs.getLong("plan_used_total"),
                    rs.getLong("family_used_total"),
                    rs.getLong("total_used"),
                    rs.getString("daily_usage_json"),
                    rs.getString("gift_usage_json"),
                    rs.getLong("last_applied_seq"));
        }
    }
}
