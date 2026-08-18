package likelion.flourishing.domain.onboarding.entity;

/**
 * user_consents.consent_type에 저장하는 동의 종류.
 *
 * <p>명세 v2_1의 온보딩은 동의 2건을 받는다. {@link #SENSITIVE_DATA}는 항상 필수이고,
 * {@link #NOTIFICATION}은 알림을 켜는 경우에만 필수다. 알림을 받지 않겠다고 한 사용자는
 * 동의한 적이 없으므로 행을 남기지 않는다. DDL의 CHECK가 accepted = TRUE만 허용해서이기도 하다.
 *
 * <p>{@link #SERVICE_SCOPE}는 DDL이 허용하는 값이라 함께 정의해 두고, 별도 동의 항목이 요청에
 * 추가되면 그때 사용한다.
 */
public enum ConsentType {
    SERVICE_SCOPE,
    SENSITIVE_DATA,
    NOTIFICATION
}
