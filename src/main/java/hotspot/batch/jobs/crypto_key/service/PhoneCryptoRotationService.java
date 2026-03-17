package hotspot.batch.jobs.crypto_key.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import hotspot.batch.jobs.crypto_key.config.CryptoKeyRotationProperties;
import hotspot.batch.jobs.crypto_key.dto.SubscriptionKeyVersion;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;

@Service
@Slf4j
public class PhoneCryptoRotationService {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^01(?:0|1|[6-9])(?:\\d{3}|\\d{4})\\d{4}$");
    private static final String GCM_PREFIX = "gcm:";
    private static final String PROVIDER_KMS = "kms";
    private static final int GCM_NONCE_SIZE = 12;
    private static final int GCM_TAG_SIZE = 16;
    private static final int CBC_IV_SIZE = 16;
    private static final int DEK_LENGTH = 32;

    private final CryptoKeyRotationProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile KmsClient kmsClient;

    public PhoneCryptoRotationService(CryptoKeyRotationProperties properties) {
        this.properties = properties;
    }

    public GeneratedDek generateNewDek() {
        if (isKmsProvider()) {
            GenerateDataKeyRequest request = GenerateDataKeyRequest.builder()
                    .keyId(properties.kmsKeyId())
                    .keySpec("AES_256")
                    .build();
            var response = getKmsClient().generateDataKey(request);
            return new GeneratedDek(
                    response.plaintext().asByteArray(),
                    Base64.getEncoder().encodeToString(response.ciphertextBlob().asByteArray()),
                    properties.kmsKeyId());
        }

        byte[] dek = new byte[DEK_LENGTH];
        secureRandom.nextBytes(dek);
        return new GeneratedDek(dek, encryptPayload(dek, localSecretKey()), properties.kmsKeyId());
    }

    public byte[] unwrapDek(SubscriptionKeyVersion keyVersion) {
        if (isKmsProvider()) {
            DecryptRequest.Builder builder = DecryptRequest.builder()
                    .ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(keyVersion.encryptedDek())));
            String keyId = Optional.ofNullable(keyVersion.kekKeyId())
                    .filter(value -> !value.isBlank())
                    .orElse(properties.kmsKeyId());
            if (keyId != null && !keyId.isBlank()) {
                builder.keyId(keyId);
            }
            return getKmsClient().decrypt(builder.build()).plaintext().asByteArray();
        }

        return decryptPayloadToBytes(keyVersion.encryptedDek(), localSecretKey());
    }

    public String decryptPhone(String phoneEnc, byte[] dek) {
        byte[] plain = decryptPayloadToBytes(phoneEnc, dek);
        return normalizePhone(new String(plain, StandardCharsets.UTF_8));
    }

    public String encryptPhone(String phone, byte[] dek) {
        return encryptPayload(normalizePhone(phone), dek);
    }

    private boolean isKmsProvider() {
        return PROVIDER_KMS.equalsIgnoreCase(properties.encryptionProvider());
    }

    private KmsClient getKmsClient() {
        KmsClient current = kmsClient;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (kmsClient == null) {
                var builder = KmsClient.builder();
                if (properties.awsRegion() != null && !properties.awsRegion().isBlank()) {
                    builder.region(Region.of(properties.awsRegion()));
                }
                kmsClient = builder.build();
            }
            return kmsClient;
        }
    }

    private byte[] localSecretKey() {
        String secretKey = properties.secretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("SECRET_KEY is required for local encryption provider");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(secretKey);
            if (decoded.length == DEK_LENGTH) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            log.debug("SECRET_KEY is not base64 encoded. falling back to raw bytes length check.");
        }

        byte[] raw = secretKey.getBytes(StandardCharsets.UTF_8);
        if (raw.length != DEK_LENGTH) {
            throw new IllegalStateException("SECRET_KEY must be 32 bytes for local encryption provider");
        }
        return raw;
    }

    private String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new IllegalStateException("Phone must not be blank");
        }

        String digits = rawPhone.replaceAll("\\D", "");
        if (!MOBILE_PATTERN.matcher(digits).matches()) {
            throw new IllegalStateException("Invalid phone number format");
        }

        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
    }

    private String encryptPayload(String plainText, byte[] key) {
        return encryptPayload(plainText.getBytes(StandardCharsets.UTF_8), key);
    }

    private String encryptPayload(byte[] plainBytes, byte[] key) {
        try {
            byte[] nonce = new byte[GCM_NONCE_SIZE];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_SIZE * 8, nonce));
            byte[] encrypted = cipher.doFinal(plainBytes);

            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);

            return GCM_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt payload", e);
        }
    }

    private byte[] decryptPayloadToBytes(String payload, byte[] key) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalStateException("Encrypted payload must not be blank");
        }

        try {
            if (payload.startsWith(GCM_PREFIX)) {
                byte[] decoded = Base64.getDecoder().decode(payload.substring(GCM_PREFIX.length()));
                byte[] nonce = Arrays.copyOfRange(decoded, 0, GCM_NONCE_SIZE);
                byte[] ciphertextAndTag = Arrays.copyOfRange(decoded, GCM_NONCE_SIZE, decoded.length);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_SIZE * 8, nonce));
                return cipher.doFinal(ciphertextAndTag);
            }

            byte[] raw = Base64.getDecoder().decode(payload);
            byte[] iv = Arrays.copyOfRange(raw, 0, CBC_IV_SIZE);
            byte[] cipherText = Arrays.copyOfRange(raw, CBC_IV_SIZE, raw.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt payload", e);
        }
    }

    public record GeneratedDek(
            byte[] plainDek,
            String encryptedDek,
            String kekKeyId
    ) {
    }
}
