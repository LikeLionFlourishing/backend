package likelion.flourishing.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import likelion.flourishing.domain.report.repository.CareResultIngredientRepository;
import likelion.flourishing.domain.report.repository.CareResultIngredientRuleRepository;
import likelion.flourishing.domain.report.service.GuideSectionFixtures;
import likelion.flourishing.domain.report.entity.CareResultIngredient;
import likelion.flourishing.domain.report.entity.CareResultIngredientRule;
import likelion.flourishing.domain.report.entity.GuideSectionKey;
import likelion.flourishing.domain.report.entity.CareRule;
import likelion.flourishing.domain.report.entity.CareRuleVersion;
import likelion.flourishing.domain.report.entity.CareResultRule;
import likelion.flourishing.domain.report.entity.MatchReason;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import likelion.flourishing.domain.auth.security.AuthenticatedUser;
import likelion.flourishing.domain.followup.entity.ActionCompletion;
import likelion.flourishing.domain.followup.entity.FollowUp;
import likelion.flourishing.domain.followup.entity.FollowUpKind;
import likelion.flourishing.domain.followup.entity.SkinChange;
import likelion.flourishing.domain.followup.repository.FollowUpRepository;
import likelion.flourishing.domain.record.cursor.SkinReportCursor;
import likelion.flourishing.domain.record.cursor.SkinReportCursorCodec;
import likelion.flourishing.domain.record.dto.response.SkinReportDetailResponse;
import likelion.flourishing.domain.record.dto.response.SkinReportListResponse;
import likelion.flourishing.domain.report.crypto.ReportTextCipher;
import likelion.flourishing.domain.report.entity.AiGenerationStatus;
import likelion.flourishing.domain.report.entity.Appearance;
import likelion.flourishing.domain.report.entity.BodyArea;
import likelion.flourishing.domain.report.entity.CareAvailability;
import likelion.flourishing.domain.report.entity.CareResult;
import likelion.flourishing.domain.report.entity.CareResultItem;
import likelion.flourishing.domain.report.entity.CareResultItemType;
import likelion.flourishing.domain.report.entity.PreCareCheck;
import likelion.flourishing.domain.report.entity.ReportStatus;
import likelion.flourishing.domain.report.entity.ResultType;
import likelion.flourishing.domain.report.entity.RuleSet;
import likelion.flourishing.domain.report.entity.Sensation;
import likelion.flourishing.domain.report.entity.Situation;
import likelion.flourishing.domain.report.entity.SkinReport;
import likelion.flourishing.domain.report.repository.CareResultItemRepository;
import likelion.flourishing.domain.report.repository.CareResultRepository;
import likelion.flourishing.domain.report.repository.CareResultRuleRepository;
import likelion.flourishing.domain.report.repository.CareRuleRepository;
import likelion.flourishing.domain.report.repository.CareRuleVersionRepository;
import likelion.flourishing.domain.report.repository.RuleSetRepository;
import likelion.flourishing.domain.report.repository.SkinReportRepository;
import likelion.flourishing.global.exception.BusinessException;
import likelion.flourishing.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkinRecordServiceTest {

    private static final UUID USER_ID = UUID.fromString("2c56fe08-ea1f-45fc-915d-c35b7c0bca39");
    private static final UUID SESSION_ID = UUID.fromString("5ecb88d8-6a21-4a54-8967-72599f078963");
    private static final UUID FIRST_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000002");
    private static final UUID SECOND_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000001");
    private static final UUID CARE_RESULT_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000010");
    private static final UUID RULE_SET_ID = UUID.fromString("0198a31f-f33f-7000-8000-000000000020");

    @Mock private SkinReportRepository skinReportRepository;
    @Mock private CareResultRepository careResultRepository;
    @Mock private CareResultRuleRepository careResultRuleRepository;
    @Mock private CareResultItemRepository careResultItemRepository;
    @Mock private CareRuleVersionRepository careRuleVersionRepository;
    @Mock private CareRuleRepository careRuleRepository;
    @Mock private RuleSetRepository ruleSetRepository;
    @Mock private FollowUpRepository followUpRepository;
    @Mock private CareResultIngredientRepository careResultIngredientRepository;
    @Mock private CareResultIngredientRuleRepository careResultIngredientRuleRepository;
    @Mock private ReportTextCipher reportTextCipher;
    @Mock private SkinReportCursorCodec cursorCodec;

    private SkinRecordService skinRecordService;

    @BeforeEach
    void setUp() {
        skinRecordService = new SkinRecordService(
                skinReportRepository,
                careResultRepository,
                careResultRuleRepository,
                careResultItemRepository,
                careRuleVersionRepository,
                careRuleRepository,
                ruleSetRepository,
                followUpRepository,
                careResultIngredientRepository,
                careResultIngredientRuleRepository,
                GuideSectionFixtures.assembler(),
                reportTextCipher,
                cursorCodec
        );
        when(followUpRepository.findAllByReportIdInAndUserId(any(), any())).thenReturn(List.of());
    }

    @Test
    void listUsesLimitPlusOneAndBuildsNextCursor() {
        SkinReport first = summaryReport(FIRST_ID, LocalDateTime.of(2026, 8, 15, 3, 0));
        SkinReport second = summaryReport(SECOND_ID, LocalDateTime.of(2026, 8, 14, 3, 0));
        when(skinReportRepository.findOwnedPage(
                USER_ID, ReportStatus.COMPLETED, ResultType.SELF_CARE_GUIDE, null, 2
        )).thenReturn(List.of(first, second));
        when(cursorCodec.encode(any())).thenReturn("signed-next-cursor");

        SkinReportListResponse response = skinRecordService.getRecords(
                principal(), null, 1, ReportStatus.COMPLETED, ResultType.SELF_CARE_GUIDE
        );

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().getFirst().getId()).isEqualTo(FIRST_ID);
        assertThat(response.getPagination().isHasMore()).isTrue();
        assertThat(response.getPagination().getNextCursor()).isEqualTo("signed-next-cursor");
        assertThat(response.getPagination().getLimit()).isEqualTo(1);

        ArgumentCaptor<SkinReportCursor> cursor = ArgumentCaptor.forClass(SkinReportCursor.class);
        verify(cursorCodec).encode(cursor.capture());
        assertThat(cursor.getValue().id()).isEqualTo(FIRST_ID);
    }

    @Test
    void listRejectsLimitOutsideContractBeforeQuerying() {
        assertThatThrownBy(() -> skinRecordService.getRecords(principal(), null, 101, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(skinReportRepository, never()).findOwnedPage(any(), any(), any(), any(), anyInt());
    }

    @Test
    void detailHidesReportOwnedByAnotherUser() {
        when(skinReportRepository.findByIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skinRecordService.getRecord(principal(), FIRST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(careResultRepository, never()).findByReportIdAndUserId(any(), any());
    }

    @Test
    void detailRejectsCareResultWithDifferentResultType() {
        SkinReport report = summaryReport(FIRST_ID, LocalDateTime.of(2026, 8, 15, 3, 0));
        CareResult careResult = org.mockito.Mockito.mock(CareResult.class);
        when(careResult.getResultType()).thenReturn(ResultType.CLINICIAN_CHECK);
        when(skinReportRepository.findByIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(report));
        when(careResultRepository.findByReportIdAndUserId(FIRST_ID, USER_ID))
                .thenReturn(Optional.of(careResult));

        assertThatThrownBy(() -> skinRecordService.getRecord(principal(), FIRST_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결과 유형");

        verify(followUpRepository, never()).findByReportIdAndUserId(any(), any());
    }

    /**
     * 아래 세 테스트는 이 서비스가 기대하는 데이터 불변식을 고정한다.
     *
     * <p>읽기 경로가 이 불변식을 500으로 드러내는 것은 의도된 선택이다. 보고·관리 결과·경과는
     * 보고 확정 흐름이 한 트랜잭션에서 함께 커밋하므로 어긋난 행은 데이터 손상이고, 조용히 가린
     * 응답을 내보내면 손상을 모르고 지나간다. 대신 그 결합을 테스트로 못 박아 두어, 쓰기 쪽이
     * 불변식을 깨는 방향으로 바뀌면 여기서 먼저 깨지게 한다.
     */
    @Test
    void detailRejectsReportWithoutCareResult() {
        SkinReport report = summaryReport(FIRST_ID, LocalDateTime.of(2026, 8, 15, 3, 0));
        when(skinReportRepository.findByIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(report));
        when(careResultRepository.findByReportIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skinRecordService.getRecord(principal(), FIRST_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("관리 결과");
    }

    @Test
    void detailRejectsCompletedReportWithoutFollowUp() {
        SkinReport report = detailReport();
        CareResult careResult = careResult();
        when(skinReportRepository.findByIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(report));
        when(careResultRepository.findByReportIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(careResult));
        when(followUpRepository.findByReportIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> skinRecordService.getRecord(principal(), FIRST_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("경과가 없습니다");
    }

    @Test
    void detailRejectsSimilarExperienceWithScoreButNoReport() {
        SkinReport report = detailReport();
        CareResult careResult = careResult();
        FollowUp followUp = followUp();
        RuleSet ruleSet = org.mockito.Mockito.mock(RuleSet.class);
        when(careResult.getSimilarReportId()).thenReturn(null);
        when(careResult.getSimilarityScore()).thenReturn(72);
        when(skinReportRepository.findByIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(report));
        when(careResultRepository.findByReportIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(careResult));
        when(followUpRepository.findByReportIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(followUp));
        when(ruleSetRepository.findById(RULE_SET_ID)).thenReturn(Optional.of(ruleSet));

        assertThatThrownBy(() -> skinRecordService.getRecord(principal(), FIRST_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("유사 경험");
    }

    @Test
    void completedDetailContainsDecryptedTextCareResultAndFollowUp() {
        SkinReport report = detailReport();
        CareResult careResult = careResult();
        FollowUp followUp = followUp();
        RuleSet ruleSet = org.mockito.Mockito.mock(RuleSet.class);
        when(ruleSet.getVersionCode()).thenReturn("2026-08-09-v1");

        when(skinReportRepository.findByIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(report));
        when(careResultRepository.findByReportIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(careResult));
        when(followUpRepository.findByReportIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(followUp));
        when(careResultRuleRepository.findAllByIdCareResultIdOrderByApplicationOrder(CARE_RESULT_ID))
                .thenReturn(List.of());
        when(ruleSetRepository.findById(RULE_SET_ID)).thenReturn(Optional.of(ruleSet));
        List<CareResultItem> careItems = List.of(
                careItem(CareResultItemType.DO_TODAY, "미지근한 물로 씻기", 1),
                careItem(CareResultItemType.AVOID_TODAY, "손으로 만지지 않기", 1),
                careItem(CareResultItemType.CHECK_NEXT, "붉은 범위 확인", 1)
        );
        when(careResultItemRepository.findAllByCareResultIdOrderByItemTypeAscDisplayOrderAsc(CARE_RESULT_ID))
                .thenReturn(careItems);
        when(reportTextCipher.decrypt(report.getRawTextEncrypted())).thenReturn("오른쪽 턱이 빨갛고 따가워요.");
        when(reportTextCipher.decrypt(report.getOtherAreasNoteEncrypted())).thenReturn(null);

        SkinReportDetailResponse response = skinRecordService.getRecord(principal(), FIRST_ID);

        assertThat(response.getRawText()).isEqualTo("오른쪽 턱이 빨갛고 따가워요.");
        assertThat(response.getConfirmed().getAppearances()).containsExactly(Appearance.REDNESS);
        assertThat(response.getCareResult().getRuleVersion()).isEqualTo("2026-08-09-v1");
        assertThat(response.getCareResult().getDoToday()).containsExactly("미지근한 물로 씻기");
        assertThat(response.getFollowUp().getSkinChange()).isEqualTo(SkinChange.IMPROVED);
        assertThat(response.getSkinChange()).isEqualTo(SkinChange.IMPROVED);
    }

    /**
     * 명세 SkinReportDetail.careResult가 CareResult를 그대로 참조하므로, 상세 조회도 보고 생성과
     * 같은 필드를 내보내야 한다. 같은 보고인데 경로에 따라 화면이 달라지면 안 된다.
     */
    @Test
    void detailCarriesGuideSectionsAndStoredIngredients() {
        stubCompletedDetail();
        UUID storedId = UUID.fromString("0198a31f-f33f-7000-8000-0000000000f1");
        CareResultIngredient stored = org.mockito.Mockito.mock(CareResultIngredient.class);
        when(stored.getId()).thenReturn(storedId);
        when(stored.getIngredientCode()).thenReturn("ING_PANTHENOL");
        when(stored.getNameSnapshot()).thenReturn("판테놀");
        when(stored.getDescriptionSnapshot()).thenReturn("진정에 쓰이는 성분입니다.");
        when(stored.getCautionNoteSnapshot()).thenReturn(null);
        when(careResultIngredientRepository.findAllByCareResultIdOrderByDisplayOrderAsc(CARE_RESULT_ID))
                .thenReturn(List.of(stored));
        when(careResultIngredientRuleRepository.findAllByIdCareResultIngredientIdIn(anyList()))
                .thenReturn(List.of(CareResultIngredientRule.of(storedId, "GEN-001", 1)));

        SkinReportDetailResponse response = skinRecordService.getRecord(principal(), FIRST_ID);

        assertThat(response.getCareResult().getGuideSections()).hasSize(GuideSectionKey.SECTION_COUNT);
        assertThat(response.getCareResult().getRecommendedIngredients())
                .singleElement()
                .satisfies(ingredient -> {
                    assertThat(ingredient.getId()).isEqualTo("ING_PANTHENOL");
                    assertThat(ingredient.getSourceRuleIds()).containsExactly("GEN-001");
                });
    }

    /** 저장된 성분이 없으면 빈 배열이고 해당 섹션이 비었다고 표시된다. */
    @Test
    void detailWithoutStoredIngredientsStillCarriesSixSections() {
        stubCompletedDetail();

        SkinReportDetailResponse response = skinRecordService.getRecord(principal(), FIRST_ID);

        assertThat(response.getCareResult().getRecommendedIngredients()).isEmpty();
        assertThat(response.getCareResult().getGuideSections()).hasSize(GuideSectionKey.SECTION_COUNT);
        assertThat(response.getCareResult().getGuideSections())
                .filteredOn(section -> section.getKey() == GuideSectionKey.RECOMMENDED_INGREDIENTS)
                .singleElement()
                .satisfies(section -> assertThat(section.isEmpty()).isTrue());
    }

    /**
     * 근거 규칙이 적용 규칙 밖을 가리키면 그 성분을 뺀다. 보고 생성 쪽과 같은 판단이다.
     * 근거 없는 성분 추천은 내보내지 않는다.
     */
    @Test
    void detailDropsIngredientWhoseSourceRuleIsNotApplied() {
        stubCompletedDetail();
        UUID storedId = UUID.fromString("0198a31f-f33f-7000-8000-0000000000f2");
        CareResultIngredient stored = org.mockito.Mockito.mock(CareResultIngredient.class);
        when(stored.getId()).thenReturn(storedId);
        when(stored.getIngredientCode()).thenReturn("ING_GHOST");
        when(stored.getNameSnapshot()).thenReturn("정체불명");
        when(stored.getDescriptionSnapshot()).thenReturn("설명");
        when(careResultIngredientRepository.findAllByCareResultIdOrderByDisplayOrderAsc(CARE_RESULT_ID))
                .thenReturn(List.of(stored));
        when(careResultIngredientRuleRepository.findAllByIdCareResultIngredientIdIn(anyList()))
                .thenReturn(List.of(CareResultIngredientRule.of(storedId, "NOT-APPLIED-999", 1)));

        SkinReportDetailResponse response = skinRecordService.getRecord(principal(), FIRST_ID);

        assertThat(response.getCareResult().getRecommendedIngredients()).isEmpty();
    }

    /** 위 세 테스트가 함께 쓰는 상세 조회 스텁. */
    private void stubCompletedDetail() {
        SkinReport report = detailReport();
        CareResult careResult = careResult();
        FollowUp followUp = followUp();
        RuleSet ruleSet = org.mockito.Mockito.mock(RuleSet.class);
        when(ruleSet.getVersionCode()).thenReturn("2026-08-09-v1");

        when(skinReportRepository.findByIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(report));
        when(careResultRepository.findByReportIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(careResult));
        when(followUpRepository.findByReportIdAndUserId(FIRST_ID, USER_ID)).thenReturn(Optional.of(followUp));
        // 성분의 근거 규칙(sourceRuleIds)이 적용 규칙 안에 있어야 응답에 남는다.
        UUID versionId = UUID.fromString("0198a31f-f33f-7000-8000-0000000000c1");
        UUID ruleId = UUID.fromString("0198a31f-f33f-7000-8000-0000000000c0");
        CareRuleVersion version = org.mockito.Mockito.mock(CareRuleVersion.class);
        when(version.getId()).thenReturn(versionId);
        when(version.getRuleSetId()).thenReturn(RULE_SET_ID);
        when(version.getRuleId()).thenReturn(ruleId);
        CareRule rule = org.mockito.Mockito.mock(CareRule.class);
        when(rule.getId()).thenReturn(ruleId);
        when(rule.getRuleCode()).thenReturn("GEN-001");
        List<CareResultRule> applied = List.of(
                CareResultRule.of(CARE_RESULT_ID, versionId, 1, MatchReason.COMMON)
        );
        when(careResultRuleRepository.findAllByIdCareResultIdOrderByApplicationOrder(CARE_RESULT_ID))
                .thenReturn(applied);
        when(careRuleVersionRepository.findAllById(List.of(versionId))).thenReturn(List.of(version));
        when(careRuleRepository.findAllById(List.of(ruleId))).thenReturn(List.of(rule));
        when(ruleSetRepository.findById(RULE_SET_ID)).thenReturn(Optional.of(ruleSet));
        // careItem()이 안에서 스텁을 만들므로 thenReturn 인자 안에서 부르면 중첩 스터빙이 된다.
        List<CareResultItem> careItems = List.of(careItem(CareResultItemType.DO_TODAY, "미지근한 물로 씻기", 1));
        when(careResultItemRepository.findAllByCareResultIdOrderByItemTypeAscDisplayOrderAsc(CARE_RESULT_ID))
                .thenReturn(careItems);
        when(reportTextCipher.decrypt(report.getRawTextEncrypted())).thenReturn("오른쪽 턱이 빨갛고 따가워요.");
        when(reportTextCipher.decrypt(report.getOtherAreasNoteEncrypted())).thenReturn(null);
    }

    private SkinReport summaryReport(UUID id, LocalDateTime createdAt) {
        SkinReport report = org.mockito.Mockito.mock(SkinReport.class);
        when(report.getId()).thenReturn(id);
        when(report.getCreatedAt()).thenReturn(createdAt);
        when(report.getReportDate()).thenReturn(createdAt.toLocalDate());
        when(report.getPrimaryArea()).thenReturn(BodyArea.RIGHT_CHIN);
        when(report.getAppearances()).thenReturn(EnumSet.of(Appearance.REDNESS));
        when(report.getSensations()).thenReturn(EnumSet.of(Sensation.STINGING_BURNING));
        when(report.getSituations()).thenReturn(EnumSet.of(Situation.SHAVING));
        when(report.getResultType()).thenReturn(ResultType.SELF_CARE_GUIDE);
        when(report.getStatus()).thenReturn(ReportStatus.COMPLETED);
        return report;
    }

    private SkinReport detailReport() {
        SkinReport report = summaryReport(FIRST_ID, LocalDateTime.of(2026, 8, 15, 3, 0));
        when(report.getRawTextEncrypted()).thenReturn(new byte[]{1, 2, 3});
        when(report.getOtherAreasNoteEncrypted()).thenReturn(null);
        when(report.getCareAvailability()).thenReturn(CareAvailability.ALREADY_WASHED);
        when(report.getPreCareChecks()).thenReturn(EnumSet.of(PreCareCheck.NONE));
        return report;
    }

    private CareResult careResult() {
        CareResult careResult = org.mockito.Mockito.mock(CareResult.class);
        when(careResult.getId()).thenReturn(CARE_RESULT_ID);
        when(careResult.getRuleSetId()).thenReturn(RULE_SET_ID);
        when(careResult.getResultType()).thenReturn(ResultType.SELF_CARE_GUIDE);
        when(careResult.getSummary()).thenReturn("면도와 훈련 뒤 생긴 불편을 자극 없이 관리해 보세요.");
        when(careResult.getAiGenerationStatus()).thenReturn(AiGenerationStatus.GENERATED);
        when(careResult.getGeneratedAt()).thenReturn(LocalDateTime.of(2026, 8, 15, 3, 1));
        when(careResult.getSimilarReportId()).thenReturn(null);
        when(careResult.getSimilarityScore()).thenReturn(null);
        return careResult;
    }

    private FollowUp followUp() {
        FollowUp followUp = org.mockito.Mockito.mock(FollowUp.class);
        when(followUp.getReportId()).thenReturn(FIRST_ID);
        when(followUp.getKind()).thenReturn(FollowUpKind.SELF_CARE);
        when(followUp.getSkinChange()).thenReturn(SkinChange.IMPROVED);
        when(followUp.getActionCompletion()).thenReturn(ActionCompletion.MOSTLY_DONE);
        when(followUp.getSubmittedAt()).thenReturn(LocalDateTime.of(2026, 8, 16, 3, 0));
        return followUp;
    }

    private CareResultItem careItem(CareResultItemType type, String content, int order) {
        CareResultItem item = org.mockito.Mockito.mock(CareResultItem.class);
        when(item.getItemType()).thenReturn(type);
        when(item.getContentSnapshot()).thenReturn(content);
        when(item.getDisplayOrder()).thenReturn(order);
        return item;
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                USER_ID,
                SESSION_ID,
                LocalDateTime.of(2026, 8, 24, 0, 0),
                "csrf-token-value-that-is-long-enough"
        );
    }
}
