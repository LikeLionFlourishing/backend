-- 관리하는 행보관 MySQL DDL
-- 작성일: 2026-08-09
-- 대상: MySQL 8.0.19 이상 / InnoDB / utf8mb4
--       (8.0.16 CHECK 제약, 8.0.19 db/seed의 INSERT ... AS new 별칭 문법)
-- ORM: 미정
--
-- 중요:
-- 1. 모든 BINARY(16) ID는 애플리케이션에서 UUIDv7을 생성해 UUID_TO_BIN(?, 1)로 저장합니다.
-- 2. 모든 DATETIME(6)은 UTC로 저장하고 일일 정책만 Asia/Seoul로 계산합니다.
-- 3. raw_text_encrypted, Push 구독 비밀, 멱등 응답은 애플리케이션에서 AEAD 암호화한 바이트입니다.
-- 4. 현재 관리규칙 v0.1은 전문가 검토 전이므로 REVIEW_REQUIRED 상태로만 적재합니다.
-- 5. ACTIVE 규칙 세트만 신규 관리 결과 생성에 사용할 수 있습니다.
-- 6. 다른 사용자 데이터 접근 방지는 모든 저장소 쿼리의 user_id 조건과 통합 테스트로 강제합니다.

CREATE DATABASE IF NOT EXISTS flourishing
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE flourishing;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- -----------------------------------------------------------------------------
-- 사용자와 인증
-- -----------------------------------------------------------------------------

CREATE TABLE users (
    id BINARY(16) NOT NULL,
    email VARCHAR(254) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    signup_completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_normalized_email UNIQUE (normalized_email),
    CONSTRAINT ck_users_email_not_blank CHECK (CHAR_LENGTH(TRIM(email)) > 0),
    CONSTRAINT ck_users_normalized_email_not_blank
        CHECK (CHAR_LENGTH(TRIM(normalized_email)) > 0),
    CONSTRAINT ck_users_password_hash_not_blank
        CHECK (CHAR_LENGTH(TRIM(password_hash)) > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '이메일·비밀번호 사용자 계정';

CREATE TABLE user_sessions (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    session_token_hash BINARY(32) NOT NULL,
    csrf_secret_hash BINARY(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT uq_user_sessions_token_hash UNIQUE (session_token_hash),
    CONSTRAINT fk_user_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_user_sessions_expiry
        CHECK (expires_at > created_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'HttpOnly 쿠키용 서버 세션';

CREATE INDEX ix_user_sessions_user_expires
    ON user_sessions (user_id, expires_at);

CREATE TABLE user_consents (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    consent_version VARCHAR(50) NOT NULL,
    consent_type VARCHAR(40) NOT NULL,
    accepted BOOLEAN NOT NULL,
    consented_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_user_consents PRIMARY KEY (id),
    CONSTRAINT uq_user_consents_version
        UNIQUE (user_id, consent_type, consent_version),
    CONSTRAINT fk_user_consents_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_user_consents_type
        CHECK (consent_type IN ('SERVICE_SCOPE', 'SENSITIVE_DATA', 'NOTIFICATION')),
    -- 동의하지 않은 사실은 행을 남기지 않는 것으로 표현한다. 거절을 저장하지 않으므로
    -- NOTIFICATION 동의도 이 CHECK를 그대로 지킨다.
    CONSTRAINT ck_user_consents_required_accepted
        CHECK (accepted = TRUE)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '버전별 동의 이력';

CREATE INDEX ix_user_consents_user_consented
    ON user_consents (user_id, consented_at DESC);

CREATE TABLE notification_settings (
    user_id BINARY(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- CHAR(5)에서 옮겼다. 이 스키마의 다른 문자열 컬럼과 표기를 맞추고,
    -- CHAR의 공백 패딩이 문자열 비교에 끼어들지 않게 한다.
    notification_time VARCHAR(5) NOT NULL DEFAULT '17:30',
    timezone VARCHAR(40) NOT NULL DEFAULT 'Asia/Seoul',
    permission_state VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_notification_settings PRIMARY KEY (user_id),
    CONSTRAINT fk_notification_settings_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    -- 명세 v2_1에서 발송 시각이 17:30 고정에서 온보딩 시간 피커 값으로 바뀌어
    -- ck_notification_settings_time(= '17:30')을 뺐다.
    -- HH:mm 형식은 care_rules.rule_code와 같은 이유로 애플리케이션 입력 검증에서 강제한다.
    -- DB에서는 버전별 CHECK 함수 호환성을 고려해 형식 검사를 두지 않는다.
    CONSTRAINT ck_notification_settings_timezone
        CHECK (timezone = 'Asia/Seoul'),
    CONSTRAINT ck_notification_settings_permission
        CHECK (permission_state IN ('DEFAULT', 'GRANTED', 'DENIED', 'UNSUPPORTED'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자별 알림 설정';

-- 발송 대상 조회가 쓰는 인덱스. 매 분 도는 조회라 두 컬럼을 함께 건다.
-- enabled 를 앞에 둔 것은 그쪽 선택도가 먼저 걸리기 때문이다.
CREATE INDEX ix_notification_settings_dispatch
    ON notification_settings (enabled, notification_time);

-- -----------------------------------------------------------------------------
-- 관리규칙 정의와 승인
-- -----------------------------------------------------------------------------

CREATE TABLE care_rules (
    id BINARY(16) NOT NULL,
    rule_code VARCHAR(20) NOT NULL,
    category VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_care_rules PRIMARY KEY (id),
    CONSTRAINT uq_care_rules_code UNIQUE (rule_code),
    -- 관리규칙 v0.3의 9개 prefix에 대응한다.
    -- CR=COMMON, ENV=ENVIRONMENT, SIT=SITUATION, APP=APPEARANCE, ST=CURRENT_STATE,
    -- SAF=SAFETY, HR=HISTORY, ING=INGREDIENT, FALLBACK=FALLBACK
    CONSTRAINT ck_care_rules_category
        CHECK (
            category IN (
                'COMMON', 'ENVIRONMENT', 'SITUATION', 'APPEARANCE', 'CURRENT_STATE',
                'SAFETY', 'HISTORY', 'INGREDIENT', 'FALLBACK'
            )
        ),
    -- rule_code 형식(예: CR-001)은 애플리케이션 입력 검증에서 강제한다.
    -- DB에서는 버전별 CHECK 함수 호환성을 고려해 유일성만 보장한다.
    CONSTRAINT ck_care_rules_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CR-001 등 안정적인 규칙 식별자';

CREATE TABLE rule_sets (
    id BINARY(16) NOT NULL,
    version_code VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REVIEW_REQUIRED',
    approved_at DATETIME(6) NULL,
    approved_by_user_id BINARY(16) NULL COMMENT '외부 운영자 식별자; 앱 users FK 아님',
    activated_at DATETIME(6) NULL,
    retired_at DATETIME(6) NULL,
    active_guard TINYINT
        GENERATED ALWAYS AS (
            CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END
        ) STORED,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_rule_sets PRIMARY KEY (id),
    CONSTRAINT uq_rule_sets_version UNIQUE (version_code),
    CONSTRAINT uq_rule_sets_single_active UNIQUE (active_guard),
    CONSTRAINT ck_rule_sets_status
        CHECK (status IN ('REVIEW_REQUIRED', 'APPROVED', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_rule_sets_approval
        CHECK (
            (status = 'REVIEW_REQUIRED'
                AND approved_at IS NULL
                AND approved_by_user_id IS NULL
                AND activated_at IS NULL)
            OR
            (status IN ('APPROVED', 'ACTIVE', 'RETIRED')
                AND approved_at IS NOT NULL
                AND approved_by_user_id IS NOT NULL)
        ),
    CONSTRAINT ck_rule_sets_activation
        CHECK (
            (status <> 'ACTIVE' OR activated_at IS NOT NULL)
            AND (status <> 'RETIRED' OR retired_at IS NOT NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '함께 배포되는 관리규칙 버전 묶음';

CREATE INDEX ix_rule_sets_status
    ON rule_sets (status, created_at DESC);

CREATE TABLE care_rule_versions (
    id BINARY(16) NOT NULL,
    rule_id BINARY(16) NOT NULL,
    rule_set_id BINARY(16) NOT NULL,
    version_code VARCHAR(20) NOT NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'REVIEW_REQUIRED',
    application_summary TEXT NOT NULL,
    exclusion_summary TEXT NULL,
    forbidden_expressions TEXT NULL,
    fallback_text TEXT NULL,
    priority INT NOT NULL DEFAULT 100,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_care_rule_versions PRIMARY KEY (id),
    CONSTRAINT uq_care_rule_versions_set_rule UNIQUE (rule_set_id, rule_id),
    CONSTRAINT uq_care_rule_versions_rule_version UNIQUE (rule_id, version_code),
    CONSTRAINT fk_care_rule_versions_rule
        FOREIGN KEY (rule_id) REFERENCES care_rules (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_care_rule_versions_set
        FOREIGN KEY (rule_set_id) REFERENCES rule_sets (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_care_rule_versions_status
        CHECK (review_status IN ('REVIEW_REQUIRED', 'APPROVED', 'RETIRED')),
    CONSTRAINT ck_care_rule_versions_priority
        CHECK (priority BETWEEN 1 AND 10000),
    CONSTRAINT ck_care_rule_versions_application_not_blank
        CHECK (CHAR_LENGTH(TRIM(application_summary)) > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '수정하지 않는 개별 관리규칙 버전';

CREATE INDEX ix_care_rule_versions_rule
    ON care_rule_versions (rule_id);

CREATE INDEX ix_care_rule_versions_set_status_priority
    ON care_rule_versions (rule_set_id, review_status, priority);

CREATE TABLE rule_conditions (
    id BINARY(16) NOT NULL,
    rule_version_id BINARY(16) NOT NULL,
    condition_group INT NOT NULL DEFAULT 1,
    field_code VARCHAR(40) NOT NULL,
    operator_code VARCHAR(30) NOT NULL,
    value_code VARCHAR(100) NOT NULL,
    negated BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_rule_conditions PRIMARY KEY (id),
    CONSTRAINT uq_rule_conditions_definition
        UNIQUE (
            rule_version_id,
            condition_group,
            field_code,
            operator_code,
            value_code,
            negated
        ),
    CONSTRAINT fk_rule_conditions_version
        FOREIGN KEY (rule_version_id) REFERENCES care_rule_versions (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_rule_conditions_group
        CHECK (condition_group >= 1),
    CONSTRAINT ck_rule_conditions_field
        CHECK (
            field_code IN (
                'primaryArea',
                'appearances',
                'sensations',
                'situations',
                'careAvailability',
                'preCareChecks',
                'completedHistory',
                -- 온보딩에서 1회 설정하는 예상 환경(ENV-*). 값이 없으면 환경 보정 없이 진행한다.
                'environments'
            )
        ),
    CONSTRAINT ck_rule_conditions_operator
        CHECK (
            operator_code IN (
                'EQUALS',
                'NOT_EQUALS',
                'CONTAINS',
                'CONTAINS_ANY',
                'NOT_CONTAINS_ANY',
                'EXISTS'
            )
        ),
    CONSTRAINT ck_rule_conditions_display_order
        CHECK (display_order >= 1)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '규칙 엔진이 평가하는 구조화 조건';

CREATE INDEX ix_rule_conditions_version
    ON rule_conditions (rule_version_id);

CREATE INDEX ix_rule_conditions_match
    ON rule_conditions (field_code, value_code, rule_version_id);

CREATE TABLE rule_actions (
    id BINARY(16) NOT NULL,
    rule_version_id BINARY(16) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    display_order INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_rule_actions PRIMARY KEY (id),
    CONSTRAINT uq_rule_actions_order
        UNIQUE (rule_version_id, action_type, display_order),
    CONSTRAINT fk_rule_actions_version
        FOREIGN KEY (rule_version_id) REFERENCES care_rule_versions (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_rule_actions_type
        CHECK (
            action_type IN (
                'DO_TODAY',
                'AVOID_TODAY',
                'CHECK_NEXT',
                'CLINICIAN_MESSAGE',
                'FALLBACK'
            )
        ),
    CONSTRAINT ck_rule_actions_content_not_blank
        CHECK (CHAR_LENGTH(TRIM(content)) > 0),
    CONSTRAINT ck_rule_actions_priority
        CHECK (priority BETWEEN 1 AND 10000),
    CONSTRAINT ck_rule_actions_display_order
        CHECK (display_order >= 1)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '규칙이 허용하는 행동과 안내 문구';

CREATE INDEX ix_rule_actions_version_active_priority
    ON rule_actions (rule_version_id, active, priority, display_order);

-- -----------------------------------------------------------------------------
-- 추천 성분과 가이드 섹션 (명세 v2_1 개선 요약 7·8번)
-- -----------------------------------------------------------------------------

-- 관리 규칙표가 관리하는 성분 사전.
-- AI는 이 표에 없는 성분을 만들어 낼 수 없다. 특정 제품·브랜드·의약품은 담지 않는다.
CREATE TABLE care_ingredients (
    id BINARY(16) NOT NULL,
    ingredient_code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL,
    caution_note VARCHAR(300) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_care_ingredients PRIMARY KEY (id),
    CONSTRAINT uq_care_ingredients_code UNIQUE (ingredient_code),
    CONSTRAINT ck_care_ingredients_code_format
        CHECK (ingredient_code REGEXP '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_care_ingredients_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_care_ingredients_description_not_blank
        CHECK (CHAR_LENGTH(TRIM(description)) > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '관리 규칙표가 관리하는 추천 성분 사전';

-- 규칙 버전이 권하는 성분. 한 규칙이 여러 성분을, 한 성분이 여러 규칙에 걸릴 수 있다.
CREATE TABLE rule_version_ingredients (
    rule_version_id BINARY(16) NOT NULL,
    ingredient_id BINARY(16) NOT NULL,
    display_order INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_rule_version_ingredients PRIMARY KEY (rule_version_id, ingredient_id),
    CONSTRAINT uq_rule_version_ingredients_order
        UNIQUE (rule_version_id, display_order),
    CONSTRAINT fk_rule_version_ingredients_version
        FOREIGN KEY (rule_version_id) REFERENCES care_rule_versions (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_rule_version_ingredients_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES care_ingredients (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_rule_version_ingredients_order
        CHECK (display_order >= 1)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '규칙 버전이 권하는 성분';

CREATE INDEX ix_rule_version_ingredients_ingredient
    ON rule_version_ingredients (ingredient_id);

-- 결과 카드의 가이드 섹션 제목·설명.
-- 프론트가 문구를 하드코딩하지 않도록 서버가 준다. 결과가 없을 때도 화면을 그릴 수 있게
-- /v1/reference-data/skin-report-options 에서도 같은 값을 내보낸다.
--
-- 결과에 스냅샷하지 않는 이유: 항목 문구와 달리 섹션 제목은 편집상의 표현이라, 나중에 다듬은
-- 문구가 과거 결과에도 반영되는 편이 자연스럽다. 사용자에게 안내한 내용 자체는
-- care_result_items 와 care_result_ingredients 가 스냅샷으로 붙잡는다.
CREATE TABLE guide_sections (
    section_key VARCHAR(30) NOT NULL,
    title VARCHAR(50) NOT NULL,
    description VARCHAR(300) NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_guide_sections PRIMARY KEY (section_key),
    CONSTRAINT uq_guide_sections_order UNIQUE (display_order),
    CONSTRAINT ck_guide_sections_key
        CHECK (
            section_key IN (
                'CURRENT_SUMMARY',
                'DO_TODAY',
                'AVOID_TODAY',
                'SIMILAR_EXPERIENCE',
                'CHECK_NEXT',
                'RECOMMENDED_INGREDIENTS'
            )
        ),
    CONSTRAINT ck_guide_sections_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_guide_sections_description_not_blank
        CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT ck_guide_sections_order
        CHECK (display_order BETWEEN 1 AND 6)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '결과 카드 가이드 섹션의 제목과 설명';

CREATE TABLE rule_evidence_sources (
    id BINARY(16) NOT NULL,
    rule_version_id BINARY(16) NOT NULL,
    title VARCHAR(300) NOT NULL,
    source_url VARCHAR(2048) NULL,
    source_type VARCHAR(40) NOT NULL,
    reviewed_at DATETIME(6) NULL,
    reviewed_by VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_rule_evidence_sources PRIMARY KEY (id),
    CONSTRAINT fk_rule_evidence_sources_version
        FOREIGN KEY (rule_version_id) REFERENCES care_rule_versions (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_rule_evidence_sources_type
        CHECK (source_type IN ('GUIDELINE', 'JOURNAL', 'PUBLIC_HEALTH', 'USER_RECORD', 'OTHER')),
    CONSTRAINT ck_rule_evidence_sources_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_rule_evidence_sources_review_pair
        CHECK (
            (reviewed_at IS NULL AND reviewed_by IS NULL)
            OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '관리규칙 근거 출처와 검토 상태';

CREATE INDEX ix_rule_evidence_sources_version
    ON rule_evidence_sources (rule_version_id);

CREATE TABLE rule_dependencies (
    id BINARY(16) NOT NULL,
    rule_version_id BINARY(16) NOT NULL,
    required_rule_id BINARY(16) NOT NULL,
    dependency_type VARCHAR(30) NOT NULL,
    application_order INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_rule_dependencies PRIMARY KEY (id),
    CONSTRAINT uq_rule_dependencies_definition
        UNIQUE (rule_version_id, required_rule_id, dependency_type),
    CONSTRAINT fk_rule_dependencies_version
        FOREIGN KEY (rule_version_id) REFERENCES care_rule_versions (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_rule_dependencies_required_rule
        FOREIGN KEY (required_rule_id) REFERENCES care_rules (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_rule_dependencies_type
        CHECK (dependency_type IN ('ALWAYS_WITH', 'BEFORE', 'AFTER', 'EXCLUDES')),
    CONSTRAINT ck_rule_dependencies_order
        CHECK (application_order >= 1)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '규칙 간 함께 적용·순서·배제 관계';

CREATE INDEX ix_rule_dependencies_version
    ON rule_dependencies (rule_version_id);

CREATE INDEX ix_rule_dependencies_required_rule
    ON rule_dependencies (required_rule_id);

-- -----------------------------------------------------------------------------
-- 피부 보고와 사용자 확정 선택값
-- -----------------------------------------------------------------------------

CREATE TABLE skin_reports (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    report_date DATE NOT NULL COMMENT 'Asia/Seoul 기준 날짜',
    raw_text_encrypted VARBINARY(4096) NOT NULL,
    primary_area VARCHAR(40) NOT NULL,
    other_areas_note_encrypted VARBINARY(4096) NULL,
    care_availability VARCHAR(50) NOT NULL,
    result_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'FOLLOW_UP_PENDING',
    follow_up_available_at DATETIME(6) NOT NULL,
    follow_up_expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_skin_reports PRIMARY KEY (id),
    CONSTRAINT uq_skin_reports_user_date UNIQUE (user_id, report_date),
    CONSTRAINT uq_skin_reports_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_skin_reports_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_skin_reports_primary_area
        CHECK (
            primary_area IN (
                'LEFT_FOREHEAD', 'CENTER_FOREHEAD', 'RIGHT_FOREHEAD',
                'NOSE', 'LEFT_CHEEK', 'RIGHT_CHEEK', 'AROUND_MOUTH',
                'LEFT_CHIN', 'RIGHT_CHIN', 'LEFT_JAWLINE',
                'RIGHT_JAWLINE', 'NECK', 'OTHER'
            )
        ),
    CONSTRAINT ck_skin_reports_care_availability
        CHECK (
            care_availability IN (
                'BEFORE_WASH_CAN_WASH_LATER',
                'ALREADY_WASHED',
                'CAN_CARE_BEFORE_SLEEP',
                'ADDITIONAL_CARE_DIFFICULT'
            )
        ),
    CONSTRAINT ck_skin_reports_result_type
        CHECK (result_type IN ('SELF_CARE_GUIDE', 'CLINICIAN_CHECK')),
    CONSTRAINT ck_skin_reports_status
        CHECK (status IN ('FOLLOW_UP_PENDING', 'COMPLETED', 'EXPIRED')),
    CONSTRAINT ck_skin_reports_follow_up_window
        CHECK (follow_up_expires_at > follow_up_available_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자가 최종 확정한 하루 한 건의 피부 보고';

CREATE INDEX ix_skin_reports_user_created_cursor
    ON skin_reports (user_id, created_at DESC, id DESC);

CREATE INDEX ix_skin_reports_user_pending_follow_up
    ON skin_reports (user_id, status, follow_up_available_at);

CREATE INDEX ix_skin_reports_user_completed_date
    ON skin_reports (user_id, status, report_date DESC);

CREATE TABLE report_appearances (
    report_id BINARY(16) NOT NULL,
    appearance_code VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_report_appearances PRIMARY KEY (report_id, appearance_code),
    CONSTRAINT fk_report_appearances_report
        FOREIGN KEY (report_id) REFERENCES skin_reports (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_report_appearances_code
        CHECK (
            appearance_code IN (
                'APP_REDNESS', 'APP_BUMP', 'APP_PUS_BUMP',
                'APP_DRYNESS', 'APP_OILINESS', 'APP_OTHER'
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '피부 보고의 겉모습 다중 선택';

CREATE INDEX ix_report_appearances_code_report
    ON report_appearances (appearance_code, report_id);

CREATE TABLE report_sensations (
    report_id BINARY(16) NOT NULL,
    sensation_code VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_report_sensations PRIMARY KEY (report_id, sensation_code),
    CONSTRAINT fk_report_sensations_report
        FOREIGN KEY (report_id) REFERENCES skin_reports (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_report_sensations_code
        CHECK (
            sensation_code IN (
                'REDNESS', 'EXCESS_SEBUM', 'BREAKOUT'
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '피부 보고의 느껴지는 불편 다중 선택';

CREATE INDEX ix_report_sensations_code_report
    ON report_sensations (sensation_code, report_id);

CREATE TABLE report_situations (
    report_id BINARY(16) NOT NULL,
    situation_code VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_report_situations PRIMARY KEY (report_id, situation_code),
    CONSTRAINT fk_report_situations_report
        FOREIGN KEY (report_id) REFERENCES skin_reports (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_report_situations_code
        CHECK (
            situation_code IN (
                'PROTECTIVE_GEAR_OR_MASK', 'SHAVING', 'SQUEEZED_ACNE',
                'NEW_PRODUCT', 'SWEAT_OR_SEBUM', 'NONE_RECALLED'
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '피부 불편 직전 상황 다중 선택';

CREATE INDEX ix_report_situations_code_report
    ON report_situations (situation_code, report_id);

CREATE TABLE report_pre_care_checks (
    report_id BINARY(16) NOT NULL,
    check_code VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_report_pre_care_checks PRIMARY KEY (report_id, check_code),
    CONSTRAINT fk_report_pre_care_checks_report
        FOREIGN KEY (report_id) REFERENCES skin_reports (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_report_pre_care_checks_code
        CHECK (
            check_code IN (
                'SPREADING_RAPIDLY',
                'SEVERE_PAIN_HEAT_SWELLING',
                'PUS_OOZING_BLISTER',
                'NONE'
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자가 최종 확인한 관리 전 확인';

-- -----------------------------------------------------------------------------
-- 관리 결과와 다음 날 경과
-- -----------------------------------------------------------------------------

CREATE TABLE care_results (
    id BINARY(16) NOT NULL,
    report_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    rule_set_id BINARY(16) NOT NULL,
    similar_report_id BINARY(16) NULL,
    similarity_score INT NULL,
    result_type VARCHAR(30) NOT NULL,
    ai_generation_status VARCHAR(30) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    clinician_message VARCHAR(1000) NULL,
    retry_used BOOLEAN NOT NULL DEFAULT FALSE,
    generated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_care_results PRIMARY KEY (id),
    CONSTRAINT uq_care_results_report UNIQUE (report_id),
    CONSTRAINT fk_care_results_report_owner
        FOREIGN KEY (report_id, user_id) REFERENCES skin_reports (id, user_id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_care_results_rule_set
        FOREIGN KEY (rule_set_id) REFERENCES rule_sets (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_care_results_similar_report
        FOREIGN KEY (similar_report_id) REFERENCES skin_reports (id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT ck_care_results_result_type
        CHECK (result_type IN ('SELF_CARE_GUIDE', 'CLINICIAN_CHECK')),
    CONSTRAINT ck_care_results_ai_status
        CHECK (ai_generation_status IN ('GENERATED', 'FALLBACK', 'NOT_APPLICABLE')),
    CONSTRAINT ck_care_results_type_payload
        CHECK (
            (
                result_type = 'SELF_CARE_GUIDE'
                AND clinician_message IS NULL
                AND ai_generation_status IN ('GENERATED', 'FALLBACK')
            )
            OR
            (
                result_type = 'CLINICIAN_CHECK'
                AND clinician_message IS NOT NULL
                AND ai_generation_status = 'NOT_APPLICABLE'
                AND retry_used = FALSE
            )
        ),
    -- similar_report_id는 fk_care_results_similar_report의 ON DELETE SET NULL 대상이므로
    -- MySQL 제약상 CHECK에 사용할 수 없다. 점수 하한만 DB에서 강제하고
    -- similar_report_id와 similarity_score의 짝 맞춤은 서비스 계층 규칙 G에서 보장한다.
    CONSTRAINT ck_care_results_similarity_score
        CHECK (similarity_score IS NULL OR similarity_score >= 5),
    CONSTRAINT ck_care_results_summary_not_blank
        CHECK (CHAR_LENGTH(TRIM(summary)) > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '피부 보고당 한 건의 저장된 결과';

CREATE INDEX ix_care_results_user
    ON care_results (user_id);

CREATE INDEX ix_care_results_report_owner
    ON care_results (report_id, user_id);

CREATE INDEX ix_care_results_rule_set
    ON care_results (rule_set_id);

CREATE INDEX ix_care_results_similar_report
    ON care_results (similar_report_id);

CREATE TABLE care_result_rules (
    care_result_id BINARY(16) NOT NULL,
    rule_version_id BINARY(16) NOT NULL,
    application_order INT NOT NULL,
    match_reason VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_care_result_rules PRIMARY KEY (care_result_id, rule_version_id),
    CONSTRAINT uq_care_result_rules_order UNIQUE (care_result_id, application_order),
    CONSTRAINT fk_care_result_rules_result
        FOREIGN KEY (care_result_id) REFERENCES care_results (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_care_result_rules_version
        FOREIGN KEY (rule_version_id) REFERENCES care_rule_versions (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_care_result_rules_order
        CHECK (application_order >= 1),
    CONSTRAINT ck_care_result_rules_reason
        CHECK (
            match_reason IN (
                'SAFETY', 'PROHIBITION', 'CURRENT_STATE',
                'SITUATION', 'COMMON', 'HISTORY',
                'ENVIRONMENT', 'APPEARANCE', 'INGREDIENT', 'FALLBACK'
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '결과에 실제 적용된 불변 규칙 버전';

CREATE INDEX ix_care_result_rules_version
    ON care_result_rules (rule_version_id);

CREATE TABLE care_result_items (
    id BINARY(16) NOT NULL,
    care_result_id BINARY(16) NOT NULL,
    source_rule_action_id BINARY(16) NULL,
    item_type VARCHAR(30) NOT NULL,
    content_snapshot VARCHAR(500) NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_care_result_items PRIMARY KEY (id),
    CONSTRAINT uq_care_result_items_order
        UNIQUE (care_result_id, item_type, display_order),
    CONSTRAINT fk_care_result_items_result
        FOREIGN KEY (care_result_id) REFERENCES care_results (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_care_result_items_source_action
        FOREIGN KEY (source_rule_action_id) REFERENCES rule_actions (id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT ck_care_result_items_type
        CHECK (
            item_type IN (
                'DO_TODAY', 'AVOID_TODAY', 'CHECK_NEXT', 'CLINICIAN_MESSAGE'
            )
        ),
    CONSTRAINT ck_care_result_items_content_not_blank
        CHECK (CHAR_LENGTH(TRIM(content_snapshot)) > 0),
    CONSTRAINT ck_care_result_items_order
        CHECK (display_order BETWEEN 1 AND 2)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자에게 표시한 결과 항목의 스냅샷';

CREATE INDEX ix_care_result_items_result_type_order
    ON care_result_items (care_result_id, item_type, display_order);

CREATE INDEX ix_care_result_items_source_action
    ON care_result_items (source_rule_action_id);

-- 사용자에게 보여 준 추천 성분의 스냅샷.
--
-- 성분 사전이 나중에 바뀌어도 과거 결과에 안내한 내용이 달라지지 않게 값을 복사해 둔다.
-- care_result_items 가 문구를 스냅샷하는 것과 같은 이유다.
--
-- 명세 maxItems 3 은 display_order CHECK 으로 강제한다. CLINICIAN_CHECK 결과에는
-- 성분을 주지 않으므로 이 표에 행이 생기지 않는다.
CREATE TABLE care_result_ingredients (
    id BINARY(16) NOT NULL,
    care_result_id BINARY(16) NOT NULL,
    source_ingredient_id BINARY(16) NULL,
    ingredient_code VARCHAR(100) NOT NULL,
    name_snapshot VARCHAR(100) NOT NULL,
    description_snapshot VARCHAR(300) NOT NULL,
    caution_note_snapshot VARCHAR(300) NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_care_result_ingredients PRIMARY KEY (id),
    CONSTRAINT uq_care_result_ingredients_order
        UNIQUE (care_result_id, display_order),
    CONSTRAINT uq_care_result_ingredients_code
        UNIQUE (care_result_id, ingredient_code),
    CONSTRAINT fk_care_result_ingredients_result
        FOREIGN KEY (care_result_id) REFERENCES care_results (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_care_result_ingredients_source
        FOREIGN KEY (source_ingredient_id) REFERENCES care_ingredients (id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT ck_care_result_ingredients_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(name_snapshot)) > 0),
    CONSTRAINT ck_care_result_ingredients_order
        CHECK (display_order BETWEEN 1 AND 3)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자에게 표시한 추천 성분의 스냅샷';

CREATE INDEX ix_care_result_ingredients_result_order
    ON care_result_ingredients (care_result_id, display_order);

-- 각 추천 성분을 내놓은 규칙. 명세 RecommendedIngredient.sourceRuleIds 다.
-- 값은 규칙 코드이며 같은 결과의 matchedRuleIds 의 부분집합이어야 한다.
CREATE TABLE care_result_ingredient_rules (
    care_result_ingredient_id BINARY(16) NOT NULL,
    rule_code VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 1,

    CONSTRAINT pk_care_result_ingredient_rules
        PRIMARY KEY (care_result_ingredient_id, rule_code),
    CONSTRAINT fk_care_result_ingredient_rules_ingredient
        FOREIGN KEY (care_result_ingredient_id) REFERENCES care_result_ingredients (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_care_result_ingredient_rules_code_not_blank
        CHECK (CHAR_LENGTH(TRIM(rule_code)) > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '추천 성분을 내놓은 규칙 코드 (명세 sourceRuleIds)';

CREATE TABLE follow_ups (
    id BINARY(16) NOT NULL,
    report_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    skin_change VARCHAR(30) NOT NULL,
    -- 명세 v2_1에서 두 종류 모두 받는 값이 되었다. NOT NULL로 좁히지 않는 이유는
    -- ck_follow_ups_type_payload가 이미 종류별 필수 여부를 강제하고 있어서다.
    action_completion VARCHAR(30) NULL,
    clinician_check_status VARCHAR(40) NULL,
    submitted_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_follow_ups PRIMARY KEY (id),
    CONSTRAINT uq_follow_ups_report UNIQUE (report_id),
    CONSTRAINT fk_follow_ups_report_owner
        FOREIGN KEY (report_id, user_id) REFERENCES skin_reports (id, user_id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_follow_ups_kind
        CHECK (kind IN ('SELF_CARE', 'CLINICIAN_CHECK')),
    -- 명세 v2_1에서 NEW_AREA, UNSURE가 빠져 세 개가 됐다. 두 값이 남아 있는 기존 행이 있으면
    -- 이 CHECK를 만들 수 없으므로 배포 전에 마이그레이션이 필요하다.
    CONSTRAINT ck_follow_ups_skin_change
        CHECK (skin_change IN ('IMPROVED', 'SIMILAR', 'WORSENED')),
    CONSTRAINT ck_follow_ups_action_completion
        CHECK (
            action_completion IS NULL
            OR action_completion IN ('MOSTLY_DONE', 'PARTLY_DONE', 'NOT_DONE')
        ),
    CONSTRAINT ck_follow_ups_clinician_status
        CHECK (
            clinician_check_status IS NULL
            OR clinician_check_status IN ('CHECKED', 'NOT_YET', 'PREFER_NOT_TO_RECORD')
        ),
    -- 명세 v2_1에서 CLINICIAN_CHECK도 action_completion을 받게 되어, 이 값은 종류와 무관하게
    -- 항상 있어야 한다. 두 모양을 가르는 것은 clinician_check_status 하나만 남았다.
    CONSTRAINT ck_follow_ups_type_payload
        CHECK (
            action_completion IS NOT NULL
            AND (
                (kind = 'SELF_CARE' AND clinician_check_status IS NULL)
                OR
                (kind = 'CLINICIAN_CHECK' AND clinician_check_status IS NOT NULL)
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '피부 보고와 1대0..1 관계인 다음 날 경과';

CREATE INDEX ix_follow_ups_user_submitted
    ON follow_ups (user_id, submitted_at DESC);

CREATE INDEX ix_follow_ups_report_owner
    ON follow_ups (report_id, user_id);

-- -----------------------------------------------------------------------------
-- 오늘 점호, PWA Web Push, 멱등성, 측정 이벤트
-- -----------------------------------------------------------------------------

CREATE TABLE daily_check_ins (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    check_in_date DATE NOT NULL COMMENT 'Asia/Seoul 기준 날짜',
    state VARCHAR(20) NOT NULL,
    report_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_daily_check_ins PRIMARY KEY (id),
    CONSTRAINT uq_daily_check_ins_user_date UNIQUE (user_id, check_in_date),
    CONSTRAINT uq_daily_check_ins_report UNIQUE (report_id),
    CONSTRAINT fk_daily_check_ins_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_daily_check_ins_report
        FOREIGN KEY (report_id) REFERENCES skin_reports (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_daily_check_ins_state
        CHECK (state IN ('NO_DISCOMFORT', 'SKIN_REPORT')),
    CONSTRAINT ck_daily_check_ins_state_report
        CHECK (
            (state = 'NO_DISCOMFORT' AND report_id IS NULL)
            OR (state = 'SKIN_REPORT' AND report_id IS NOT NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '하루 피부 점호 상태';

CREATE INDEX ix_daily_check_ins_report
    ON daily_check_ins (report_id);

CREATE TABLE push_subscriptions (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    endpoint_fingerprint BINARY(32) NOT NULL,
    endpoint_ciphertext MEDIUMBLOB NOT NULL,
    p256dh_ciphertext VARBINARY(1024) NOT NULL,
    auth_ciphertext VARBINARY(512) NOT NULL,
    user_agent VARCHAR(512) NOT NULL,
    expires_at DATETIME(6) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_success_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_push_subscriptions PRIMARY KEY (id),
    CONSTRAINT uq_push_subscriptions_user_endpoint
        UNIQUE (user_id, endpoint_fingerprint),
    CONSTRAINT fk_push_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '암호화한 표준 Web Push 구독';

CREATE INDEX ix_push_subscriptions_user_active
    ON push_subscriptions (user_id, active);

CREATE TABLE notification_deliveries (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    target_report_id BINARY(16) NULL,
    notification_date DATE NOT NULL COMMENT 'Asia/Seoul 기준 날짜',
    notification_type VARCHAR(30) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    error_code VARCHAR(100) NULL,
    attempted_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_notification_deliveries PRIMARY KEY (id),
    CONSTRAINT uq_notification_deliveries_user_date
        UNIQUE (user_id, notification_date),
    CONSTRAINT fk_notification_deliveries_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_notification_deliveries_report
        FOREIGN KEY (target_report_id) REFERENCES skin_reports (id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT ck_notification_deliveries_type
        CHECK (notification_type IN ('FOLLOW_UP', 'DAILY_CHECK_IN')),
    CONSTRAINT ck_notification_deliveries_status
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_notification_deliveries_sent_at
        CHECK (
            (delivery_status = 'SENT' AND sent_at IS NOT NULL)
            OR (delivery_status <> 'SENT' AND sent_at IS NULL)
        ),
    CONSTRAINT ck_notification_deliveries_error
        CHECK (
            (delivery_status = 'FAILED' AND error_code IS NOT NULL)
            OR (delivery_status <> 'FAILED' AND error_code IS NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자·날짜당 한 번의 Web Push 발송 이력';

CREATE INDEX ix_notification_deliveries_report
    ON notification_deliveries (target_report_id);

CREATE INDEX ix_notification_deliveries_date_status
    ON notification_deliveries (notification_date, delivery_status);

CREATE TABLE idempotency_records (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    operation_id VARCHAR(80) NOT NULL,
    idempotency_key BINARY(16) NOT NULL,
    request_hash BINARY(32) NOT NULL,
    response_status SMALLINT UNSIGNED NOT NULL,
    response_body_encrypted LONGBLOB NOT NULL,
    resource_id BINARY(16) NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_idempotency_records PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_records_scope
        UNIQUE (user_id, operation_id, idempotency_key),
    CONSTRAINT fk_idempotency_records_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_idempotency_records_status
        CHECK (response_status BETWEEN 100 AND 599),
    CONSTRAINT ck_idempotency_records_expiry
        CHECK (expires_at > created_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '24시간 보관하는 중복 요청 응답';

CREATE INDEX ix_idempotency_records_expires
    ON idempotency_records (expires_at);

CREATE TABLE analytics_events (
    event_id BINARY(16) NOT NULL,
    user_id BINARY(16) NULL,
    event_name VARCHAR(50) NOT NULL,
    allowed_properties JSON NULL,
    occurred_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_analytics_events PRIMARY KEY (event_id),
    CONSTRAINT fk_analytics_events_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT ck_analytics_events_name
        CHECK (
            event_name IN (
                'ONBOARDING_COMPLETED',
                'REPORT_STARTED',
                'INPUT_ASSIST_OPENED',
                'AI_STRUCTURING_SUCCEEDED',
                'AI_STRUCTURING_FAILED',
                'REPORT_SUBMITTED',
                'CARE_RESULT_VIEWED',
                'FOLLOW_UP_SUBMITTED',
                'SIMILAR_EXPERIENCE_VIEWED',
                'RECOMMENDED_INGREDIENTS_VIEWED',
                'NOTIFICATION_TIME_SET'
            )
        ),
    CONSTRAINT ck_analytics_events_properties_object
        CHECK (
            allowed_properties IS NULL
            OR JSON_TYPE(allowed_properties) = 'OBJECT'
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '피부 상세정보를 금지한 측정 이벤트';

CREATE INDEX ix_analytics_events_user
    ON analytics_events (user_id);

CREATE INDEX ix_analytics_events_name_occurred
    ON analytics_events (event_name, occurred_at);

-- -----------------------------------------------------------------------------
-- 서비스 계층에서 추가로 보장해야 하는 교차 행 규칙
-- -----------------------------------------------------------------------------
--
-- A. report_appearances:
--    UNSURE는 다른 appearance_code와 동시에 저장할 수 없습니다.
-- B. report_sensations:
--    NONE은 다른 sensation_code와 동시에 저장할 수 없습니다.
-- C. report_situations:
--    NONE_RECALLED는 다른 situation_code와 동시에 저장할 수 없습니다.
-- D. report_pre_care_checks:
--    NONE은 다른 check_code와 동시에 저장할 수 없습니다.
-- E. skin_reports.result_type:
--    pre-care NONE만 있으면 SELF_CARE_GUIDE,
--    위험 코드가 하나 이상이면 CLINICIAN_CHECK여야 합니다.
-- F. follow_ups:
--    kind는 원 보고 result_type과 일치해야 하고,
--    submitted_at은 원 보고의 follow-up 입력 가능 구간 안이어야 합니다.
-- G. care_results:
--    result_type은 원 보고 result_type과 일치해야 하며 similar_report_id는
--    같은 사용자, 과거 날짜, COMPLETED 상태, 유사도 5점 이상이어야 합니다.
--    similar_report_id와 similarity_score는 항상 함께 NULL이거나 함께 값을 가져야 합니다.
--    참조한 보고가 삭제되어 similar_report_id가 NULL이 되면 similarity_score도 NULL로 정리합니다.
-- H. rule_sets / care_rule_versions:
--    신규 결과에는 ACTIVE 규칙 세트의 APPROVED 규칙 버전만 적용합니다.
-- I. care_result_items:
--    DO_TODAY, AVOID_TODAY, CHECK_NEXT는 각 최대 2개이며,
--    SELF_CARE_GUIDE에는 CLINICIAN_MESSAGE를 넣지 않습니다.
-- J. analytics_events.allowed_properties:
--    durationMs, inputAssistUsed, resultType, aiSucceeded 외 키를 거부하고
--    원문·부위·겉모습·불편·군번·부대명·위치·의료진 내용을 저장하지 않습니다.
