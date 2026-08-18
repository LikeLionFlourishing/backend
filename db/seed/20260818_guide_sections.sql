-- 결과 카드 가이드 섹션의 기본 제목·설명 (명세 v2_1 개선 요약 8번)
-- 관련 이슈: #25
--
-- 여섯 개 키는 명세 GuideSection.key 가 고정한 값이라 행이 없으면 결과 카드와
-- /v1/reference-data/skin-report-options 의 guideSections 가 빈 배열로 나간다.
-- 문구는 운영 중에 다듬을 수 있고, 키와 순서는 스키마가 고정한다.
--
-- 재실행해도 안전하다. 이미 있으면 문구만 최신으로 맞춘다.

USE flourishing;

SET NAMES utf8mb4;

INSERT INTO guide_sections (section_key, title, description, display_order) VALUES
    ('CURRENT_SUMMARY', '지금 상태',
     '오늘 남긴 기록을 두 문장으로 정리한 내용입니다.', 1),
    ('DO_TODAY', '오늘 할 일',
     '지금 상태에서 오늘 시도해볼 수 있는 행동입니다.', 2),
    ('AVOID_TODAY', '오늘 피할 일',
     '지금 상태에서 오늘은 하지 않는 편이 나은 행동입니다.', 3),
    ('SIMILAR_EXPERIENCE', '이전 비슷한 경험',
     '지금과 비슷했던 지난 기록과 그때의 변화입니다.', 4),
    ('CHECK_NEXT', '다음에 확인할 변화',
     '내일 다시 볼 때 눈여겨볼 지점입니다.', 5),
    ('RECOMMENDED_INGREDIENTS', '추천 성분 보기',
     '관리 규칙에서 고른 성분 정보입니다. 특정 제품이나 의약품을 권하지 않습니다.', 6)
AS new
ON DUPLICATE KEY UPDATE
    title = new.title,
    description = new.description,
    display_order = new.display_order;
