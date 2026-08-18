-- 관리규칙 DB MVP v0.3 시드
-- 관련 이슈: #35
--
-- 규칙 26건과 성분 8건을 넣는다. 활성 규칙 세트가 없으면 피부 보고 제출이
-- RULE_ENGINE_UNAVAILABLE(503)로 막히므로, 이 파일은 배포에 반드시 따라가야 한다.
--
-- 실행 순서
--   1. db/schema.sql (또는 db/migration/20260818_rule_taxonomy_v0_3.sql)
--   2. db/seed/20260818_guide_sections.sql
--   3. 이 파일
--
-- 재실행해도 안전하다. 모든 식별자를 코드에서 파생시키고 INSERT ... ON DUPLICATE KEY UPDATE로
-- 문구만 최신으로 맞춘다.
--
-- 식별자를 왜 SHA2로 만드는가
--   규칙 테이블의 PK는 BINARY(16)이고 자연키가 아니다. UUID()를 쓰면 재실행마다 새 행이 생기고,
--   손으로 UUID를 나열하면 수십 개를 사람이 관리해야 한다. 그래서 규칙 코드 같은 자연키에서
--   UNHEX(LEFT(SHA2(...), 32))로 16바이트를 파생시킨다. 같은 코드는 언제 어디서 실행해도 같은
--   식별자가 되므로 개발·스테이징·운영의 규칙 ID가 일치한다.
--   접두사(rule:, ver:, cond:, act:, evi:, ing:)는 서로 다른 테이블이 같은 값을 갖지 않게 한다.
--
-- 규칙 코드 체계 (문서 0장)
--   CR  공통        -> category COMMON
--   ENV 예상 환경   -> ENVIRONMENT
--   SIT 상황        -> SITUATION
--   APP 겉모습      -> APPEARANCE
--   ST  현재 상태   -> CURRENT_STATE
--   SAF 안전 분기   -> SAFETY
--   HR  이전 기록   -> HISTORY
--   ING 성분 기능   -> INGREDIENT
--   FALLBACK 예외   -> FALLBACK
--
-- 검토 상태에 대하여
--   문서는 SAF-001을 "전문가·멘토 검토 전 사용 금지"로 두고 잠정 운영 (B)를 채택했다. (B)는
--   서비스를 운영하는 방안이라 SAF-001도 review_status APPROVED로 넣는다. 넣지 않으면 위험
--   신호를 고른 사용자에게 안내할 문구가 없어 503이 나가고, 그것이 더 위험하다.
--   대신 rule_evidence_sources.reviewed_at / reviewed_by를 NULL로 두어 "근거는 있으나 검토는
--   끝나지 않았다"를 데이터에 남긴다. 검토가 끝나면 그 두 컬럼을 채운다.
--
-- 이 시드가 담지 않는 것
--   rule_dependencies("함께 적용" 관계)는 넣지 않는다. 애플리케이션이 이 테이블을 읽지 않고,
--   함께 적용 관계는 이미 조건과 카테고리 우선순위로 표현된다. 읽지 않는 데이터를 넣으면
--   나중에 규칙을 고칠 때 두 곳을 맞춰야 한다.

USE flourishing;

SET NAMES utf8mb4;

-- 규칙 세트 식별자와 검토자. 아래 INSERT 여러 곳에서 재사용한다.
SET @rule_set_id := UNHEX(LEFT(SHA2('set:v0.3-mvp', 256), 32));
SET @reviewer_id := UNHEX(LEFT(SHA2('rule_reviewer:mvp-v0.3', 256), 32));
SET @approved_at := TIMESTAMP('2026-08-18 00:00:00');

-- ---------------------------------------------------------------------------
-- 1. 규칙 세트
--
-- uq_rule_sets_single_active가 ACTIVE 세트를 하나로 묶는다. 재실행하면 같은 행을 갱신하므로
-- 제약에 걸리지 않는다. status가 ACTIVE면 승인·활성 시각과 승인자가 모두 채워져 있어야 한다는
-- ck_rule_sets_approval / ck_rule_sets_activation 을 만족시킨다.
-- ---------------------------------------------------------------------------

INSERT INTO rule_sets (
    id, version_code, status, approved_at, approved_by_user_id, activated_at
) VALUES (
    @rule_set_id, 'v0.3-mvp', 'ACTIVE', @approved_at, @reviewer_id, @approved_at
) AS new
ON DUPLICATE KEY UPDATE
    status = new.status,
    approved_at = new.approved_at,
    approved_by_user_id = new.approved_by_user_id,
    activated_at = new.activated_at;

-- ---------------------------------------------------------------------------
-- 2. 규칙 식별자 26건
-- ---------------------------------------------------------------------------

INSERT INTO care_rules (id, rule_code, category, name) VALUES
    (UNHEX(LEFT(SHA2('rule:CR-001', 256), 32)), 'CR-001', 'COMMON', '피부 자극 최소화'),

    (UNHEX(LEFT(SHA2('rule:ENV-001', 256), 32)), 'ENV-001', 'ENVIRONMENT', '야외활동·훈련 환경'),
    (UNHEX(LEFT(SHA2('rule:ENV-002', 256), 32)), 'ENV-002', 'ENVIRONMENT', '야간·교대 일정'),
    (UNHEX(LEFT(SHA2('rule:ENV-003', 256), 32)), 'ENV-003', 'ENVIRONMENT', '덥고 습한 환경'),

    (UNHEX(LEFT(SHA2('rule:SIT-001', 256), 32)), 'SIT-001', 'SITUATION', '면도 후 피부 불편'),
    (UNHEX(LEFT(SHA2('rule:SIT-002', 256), 32)), 'SIT-002', 'SITUATION', '훈련 후 땀·먼지'),
    (UNHEX(LEFT(SHA2('rule:SIT-003', 256), 32)), 'SIT-003', 'SITUATION', '보호장비·마스크 마찰'),
    (UNHEX(LEFT(SHA2('rule:SIT-004', 256), 32)), 'SIT-004', 'SITUATION', '새 제품 사용 후 피부 불편'),
    (UNHEX(LEFT(SHA2('rule:SIT-005', 256), 32)), 'SIT-005', 'SITUATION', '피부를 짜거나 만진 후'),
    (UNHEX(LEFT(SHA2('rule:SIT-006', 256), 32)), 'SIT-006', 'SITUATION', '특별히 떠오르는 상황 없음'),

    (UNHEX(LEFT(SHA2('rule:APP-REDNESS', 256), 32)), 'APP-REDNESS', 'APPEARANCE', '겉모습 붉어짐'),
    (UNHEX(LEFT(SHA2('rule:APP-BUMP', 256), 32)), 'APP-BUMP', 'APPEARANCE', '겉모습 돌기·울퉁불퉁함'),
    (UNHEX(LEFT(SHA2('rule:APP-PUS-BUMP', 256), 32)), 'APP-PUS-BUMP', 'APPEARANCE', '겉모습 고름이 찬 돌기'),
    (UNHEX(LEFT(SHA2('rule:APP-DRYNESS', 256), 32)), 'APP-DRYNESS', 'APPEARANCE', '겉모습 건조·각질'),
    (UNHEX(LEFT(SHA2('rule:APP-OILINESS', 256), 32)), 'APP-OILINESS', 'APPEARANCE', '겉모습 번들거림·유분'),
    (UNHEX(LEFT(SHA2('rule:APP-OTHER', 256), 32)), 'APP-OTHER', 'APPEARANCE', '겉모습 기타'),

    (UNHEX(LEFT(SHA2('rule:ST-001', 256), 32)), 'ST-001', 'CURRENT_STATE', '아직 세안 전'),
    (UNHEX(LEFT(SHA2('rule:ST-002', 256), 32)), 'ST-002', 'CURRENT_STATE', '이미 세안함'),
    (UNHEX(LEFT(SHA2('rule:ST-003', 256), 32)), 'ST-003', 'CURRENT_STATE', '오늘 추가 관리가 어려움'),

    (UNHEX(LEFT(SHA2('rule:SAF-001', 256), 32)), 'SAF-001', 'SAFETY', '의료진 확인 우선'),

    (UNHEX(LEFT(SHA2('rule:HR-001', 256), 32)), 'HR-001', 'HISTORY', '유사 과거 기록 참고'),

    (UNHEX(LEFT(SHA2('rule:ING-001', 256), 32)), 'ING-001', 'INGREDIENT', '면도 후 자극 관련 성분'),
    (UNHEX(LEFT(SHA2('rule:ING-002', 256), 32)), 'ING-002', 'INGREDIENT', '압출 후 자극 관련 성분'),
    (UNHEX(LEFT(SHA2('rule:ING-003', 256), 32)), 'ING-003', 'INGREDIENT', '보호장비 후 유분·땀 관련 성분'),
    (UNHEX(LEFT(SHA2('rule:ING-004', 256), 32)), 'ING-004', 'INGREDIENT', '건조·각질 관련 성분'),

    (UNHEX(LEFT(SHA2('rule:FALLBACK-001', 256), 32)), 'FALLBACK-001', 'FALLBACK', '미인식 예외 대응')
AS new
ON DUPLICATE KEY UPDATE
    category = new.category,
    name = new.name;

-- ---------------------------------------------------------------------------
-- 3. 규칙 버전 26건
--
-- priority는 같은 카테고리 안에서만 비교한다. 카테고리 사이 순서는 애플리케이션의
-- RuleCategory.precedence가 정한다.
--
-- forbidden_expressions는 애플리케이션이 [\r\n,]+ 로 쪼갠다. 그래서 항목 구분은 개행으로 하고
-- 항목 안에는 쉼표를 쓰지 않는다. 쉼표를 쓰면 한 항목이 둘로 갈라진다.
--
-- fallback_text는 AI 설명 생성이 실패했을 때 요약 자리에 들어간다. 걸린 규칙 중 가장 앞선
-- 규칙의 것을 쓰므로, 어떤 규칙이 첫 번째가 되어도 말이 되도록 규칙마다 채운다. 겉모습(APP-*)만
-- 예외로 비워 둔다. 문서가 겉모습을 "행동을 만들지 않는 입력값"으로 정했기 때문이다.
-- ---------------------------------------------------------------------------

INSERT INTO care_rule_versions (
    id, rule_id, rule_set_id, version_code, review_status,
    application_summary, exclusion_summary, forbidden_expressions, fallback_text, priority
) VALUES
    (UNHEX(LEFT(SHA2('ver:CR-001', 256), 32)), UNHEX(LEFT(SHA2('rule:CR-001', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '피부 불편이 기록된 일반적인 상황에 공통으로 적용한다.',
     '안전 분기 조건에 해당하면 안전 안내가 앞선다.',
     '질환명 단정\n원인 확정\n치료 효과 보장',
     '오늘은 피부에 닿는 자극을 줄이는 데 집중하는 편이 좋겠습니다.', 100),

    (UNHEX(LEFT(SHA2('ver:ENV-001', 256), 32)), UNHEX(LEFT(SHA2('rule:ENV-001', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '야외훈련·행군 등 땀·먼지·자외선 노출이 있고 세안 접근이 제한될 수 있는 환경에 적용한다.',
     '피부에 영향을 줄 정도의 노출이 없는 짧은 야외 이동은 제외한다.',
     '특정 제품이나 치료를 직접 판단하는 표현\n질환 확정',
     '야외활동이 많은 날은 씻을 수 있게 되는 시점에 한 번 정리하는 편이 좋겠습니다.', 100),

    (UNHEX(LEFT(SHA2('ver:ENV-002', 256), 32)), UNHEX(LEFT(SHA2('rule:ENV-002', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '야간근무·당직·경계근무 등 평소 수면과 생활시간이 달라지는 환경에 적용한다.',
     '늦게 잠든 정도로 생활패턴 변화가 크지 않은 경우는 제외한다.',
     '수면 부족 때문에 특정 피부질환이 생겼다는 단정',
     '일정이 불규칙한 날은 실제로 시간이 나는 때에 한 번만 관리하는 편이 좋겠습니다.', 200),

    (UNHEX(LEFT(SHA2('ver:ENV-003', 256), 32)), UNHEX(LEFT(SHA2('rule:ENV-003', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '여름철·고온다습한 실내외 환경에 적용한다.',
     '사용자가 덥고 습한 환경을 직접 고르지 않은 경우는 제외한다. 기온·습도 수치로 자동 판정하지 않는다.',
     '특정 피부질환 확정',
     '덥고 습한 날은 땀을 오래 두지 않는 것만으로도 도움이 될 수 있습니다.', 300),

    (UNHEX(LEFT(SHA2('ver:SIT-001', 256), 32)), UNHEX(LEFT(SHA2('rule:SIT-001', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '면도 이후 붉어짐·따가움·자극이 생긴 경우에 적용한다.',
     '면도와 관계없는 상황이거나 안전 분기에 해당하면 제외한다.',
     '면도 트러블이라는 원인 확정\n질환 확정\n면도 방법을 바꾸면 낫는다는 단정',
     '면도한 부위는 오늘 하루 쉬게 두는 편이 좋겠습니다.', 100),

    (UNHEX(LEFT(SHA2('ver:SIT-002', 256), 32)), UNHEX(LEFT(SHA2('rule:SIT-002', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '훈련 후 땀·먼지에 노출된 뒤 피부 불편이 생긴 경우에 적용한다.',
     '안전 분기에 해당하면 제외한다. 세안 여부는 현재 상태 규칙이 함께 보정한다.',
     '땀이나 먼지를 피부 문제의 원인으로 확정',
     '훈련 뒤에는 씻을 수 있게 되는 때에 한 번만 정리하는 편이 좋겠습니다.', 200),

    (UNHEX(LEFT(SHA2('ver:SIT-003', 256), 32)), UNHEX(LEFT(SHA2('rule:SIT-003', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '방독면·마스크 등 얼굴에 밀착되는 보호장비 착용 후 압박감·마찰·붉어짐·따가움이 생긴 경우에 적용한다.',
     '보호장비와 관계없는 상황이거나 안전 분기에 해당하면 제외한다.',
     '마찰성 피부염 등 질환명 확정',
     '장비가 닿는 부위는 쉬는 시간에 말리고 그대로 두는 편이 좋겠습니다.', 300),

    (UNHEX(LEFT(SHA2('ver:SIT-004', 256), 32)), UNHEX(LEFT(SHA2('rule:SIT-004', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '새로운 피부관리 제품을 쓴 뒤 새로운 불편이 생긴 경우에 적용한다. 성분 참고 정보는 함께 내보내지 않는다.',
     '새 제품 사용과 관계없는 상황이거나 안전 분기에 해당하면 제외한다.',
     '알레르기나 접촉피부염 등 질환 확정\n특정 성분을 원인으로 확정',
     '새로 쓴 제품은 오늘 멈추고 피부를 그대로 두는 편이 좋겠습니다.', 400),

    (UNHEX(LEFT(SHA2('ver:SIT-005', 256), 32)), UNHEX(LEFT(SHA2('rule:SIT-005', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '얼굴의 트러블을 손으로 짜거나 눌렀거나 반복해서 만진 경우에 적용한다.',
     '해당 행동이 없었거나 안전 분기에 해당하면 제외한다.',
     '여드름이나 감염 발생 확정\n흉터가 남는다는 확정',
     '건드린 부위는 오늘 그대로 두는 편이 좋겠습니다.', 500),

    (UNHEX(LEFT(SHA2('ver:SIT-006', 256), 32)), UNHEX(LEFT(SHA2('rule:SIT-006', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '면도·훈련·보호장비·새 제품·압출 등 떠오르는 상황이 없다고 사용자가 직접 고른 경우에 적용한다.',
     '특정 상황이 분명한 경우나 안전 분기에 해당하면 제외한다. 시스템이 규칙을 찾지 못한 경우의 폴백과 구분한다.',
     '피로나 스트레스 때문이라는 원인 단정\n질환명 확정\n특정 화장품 구매 권유',
     '특별한 일이 없던 날은 자극을 줄이고 평소 하던 관리만 유지하는 편이 좋겠습니다.', 600),

    (UNHEX(LEFT(SHA2('ver:APP-REDNESS', 256), 32)), UNHEX(LEFT(SHA2('rule:APP-REDNESS', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '사용자가 피부가 붉어 보인다고 고른 경우의 상태값이다. 이 규칙만으로 행동을 만들지 않는다.',
     '붉어짐이 관찰되지 않으면 제외한다.',
     '질환 확정\n원인 확정\n치료 효과 확정',
     NULL, 100),

    (UNHEX(LEFT(SHA2('ver:APP-BUMP', 256), 32)), UNHEX(LEFT(SHA2('rule:APP-BUMP', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '피부 표면의 돌기·울퉁불퉁함을 고른 경우의 상태값이다. 이 규칙만으로 행동을 만들지 않는다.',
     '해당 외관이 관찰되지 않으면 제외한다.',
     '여드름이나 모낭염 등 질환 확정',
     NULL, 200),

    (UNHEX(LEFT(SHA2('ver:APP-PUS-BUMP', 256), 32)), UNHEX(LEFT(SHA2('rule:APP-PUS-BUMP', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '하얗거나 노란 내용물이 찬 돌기를 고른 경우의 상태값이다. 안전 확인을 먼저 본다.',
     '해당 외관이 관찰되지 않으면 제외한다.',
     '화농성 여드름이나 모낭염 등 질환 확정',
     NULL, 300),

    (UNHEX(LEFT(SHA2('ver:APP-DRYNESS', 256), 32)), UNHEX(LEFT(SHA2('rule:APP-DRYNESS', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '건조함이나 각질을 고른 경우의 상태값이다. 이 규칙만으로 행동을 만들지 않는다.',
     '해당 외관이 관찰되지 않으면 제외한다.',
     '장벽 손상 확정\n피부질환 확정',
     NULL, 400),

    (UNHEX(LEFT(SHA2('ver:APP-OILINESS', 256), 32)), UNHEX(LEFT(SHA2('rule:APP-OILINESS', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '번들거림이나 유분을 고른 경우의 상태값이다. 이 규칙만으로 행동을 만들지 않는다.',
     '해당 외관이 관찰되지 않으면 제외한다.',
     '지성 피부 확정\n피지 과다 확정',
     NULL, 500),

    (UNHEX(LEFT(SHA2('ver:APP-OTHER', 256), 32)), UNHEX(LEFT(SHA2('rule:APP-OTHER', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '다섯 가지 겉모습으로 설명하기 어려운 외관을 고른 경우의 상태값이다. 이것만으로는 성분을 내보내지 않는다.',
     '다섯 가지 겉모습으로 설명할 수 있으면 제외한다.',
     '기타 선택을 임의로 질환명으로 바꾸는 표현',
     NULL, 600),

    (UNHEX(LEFT(SHA2('ver:ST-001', 256), 32)), UNHEX(LEFT(SHA2('rule:ST-001', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '아직 세안하지 않았고 땀·먼지·피지·선크림 등이 피부에 남아 있는 상태에 적용한다.',
     '이미 세안한 경우는 제외한다.',
     '특정 제품이나 성분을 반드시 써야 한다는 표현\n이중 세안이 필수라는 표현\n피지를 완전히 제거해야 한다는 표현',
     '아직 씻지 않았다면 미지근한 물로 한 번만 부드럽게 세안하는 편이 좋겠습니다.', 100),

    (UNHEX(LEFT(SHA2('ver:ST-002', 256), 32)), UNHEX(LEFT(SHA2('rule:ST-002', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '해당 상황 이후 이미 세안을 마친 상태에 적용한다.',
     '아직 세안하지 않은 경우는 제외한다.',
     '피지를 완전히 제거해야 한다는 표현\n한 번 더 씻어야 한다는 표현\n특정 제품을 반드시 써야 한다는 표현',
     '이미 씻으셨다면 오늘은 더 씻지 않고 그대로 두는 편이 좋겠습니다.', 200),

    (UNHEX(LEFT(SHA2('ver:ST-003', 256), 32)), UNHEX(LEFT(SHA2('rule:ST-003', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '근무·훈련 등으로 지금 세안이나 추가 관리를 하기 어려운 상태에 적용한다.',
     '지금 바로 관리할 수 있는 경우는 제외한다.',
     '군 복무 환경에서 할 수 없는 행동을 요구하는 표현\n기름종이가 피지 분비를 줄인다는 표현',
     '오늘 관리가 어렵다면 손을 대지 않고 두는 것만으로도 도움이 될 수 있습니다.', 300),

    (UNHEX(LEFT(SHA2('ver:SAF-001', 256), 32)), UNHEX(LEFT(SHA2('rule:SAF-001', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '빠르게 퍼지는 붉어짐 심한 통증·붓기·열감 고름·진물·물집 중 하나 이상을 사용자가 확인한 경우 일반 관리보다 먼저 적용한다.',
     '위험 신호가 없다고 답한 경우는 제외하고 상황별 관리 규칙으로 넘긴다.',
     '질환 진단\n감염 여부 판단\n치료제 처방\n괜찮다는 확정적 표현',
     '지금 남겨 주신 변화는 직접 확인받는 편이 안전합니다.', 100),

    (UNHEX(LEFT(SHA2('ver:HR-001', 256), 32)), UNHEX(LEFT(SHA2('rule:HR-001', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '현재 상황과 비슷한 과거 기록이 있으면 참고 정보로 적용한다. 현재 규칙보다 우선하지 않는다.',
     '유사 기록이 없거나 경과가 입력되지 않았거나 정보가 부족한 기록은 제외한다.',
     '지난번에 좋아졌으니 이번에도 효과가 있다는 표현\n과거 기록으로 원인이나 질환을 판단하는 표현',
     '지난 기록은 참고만 하고 오늘 상태에 맞춰 관리하는 편이 좋겠습니다.', 100),

    (UNHEX(LEFT(SHA2('ver:ING-001', 256), 32)), UNHEX(LEFT(SHA2('rule:ING-001', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '면도 후 붉어짐이나 자극이 있는 상태에서 참고할 수 있는 성분을 연결한다.',
     '새 제품 사용 후 상황이거나 안전 분기에 해당하면 성분을 내보내지 않는다.',
     '치료 표현\n개선 보장\n효과 보장\n특정 제품 추천',
     NULL, 100),

    (UNHEX(LEFT(SHA2('ver:ING-002', 256), 32)), UNHEX(LEFT(SHA2('rule:ING-002', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '피부를 짜거나 만진 뒤 붉어짐·돌기가 있는 상태에서 참고할 수 있는 성분을 연결한다.',
     '새 제품 사용 후 상황이거나 안전 분기에 해당하면 성분을 내보내지 않는다.',
     '치료 표현\n흉터가 없어진다는 표현\n효과 보장\n특정 제품 추천',
     NULL, 200),

    (UNHEX(LEFT(SHA2('ver:ING-003', 256), 32)), UNHEX(LEFT(SHA2('rule:ING-003', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '보호장비 착용이나 땀 이후 번들거림·유분이 있는 상태에서 참고할 수 있는 성분을 연결한다.',
     '새 제품 사용 후 상황이거나 안전 분기에 해당하면 성분을 내보내지 않는다.',
     '피지가 완전히 없어진다는 표현\n여드름 치료 표현\n모공이 줄어든다는 표현\n특정 제품 추천',
     NULL, 300),

    (UNHEX(LEFT(SHA2('ver:ING-004', 256), 32)), UNHEX(LEFT(SHA2('rule:ING-004', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '건조하거나 각질이 보이는 상태에서 참고할 수 있는 성분을 연결한다.',
     '새 제품 사용 후 상황이거나 안전 분기에 해당하면 성분을 내보내지 않는다.',
     '장벽이 회복된다는 표현\n재생 표현\n효과 보장\n특정 제품 추천',
     NULL, 400),

    (UNHEX(LEFT(SHA2('ver:FALLBACK-001', 256), 32)), UNHEX(LEFT(SHA2('rule:FALLBACK-001', 256), 32)),
     @rule_set_id, 'v0.3', 'APPROVED',
     '어떤 상황 규칙에도 걸리지 않을 때 단독으로 적용한다. 다른 규칙과 조합하지 않고 성분도 내보내지 않는다.',
     '상황 규칙이 하나라도 걸린 경우와 사용자가 떠오르는 상황이 없다고 직접 고른 경우는 제외한다.',
     '별일 아니라는 임의의 안심\n질환명 추측\n특정 제품 사용 권장',
     '기록에서 구체적인 자극 상황을 찾기 어려워 불필요한 자극을 멈추고 피부를 쉬게 하는 최소 관리 원칙을 안내합니다.', 100)
AS new
ON DUPLICATE KEY UPDATE
    review_status = new.review_status,
    application_summary = new.application_summary,
    exclusion_summary = new.exclusion_summary,
    forbidden_expressions = new.forbidden_expressions,
    fallback_text = new.fallback_text,
    priority = new.priority;

-- ---------------------------------------------------------------------------
-- 4. 규칙 조건
--
-- 같은 condition_group 안의 조건은 AND로 묶이고 그룹끼리는 OR다. 여기서는 모두 그룹 1이므로
-- 한 규칙의 조건은 전부 만족해야 걸린다.
--
-- 조건이 없는 규칙은 항상 걸린다. CR-001(공통)과 FALLBACK-001(폴백)이 그렇다. 폴백은 항상
-- 후보에 오르지만 애플리케이션이 상황 규칙 유무를 보고 단독 실행할 때만 쓴다.
--
-- 현재 상태(ST-*)와 careAvailability 네 값의 대응
--   BEFORE_WASH_CAN_WASH_LATER  아직 세안 전         -> ST-001
--   CAN_CARE_BEFORE_SLEEP       아직 세안 전이지만 취침 전 관리 가능 -> ST-001
--   ALREADY_WASHED              이미 세안함           -> ST-002
--   ADDITIONAL_CARE_DIFFICULT   추가 관리 어려움      -> ST-003
-- 네 값이 빠짐없이 어딘가에 걸리므로 현재 상태 규칙은 항상 하나가 적용된다.
--
-- ING-* 조건에 들어간 situations NOT_CONTAINS_ANY NEW_PRODUCT 는 문서의 ING 공통 호출 조건
-- "SIT-004 새 제품 사용 후에는 성분 미출력"을 규칙 조건으로 옮긴 것이다. 새 제품을 멈추라고
-- 안내하면서 성분을 함께 권하면 서로 어긋난다.
--
-- 겉모습이 기타 단독일 때 성분을 내보내지 않는 것은 별도 조건 없이 성립한다. ING-* 가 모두
-- 특정 겉모습을 요구하므로 기타만 고른 입력에는 어느 ING 규칙도 걸리지 않는다.
-- ---------------------------------------------------------------------------

INSERT INTO rule_conditions (
    id, rule_version_id, condition_group, field_code, operator_code, value_code, negated, display_order
) VALUES
    -- 예상 환경
    (UNHEX(LEFT(SHA2('cond:ENV-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-001', 256), 32)),
     1, 'environments', 'CONTAINS', 'OUTDOOR_TRAINING', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:ENV-002:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-002', 256), 32)),
     1, 'environments', 'CONTAINS', 'NIGHT_OR_SHIFT_DUTY', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:ENV-003:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-003', 256), 32)),
     1, 'environments', 'CONTAINS', 'HOT_AND_HUMID', FALSE, 1),

    -- 상황
    (UNHEX(LEFT(SHA2('cond:SIT-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-001', 256), 32)),
     1, 'situations', 'CONTAINS', 'SHAVING', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:SIT-002:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-002', 256), 32)),
     1, 'situations', 'CONTAINS', 'SWEAT_OR_SEBUM', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:SIT-003:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-003', 256), 32)),
     1, 'situations', 'CONTAINS', 'PROTECTIVE_GEAR_OR_MASK', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:SIT-004:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-004', 256), 32)),
     1, 'situations', 'CONTAINS', 'NEW_PRODUCT', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:SIT-005:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-005', 256), 32)),
     1, 'situations', 'CONTAINS', 'SQUEEZED_ACNE', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:SIT-006:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-006', 256), 32)),
     1, 'situations', 'CONTAINS', 'NONE_RECALLED', FALSE, 1),

    -- 겉모습
    (UNHEX(LEFT(SHA2('cond:APP-REDNESS:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-REDNESS', 256), 32)),
     1, 'appearances', 'CONTAINS', 'APP_REDNESS', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:APP-BUMP:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-BUMP', 256), 32)),
     1, 'appearances', 'CONTAINS', 'APP_BUMP', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:APP-PUS-BUMP:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-PUS-BUMP', 256), 32)),
     1, 'appearances', 'CONTAINS', 'APP_PUS_BUMP', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:APP-DRYNESS:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-DRYNESS', 256), 32)),
     1, 'appearances', 'CONTAINS', 'APP_DRYNESS', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:APP-OILINESS:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-OILINESS', 256), 32)),
     1, 'appearances', 'CONTAINS', 'APP_OILINESS', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:APP-OTHER:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-OTHER', 256), 32)),
     1, 'appearances', 'CONTAINS', 'APP_OTHER', FALSE, 1),

    -- 현재 상태
    (UNHEX(LEFT(SHA2('cond:ST-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-001', 256), 32)),
     1, 'careAvailability', 'CONTAINS_ANY', 'BEFORE_WASH_CAN_WASH_LATER,CAN_CARE_BEFORE_SLEEP', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:ST-002:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-002', 256), 32)),
     1, 'careAvailability', 'CONTAINS', 'ALREADY_WASHED', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:ST-003:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-003', 256), 32)),
     1, 'careAvailability', 'CONTAINS', 'ADDITIONAL_CARE_DIFFICULT', FALSE, 1),

    -- 안전 분기. 사용자가 위험 신호를 하나라도 고른 경우다.
    (UNHEX(LEFT(SHA2('cond:SAF-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SAF-001', 256), 32)),
     1, 'preCareChecks', 'CONTAINS_ANY',
     'SPREADING_RAPIDLY,SEVERE_PAIN_HEAT_SWELLING,PUS_OOZING_BLISTER', FALSE, 1),

    -- 이전 기록
    (UNHEX(LEFT(SHA2('cond:HR-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:HR-001', 256), 32)),
     1, 'completedHistory', 'CONTAINS', 'SIMILAR_EXPERIENCE_FOUND', FALSE, 1),

    -- 성분 ING-001 면도 후 붉어짐·돌기
    (UNHEX(LEFT(SHA2('cond:ING-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-001', 256), 32)),
     1, 'situations', 'CONTAINS', 'SHAVING', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:ING-001:2', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-001', 256), 32)),
     1, 'appearances', 'CONTAINS_ANY', 'APP_REDNESS,APP_BUMP', FALSE, 2),
    (UNHEX(LEFT(SHA2('cond:ING-001:3', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-001', 256), 32)),
     1, 'situations', 'NOT_CONTAINS_ANY', 'NEW_PRODUCT', FALSE, 3),

    -- 성분 ING-002 압출 후 붉어짐·돌기
    (UNHEX(LEFT(SHA2('cond:ING-002:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-002', 256), 32)),
     1, 'situations', 'CONTAINS', 'SQUEEZED_ACNE', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:ING-002:2', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-002', 256), 32)),
     1, 'appearances', 'CONTAINS_ANY', 'APP_REDNESS,APP_BUMP,APP_PUS_BUMP', FALSE, 2),
    (UNHEX(LEFT(SHA2('cond:ING-002:3', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-002', 256), 32)),
     1, 'situations', 'NOT_CONTAINS_ANY', 'NEW_PRODUCT', FALSE, 3),

    -- 성분 ING-003 보호장비·땀 이후 유분
    (UNHEX(LEFT(SHA2('cond:ING-003:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-003', 256), 32)),
     1, 'situations', 'CONTAINS_ANY', 'PROTECTIVE_GEAR_OR_MASK,SWEAT_OR_SEBUM', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:ING-003:2', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-003', 256), 32)),
     1, 'appearances', 'CONTAINS_ANY', 'APP_OILINESS,APP_BUMP', FALSE, 2),
    (UNHEX(LEFT(SHA2('cond:ING-003:3', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-003', 256), 32)),
     1, 'situations', 'NOT_CONTAINS_ANY', 'NEW_PRODUCT', FALSE, 3),

    -- 성분 ING-004 건조·각질
    (UNHEX(LEFT(SHA2('cond:ING-004:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-004', 256), 32)),
     1, 'appearances', 'CONTAINS', 'APP_DRYNESS', FALSE, 1),
    (UNHEX(LEFT(SHA2('cond:ING-004:2', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-004', 256), 32)),
     1, 'situations', 'CONTAINS_ANY', 'SHAVING,SWEAT_OR_SEBUM,PROTECTIVE_GEAR_OR_MASK', FALSE, 2),
    (UNHEX(LEFT(SHA2('cond:ING-004:3', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-004', 256), 32)),
     1, 'situations', 'NOT_CONTAINS_ANY', 'NEW_PRODUCT', FALSE, 3)
AS new
ON DUPLICATE KEY UPDATE
    condition_group = new.condition_group,
    field_code = new.field_code,
    operator_code = new.operator_code,
    value_code = new.value_code,
    negated = new.negated,
    display_order = new.display_order;

-- ---------------------------------------------------------------------------
-- 5. 규칙이 허용하는 행동 문구
--
-- 여기 있는 문구가 결과에 나갈 수 있는 전부다. AI는 이 목록에서 고르기만 하고 새 문장을 쓰지
-- 않는다. 목록에 없는 문장은 저장 직전에 다시 걸러진다.
--
-- 유형별로 두 개까지만 둔다. 결과 항목의 display_order가 1과 2만 허용되고, 오늘 할 일이 다섯
-- 개면 아무것도 하지 않게 된다.
--
-- 모든 문구를 권고형으로 쓴다. 문서가 "할 행동·피할 행동을 명령어가 아닌 권고형으로 수정한다"를
-- 전제로 두었다.
--
-- 겉모습(APP-*)과 성분(ING-*)에는 행동을 달지 않는다. 겉모습은 상태값이고, 성분은 별도 흐름으로
-- recommendedIngredients 자리에 나간다. 성분 문장을 오늘 할 일에 섞으면 관리 행동 두 자리를
-- 성분 안내가 차지한다.
-- ---------------------------------------------------------------------------

INSERT INTO rule_actions (
    id, rule_version_id, action_type, content, priority, display_order, active
) VALUES
    -- CR-001 공통
    (UNHEX(LEFT(SHA2('act:CR-001:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:CR-001', 256), 32)),
     'DO_TODAY',
     '세안이나 샤워 뒤 남은 물기는 수건으로 문지르지 않고 깨끗한 수건을 피부에 가볍게 대어 흡수하는 방법을 고려할 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:CR-001:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:CR-001', 256), 32)),
     'DO_TODAY',
     '피부가 거칠거나 불편하게 느껴져도 손으로 상태를 반복해서 확인하지 않고 그대로 두는 방법을 고려할 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:CR-001:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:CR-001', 256), 32)),
     'AVOID_TODAY', '피부를 문지르거나 긁는 행동은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:CR-001:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:CR-001', 256), 32)),
     'AVOID_TODAY', '트러블이 있는 부위를 짜거나 반복해서 만지는 행동은 피하시는 편이 좋습니다.', 100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:CR-001:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:CR-001', 256), 32)),
     'CHECK_NEXT', '붉어짐과 통증 가려움 붓기가 어제와 비교해 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- ENV-001 야외활동·훈련
    (UNHEX(LEFT(SHA2('act:ENV-001:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-001', 256), 32)),
     'DO_TODAY',
     '씻을 수 있게 되는 시점에 피부를 한 번 정리하고 그 전까지는 피부를 반복해서 만지지 않는 방법을 고려할 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ENV-001:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-001', 256), 32)),
     'AVOID_TODAY', '필요 이상으로 여러 제품을 새로 얹는 것은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ENV-001:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-001', 256), 32)),
     'CHECK_NEXT', '붉어짐과 따가움 가려움 트러블이 이어지거나 심해지는지 확인해 보세요.', 100, 1, TRUE),

    -- ENV-002 야간·교대 일정
    (UNHEX(LEFT(SHA2('act:ENV-002:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-002', 256), 32)),
     'DO_TODAY',
     '실제로 관리할 수 있는 시간을 기준으로 오늘 할 수 있는 만큼만 하는 방법을 고려할 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ENV-002:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-002', 256), 32)),
     'AVOID_TODAY', '시간이 부족한 상황에서 여러 단계를 무리해서 이어 하는 것은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ENV-002:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-002', 256), 32)),
     'CHECK_NEXT', '피부 상태와 함께 수면이나 근무 일정이 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- ENV-003 덥고 습한 환경
    (UNHEX(LEFT(SHA2('act:ENV-003:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-003', 256), 32)),
     'DO_TODAY', '씻을 수 있을 때 피부를 정리하고 땀을 오래 두지 않는 방법을 고려할 수 있습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ENV-003:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-003', 256), 32)),
     'AVOID_TODAY', '피부를 반복해서 만지거나 필요 이상으로 여러 번 세안하는 것은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ENV-003:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-003', 256), 32)),
     'CHECK_NEXT', '유분감과 붉어짐 따가움 트러블이 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- SIT-001 면도 후
    (UNHEX(LEFT(SHA2('act:SIT-001:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-001', 256), 32)),
     'DO_TODAY',
     '면도 후 자극이 있는 부위에는 차가운 물에 적신 깨끗한 수건을 5분 정도 가볍게 대어 피부를 식히는 방법을 고려할 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-001:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-001', 256), 32)),
     'DO_TODAY',
     '오늘 추가 면도가 필요하지 않다면 자극이 가라앉을 때까지 그 부위의 면도를 쉬고 손으로 만지지 않는 방법을 권해 드립니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-001:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-001', 256), 32)),
     'AVOID_TODAY', '같은 부위를 반복해서 면도하는 것은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-001:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-001', 256), 32)),
     'AVOID_TODAY', '털이 자라는 반대 방향으로 면도하는 것은 피하시는 편이 좋습니다.', 100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-001:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-001', 256), 32)),
     'CHECK_NEXT', '붉어짐과 따가움 그리고 불편한 범위가 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- SIT-002 훈련 후 땀·먼지
    (UNHEX(LEFT(SHA2('act:SIT-002:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-002', 256), 32)),
     'DO_TODAY',
     '씻을 수 있게 되면 미지근한 물과 순한 세안제로 한 번 세안하고 손으로 세게 문지르지 않는 방법을 고려할 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-002:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-002', 256), 32)),
     'DO_TODAY',
     '트러블이 반복되는 부위라도 한 번에 여러 번 세안하지 않고 세안 후에는 더 씻지 않는 방법을 고려할 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-002:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-002', 256), 32)),
     'AVOID_TODAY', '땀과 먼지를 없애려고 얼굴을 세게 문지르거나 스크럽하는 것은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-002:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-002', 256), 32)),
     'AVOID_TODAY', '짧은 시간에 반복해서 세안하는 것은 피하시는 편이 좋습니다.', 100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-002:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-002', 256), 32)),
     'CHECK_NEXT', '붉어짐과 가려움 따가움이 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- SIT-003 보호장비·마스크 마찰
    (UNHEX(LEFT(SHA2('act:SIT-003:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-003', 256), 32)),
     'DO_TODAY',
     '붉어지거나 열감이 있는 부위에 차가운 물에 적신 깨끗한 수건을 짧게 대어 식히는 방법이 도움이 될 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-003:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-003', 256), 32)),
     'DO_TODAY',
     '장비를 계속 착용해야 한다면 밀착 상태를 임의로 바꾸지 않고 허용된 휴식 시간에 장비를 벗어 접촉 부위의 땀과 습기를 말리는 방법이 도움이 될 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-003:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-003', 256), 32)),
     'AVOID_TODAY',
     '크림이나 연고가 충분히 흡수되지 않은 상태로 밀착형 보호장비를 착용하는 것은 피하시는 편이 좋습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-003:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-003', 256), 32)),
     'AVOID_TODAY',
     '피부 불편이 있는 상태에서 장비의 밀착 위치를 임의로 바꾸거나 느슨하게 착용하는 것은 피하시는 편이 좋습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-003:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-003', 256), 32)),
     'CHECK_NEXT', '장비가 닿은 부위의 붉어짐과 통증 그리고 범위가 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- SIT-004 새 제품 사용 후
    (UNHEX(LEFT(SHA2('act:SIT-004:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-004', 256), 32)),
     'DO_TODAY',
     '새 제품을 쓴 뒤 불편이 생겼다면 그 제품 사용을 바로 멈추고 반응을 확인하려고 다시 바르지 않는 방법이 도움이 될 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-004:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-004', 256), 32)),
     'DO_TODAY',
     '제품이 남아 있고 씻을 수 있다면 미지근한 흐르는 물로 충분히 씻어낸 뒤 그 부위를 반복해서 문지르지 않는 방법을 고려할 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-004:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-004', 256), 32)),
     'AVOID_TODAY', '새로운 제품을 추가로 얹는 것은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-004:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-004', 256), 32)),
     'AVOID_TODAY', '불편이 생긴 제품을 다시 사용하는 것은 피하시는 편이 좋습니다.', 100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-004:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-004', 256), 32)),
     'CHECK_NEXT', '붉어짐과 가려움 따가움 붓기가 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- SIT-005 짜거나 만진 후
    (UNHEX(LEFT(SHA2('act:SIT-005:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-005', 256), 32)),
     'DO_TODAY',
     '짜거나 만진 직후 출혈이 있다면 깨끗한 거즈로 문지르지 않고 가볍게 눌러 지혈하는 방법을 고려할 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-005:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-005', 256), 32)),
     'DO_TODAY',
     '지혈된 뒤에는 그 부위를 그대로 두고 남은 내용물을 더 빼내려고 누르지 않는 방법이 도움이 될 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-005:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-005', 256), 32)),
     'AVOID_TODAY', '같은 부위를 다시 짜는 것은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-005:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-005', 256), 32)),
     'AVOID_TODAY', '그 부위를 반복해서 만지는 것은 피하시는 편이 좋습니다.', 100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-005:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-005', 256), 32)),
     'CHECK_NEXT', '붉어짐과 통증 붓기 출혈 진물이 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- SIT-006 떠오르는 상황 없음
    (UNHEX(LEFT(SHA2('act:SIT-006:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-006', 256), 32)),
     'DO_TODAY',
     '샤워기 물을 얼굴에 바로 맞히지 않고 미지근한 물로 손끝으로 가볍게 헹군 뒤 깨끗한 수건을 대어 물기만 흡수하는 방법이 도움이 될 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-006:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-006', 256), 32)),
     'DO_TODAY',
     '세안 후 물기가 마른 직후 얼굴을 비비지 않고 평소 쓰던 제품이 있다면 가볍게 얹어 두고 그대로 두는 방법을 고려할 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-006:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-006', 256), 32)),
     'AVOID_TODAY',
     '원인을 찾으려고 거울을 보며 트러블을 손톱으로 만지거나 뜯는 것은 피하시는 편이 좋습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-006:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-006', 256), 32)),
     'AVOID_TODAY',
     '찝찝하다는 이유로 얼굴을 강하게 문지르거나 하루 두 번을 넘겨 반복해서 세안하는 것은 피하시는 편이 좋습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:SIT-006:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-006', 256), 32)),
     'CHECK_NEXT',
     '자극을 줄인 상태로 하룻밤 지난 뒤 당김과 거칠어짐 붉은기가 가라앉았는지 확인해 보세요.',
     100, 1, TRUE),

    -- ST-001 아직 세안 전
    (UNHEX(LEFT(SHA2('act:ST-001:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-001', 256), 32)),
     'DO_TODAY',
     '샤워기 물을 얼굴에 직접 세게 맞히지 않고 미지근한 흐르는 물로 적신 뒤 손끝으로 세안하는 방법이 도움이 될 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-001:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-001', 256), 32)),
     'DO_TODAY',
     '잘 지워지지 않는 잔여물이 있다면 한 번에 세게 문지르지 않고 세안제를 손끝으로 충분히 펴 바른 뒤 미지근한 물로 헹구는 방법이 도움이 될 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-001:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-001', 256), 32)),
     'AVOID_TODAY', '얼굴을 세게 문지르는 것은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-001:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-001', 256), 32)),
     'CHECK_NEXT', '세안한 뒤 피부 불편이 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- ST-002 이미 세안함
    (UNHEX(LEFT(SHA2('act:ST-002:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-002', 256), 32)),
     'DO_TODAY',
     '세안 후에도 찝찝하다고 다시 씻지 않고 건조하거나 당긴다면 평소 쓰던 제품만 평소 양으로 바르는 방법이 도움이 될 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-002:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-002', 256), 32)),
     'DO_TODAY',
     '세안 후 특정 부위가 따갑거나 붉어졌다면 다시 닦지 않고 그대로 두면서 변화를 지켜보는 방법을 고려할 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-002:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-002', 256), 32)),
     'AVOID_TODAY', '유분을 없애려고 반복해서 세안하는 것은 피하시는 편이 좋습니다.', 100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-002:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-002', 256), 32)),
     'AVOID_TODAY', '스크럽 등으로 각질을 물리적으로 밀어내는 것은 피하시는 편이 좋습니다.', 100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-002:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-002', 256), 32)),
     'CHECK_NEXT',
     '세안 후 생긴 붉어짐과 따가움 당김이 줄었는지 그리고 새로운 자극 부위가 생겼는지 확인해 보세요.',
     100, 1, TRUE),

    -- ST-003 오늘 추가 관리가 어려움
    (UNHEX(LEFT(SHA2('act:ST-003:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-003', 256), 32)),
     'DO_TODAY',
     '씻을 수 없는 동안 유분이나 땀이 많이 느껴진다면 깨끗한 기름종이를 문지르지 않고 가볍게 눌러 유분만 걷어내는 방법이 도움이 될 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-003:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-003', 256), 32)),
     'DO_TODAY',
     '씻을 수 없는 동안에는 손이나 수건으로 얼굴을 반복해서 닦지 않고 씻을 수 있는 첫 시점에 평소 방식으로 한 번 세안하는 방법이 도움이 될 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-003:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-003', 256), 32)),
     'AVOID_TODAY',
     '씻을 수 없다는 이유로 물티슈나 수건으로 얼굴을 반복해서 닦는 것은 피하시는 편이 좋습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-003:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-003', 256), 32)),
     'AVOID_TODAY', '새 제품과 각질제거 압출을 한꺼번에 시도하는 것은 피하시는 편이 좋습니다.', 100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:ST-003:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-003', 256), 32)),
     'CHECK_NEXT', '관리할 수 있게 된 뒤 피부가 어떻게 바뀌었는지 확인해 보세요.', 100, 1, TRUE),

    -- SAF-001 의료진 확인 우선
    (UNHEX(LEFT(SHA2('act:SAF-001:CLINICIAN_MESSAGE:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SAF-001', 256), 32)),
     'CLINICIAN_MESSAGE',
     '지금 남겨 주신 변화는 혼자 관리하기보다 상태를 직접 확인받는 편이 안전합니다. 부대 내 의무실처럼 이용할 수 있는 의료진에게 상태를 보여 주시고 그 전까지는 해당 부위를 그대로 두시는 것을 권해 드립니다. 여기서는 어떤 질환인지 판단하지 않습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SAF-001:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SAF-001', 256), 32)),
     'DO_TODAY',
     '추가로 짜거나 긁거나 문지르지 않고 새 제품도 얹지 않은 채 해당 부위를 그대로 두시는 것을 권해 드립니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SAF-001:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SAF-001', 256), 32)),
     'AVOID_TODAY',
     '질환명을 스스로 판단하거나 인터넷 정보만으로 연고나 치료제를 임의로 쓰는 것은 피해 주세요.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:SAF-001:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SAF-001', 256), 32)),
     'CHECK_NEXT',
     '의료진 확인 이후 범위와 통증 부종 열감 진물이 어떻게 바뀌었는지 확인해 보세요.',
     100, 1, TRUE),

    -- HR-001 유사 과거 기록
    (UNHEX(LEFT(SHA2('act:HR-001:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:HR-001', 256), 32)),
     'DO_TODAY',
     '지난 비슷한 기록에서 어떤 행동을 했고 다음 날 어떻게 바뀌었는지 함께 살펴보시면 도움이 될 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:HR-001:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:HR-001', 256), 32)),
     'AVOID_TODAY',
     '지난번에 좋아졌다는 이유만으로 같은 행동을 그대로 반복하는 것은 피하시는 편이 좋습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:HR-001:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:HR-001', 256), 32)),
     'CHECK_NEXT',
     '오늘 한 행동과 함께 붉어짐과 통증 붓기 열감 불편감의 변화를 기록해 두세요.',
     100, 1, TRUE),

    -- FALLBACK-001 미인식 예외
    (UNHEX(LEFT(SHA2('act:FALLBACK-001:DO_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:FALLBACK-001', 256), 32)),
     'DO_TODAY',
     '토너와 앰플 패치 같은 여러 단계를 생략하고 평소 트러블이 없던 순한 기본 로션 한 가지만 소량 얇게 바르는 방법을 고려할 수 있습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:FALLBACK-001:DO_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:FALLBACK-001', 256), 32)),
     'DO_TODAY',
     '흡수시키려고 얼굴을 두드리거나 문지르지 않고 가볍게 얹어 둔 뒤 잠들기 전까지 피부에 손을 대지 않는 방법이 도움이 될 수 있습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:FALLBACK-001:AVOID_TODAY:1', 256), 32)), UNHEX(LEFT(SHA2('ver:FALLBACK-001', 256), 32)),
     'AVOID_TODAY',
     '원인을 모르는 상태에서 평소 쓰지 않던 연고나 패치 기능성 제품을 임의로 시도하는 것은 피하시는 편이 좋습니다.',
     100, 1, TRUE),
    (UNHEX(LEFT(SHA2('act:FALLBACK-001:AVOID_TODAY:2', 256), 32)), UNHEX(LEFT(SHA2('ver:FALLBACK-001', 256), 32)),
     'AVOID_TODAY',
     '불편을 없애려고 샤워 타월이나 손가락으로 그 부위를 긁거나 문지르는 것은 피하시는 편이 좋습니다.',
     100, 2, TRUE),
    (UNHEX(LEFT(SHA2('act:FALLBACK-001:CHECK_NEXT:1', 256), 32)), UNHEX(LEFT(SHA2('ver:FALLBACK-001', 256), 32)),
     'CHECK_NEXT',
     '아무 조치도 더하지 않고 쉬게 둔 뒤 붉어짐과 가려움 통증이 스스로 가라앉는지 확인해 보세요.',
     100, 1, TRUE)
AS new
ON DUPLICATE KEY UPDATE
    action_type = new.action_type,
    content = new.content,
    priority = new.priority,
    display_order = new.display_order,
    active = new.active;

-- ---------------------------------------------------------------------------
-- 6. 성분 사전
--
-- 성분 코드는 care_ingredients.ingredient_code의 CHECK가 대문자와 숫자 밑줄만 허용한다.
-- 그래서 규칙 코드처럼 하이픈을 쓰지 않고 명세 예시(ING_PANTHENOL)를 따른다.
--
-- 설명은 화장품에 쓰이는 일반적인 목적만 적는다. 효능 보장과 치료 표현 그리고 제품 추천은
-- 명세가 금지했다. 여기 적은 문장이 그대로 결과에 나가므로 문구 자체가 그 제약을 지켜야 한다.
-- ---------------------------------------------------------------------------

INSERT INTO care_ingredients (id, ingredient_code, name, description, caution_note, active) VALUES
    (UNHEX(LEFT(SHA2('ing:ING_PANTHENOL', 256), 32)), 'ING_PANTHENOL', '판테놀',
     '보습과 피부 장벽 유지를 목적으로 화장품에 쓰이는 성분입니다.', NULL, TRUE),
    (UNHEX(LEFT(SHA2('ing:ING_ALLANTOIN', 256), 32)), 'ING_ALLANTOIN', '알란토인',
     '피부 컨디셔닝을 목적으로 화장품에 쓰이는 성분입니다.', NULL, TRUE),
    (UNHEX(LEFT(SHA2('ing:ING_CENTELLA', 256), 32)), 'ING_CENTELLA', '병풀추출물',
     '피부 컨디셔닝과 수분 유지를 목적으로 화장품에 쓰이는 성분입니다.', NULL, TRUE),
    (UNHEX(LEFT(SHA2('ing:ING_MADECASSOSIDE', 256), 32)), 'ING_MADECASSOSIDE', '마데카소사이드',
     '자극을 받은 피부의 장벽 관리를 돕는 목적으로 화장품에 쓰이는 성분입니다.', NULL, TRUE),
    (UNHEX(LEFT(SHA2('ing:ING_SALICYLIC_ACID', 256), 32)), 'ING_SALICYLIC_ACID', '살리실릭애씨드',
     '각질 정돈과 유분 관리를 목적으로 화장품에 쓰이는 성분입니다.',
     '피부가 예민한 상태에서는 자극이 느껴질 수 있습니다. 쓰는 중 불편하면 사용을 멈춰 주세요.', TRUE),
    (UNHEX(LEFT(SHA2('ing:ING_SILICA', 256), 32)), 'ING_SILICA', '실리카',
     '피부 표면의 유분과 땀을 흡착해 번들거림을 줄이는 목적으로 화장품에 쓰이는 성분입니다.', NULL, TRUE),
    (UNHEX(LEFT(SHA2('ing:ING_CERAMIDE_NP', 256), 32)), 'ING_CERAMIDE_NP', '세라마이드엔피',
     '각질층의 수분 유지를 돕는 목적으로 화장품에 쓰이는 성분입니다.', NULL, TRUE),
    (UNHEX(LEFT(SHA2('ing:ING_SQUALANE', 256), 32)), 'ING_SQUALANE', '스쿠알란',
     '수분 손실을 줄이는 목적으로 화장품에 쓰이는 성분입니다.', NULL, TRUE)
AS new
ON DUPLICATE KEY UPDATE
    name = new.name,
    description = new.description,
    caution_note = new.caution_note,
    active = new.active;

-- ---------------------------------------------------------------------------
-- 7. 규칙 버전과 성분 연결
--
-- 한 규칙에 두 개씩 붙인다. 결과에 담기는 성분 상한도 두 개다. 규칙이 여러 개 걸리면 우선순위가
-- 앞선 규칙의 성분이 남는다.
-- ---------------------------------------------------------------------------

INSERT INTO rule_version_ingredients (rule_version_id, ingredient_id, display_order) VALUES
    -- ING-001 면도 후 자극
    (UNHEX(LEFT(SHA2('ver:ING-001', 256), 32)), UNHEX(LEFT(SHA2('ing:ING_PANTHENOL', 256), 32)), 1),
    (UNHEX(LEFT(SHA2('ver:ING-001', 256), 32)), UNHEX(LEFT(SHA2('ing:ING_ALLANTOIN', 256), 32)), 2),

    -- ING-002 압출 후 자극
    (UNHEX(LEFT(SHA2('ver:ING-002', 256), 32)), UNHEX(LEFT(SHA2('ing:ING_CENTELLA', 256), 32)), 1),
    (UNHEX(LEFT(SHA2('ver:ING-002', 256), 32)), UNHEX(LEFT(SHA2('ing:ING_MADECASSOSIDE', 256), 32)), 2),

    -- ING-003 보호장비 후 유분·땀
    (UNHEX(LEFT(SHA2('ver:ING-003', 256), 32)), UNHEX(LEFT(SHA2('ing:ING_SALICYLIC_ACID', 256), 32)), 1),
    (UNHEX(LEFT(SHA2('ver:ING-003', 256), 32)), UNHEX(LEFT(SHA2('ing:ING_SILICA', 256), 32)), 2),

    -- ING-004 건조·각질
    (UNHEX(LEFT(SHA2('ver:ING-004', 256), 32)), UNHEX(LEFT(SHA2('ing:ING_CERAMIDE_NP', 256), 32)), 1),
    (UNHEX(LEFT(SHA2('ver:ING-004', 256), 32)), UNHEX(LEFT(SHA2('ing:ING_SQUALANE', 256), 32)), 2)
AS new
ON DUPLICATE KEY UPDATE
    display_order = new.display_order;

-- ---------------------------------------------------------------------------
-- 8. 근거 출처
--
-- reviewed_at과 reviewed_by를 비워 둔다. 근거는 모아 두었지만 전문가·멘토 검토는 끝나지
-- 않았다는 뜻이다. ck_rule_evidence_sources_review_pair가 두 컬럼을 함께 채우거나 함께
-- 비우도록 강제하므로, 검토가 끝나면 두 값을 같이 넣는다.
--
-- 규칙마다 대표 근거 하나씩만 남긴다. 문서에는 규칙별로 여러 개가 붙어 있지만, 여기 넣은
-- 것은 운영 중에 근거를 되짚을 때 첫 갈래로 쓸 링크다. 나머지는 문서를 정본으로 둔다.
-- ---------------------------------------------------------------------------

INSERT INTO rule_evidence_sources (id, rule_version_id, title, source_url, source_type) VALUES
    (UNHEX(LEFT(SHA2('evi:CR-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:CR-001', 256), 32)),
     'American Academy of Dermatology - Face washing 101',
     'https://www.aad.org/public/everyday-care/skin-care-basics/care/face-washing-101', 'GUIDELINE'),

    (UNHEX(LEFT(SHA2('evi:ENV-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-001', 256), 32)),
     '군 복무 경험자 인터뷰. 전문가 검수 필요', NULL, 'OTHER'),
    (UNHEX(LEFT(SHA2('evi:ENV-002:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-002', 256), 32)),
     '군 복무 경험자 인터뷰. 전문가 검수 필요', NULL, 'OTHER'),
    (UNHEX(LEFT(SHA2('evi:ENV-003:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ENV-003', 256), 32)),
     '군 복무 경험자 인터뷰. 전문가 검수 필요', NULL, 'OTHER'),

    (UNHEX(LEFT(SHA2('evi:SIT-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-001', 256), 32)),
     'American Academy of Dermatology - Razor bump remedies',
     'https://www.aad.org/public/everyday-care/skin-care-basics/hair/razor-bump-remedies', 'GUIDELINE'),
    (UNHEX(LEFT(SHA2('evi:SIT-002:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-002', 256), 32)),
     'American Academy of Dermatology - Is your workout causing your acne',
     'https://www.aad.org/public/diseases/acne/causes/could-your-workout-cause-acne', 'GUIDELINE'),
    (UNHEX(LEFT(SHA2('evi:SIT-003:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-003', 256), 32)),
     'Skin reactions related to personal protective equipment. PubMed 38771104',
     'https://pubmed.ncbi.nlm.nih.gov/38771104/', 'JOURNAL'),
    (UNHEX(LEFT(SHA2('evi:SIT-004:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-004', 256), 32)),
     'American Academy of Dermatology - How to test skin care products',
     'https://www.aad.org/public/everyday-care/skin-care-secrets/prevent-skin-problems/test-skin-care-products',
     'GUIDELINE'),
    (UNHEX(LEFT(SHA2('evi:SIT-005:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-005', 256), 32)),
     'American Academy of Dermatology - Acne scars causes',
     'https://www.aad.org/public/diseases/acne/derm-treat/scars/causes', 'GUIDELINE'),
    (UNHEX(LEFT(SHA2('evi:SIT-006:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SIT-006', 256), 32)),
     'American Academy of Dermatology - Face washing 101',
     'https://www.aad.org/public/everyday-care/skin-care-basics/care/face-washing-101', 'GUIDELINE'),

    (UNHEX(LEFT(SHA2('evi:APP-REDNESS:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-REDNESS', 256), 32)),
     'Shaving and skin irritation findings. PubMed 30909328',
     'https://pubmed.ncbi.nlm.nih.gov/30909328/', 'JOURNAL'),
    (UNHEX(LEFT(SHA2('evi:APP-BUMP:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-BUMP', 256), 32)),
     'Mechanical irritation and inflammatory papules. PubMed 26069089',
     'https://pubmed.ncbi.nlm.nih.gov/26069089/', 'JOURNAL'),
    (UNHEX(LEFT(SHA2('evi:APP-PUS-BUMP:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-PUS-BUMP', 256), 32)),
     'Papule and pustule are distinct lesion types. PubMed 30909328',
     'https://pubmed.ncbi.nlm.nih.gov/30909328/', 'JOURNAL'),
    (UNHEX(LEFT(SHA2('evi:APP-DRYNESS:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-DRYNESS', 256), 32)),
     'Mask related dryness and scaling. PubMed 34450685',
     'https://pubmed.ncbi.nlm.nih.gov/34450685/', 'JOURNAL'),
    (UNHEX(LEFT(SHA2('evi:APP-OILINESS:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-OILINESS', 256), 32)),
     'Sweat and occlusion in exercise related skin conditions. PubMed 30883890',
     'https://pubmed.ncbi.nlm.nih.gov/30883890/', 'JOURNAL'),
    (UNHEX(LEFT(SHA2('evi:APP-OTHER:1', 256), 32)), UNHEX(LEFT(SHA2('ver:APP-OTHER', 256), 32)),
     '다섯 가지로 설명하기 어려운 외관을 담는 자리. 별도 임상 근거를 두지 않는다', NULL, 'OTHER'),

    (UNHEX(LEFT(SHA2('evi:ST-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-001', 256), 32)),
     'American Academy of Dermatology - Face washing 101',
     'https://www.aad.org/public/everyday-care/skin-care-basics/care/face-washing-101', 'GUIDELINE'),
    (UNHEX(LEFT(SHA2('evi:ST-002:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-002', 256), 32)),
     'American Academy of Dermatology - 10 skin care habits that can worsen acne',
     'https://www.aad.org/public/diseases/acne/skin-care/habits-stop', 'GUIDELINE'),
    (UNHEX(LEFT(SHA2('evi:ST-003:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ST-003', 256), 32)),
     'American Academy of Dermatology - Acne skin care tips',
     'https://www.aad.org/public/diseases/acne/skin-care/tips', 'GUIDELINE'),

    (UNHEX(LEFT(SHA2('evi:SAF-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:SAF-001', 256), 32)),
     'NHS - Cellulitis',
     'https://www.nhs.uk/conditions/cellulitis/', 'PUBLIC_HEALTH'),
    (UNHEX(LEFT(SHA2('evi:SAF-001:2', 256), 32)), UNHEX(LEFT(SHA2('ver:SAF-001', 256), 32)),
     'NHS - Skin abscess',
     'https://www.nhs.uk/conditions/skin-abscess/', 'PUBLIC_HEALTH'),

    (UNHEX(LEFT(SHA2('evi:HR-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:HR-001', 256), 32)),
     '사용자 본인의 과거 기록', NULL, 'USER_RECORD'),

    (UNHEX(LEFT(SHA2('evi:ING-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-001', 256), 32)),
     'Panthenol in skin barrier and moisturization. PubMed 27425824',
     'https://pubmed.ncbi.nlm.nih.gov/27425824/', 'JOURNAL'),
    (UNHEX(LEFT(SHA2('evi:ING-002:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-002', 256), 32)),
     'Centella asiatica in cosmetology. PMC3834700',
     'https://pmc.ncbi.nlm.nih.gov/articles/PMC3834700/', 'JOURNAL'),
    (UNHEX(LEFT(SHA2('evi:ING-003:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-003', 256), 32)),
     'Salicylic acid as a peeling agent. PMC4554394',
     'https://pmc.ncbi.nlm.nih.gov/articles/PMC4554394/', 'JOURNAL'),
    (UNHEX(LEFT(SHA2('evi:ING-004:1', 256), 32)), UNHEX(LEFT(SHA2('ver:ING-004', 256), 32)),
     'The role of ceramides in skin barrier function. PubMed 29396788',
     'https://pubmed.ncbi.nlm.nih.gov/29396788/', 'JOURNAL'),

    (UNHEX(LEFT(SHA2('evi:FALLBACK-001:1', 256), 32)), UNHEX(LEFT(SHA2('ver:FALLBACK-001', 256), 32)),
     'American Academy of Dermatology - Skin care routine for sensitive skin',
     'https://www.aad.org/public/everyday-care/skin-care-secrets/routine/sensitive-skin', 'GUIDELINE')
AS new
ON DUPLICATE KEY UPDATE
    title = new.title,
    source_url = new.source_url,
    source_type = new.source_type;

-- ---------------------------------------------------------------------------
-- 적용 후 확인
--
--   SELECT category, COUNT(*) FROM care_rules GROUP BY category;          -- 합계 26
--   SELECT COUNT(*) FROM care_rule_versions WHERE review_status='APPROVED'; -- 26
--   SELECT COUNT(*) FROM care_ingredients WHERE active = TRUE;             -- 8
--   SELECT version_code, status FROM rule_sets;                            -- v0.3-mvp ACTIVE
--
-- 검토가 끝나면 할 일
--   UPDATE rule_evidence_sources SET reviewed_at = NOW(6), reviewed_by = '<검토자>'
--    WHERE rule_version_id = UNHEX(LEFT(SHA2('ver:SAF-001', 256), 32));
--   그리고 APP-PUS-BUMP 선택지를 프론트 화면에 되살린다. 백엔드 선택값은 계속 열려 있다.
-- ---------------------------------------------------------------------------
