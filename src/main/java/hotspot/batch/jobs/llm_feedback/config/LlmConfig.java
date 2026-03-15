package hotspot.batch.jobs.llm_feedback.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${llm.client.connect-timeout-millis}")
    private int connectTimeoutMillis;

    @Value("${llm.client.read-timeout-millis}")
    private int readTimeoutMillis;

    @Value("${llm.client.max-connections}")
    private int maxConnections;

    @Value("${llm.client.pending-acquire-max-count}")
    private int pendingAcquireMaxCount;

    @Bean
    public WebClient llmWebClient() {
        // 비동기 동시 요청을 충분히 수용하기 위한 Connection Pool 설정
        ConnectionProvider provider = ConnectionProvider.builder("llm-connection-provider")
                .maxConnections(maxConnections)
                .pendingAcquireMaxCount(pendingAcquireMaxCount)
                .pendingAcquireTimeout(Duration.ofMillis(readTimeoutMillis)) // 읽기 타임아웃과 동기화
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(Duration.ofMillis(readTimeoutMillis))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://api.openai.com") // 기본 URL (구현체에서 덮어쓰기 가능)
                .build();
    }
}
