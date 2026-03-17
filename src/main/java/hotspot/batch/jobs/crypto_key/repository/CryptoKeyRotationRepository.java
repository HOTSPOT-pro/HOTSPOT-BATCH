package hotspot.batch.jobs.crypto_key.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import hotspot.batch.jobs.crypto_key.dto.PhoneRotationTarget;
import hotspot.batch.jobs.crypto_key.dto.SubscriptionKeyVersion;

@Repository
public class CryptoKeyRotationRepository {

    private final NamedParameterJdbcTemplate mainJdbcTemplate;
    private final DataSource mainDataSource;

    public CryptoKeyRotationRepository(
            @Qualifier("mainJdbcTemplate") NamedParameterJdbcTemplate mainJdbcTemplate,
            @Qualifier("mainDataSource") DataSource mainDataSource) {
        this.mainJdbcTemplate = mainJdbcTemplate;
        this.mainDataSource = mainDataSource;
    }

    public Optional<SubscriptionKeyVersion> findActiveKey(int bucketId) {
        String sql = """
                select bucket_id, key_version, encrypted_dek, kek_key_id, status
                from subscription_key
                where bucket_id = :bucketId
                  and status = 'active'
                order by key_version desc
                limit 1
                """;

        return mainJdbcTemplate.query(sql, new MapSqlParameterSource("bucketId", bucketId), (rs, rowNum) ->
                        new SubscriptionKeyVersion(
                                rs.getInt("bucket_id"),
                                rs.getInt("key_version"),
                                rs.getString("encrypted_dek"),
                                rs.getString("kek_key_id"),
                                rs.getString("status")))
                .stream()
                .findFirst();
    }

    public Optional<SubscriptionKeyVersion> findKey(int bucketId, int keyVersion) {
        String sql = """
                select bucket_id, key_version, encrypted_dek, kek_key_id, status
                from subscription_key
                where bucket_id = :bucketId
                  and key_version = :keyVersion
                limit 1
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bucketId", bucketId)
                .addValue("keyVersion", keyVersion);

        return mainJdbcTemplate.query(sql, params, (rs, rowNum) ->
                        new SubscriptionKeyVersion(
                                rs.getInt("bucket_id"),
                                rs.getInt("key_version"),
                                rs.getString("encrypted_dek"),
                                rs.getString("kek_key_id"),
                                rs.getString("status")))
                .stream()
                .findFirst();
    }

    public List<Integer> findTargetBucketIds() {
        String sql = """
                select distinct s.phone_key_bucket_id
                from subscription s
                join subscription_key sk
                  on sk.bucket_id = s.phone_key_bucket_id
                 and sk.key_version = s.phone_key_version
                where s.is_deleted = false
                  and s.phone_enc is not null
                  and s.phone_enc <> ''
                  and sk.status = 'active'
                order by s.phone_key_bucket_id
                """;

        return mainJdbcTemplate.getJdbcTemplate().queryForList(sql, Integer.class);
    }

    public int findNextKeyVersion(int bucketId) {
        String sql = "select coalesce(max(key_version), 0) + 1 from subscription_key where bucket_id = :bucketId";
        Integer nextVersion = mainJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("bucketId", bucketId),
                Integer.class);
        return nextVersion == null ? 1 : nextVersion;
    }

    public void insertKey(int bucketId, int keyVersion, String encryptedDek, String kekKeyId, String status) {
        String sql = """
                insert into subscription_key (
                    bucket_id, key_version, encrypted_dek, kek_key_id, status, created_time, modified_time
                ) values (
                    :bucketId, :keyVersion, :encryptedDek, :kekKeyId, :status, now(), now()
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bucketId", bucketId)
                .addValue("keyVersion", keyVersion)
                .addValue("encryptedDek", encryptedDek)
                .addValue("kekKeyId", kekKeyId)
                .addValue("status", status);
        mainJdbcTemplate.update(sql, params);
    }

    public void updateKeyStatus(int bucketId, int keyVersion, String status) {
        String sql = """
                update subscription_key
                   set status = :status,
                       modified_time = now()
                 where bucket_id = :bucketId
                   and key_version = :keyVersion
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bucketId", bucketId)
                .addValue("keyVersion", keyVersion)
                .addValue("status", status);
        mainJdbcTemplate.update(sql, params);
    }

    public long countSubscriptionsByVersion(int bucketId, int keyVersion) {
        String sql = """
                select count(*)
                from subscription
                where phone_key_bucket_id = :bucketId
                  and phone_key_version = :keyVersion
                  and is_deleted = false
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bucketId", bucketId)
                .addValue("keyVersion", keyVersion);
        Long count = mainJdbcTemplate.queryForObject(sql, params, Long.class);
        return count == null ? 0L : count;
    }

    public int updateSubscriptionPhone(Long subId, int bucketId, String phoneEnc, int sourceKeyVersion, int targetKeyVersion) {
        String sql = """
                update subscription
                   set phone_enc = :phoneEnc,
                       phone_key_version = :targetKeyVersion,
                       modified_time = now()
                 where sub_id = :subId
                   and phone_key_bucket_id = :bucketId
                   and phone_key_version = :sourceKeyVersion
                   and is_deleted = false
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("subId", subId)
                .addValue("bucketId", bucketId)
                .addValue("phoneEnc", phoneEnc)
                .addValue("sourceKeyVersion", sourceKeyVersion)
                .addValue("targetKeyVersion", targetKeyVersion);
        return mainJdbcTemplate.update(sql, params);
    }

    public JdbcPagingItemReader<PhoneRotationTarget> buildRotationReader(String name, int chunkSize, int bucketId, int sourceKeyVersion) {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("sub_id, phone_enc, phone_key_version");
        queryProvider.setFromClause("from subscription");
        queryProvider.setWhereClause("""
                where phone_key_bucket_id = :bucketId
                  and phone_key_version = :sourceKeyVersion
                  and is_deleted = false
                  and phone_enc is not null
                  and phone_enc <> ''
                """);
        queryProvider.setSortKeys(Map.of("sub_id", Order.ASCENDING));

        try {
            return new JdbcPagingItemReaderBuilder<PhoneRotationTarget>()
                    .name(name)
                    .dataSource(mainDataSource)
                    .queryProvider(queryProvider)
                    .parameterValues(Map.of(
                            "bucketId", bucketId,
                            "sourceKeyVersion", sourceKeyVersion))
                    .pageSize(chunkSize)
                    .rowMapper(new DataClassRowMapper<>(PhoneRotationTarget.class))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build crypto key rotation reader", e);
        }
    }
}
