package hotspot.batch.jobs.llm_feedback.client.impl;

import hotspot.batch.jobs.llm_feedback.client.LlmApiClient;
import hotspot.batch.jobs.llm_feedback.dto.AiFeedback;
import hotspot.batch.jobs.llm_feedback.dto.Feedback;
import hotspot.batch.jobs.llm_feedback.dto.PolicyRecommend;
import hotspot.batch.jobs.llm_feedback.dto.SummaryText;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 테스트 및 검증을 위한 Mock LLM API 클라이언트
 * - 실제 API 호출 없이 더미 데이터를 반환함
 * - 1초의 지연(Latency)을 주어 AsyncItemProcessor의 병렬 처리 확인 가능
 * - 10% 확률로 에러를 발생시켜 Skip/Retry 로직 확인 가능
 */
@Slf4j
@Service
@Profile("mock") // 'mock' 프로파일 활성화 시에만 빈으로 등록됨
public class MockLlmApiClient implements LlmApiClient {

    private final Random random = new Random();

    @Override
    public Mono<AiFeedback> generateFeedback(String prompt) {
        // 10% 확률로 에러 발생 (Skip/Retry 테스트용)
        if (random.nextInt(10) == 0) {
            log.error("[MOCK] Simulated API Failure for testing skip/retry");
            return Mono.error(new RuntimeException("Mock API Failure"));
        }

        // 1초 지연 후 더미 데이터 반환 (비동기 병렬 처리 확인용)
        return Mono.just(createDummyFeedback())
                .delayElement(Duration.ofSeconds(1))
                .doOnNext(data -> log.info("[MOCK] Generated dummy feedback successfully"));
    }

    private AiFeedback createDummyFeedback() {
        return AiFeedback.builder()
                .summaryText(SummaryText.builder()
                        .overall("[MOCK] 이번 주는 전반적으로 사용량이 안정적이었습니다.")
                        .daily("주말 사용량이 평일보다 약간 높게 나타났습니다.")
                        .hourly("저녁 8시 이후 사용 비중이 가장 큽니다.")
                        .category("학습 앱 사용 비중이 40%로 가장 높습니다.")
                        .build())
                .keyInsights(List.of(
                        "[MOCK] 학습 앱 위주의 건전한 사용 패턴",
                        "[MOCK] 취침 전 사용 시간 조율 필요",
                        "[MOCK] 주말 여가 사용 시간 관리"
                ))
                .feedback(Feedback.builder()
                        .toParent("아이가 스스로 사용 시간을 잘 지키고 있습니다. 칭찬해 주세요!")
                        .toChild("이번 주에도 스마트폰을 규칙적으로 잘 썼네! 최고야!")
                        .build())
                .policyRecommendList(List.of(
                        PolicyRecommend.builder()
                                .title("취침 전 30분 디지털 프리")
                                .description("취침 전에는 눈의 휴식을 위해 스마트폰을 내려놓아요.")
                                .reason("심야 시간대 블루라이트 노출을 줄이기 위함입니다.")
                                .build()
                ))
                .build();
    }
}
