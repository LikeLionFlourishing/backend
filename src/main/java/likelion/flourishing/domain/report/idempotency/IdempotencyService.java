package likelion.flourishing.domain.report.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key로 같은 요청의 재전송을 걸러 내는 공통 처리.
 *
 * <p>네트워크가 끊겨 같은 요청을 다시 보낸 것과 값을 바꿔 보낸 것을 본문 해시로 가른다.
 * 같으면 처음 만든 응답을 그대로 돌려주고, 다르면 409로 막는다. AI 호출과 결과 생성이 붙어 있는
 * 작업이라 두 번 실행되면 사용자에게 다른 결과가 두 개 생긴다.
 *
 * <p>저장은 결과를 만든 트랜잭션 안에서 한다. 결과가 롤백되면 응답 기록도 함께 사라져야 하고,
 * 그래야 다음 재전송이 없는 결과를 가리키지 않는다.
 *
 * <p>지금은 보고 관련 작업만 쓰지만 operationId로 작업을 구분하므로 다른 도메인도 그대로 쓸 수 있다.
 */
@Service
public class IdempotencyService {

    /** DDL 주석과 같은 보관 기간. 지나면 같은 키를 새 요청에 다시 쓸 수 있다. */
    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final IdempotencyPayloadCipher payloadCipher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(
            IdempotencyRecordRepository idempotencyRecordRepository,
            IdempotencyPayloadCipher payloadCipher,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.payloadCipher = payloadCipher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 저장된 응답을 찾는다.
     *
     * <p>같은 키에 다른 본문이면 {@link ErrorCode#IDEMPOTENCY_KEY_REUSED}를 던진다. 보관 기간이 지난
     * 기록은 지우고 없는 것으로 다룬다. 남겨 두면 유니크 제약 때문에 그 키를 영구히 못 쓴다.
     *
     * @param requestFingerprint 본문을 정규화한 값. 의미가 같은 요청이면 같은 값이어야 한다.
     */
    @Transactional
    public Optional<IdempotentResponse> findReplay(
            UUID userId,
            String operationId,
            UUID idempotencyKey,
            Object requestFingerprint
    ) {
        Optional<IdempotencyRecord> found = idempotencyRecordRepository
                .findByUserIdAndOperationIdAndIdempotencyKey(userId, operationId, idempotencyKey);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecord record = found.get();
        if (record.isExpiredAt(LocalDateTime.now(clock))) {
            idempotencyRecordRepository.delete(record);
            idempotencyRecordRepository.flush();
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(record.getRequestHash(), fingerprint(requestFingerprint))) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return Optional.of(IdempotentResponse.replay(
                record.getResponseStatus(),
                payloadCipher.decrypt(record.getResponseBodyEncrypted()),
                record.getResourceId()
        ));
    }

    /** 새로 만든 응답을 저장한다. */
    @Transactional
    public void store(
            UUID userId,
            String operationId,
            UUID idempotencyKey,
            Object requestFingerprint,
            IdempotentResponse response
    ) {
        idempotencyRecordRepository.save(IdempotencyRecord.of(
                userId,
                operationId,
                idempotencyKey,
                fingerprint(requestFingerprint),
                response.status(),
                payloadCipher.encrypt(response.jsonBody()),
                response.resourceId(),
                LocalDateTime.now(clock).plus(RETENTION)
        ));
    }

    /** DTO를 JSON 문자열로 만든다. 저장하는 본문과 응답으로 나가는 본문을 같게 하기 위해 한곳에 둔다. */
    public String serialize(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("응답 본문을 직렬화하지 못했습니다.", exception);
        }
    }

    /**
     * 본문 해시. 원문이 담긴 본문을 그대로 저장하지 않기 위해 SHA-256으로 줄인다.
     *
     * <p>넘어오는 값은 호출한 쪽이 정규화한 객체다. 다중 선택의 순서만 다른 요청을 다른 본문으로
     * 보면 같은 뜻의 재전송이 409가 되므로, 정규화는 호출하는 서비스가 책임진다.
     */
    private byte[] fingerprint(Object requestFingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(serialize(requestFingerprint).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("요청 해시를 계산하지 못했습니다.", exception);
        }
    }
}
