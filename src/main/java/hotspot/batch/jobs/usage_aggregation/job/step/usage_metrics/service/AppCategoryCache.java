package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import java.util.Collections;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.repository.AppCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 앱-카테고리 매핑 정보를 메모리에 캐싱하여 DB 조회를 최소화함
 * 배치 시작 시점에 데이터를 로딩하는 싱글톤 캐시
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppCategoryCache {

    private final AppCategoryRepository appCategoryRepository;
    private Map<Long, String> appCategoryMap;

    /**
     * 의존성 주입이 완료된 후, DB에서 모든 앱-카테고리 정보를 조회하여 맵에 저장함
     */
    @PostConstruct
    public void init() {
        log.info("Initializing AppCategoryCache...");
        try {
            appCategoryMap = appCategoryRepository.findAllAppCategories();
            log.info("AppCategoryCache initialized with {} entries", appCategoryMap.size());
        } catch (Exception e) {
            log.error("Failed to initialize AppCategoryCache", e);
            appCategoryMap = Collections.emptyMap();
        }
    }

    /**
     * appId를 받아 메모리 캐시에서 카테고리명을 즉시 반환함
     */
    public String getCategoryName(Long appId) {
        return appCategoryMap.getOrDefault(appId, "etc"); // 매핑 정보가 없으면 "etc" 반환
    }
}
