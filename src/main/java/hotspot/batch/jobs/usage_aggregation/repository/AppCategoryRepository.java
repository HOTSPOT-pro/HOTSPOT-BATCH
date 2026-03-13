package hotspot.batch.jobs.usage_aggregation.repository;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 메인 DB(hotspot)의 app_blocked_service 테이블을 조회하여 앱-카테고리 매핑 정보를 가져옴
 */
@Repository
public class AppCategoryRepository {

    private final NamedParameterJdbcTemplate mainJdbcTemplate;

    public AppCategoryRepository(@Qualifier("mainJdbcTemplate") NamedParameterJdbcTemplate mainJdbcTemplate) {
        this.mainJdbcTemplate = mainJdbcTemplate;
    }

    /**
     * 모든 앱-카테고리 정보를 조회하여 (appBlockedServiceId -> categoryName) 맵으로 반환함
     */
    public Map<Long, String> findAllAppCategories() {
        String sql = "select app_blocked_service_id, blocked_service_code from app_blocked_service where is_deleted = false";
        
        return mainJdbcTemplate.query(sql, rs -> {
            Map<Long, String> map = new HashMap<>();
            while (rs.next()) {
                long id = rs.getLong("app_blocked_service_id");
                String code = rs.getString("blocked_service_code");
                // "STUDY_abc" -> "STUDY"로 파싱
                String category = code.contains("_") ? code.substring(0, code.indexOf("_")) : code;
                map.put(id, category);
            }
            return map;
        });
    }
}
