-- 명세 v2_1의 경과 변경을 기존 데이터에 적용하는 선행 마이그레이션
-- 대상: db/schema.sql의 ck_follow_ups_skin_change, ck_follow_ups_type_payload
--
-- 이 스크립트를 먼저 돌리지 않으면 두 CHECK 제약을 만들 수 없어 스키마 적용이 실패한다.
-- 실패 시점이 배포 중이라 조용히 통과하는 것보다 낫지만, 그때 손대는 것보다 미리 도는 편이 낫다.
--
-- v2_1이 바꾼 것
--   1. SkinChange에서 NEW_AREA, UNSURE가 빠져 세 개(IMPROVED/SIMILAR/WORSENED)가 됐다.
--   2. action_completion이 종류와 무관하게 필수가 됐다. v1에서 CLINICIAN_CHECK로 저장된
--      행은 이 값이 NULL이다.
--
-- 어떻게 옮기는가
--   두 경우 모두 사용자가 실제로 고르지 않은 답을 서버가 지어내야 하므로 값을 변환하지 않는다.
--   명세 "하위 호환 안내"의 두 선택지 중 EXPIRED 취급을 택했다.
--
--   > SkinChange: NEW_AREA, UNSURE 삭제. 과거 기록은 EXPIRED 취급하거나 유사 경험 후보
--   > 대상에서 제외할 것.
--
--   경과 행을 지우고 그 보고를 EXPIRED로 내린다. 유사 경험 후보는 경과가 있는 보고에서
--   고르므로 행을 지우면 후보에서도 함께 빠진다(PR #20 SimilarExperience와 어긋나지 않는다).
--
-- 여러 번 돌려도 결과가 같다. 옮길 행이 없으면 아무것도 바꾸지 않는다.

START TRANSACTION;

-- 1) 새 제약을 만족할 수 없는 경과를 가려낸다.
CREATE TEMPORARY TABLE tmp_v2_1_unmigratable_follow_ups AS
SELECT id, report_id
FROM follow_ups
WHERE skin_change IN ('NEW_AREA', 'UNSURE')
   OR action_completion IS NULL;

-- 2) 해당 보고를 EXPIRED로 내린다. 경과를 지운 뒤에는 다시 입력할 수 없는 상태여야 한다.
UPDATE skin_reports r
   JOIN tmp_v2_1_unmigratable_follow_ups t ON t.report_id = r.id
   SET r.status = 'EXPIRED'
 WHERE r.status <> 'EXPIRED';

-- 3) 경과 행을 지운다.
DELETE f
  FROM follow_ups f
  JOIN tmp_v2_1_unmigratable_follow_ups t ON t.id = f.id;

DROP TEMPORARY TABLE tmp_v2_1_unmigratable_follow_ups;

COMMIT;

-- 확인: 두 값 모두 0이어야 새 CHECK 제약을 만들 수 있다.
SELECT
    SUM(skin_change IN ('NEW_AREA', 'UNSURE')) AS remaining_old_skin_change,
    SUM(action_completion IS NULL)             AS remaining_null_action_completion
FROM follow_ups;
