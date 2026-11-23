package ru.moscow.hotspot_tracker.main.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.AccessPointView;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.CatalogFilter;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.HourlyLoad;
import ru.moscow.hotspot_tracker.main.web.port.DayType;
import ru.moscow.hotspot_tracker.main.web.port.LoadLevel;
import ru.moscow.hotspot_tracker.main.web.port.StatsService;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.DistanceBucket;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.DistrictCount;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.OkrugCount;
import ru.moscow.hotspot_tracker.main.web.port.TransportType;
import ru.moscow.hotspot_tracker.main.web.port.UsageEventService;
import ru.moscow.hotspot_tracker.main.web.port.UsageEventService.UsageSummary;

/**
 * Excel files of the four report types (FR-REP-01\...05).
 *
 * <p>Contract from the PR description and {@link ExcelReportWriter}: ACCESS_POINT is the card with hourly load,
 * DISTRICT the points of an okrug or district plus its districts, MOSCOW the counts by okrug, by distance for each
 * transport type and the top by load, USAGE the usage summary for a period (30 days by default); points are read
 * page by page and continue on a new sheet after 1 000 000 rows; load figures are labelled as an estimate.
 */
class ExcelReportWriterTest {

    /** "Estimate" stem in Russian, present in every load header. */
    private static final String ESTIMATE = "оценка";

    @TempDir
    Path dir;

    private AccessPointService accessPoints;
    private StatsService stats;
    private UsageEventService usage;
    private ExcelReportWriter writer;

    @BeforeEach
    void setUp() {
        accessPoints = mock(AccessPointService.class);
        stats = mock(StatsService.class);
        usage = mock(UsageEventService.class);
        writer = new ExcelReportWriter(accessPoints, stats, usage);
    }

    @Test
    void accessPointReportHasCardAndTwentyFourHoursForBothDayTypes() throws Exception {
        when(accessPoints.findById(5)).thenReturn(Optional.of(point(5)));
        when(accessPoints.loadProfile(5, DayType.WEEKDAY)).thenReturn(profile(10));
        when(accessPoints.loadProfile(5, DayType.WEEKEND)).thenReturn(profile(3));

        Path file = write(ReportType.ACCESS_POINT, new ReportParams(5L, null, null, null));

        try (Workbook book = open(file)) {
            assertThat(book.getNumberOfSheets()).isEqualTo(2);
            Sheet card = book.getSheetAt(0);
            assertThat(columnValues(card, 1)).contains("Point 5", "Address 5", "Okrug A", "District A", "150", "121");
            assertThat(columnValues(card, 0)).anyMatch(title -> title.contains(ESTIMATE));
            Sheet hours = book.getSheetAt(1);
            assertThat(hours.getLastRowNum()).isEqualTo(24);
            assertThat(text(hours.getRow(0).getCell(1))).contains(ESTIMATE);
            assertThat(text(hours.getRow(0).getCell(2))).contains(ESTIMATE);
            assertThat(text(hours.getRow(19).getCell(0))).isEqualTo("18:00");
            assertThat(hours.getRow(19).getCell(1).getNumericCellValue()).isEqualTo(180);
            assertThat(hours.getRow(19).getCell(2).getNumericCellValue()).isEqualTo(54);
        }
    }

    @Test
    void accessPointReportForUnknownPointIsAUserError() {
        when(accessPoints.findById(anyLong())).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> write(ReportType.ACCESS_POINT, new ReportParams(404L, null, null, null)));
    }

    @Test
    void districtReportReadsAllPagesOfTheSelectedArea() throws Exception {
        stubPoints(1_234);
        when(stats.byDistrict("Okrug A"))
                .thenReturn(List.of(new DistrictCount("District A", 1_000), new DistrictCount("District B", 234)));

        Path file = write(ReportType.DISTRICT, new ReportParams(null, "Okrug A", "District A", null));

        ArgumentCaptor<CatalogFilter> filter = ArgumentCaptor.forClass(CatalogFilter.class);
        verify(accessPoints, org.mockito.Mockito.atLeast(2)).search(filter.capture(), any());
        assertThat(filter.getValue())
                .isEqualTo(new CatalogFilter("Okrug A", "District A", null, null, null, null, null, null, null));
        try (Workbook book = open(file)) {
            assertThat(book.getNumberOfSheets()).isEqualTo(2);
            Sheet points = book.getSheetAt(0);
            assertThat(points.getLastRowNum()).isEqualTo(1_234);
            assertThat(text(points.getRow(1).getCell(0))).isEqualTo("Point 1");
            assertThat(text(points.getRow(1_234).getCell(0))).isEqualTo("Point 1234");
            assertThat(columnValues(points, 0).getFirst()).isNotBlank();
            assertThat(IntStream.range(0, 8).mapToObj(c -> text(points.getRow(0).getCell(c))))
                    .anyMatch(title -> title.contains(ESTIMATE));
            Sheet districts = book.getSheetAt(1);
            assertThat(columnValues(districts, 0)).contains("District A", "District B");
            assertThat(districts.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1_000);
        }
    }

    @Test
    void moscowReportHasOkrugsDistanceByEveryTransportTypeAndTopFifty() throws Exception {
        when(stats.byOkrug()).thenReturn(List.of(new OkrugCount("A", 3), new OkrugCount("B", 4)));
        List<DistanceBucket> buckets = List.of(new DistanceBucket("0-100", 1), new DistanceBucket("100-250", 2));
        when(stats.byStopDistance(any())).thenReturn(buckets);
        when(stats.byStopDistance(null)).thenReturn(buckets);
        when(stats.topByLoad(anyInt())).thenReturn(List.of(point(1), point(2)));

        Path file = write(ReportType.MOSCOW, new ReportParams(null, null, null, null));

        verify(stats).topByLoad(50);
        for (TransportType type : TransportType.values()) {
            verify(stats).byStopDistance(type);
        }
        try (Workbook book = open(file)) {
            assertThat(book.getNumberOfSheets()).isEqualTo(3);
            Sheet okrugs = book.getSheetAt(0);
            assertThat(columnValues(okrugs, 0)).contains("A", "B");
            Sheet distance = book.getSheetAt(1);
            assertThat(distance.getLastRowNum()).isEqualTo(TransportType.values().length);
            assertThat(text(distance.getRow(0).getCell(1))).isEqualTo("0-100");
            assertThat(text(distance.getRow(0).getCell(2))).isEqualTo("100-250");
            assertThat(distance.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(2);
            Sheet top = book.getSheetAt(2);
            assertThat(top.getLastRowNum()).isEqualTo(2);
            assertThat(text(top.getRow(0).getCell(3))).contains(ESTIMATE);
        }
    }

    @Test
    void usageReportCoversThirtyDaysByDefault() throws Exception {
        when(usage.summary(any(), any())).thenReturn(new UsageSummary(11, 22, 33, 44, 55, 6));

        Path file = write(ReportType.USAGE, new ReportParams(null, null, null, null));

        assertThat(usagePeriod()).isEqualTo(Duration.ofDays(30));
        try (Workbook book = open(file)) {
            assertThat(book.getNumberOfSheets()).isEqualTo(1);
            assertThat(columnValues(book.getSheetAt(0), 1)).contains("30", "11", "22", "33", "44", "55", "6");
        }
    }

    @Test
    void usageReportCoversTheRequestedPeriod() throws Exception {
        when(usage.summary(any(), any())).thenReturn(new UsageSummary(0, 0, 0, 0, 0, 0));

        Path file = write(ReportType.USAGE, new ReportParams(null, null, null, 7));

        assertThat(usagePeriod()).isEqualTo(Duration.ofDays(7));
        try (Workbook book = open(file)) {
            assertThat(columnValues(book.getSheetAt(0), 1)).contains("7");
        }
    }

    @Test
    void missingDirectoriesOfTheTargetAreCreated() throws Exception {
        when(stats.byOkrug()).thenReturn(List.of());
        when(stats.byStopDistance(any())).thenReturn(List.of());
        when(stats.byStopDistance(null)).thenReturn(List.of());
        Path target = dir.resolve("nested/deeper/report.xlsx");

        writer.write(ReportType.MOSCOW, new ReportParams(null, null, null, null), target);

        assertThat(target).exists();
    }

    /**
     * Excel allows 1 048 576 rows per sheet; the writer keeps 1 000 000 data rows on a sheet and continues on the
     * next one. Writes 1 000 001 rows, so it takes a few seconds; the file is checked without loading it.
     */
    @Test
    void pointsAfterOneMillionRowsContinueOnTheNextSheet() throws Exception {
        int total = ExcelReportWriter.MAX_ROWS_PER_SHEET + 1;
        stubPoints(total);
        when(stats.byDistrict(any())).thenReturn(List.of());

        Path file = write(ReportType.DISTRICT, new ReportParams(null, "Okrug A", null, null));

        try (ZipFile zip = new ZipFile(file.toFile())) {
            List<String> sheetNames = sheetNames(zip);
            assertThat(sheetNames).hasSize(3);
            String secondPointSheet = read(zip, "xl/worksheets/sheet2.xml");
            assertThat(count(secondPointSheet, "<row ")).isEqualTo(2);
            assertThat(sharedOrInlineText(zip, secondPointSheet)).contains("Point " + total);
            assertThat(sheetNames.get(1)).startsWith(sheetNames.get(0)).isNotEqualTo(sheetNames.get(0));
        }
    }

    private Path write(ReportType type, ReportParams params) throws IOException {
        Path target = dir.resolve(type + ".xlsx");
        writer.write(type, params, target);
        return target;
    }

    private Duration usagePeriod() {
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(usage).summary(from.capture(), to.capture());
        return Duration.between(from.getValue(), to.getValue());
    }

    /** Catalog of {@code total} points named "Point 1"\..."Point N", returned page by page. */
    private void stubPoints(int total) {
        doAnswer(inv -> {
                    Pageable page = inv.getArgument(1);
                    long from = Math.min(page.getOffset(), total);
                    long to = Math.min(from + page.getPageSize(), total);
                    List<AccessPointView> content = LongStream.range(from, to)
                            .mapToObj(i -> point(i + 1))
                            .toList();
                    return new PageImpl<>(content, page, total);
                })
                .when(accessPoints)
                .search(any(), any());
    }

    private static AccessPointView point(long id) {
        return new AccessPointView(
                id,
                "Point " + id,
                "Address " + id,
                "Okrug A",
                "District A",
                TransportType.BUS,
                150,
                120.6,
                LoadLevel.MEDIUM,
                55.75,
                37.61);
    }

    /** Hourly profile where hour {@code h} has load {@code h * factor}. */
    private static List<HourlyLoad> profile(int factor) {
        return IntStream.range(0, 24)
                .mapToObj(h -> new HourlyLoad(h, h * factor))
                .toList();
    }

    private static Workbook open(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return WorkbookFactory.create(in);
        }
    }

    private static List<String> columnValues(Sheet sheet, int column) {
        List<String> values = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getCell(column) != null) {
                values.add(text(row.getCell(column)));
            }
        }
        return values;
    }

    private static String text(org.apache.poi.ss.usermodel.Cell cell) {
        return new DataFormatter().formatCellValue(cell);
    }

    private static List<String> sheetNames(ZipFile zip) throws IOException {
        Matcher m = Pattern.compile("<sheet [^>]*name=\"([^\"]*)\"").matcher(read(zip, "xl/workbook.xml"));
        List<String> names = new ArrayList<>();
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /** Text of a sheet: inline strings and the shared strings it refers to. */
    private static String sharedOrInlineText(ZipFile zip, String sheetXml) throws IOException {
        ZipEntry shared = zip.getEntry("xl/sharedStrings.xml");
        return shared == null ? sheetXml : sheetXml + read(zip, "xl/sharedStrings.xml");
    }

    private static String read(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        assertThat(entry).as(name).isNotNull();
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + token.length())) {
            count++;
        }
        return count;
    }
}
