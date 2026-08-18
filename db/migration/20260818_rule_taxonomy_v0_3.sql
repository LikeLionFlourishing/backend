-- 관리규칙 v0.3의 9개 prefix를 받도록 규칙 분류 제약을 넓히는 마이그레이션
-- 대상: MySQL 8.0.16 이상 (DROP CHECK 지원)
-- 관련 이슈: #35
--
-- v0.3은 규칙을 CR / ENV / SIT / APP / ST / SAF / HR / ING / FALLBACK 아홉 갈래로 나눈다.
-- 기존 제약은 그중 다섯(COMMON, SITUATION, CURRENT_STATE, SAFETY, HISTORY)만 허용해서
-- ENV·APP·ING·FALLBACK 규칙을 넣으면 ERROR 3819로 막힌다.
--
-- 넓히는 제약 세 개:
--   1. care_rules.ck_care_rules_category        규칙 분류 5 -> 9
--   2. rule_conditions.ck_rule_conditions_field 조건 필드에 environments 추가
--   3. care_result_rules.ck_care_result_rules_reason 적용 이유 6 -> 10
--
-- 데이터를 옮기지 않는다. 허용 목록을 넓히기만 하므로 기존 행은 모두 새 제약을 통과한다.
-- 그래서 이 스크립트는 순서에 민감하지 않고 몇 번 돌려도 결과가 같다.
--
-- 되돌리기: 아래 ADD CONSTRAINT를 옛 목록으로 다시 실행한다. 단 되돌리기 전에 새 분류로
-- 들어간 행(ENVIRONMENT, APPEARANCE, INGREDIENT, FALLBACK)을 먼저 정리해야 한다.

USE flourishing;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. care_rules.category
--
-- MySQL 8.0에는 DROP CHECK IF EXISTS가 없다. 재실행할 수 있도록 존재할 때만 제거한다.
-- ---------------------------------------------------------------------------

SET @drop_rule_category := IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_care_rules_category') > 0,
    'ALTER TABLE care_rules DROP CHECK ck_care_rules_category',
    'DO 0');
PREPARE stmt FROM @drop_rule_category; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE care_rules
    ADD CONSTRAINT ck_care_rules_category
        CHECK (category IN (
            'COMMON', 'ENVIRONMENT', 'SITUATION', 'APPEARANCE', 'CURRENT_STATE',
            'SAFETY', 'HISTORY', 'INGREDIENT', 'FALLBACK'
        ));

-- ---------------------------------------------------------------------------
-- 2. rule_conditions.field_code
--
-- environments 는 온보딩에서 1회 설정하는 예상 환경(ENV-*)이다. 입력 경로는 확정 명세에
-- 아직 없어서 값이 비어 있고, 그동안 ENV 규칙은 매칭되지 않는다. 규칙 문서 11-1이 예상
-- 환경을 선택값으로 두고 "미입력 시 환경 보정 없이 진행"으로 정한 동작과 같다.
-- ---------------------------------------------------------------------------

SET @drop_condition_field := IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_rule_conditions_field') > 0,
    'ALTER TABLE rule_conditions DROP CHECK ck_rule_conditions_field',
    'DO 0');
PREPARE stmt FROM @drop_condition_field; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE rule_conditions
    ADD CONSTRAINT ck_rule_conditions_field
        CHECK (field_code IN (
            'primaryArea',
            'appearances',
            'sensations',
            'situations',
            'careAvailability',
            'preCareChecks',
            'completedHistory',
            'environments'
        ));

-- ---------------------------------------------------------------------------
-- 3. care_result_rules.match_reason
--
-- 결과에 남기는 적용 이유는 규칙 분류에서 나온다. 분류를 넓히면 이유도 함께 넓혀야
-- 새 분류의 규칙이 걸린 결과를 저장할 수 있다.
-- ---------------------------------------------------------------------------

SET @drop_result_reason := IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_care_result_rules_reason') > 0,
    'ALTER TABLE care_result_rules DROP CHECK ck_care_result_rules_reason',
    'DO 0');
PREPARE stmt FROM @drop_result_reason; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE care_result_rules
    ADD CONSTRAINT ck_care_result_rules_reason
        CHECK (match_reason IN (
            'SAFETY', 'PROHIBITION', 'CURRENT_STATE',
            'SITUATION', 'COMMON', 'HISTORY',
            'ENVIRONMENT', 'APPEARANCE', 'INGREDIENT', 'FALLBACK'
        ));

-- ---------------------------------------------------------------------------
-- 적용 후 확인
--
--   SELECT constraint_name, check_clause FROM information_schema.check_constraints
--    WHERE constraint_schema = DATABASE()
--      AND constraint_name IN (
--          'ck_care_rules_category',
--          'ck_rule_conditions_field',
--          'ck_care_result_rules_reason');
--
-- 이 마이그레이션 다음에 db/seed/20260818_care_rules_v0_3.sql 을 실행해야 규칙 데이터가
-- 채워진다. 활성 규칙 세트가 없으면 피부 보고 제출이 RULE_ENGINE_UNAVAILABLE(503)로 막힌다.
-- ---------------------------------------------------------------------------
