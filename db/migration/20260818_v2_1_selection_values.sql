-- 선택값 세 그룹을 명세 v2.1로 옮기는 마이그레이션
-- 대상: MySQL 8.0.16 이상 (DROP CHECK 지원)
-- 관련 이슈: #24
--
-- 이 스크립트는 원자적이지 않다. MySQL은 DDL에서 암묵적 커밋을 일으키므로 CREATE TABLE과
-- ALTER TABLE이 섞인 이상 START TRANSACTION으로 전체를 묶을 수 없다. 대신 어느 지점에서
-- 멈추더라도 다시 실행할 수 있게 만들었다. 보존은 INSERT IGNORE, 제약 조작은 존재 여부를
-- 확인한 뒤 실행한다.
--
-- 실행 순서가 중요하다. 리네임이 옛 CHECK 제약보다 먼저 오면 새 값이 제약에 걸려 실패한다.
--   보존 -> 옛 제약 제거 -> 변환 -> 대응 없는 값 정리 -> 새 제약 추가
--
-- 되돌리기: archive 테이블에 원본이 남는다. 새 제약을 먼저 제거한 뒤 역방향 INSERT한다.

USE flourishing;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0. 보존 테이블
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS report_sensations_v1_archive (
    report_id BINARY(16) NOT NULL,
    sensation_code VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_report_sensations_v1_archive PRIMARY KEY (report_id, sensation_code)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'v1 느껴지는 불편 원본. 유사도 계산에 쓰지 않는다';

CREATE TABLE IF NOT EXISTS report_appearances_v1_archive (
    report_id BINARY(16) NOT NULL,
    appearance_code VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_report_appearances_v1_archive PRIMARY KEY (report_id, appearance_code)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'v1 겉모습 원본. 유사도 계산에 쓰지 않는다';

CREATE TABLE IF NOT EXISTS report_situations_v1_archive (
    report_id BINARY(16) NOT NULL,
    situation_code VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_report_situations_v1_archive PRIMARY KEY (report_id, situation_code)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'v1 직전 상황 중 v2.1에서 삭제된 값의 원본';

-- ---------------------------------------------------------------------------
-- 1. 원본 보존
--
-- 명세 하위 호환 안내가 sensations에 대해 "과거 기록은 별도 보존 컬럼으로 옮기고 유사도
-- 계산에서 제외할 것"을 요구한다. appearances도 v1과 v2.1 사이에 대응 관계가 없어 같게 다룬다.
-- situations는 명세가 리네임 두 건을 명시했으므로 그것만 변환하고 나머지를 보존 후 삭제한다.
--
-- 보존 대상은 "v2.1 목록에 없는 값"으로 고른다. 목록을 직접 나열하지 않는 이유는 재실행 때문이다.
-- v1 값을 나열하면 이미 마이그레이션된 DB에서 다시 돌릴 때 v2.1 값까지 함께 지우게 된다.
-- INSERT IGNORE는 중간에 멈춘 뒤 다시 돌리는 경우를 위한 것이다.
-- ---------------------------------------------------------------------------

INSERT IGNORE INTO report_sensations_v1_archive (report_id, sensation_code, created_at)
SELECT report_id, sensation_code, created_at
FROM report_sensations
WHERE sensation_code NOT IN ('REDNESS', 'EXCESS_SEBUM', 'BREAKOUT');

INSERT IGNORE INTO report_appearances_v1_archive (report_id, appearance_code, created_at)
SELECT report_id, appearance_code, created_at
FROM report_appearances
WHERE appearance_code NOT IN (
    'APP_REDNESS', 'APP_BUMP', 'APP_PUS_BUMP',
    'APP_DRYNESS', 'APP_OILINESS', 'APP_OTHER'
);

INSERT IGNORE INTO report_situations_v1_archive (report_id, situation_code, created_at)
SELECT report_id, situation_code, created_at
FROM report_situations
WHERE situation_code NOT IN (
    'PROTECTIVE_GEAR_OR_MASK', 'SHAVING', 'SQUEEZED_ACNE',
    'NEW_PRODUCT', 'SWEAT_OR_SEBUM', 'NONE_RECALLED'
)
  AND situation_code NOT IN ('SWEAT_OR_DUST_AFTER_TRAINING', 'TOUCHED_OR_SQUEEZED');

-- ---------------------------------------------------------------------------
-- 2. 옛 CHECK 제약 제거
--
-- 변환보다 반드시 먼저 와야 한다. SWEAT_OR_SEBUM은 v1 허용 목록에 없어서, 제약이 살아 있는
-- 상태로 UPDATE하면 ERROR 3819로 멈춘다.
--
-- MySQL 8.0에는 DROP CHECK IF EXISTS가 없다. 재실행할 수 있도록 존재할 때만 실행한다.
-- ---------------------------------------------------------------------------

SET @drop_sensations := IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_report_sensations_code') > 0,
    'ALTER TABLE report_sensations DROP CHECK ck_report_sensations_code',
    'DO 0');
PREPARE stmt FROM @drop_sensations; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_appearances := IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_report_appearances_code') > 0,
    'ALTER TABLE report_appearances DROP CHECK ck_report_appearances_code',
    'DO 0');
PREPARE stmt FROM @drop_appearances; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_situations := IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
      WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_report_situations_code') > 0,
    'ALTER TABLE report_situations DROP CHECK ck_report_situations_code',
    'DO 0');
PREPARE stmt FROM @drop_situations; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3. 변환 — 명세가 명시한 리네임 두 건만
--
-- 같은 보고가 이미 대상 값을 가지고 있으면 (report_id, situation_code) 기본키에 걸린다.
-- 먼저 중복될 행을 지운 뒤 UPDATE한다.
-- ---------------------------------------------------------------------------

DELETE old FROM report_situations old
JOIN report_situations kept
  ON kept.report_id = old.report_id
 AND kept.situation_code = 'SWEAT_OR_SEBUM'
WHERE old.situation_code = 'SWEAT_OR_DUST_AFTER_TRAINING';

DELETE old FROM report_situations old
JOIN report_situations kept
  ON kept.report_id = old.report_id
 AND kept.situation_code = 'SQUEEZED_ACNE'
WHERE old.situation_code = 'TOUCHED_OR_SQUEEZED';

UPDATE report_situations
   SET situation_code = 'SWEAT_OR_SEBUM'
 WHERE situation_code = 'SWEAT_OR_DUST_AFTER_TRAINING';

UPDATE report_situations
   SET situation_code = 'SQUEEZED_ACNE'
 WHERE situation_code = 'TOUCHED_OR_SQUEEZED';

-- ---------------------------------------------------------------------------
-- 4. 대응 관계가 없는 값 정리
--
-- 조건을 "v2.1 목록에 없는 값"으로 둔 덕분에 두 번 돌려도 v2.1 데이터가 지워지지 않는다.
-- 3번에서 이미 리네임이 끝났으므로 여기 남은 v1 값은 전부 대응 관계가 없는 것들이다.
--
-- 이 시점에 선택값이 하나도 남지 않는 보고가 생긴다. 명세는 최소 1개를 요구하지만 그것은
-- 요청 검증 기준이고 이미 저장된 과거 기록에 소급하지 않는다. 아래 "미결" 참고.
-- ---------------------------------------------------------------------------

DELETE FROM report_sensations
 WHERE sensation_code NOT IN ('REDNESS', 'EXCESS_SEBUM', 'BREAKOUT');

DELETE FROM report_appearances
 WHERE appearance_code NOT IN (
     'APP_REDNESS', 'APP_BUMP', 'APP_PUS_BUMP',
     'APP_DRYNESS', 'APP_OILINESS', 'APP_OTHER'
 );

DELETE FROM report_situations
 WHERE situation_code NOT IN (
     'PROTECTIVE_GEAR_OR_MASK', 'SHAVING', 'SQUEEZED_ACNE',
     'NEW_PRODUCT', 'SWEAT_OR_SEBUM', 'NONE_RECALLED'
 );

-- ---------------------------------------------------------------------------
-- 5. 새 CHECK 제약
-- ---------------------------------------------------------------------------

-- 2번에서 제거했으므로 이름이 비어 있다. 재실행이면 2번이 다시 제거한 뒤 여기서 다시 만든다.
ALTER TABLE report_sensations
    ADD CONSTRAINT ck_report_sensations_code
        CHECK (sensation_code IN ('REDNESS', 'EXCESS_SEBUM', 'BREAKOUT'));

ALTER TABLE report_appearances
    ADD CONSTRAINT ck_report_appearances_code
        CHECK (appearance_code IN (
            'APP_REDNESS', 'APP_BUMP', 'APP_PUS_BUMP',
            'APP_DRYNESS', 'APP_OILINESS', 'APP_OTHER'
        ));

ALTER TABLE report_situations
    ADD CONSTRAINT ck_report_situations_code
        CHECK (situation_code IN (
            'PROTECTIVE_GEAR_OR_MASK', 'SHAVING', 'SQUEEZED_ACNE',
            'NEW_PRODUCT', 'SWEAT_OR_SEBUM', 'NONE_RECALLED'
        ));

-- ---------------------------------------------------------------------------
-- 적용 후 확인
--
--   SELECT COUNT(*) FROM report_sensations_v1_archive;
--   SELECT COUNT(*) FROM report_appearances_v1_archive;
--   SELECT situation_code, COUNT(*) FROM report_situations GROUP BY situation_code;
--
-- 미결: 선택값이 비게 된 과거 보고를 어떻게 다룰지.
--   (가) 그대로 두고 유사 경험 후보에서만 제외한다 — 기록 목록에는 계속 보인다
--   (나) status를 EXPIRED로 바꾼다 — 명세가 SkinChange에 대해 제안한 방식이다
-- 운영 데이터가 없다면 이 선택 자체가 필요 없다. 배포 전 확인할 것.
-- ---------------------------------------------------------------------------
