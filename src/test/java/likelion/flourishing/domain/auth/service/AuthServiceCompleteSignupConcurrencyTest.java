package likelion.flourishing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import likelion.flourishing.domain.auth.entity.User;
import likelion.flourishing.domain.auth.repository.UserRepository;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 온보딩 완료 요청이 겹쳤을 때 최초 가입 완료 시각이 남는지 실제 DB 트랜잭션 두 개로 확인한다.
 *
 * <p>가짜 객체로는 검증할 수 없는 부분이다. 두 요청이 각자의 트랜잭션에서 같은 행을 읽고 쓰는
 * 상황 자체가 검증 대상이라 H2를 띄우고 스레드를 나눈다.
 *
 * <p>첫 요청이 잠금을 쥔 채 잠시 커밋을 미루는 동안 두 번째 요청을 보낸다. 잠금이 없으면 두 번째
 * 요청이 아직 비어 있는 signup_completed_at을 읽고 자기 시각을 써서 최초 시각이 사라진다.
 * 다른 테스트의 H2와 섞이지 않도록 이 클래스만 별도 인메모리 DB를 쓴다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:completesignup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000"
})
@ActiveProfiles("test")
class AuthServiceCompleteSignupConcurrencyTest {

    private static final LocalDateTime FIRST_TIME = LocalDateTime.of(2026, 8, 15, 9, 0);
    private static final LocalDateTime SECOND_TIME = LocalDateTime.of(2026, 8, 15, 9, 0, 30);

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID userId;

    @BeforeEach
    void saveUser() {
        userId = transactionTemplate.execute(status ->
                userRepository.saveAndFlush(User.register("soldier@example.com", "hashed")).getId());
    }

    @AfterEach
    void deleteUser() {
        transactionTemplate.executeWithoutResult(status -> userRepository.deleteAll());
    }

    @Test
    void overlappingRequestsKeepTheFirstCompletionTime() throws Exception {
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<LocalDateTime> first = executor.submit(() -> transactionTemplate.execute(status -> {
                LocalDateTime stamped = authService.completeSignup(principal(), FIRST_TIME);
                firstHoldsLock.countDown();
                holdTransactionOpen();
                return stamped;
            }));

            assertThat(firstHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();
            LocalDateTime second = transactionTemplate.execute(status ->
                    authService.completeSignup(principal(), SECOND_TIME));

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(FIRST_TIME);
            assertThat(second).isEqualTo(FIRST_TIME);
        } finally {
            executor.shutdownNow();
        }

        assertThat(userRepository.findById(userId).orElseThrow().getSignupCompletedAt())
                .isEqualTo(FIRST_TIME);
    }

    /** 두 번째 요청이 도착할 때까지 첫 트랜잭션을 열어 둔다. */
    private void holdTransactionOpen() {
        try {
            Thread.sleep(400);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                userId,
                UUID.randomUUID(),
                LocalDateTime.of(2026, 8, 29, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
    }
}
