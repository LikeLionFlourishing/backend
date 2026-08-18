package likelion.flourishing.domain.report.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.report.crypto.RecordCryptoProperties;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 멱등 처리 테스트. 암호화는 실제 구현을 쓰고 저장소만 가짜로 둔다.
 *
 * <p>확인하는 것: 같은 본문 재전송이 저장된 상태 코드와 본문을 그대로 돌려주는지, 같은 키에 다른
 * 본문이 오면 409를 내는지, 보관 기간이 지난 기록은 지우고 새로 처리하게 하는지, 저장한 응답
 * 본문이 평문으로 남지 않는지.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID KEY = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID RESOURCE_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000001");
    private static final String OPERATION_ID = "POST /v1/skin-reports";
    private static final Instant NOW = Instant.parse("2026-08-15T03:00:00Z");
    private static final String RESPONSE_BODY = "{\"id\":\"0198a31f-f33f-7000-8000-000000000001\"}";

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private IdempotencyPayloadCipher payloadCipher;
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        payloadCipher = new IdempotencyPayloadCipher(new RecordCryptoProperties(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        ));
        idempotencyService = new IdempotencyService(
                idempotencyRecordRepository,
                payloadCipher,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void noRecordMeansFreshRequest() {
        when(idempotencyRecordRepository.findByUserIdAndOperationIdAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThat(idempotencyService.findReplay(USER_ID, OPERATION_ID, KEY, fingerprint("A"))).isEmpty();
    }

    @Test
    void sameBodyReplaysStoredStatusAndBody() {
        when(idempotencyRecordRepository.findByUserIdAndOperationIdAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.of(storedRecord(fingerprint("A"))));

        Optional<IdempotentResponse> replay = idempotencyService
                .findReplay(USER_ID, OPERATION_ID, KEY, fingerprint("A"));

        assertThat(replay).isPresent();
        assertThat(replay.get().status()).isEqualTo(201);
        assertThat(replay.get().jsonBody()).isEqualTo(RESPONSE_BODY);
        assertThat(replay.get().resourceId()).isEqualTo(RESOURCE_ID);
        assertThat(replay.get().replayed()).isTrue();
    }

    @Test
    void differentBodyOnTheSameKeyIsRejected() {
        when(idempotencyRecordRepository.findByUserIdAndOperationIdAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.of(storedRecord(fingerprint("A"))));

        assertThatThrownBy(() -> idempotencyService.findReplay(USER_ID, OPERATION_ID, KEY, fingerprint("B")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    /** 보관 기간이 지난 기록을 남기면 유니크 제약 때문에 그 키를 영구히 못 쓴다. */
    @Test
    void expiredRecordIsRemovedAndTreatedAsFresh() {
        IdempotencyRecord expired = IdempotencyRecord.of(
                USER_ID,
                OPERATION_ID,
                KEY,
                new byte[32],
                201,
                payloadCipher.encrypt(RESPONSE_BODY),
                RESOURCE_ID,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(1)
        );
        when(idempotencyRecordRepository.findByUserIdAndOperationIdAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.of(expired));

        assertThat(idempotencyService.findReplay(USER_ID, OPERATION_ID, KEY, fingerprint("A"))).isEmpty();
        verify(idempotencyRecordRepository).delete(expired);
    }

    @Test
    void storedBodyIsEncryptedAndExpiresInTwentyFourHours() {
        idempotencyService.store(
                USER_ID,
                OPERATION_ID,
                KEY,
                fingerprint("A"),
                IdempotentResponse.created(RESPONSE_BODY, RESOURCE_ID)
        );

        ArgumentCaptor<IdempotencyRecord> saved = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository).save(saved.capture());
        IdempotencyRecord record = saved.getValue();

        assertThat(new String(record.getResponseBodyEncrypted())).doesNotContain("0198a31f");
        assertThat(payloadCipher.decrypt(record.getResponseBodyEncrypted())).isEqualTo(RESPONSE_BODY);
        assertThat(record.getRequestHash()).hasSize(32);
        assertThat(record.getResponseStatus()).isEqualTo((short) 201);
        assertThat(record.getResourceId()).isEqualTo(RESOURCE_ID);
        assertThat(record.getExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(24));
    }

    private IdempotencyRecord storedRecord(Object requestFingerprint) {
        return IdempotencyRecord.of(
                USER_ID,
                OPERATION_ID,
                KEY,
                hashOf(requestFingerprint),
                201,
                payloadCipher.encrypt(RESPONSE_BODY),
                RESOURCE_ID,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(1)
        );
    }

    /** 서비스와 같은 방식으로 본문 해시를 만든다. 정규화한 객체를 직렬화해 SHA-256을 취한다. */
    private byte[] hashOf(Object requestFingerprint) {
        try {
            String json = new ObjectMapper().writeValueAsString(requestFingerprint);
            return MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Object fingerprint(String rawText) {
        return new TestFingerprint(rawText, List.of("REDNESS"));
    }

    private record TestFingerprint(String rawText, List<String> appearances) {
    }
}
