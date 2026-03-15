package hotspot.batch.jobs.llm_feedback.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * LLM API 통신을 위한 WebClient 설정
 */
@Configuration
public class LlmConfig {

    private static final int TIMEOUT_MILLIS = 60000; // 60초 (LLM 응답 지연 대비)

    @Bean
    public WebClient llmWebClient() {
        // 비동기 동시 요청을 충분히 수용하기 위한 Connection Pool 설정
        ConnectionProvider provider = ConnectionProvider.builder("llm-connection-provider")
                .maxConnections(100)
                .pendingAcquireMaxCount(500)
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // Connect Timeout 5초
                .responseTimeout(Duration.ofMillis(TIMEOUT_MILLIS))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://api.openai.com") // 기본 URL (구현체에서 덮어쓰기 가능)
                .build();
    }
}
