package likelion.flourishing.domain.report.service;

import java.util.ArrayList;
import java.util.List;
import likelion.flourishing.domain.report.ai.NarrationOutcome;
import likelion.flourishing.domain.report.entity.CareResultItemType;
import likelion.flourishing.domain.report.entity.RuleActionType;
import likelion.flourishing.domain.report.rule.CareActionAllowList;
import likelion.flourishing.domain.report.rule.RuleActionSnapshot;
import org.springframework.stereotype.Component;

/**
 * 결과에 실제로 담을 항목을 정한다.
 *
 * <p>어느 경로로 오든 문구는 규칙 허용 목록에서만 나온다. AI가 고른 경우에도 목록에서 원래 행동을
 * 되찾아 출처를 남기고, 목록에 없는 문구는 애초에 여기까지 오지 않는다.
 *
 * <p>유형별 최대 두 개다. 오늘 할 일이 다섯 개면 아무것도 안 하게 되고, DDL도 display_order를
 * 1과 2로만 허용한다.
 */
@Component
public class CareGuideItemPlanner {

    /** DDL의 ck_care_result_items_order와 같은 상한. */
    public static final int MAX_ITEMS_PER_TYPE = 2;

    private static final List<RuleActionType> DISPLAY_ACTION_TYPES = List.of(
            RuleActionType.DO_TODAY,
            RuleActionType.AVOID_TODAY,
            RuleActionType.CHECK_NEXT
    );

    /**
     * AI가 고른 문구로 항목을 만든다.
     *
     * <p>고른 문구를 허용 목록에서 다시 찾아 출처를 붙인다. 찾지 못하면 목록 밖의 문구이므로
     * 항목을 만들지 않고 건너뛴다. 검증을 통과한 응답이라 실제로는 여기 걸리지 않지만, 저장
     * 직전에 한 번 더 막아 규칙 밖 문구가 DB에 들어갈 경로를 남기지 않는다.
     */
    public List<PlannedCareItem> planFromNarration(CareActionAllowList allowList, NarrationOutcome narration) {
        List<PlannedCareItem> planned = new ArrayList<>();
        planned.addAll(fromContents(allowList, RuleActionType.DO_TODAY, narration.doToday()));
        planned.addAll(fromContents(allowList, RuleActionType.AVOID_TODAY, narration.avoidToday()));
        planned.addAll(fromContents(allowList, RuleActionType.CHECK_NEXT, narration.checkNext()));
        return planned;
    }

    /**
     * 규칙 우선순위대로 앞에서부터 채운다.
     *
     * <p>AI 설명 생성이 실패했을 때 쓰는 경로다. 무엇을 고를지 판단하는 주체가 없어도 규칙이 정한
     * 순서가 있어 결과는 항상 같다.
     */
    public List<PlannedCareItem> planFromRules(CareActionAllowList allowList) {
        List<PlannedCareItem> planned = new ArrayList<>();
        for (RuleActionType actionType : DISPLAY_ACTION_TYPES) {
            List<RuleActionSnapshot> actions = allowList.topOf(actionType, MAX_ITEMS_PER_TYPE);
            for (int index = 0; index < actions.size(); index++) {
                RuleActionSnapshot action = actions.get(index);
                planned.add(new PlannedCareItem(
                        actionType.toItemType(), action.content(), action.actionId(), index + 1
                ));
            }
        }
        return planned;
    }

    /**
     * 의료진 확인 안내 문구.
     *
     * <p>승인된 문구가 없으면 빈 목록이다. 호출한 쪽은 이때 결과를 만들지 않고 503으로 답해야
     * 한다. 의료진 확인이 필요하다고 판단했는데 안내 문구를 우리가 지어내면 안 된다.
     */
    public List<PlannedCareItem> planClinicianMessage(CareActionAllowList allowList) {
        List<RuleActionSnapshot> actions = allowList.topOf(RuleActionType.CLINICIAN_MESSAGE, 1);
        if (actions.isEmpty()) {
            return List.of();
        }
        RuleActionSnapshot action = actions.getFirst();
        return List.of(new PlannedCareItem(
                CareResultItemType.CLINICIAN_MESSAGE, action.content(), action.actionId(), 1
        ));
    }

    private List<PlannedCareItem> fromContents(
            CareActionAllowList allowList,
            RuleActionType actionType,
            List<String> contents
    ) {
        List<PlannedCareItem> planned = new ArrayList<>();
        for (String content : contents) {
            if (planned.size() >= MAX_ITEMS_PER_TYPE) {
                break;
            }
            allowList.findByContent(actionType, content).ifPresent(action -> planned.add(new PlannedCareItem(
                    actionType.toItemType(), content, action.actionId(), planned.size() + 1
            )));
        }
        return planned;
    }
}
