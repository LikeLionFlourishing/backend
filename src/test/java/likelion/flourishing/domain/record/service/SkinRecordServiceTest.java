package likelion.flourishing.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
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
        assertThat(response.getConfirmed().getAppearances()).containsExactly(Appearance.APP_REDNESS);
        assertThat(response.getCareResult().getRuleVersion()).isEqualTo("2026-08-09-v1");
        assertThat(response.getCareResult().getDoToday()).containsExactly("미지근한 물로 씻기");
        assertThat(response.getFollowUp().getSkinChange()).isEqualTo(SkinChange.IMPROVED);
        assertThat(response.getSkinChange()).isEqualTo(SkinChange.IMPROVED);
    }

    private SkinReport summaryReport(UUID id, LocalDateTime createdAt) {
        SkinReport report = org.mockito.Mockito.mock(SkinReport.class);
        when(report.getId()).thenReturn(id);
        when(report.getCreatedAt()).thenReturn(createdAt);
        when(report.getReportDate()).thenReturn(createdAt.toLocalDate());
        when(report.getPrimaryArea()).thenReturn(BodyArea.RIGHT_CHIN);
        when(report.getAppearances()).thenReturn(EnumSet.of(Appearance.APP_REDNESS));
        when(report.getSensations()).thenReturn(EnumSet.of(Sensation.REDNESS));
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
