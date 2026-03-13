package hotspot.batch.common.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchDataSource;
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
     * application.yml의 spring.datasource 설정을 사용
     */
    @Primary
    @Bean
    @BatchDataSource // 스프링 배치 메타 테이블 저장소로 명시적 지정
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource batchDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * 보조 DataSource (hotspot 메인 DB)
     * postgres-main.yml의 spring.datasource.main 설정을 사용
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.main")
    public DataSource mainDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
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
