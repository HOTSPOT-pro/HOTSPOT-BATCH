package hotspot.batch.common.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 다중 DataSource(main, batch) 설정을 관리
 */
@Configuration
public class DataSourceConfig {

    /**
     * 기본 DataSource (hotspot-batch DB)
     * application.yml의 spring.datasource.hikari 설정을 사용
     */
    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource batchDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 보조 DataSource (hotspot 메인 DB)
     * postgres-main.yml의 spring.datasource.main.hikari 설정을 사용
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.main.hikari")
    public DataSource mainDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 기본 JdbcTemplate (hotspot-batch DB용)
     */
    @Primary
    @Bean
    public NamedParameterJdbcTemplate batchJdbcTemplate(@Qualifier("batchDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * 보조 JdbcTemplate (hotspot 메인 DB용)
     */
    @Bean
    public NamedParameterJdbcTemplate mainJdbcTemplate(@Qualifier("mainDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
