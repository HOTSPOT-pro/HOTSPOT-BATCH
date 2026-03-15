package hotspot.batch.jobs.llm_feedback.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@RequiredArgsConstructor
@EnableConfigurationProperties(LlmProperties.class) // LlmProperties 활성화
public class LlmConfig {

    private final LlmProperties properties;

    @Bean
    public WebClient llmWebClient() {
        var clientProps = properties.client();

        // 비동기 동시 요청을 충분히 수용하기 위한 Connection Pool 설정
        ConnectionProvider provider = ConnectionProvider.builder("llm-connection-provider")
                .maxConnections(clientProps.maxConnections())
                .pendingAcquireMaxCount(clientProps.pendingAcquireMaxCount())
                .pendingAcquireTimeout(Duration.ofMillis(clientProps.readTimeoutMillis()))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientProps.connectTimeoutMillis())
                .responseTimeout(Duration.ofMillis(clientProps.readTimeoutMillis()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(clientProps.readTimeoutMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(clientProps.readTimeoutMillis(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://api.openai.com")
                .build();
    }
}
