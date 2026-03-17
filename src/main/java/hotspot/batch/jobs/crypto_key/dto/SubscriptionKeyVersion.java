package hotspot.batch.jobs.crypto_key.dto;

public record SubscriptionKeyVersion(
        Integer bucketId,
        Integer keyVersion,
        String encryptedDek,
        String kekKeyId,
        String status
) {
}
