package ru.moscow.hotspot_tracker.main.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Report parameters (FR-REP-02\...05).
 *
 * <p>Contract: parameters are checked before the job is queued, so the user sees the error at once; they are
 * stored as a query string and read back unchanged by the worker.
 */
class ReportParamsTest {

    private static final String CYRILLIC_OKRUG = "Центральный административный округ";

    @Test
    void accessPointReportNeedsAnAccessPoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReportParams(null, null, null, null).validate(ReportType.ACCESS_POINT));
        assertThatCode(() -> new ReportParams(5L, null, null, null).validate(ReportType.ACCESS_POINT))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void districtReportNeedsAnOkrug(String okrug) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReportParams(null, okrug, "District", null).validate(ReportType.DISTRICT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReportParams(null, null, null, null).validate(ReportType.DISTRICT));
    }

    @Test
    void districtReportWithOkrugOnlyIsValid() {
        assertThatCode(() -> new ReportParams(null, "Okrug", null, null).validate(ReportType.DISTRICT))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 366})
    void usagePeriodOutsideOneToThreeHundredSixtyFiveDaysIsRejected(int days) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReportParams(null, null, null, days).validate(ReportType.USAGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 365})
    void usagePeriodWithinLimitsIsValid(int days) {
        assertThatCode(() -> new ReportParams(null, null, null, days).validate(ReportType.USAGE))
                .doesNotThrowAnyException();
    }

    @Test
    void usageWithoutPeriodAndMoscowReportNeedNoParameters() {
        ReportParams empty = new ReportParams(null, null, null, null);

        assertThatCode(() -> empty.validate(ReportType.USAGE)).doesNotThrowAnyException();
        assertThatCode(() -> empty.validate(ReportType.MOSCOW)).doesNotThrowAnyException();
    }

    @Test
    void parametersSurviveTheQueryString() {
        ReportParams params = new ReportParams(5L, CYRILLIC_OKRUG, "Tverskoy", 30);

        assertThat(ReportParams.fromQuery(params.toQuery())).isEqualTo(params);
    }

    @Test
    void emptyParametersAreStoredAsEmptyString() {
        ReportParams empty = new ReportParams(null, null, null, null);

        assertThat(empty.toQuery()).isEmpty();
        assertThat(ReportParams.fromQuery("")).isEqualTo(empty);
        assertThat(ReportParams.fromQuery(null)).isEqualTo(empty);
    }

    /** Names come from the request, so separators in them must not create or change other parameters. */
    @ParameterizedTest
    @ValueSource(strings = {"A&accessPointId=7", "A=B", "A+B", "100%", "A#B"})
    void separatorsInNamesSurviveTheQueryString(String okrug) {
        ReportParams params = new ReportParams(null, okrug, "D", null);

        assertThat(ReportParams.fromQuery(params.toQuery())).isEqualTo(params);
    }
}
