package hotspot.batch.jobs.crypto_key.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crypto-key")
public record CryptoKeyRotationProperties(
        int chunkSize,
        int gridSize,
        String encryptionProvider,
        String secretKey,
        String kmsKeyId,
        String awsRegion
) {
}
