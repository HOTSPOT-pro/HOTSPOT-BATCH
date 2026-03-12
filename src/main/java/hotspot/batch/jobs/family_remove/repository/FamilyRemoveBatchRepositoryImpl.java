package hotspot.batch.jobs.family_remove.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FamilyRemoveBatchRepositoryImpl implements FamilyRemoveBatchRepository {

    private static final long SHARED_DATA_PER_MEMBER_KB = 5L * 1024L * 1024L;

    private static final RowMapper<FamilyRemoveScheduleRow> DUE_SCHEDULE_ROW_MAPPER =
            (rs, rowNum) -> new FamilyRemoveScheduleRow(
                    rs.getLong("family_remove_schedule_id"),
                    rs.getLong("family_id"),
                    rs.getLong("target_sub_id"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FamilyRemoveBatchRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<FamilyRemoveScheduleRow> findDueSchedules(LocalDate baseDate) {
        String sql = """
                select family_remove_schedule_id, family_id, target_sub_id
                  from family_remove_schedule
                 where status = 'SCHEDULED'
                   and schedule_date <= :baseDate
                 order by family_remove_schedule_id asc
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("baseDate", baseDate);
        return jdbcTemplate.query(sql, params, DUE_SCHEDULE_ROW_MAPPER);
    }

    @Override
    public boolean existsFamilySub(Long familyId, Long targetSubId) {
        String sql = """
                select exists(
                    select 1
                      from family_sub
                     where family_id = :familyId
                       and sub_id = :targetSubId
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("targetSubId", targetSubId);
        Boolean exists = jdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public long countFamilyMembers(Long familyId) {
        String sql = """
                select count(*)
                  from family_sub
                 where family_id = :familyId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public void deleteFamilySub(Long familyId, Long targetSubId) {
        String sql = """
                delete from family_sub
                 where family_id = :familyId
                   and sub_id = :targetSubId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("targetSubId", targetSubId);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void deletePolicySub(Long targetSubId) {
        String sql = """
                delete from policy_sub
                 where sub_id = :targetSubId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("targetSubId", targetSubId);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void deleteBlockedServiceSub(Long targetSubId) {
        String sql = """
                delete from blocked_service_sub
                 where sub_id = :targetSubId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("targetSubId", targetSubId);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateFamilySummary(Long familyId, int familyNum, long familyDataAmount) {
        String sql = """
                update family
                   set family_num = :familyNum,
                       family_data_amount = :familyDataAmount,
                       modified_time = now()
                 where family_id = :familyId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("familyNum", familyNum)
                .addValue("familyDataAmount", familyDataAmount);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateFamilySubDataLimit(Long familyId, long dataLimit) {
        String sql = """
                update family_sub
                   set data_limit = :dataLimit
                 where family_id = :familyId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("dataLimit", dataLimit);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void markCompleted(Long scheduleId) {
        String sql = """
                update family_remove_schedule
                   set status = 'COMPLETED',
                       modified_time = now()
                 where family_remove_schedule_id = :scheduleId
                   and status = 'SCHEDULED'
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("scheduleId", scheduleId);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void markFailed(Long scheduleId) {
        String sql = """
                update family_remove_schedule
                   set status = 'FAILED',
                       modified_time = now()
                 where family_remove_schedule_id = :scheduleId
                   and status = 'SCHEDULED'
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("scheduleId", scheduleId);
        jdbcTemplate.update(sql, params);
    }

    long calculateFamilyDataAmount(long familyMemberCount) {
        return familyMemberCount * SHARED_DATA_PER_MEMBER_KB;
    }
}
