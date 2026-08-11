package likelion.flourishing.domain.onboarding.entity;

/**
 * user_consents.consent_type에 저장하는 필수 동의 종류.
 *
 * <p>온보딩 요청은 민감정보 동의 하나만 받으므로 지금은 {@link #SENSITIVE_DATA}만 기록한다.
 * {@link #SERVICE_SCOPE}는 DDL이 허용하는 값이라 함께 정의해 두고, 별도 동의 항목이 요청에
 * 추가되면 그때 사용한다.
 */
public enum ConsentType {
    SERVICE_SCOPE,
    SENSITIVE_DATA
}
