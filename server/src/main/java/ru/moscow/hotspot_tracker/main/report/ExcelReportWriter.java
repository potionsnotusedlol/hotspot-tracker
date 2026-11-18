package ru.moscow.hotspot_tracker.main.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.moscow.hotspot_tracker.main.accesspoint.AccessPointService;
import ru.moscow.hotspot_tracker.main.accesspoint.AccessPointService.AccessPointView;
import ru.moscow.hotspot_tracker.main.accesspoint.AccessPointService.CatalogFilter;
import ru.moscow.hotspot_tracker.main.accesspoint.AccessPointService.HourlyLoad;
import ru.moscow.hotspot_tracker.main.accesspoint.DayType;
import ru.moscow.hotspot_tracker.main.accesspoint.TransportType;
import ru.moscow.hotspot_tracker.main.stats.StatsService;
import ru.moscow.hotspot_tracker.main.stats.StatsService.DistanceBucket;
import ru.moscow.hotspot_tracker.main.stats.StatsService.DistrictCount;
import ru.moscow.hotspot_tracker.main.stats.StatsService.OkrugCount;
import ru.moscow.hotspot_tracker.main.usage.UsageEventService;
import ru.moscow.hotspot_tracker.main.usage.UsageEventService.UsageSummary;

/**
 * Пишет Excel потоком через SXSSFWorkbook: в памяти держится только окно строк, данные идут страницами (FR-REP-01).
 * Все показатели нагрузки подписаны как расчётная оценка.
 */
@Component
class ExcelReportWriter {

    /** Лимит Excel на лист 1 048 576 строк. Берём меньше, с запасом под заголовок. */
    static final int MAX_ROWS_PER_SHEET = 1_000_000;

    private static final int PAGE_SIZE = 500;
    private static final int WINDOW = 100;
    private static final int COLUMN_WIDTH = 24 * 256;
    private static final int TOP_LIMIT = 50;
    private static final int USAGE_DAYS_DEFAULT = 30;

    private final AccessPointService accessPoints;
    private final StatsService stats;
    private final UsageEventService usage;

    ExcelReportWriter(AccessPointService accessPoints, StatsService stats, UsageEventService usage) {
        this.accessPoints = accessPoints;
        this.stats = stats;
        this.usage = usage;
    }

    void write(ReportType type, ReportParams params, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW);
        try (OutputStream out = Files.newOutputStream(target)) {
            CellStyle header = headerStyle(workbook);
            switch (type) {
                case ACCESS_POINT -> accessPointSheets(workbook, header, params);
                case DISTRICT -> districtSheets(workbook, header, params);
                case MOSCOW -> moscowSheets(workbook, header);
                case USAGE -> usageSheet(workbook, header, params);
            }
            workbook.write(out);
        } finally {
            // Временные файлы SXSSF иначе останутся на диске
            workbook.dispose();
        }
    }

    private void accessPointSheets(SXSSFWorkbook wb, CellStyle header, ReportParams params) {
        AccessPointView point = accessPoints.findById(params.accessPointId())
                .orElseThrow(() -> new IllegalArgumentException("Точка не найдена"));

        Sheet card = wb.createSheet("Точка");
        headerRow(card, header, "Показатель", "Значение");
        int r = 1;
        r = keyValue(card, r, "Название", point.name());
        r = keyValue(card, r, "Адрес", point.address());
        r = keyValue(card, r, "Округ", point.okrug());
        r = keyValue(card, r, "Район", point.district());
        r = keyValue(card, r, "Ближайший транспорт", point.nearestTransport().label());
        r = keyValue(card, r, "До остановки, м", String.valueOf(point.distanceToStopM()));
        r = keyValue(card, r, "Нагрузка, чел. (расчётная оценка)", String.valueOf(Math.round(point.estimatedLoad())));
        r = keyValue(card, r, "Уровень нагрузки (оценка)", point.loadLevel().label());
        r = keyValue(card, r, "Широта", String.valueOf(point.latitude()));
        keyValue(card, r, "Долгота", String.valueOf(point.longitude()));

        List<HourlyLoad> weekday = accessPoints.loadProfile(point.id(), DayType.WEEKDAY);
        List<HourlyLoad> weekend = accessPoints.loadProfile(point.id(), DayType.WEEKEND);
        Sheet hours = wb.createSheet("Нагрузка по часам");
        headerRow(hours, header, "Час", "Будни, чел. (оценка)", "Выходные, чел. (оценка)");
        for (int i = 0; i < 24; i++) {
            Row row = hours.createRow(i + 1);
            text(row, 0, i + ":00");
            number(row, 1, Math.round(weekday.get(i).estimatedLoad()));
            number(row, 2, Math.round(weekend.get(i).estimatedLoad()));
        }
    }

    private void districtSheets(SXSSFWorkbook wb, CellStyle header, ReportParams params) {
        CatalogFilter filter = new CatalogFilter(params.okrug(), params.district(),
                null, null, null, null, null, null, null);
        writePoints(wb, header, filter);

        Sheet districts = wb.createSheet("Районы");
        headerRow(districts, header, "Район", "Точек");
        int r = 1;
        for (DistrictCount d : stats.byDistrict(params.okrug())) {
            Row row = districts.createRow(r++);
            text(row, 0, d.district());
            number(row, 1, d.count());
        }
    }

    private void moscowSheets(SXSSFWorkbook wb, CellStyle header) {
        Sheet okrugs = wb.createSheet("По округам");
        headerRow(okrugs, header, "Округ", "Точек");
        int r = 1;
        for (OkrugCount o : stats.byOkrug()) {
            Row row = okrugs.createRow(r++);
            text(row, 0, o.okrug());
            number(row, 1, o.count());
        }

        // Распределение по расстоянию в разрезе видов транспорта (FR-STAT-03)
        List<DistanceBucket> allBuckets = stats.byStopDistance(null);
        Sheet distance = wb.createSheet("По расстоянию");
        String[] titles = new String[allBuckets.size() + 1];
        titles[0] = "Вид транспорта";
        for (int i = 0; i < allBuckets.size(); i++) {
            titles[i + 1] = allBuckets.get(i).label();
        }
        headerRow(distance, header, titles);
        r = 1;
        for (TransportType type : TransportType.values()) {
            Row row = distance.createRow(r++);
            text(row, 0, type.label());
            List<DistanceBucket> buckets = stats.byStopDistance(type);
            for (int i = 0; i < buckets.size(); i++) {
                number(row, i + 1, buckets.get(i).count());
            }
        }

        Sheet top = wb.createSheet("Топ по нагрузке");
        headerRow(top, header, "Название", "Округ", "Район", "Нагрузка, чел. (оценка)");
        r = 1;
        for (AccessPointView p : stats.topByLoad(TOP_LIMIT)) {
            Row row = top.createRow(r++);
            text(row, 0, p.name());
            text(row, 1, p.okrug());
            text(row, 2, p.district());
            number(row, 3, Math.round(p.estimatedLoad()));
        }
    }

    private void usageSheet(SXSSFWorkbook wb, CellStyle header, ReportParams params) {
        int days = params.days() == null ? USAGE_DAYS_DEFAULT : params.days();
        Instant to = Instant.now();
        UsageSummary s = usage.summary(to.minus(Duration.ofDays(days)), to);

        Sheet sheet = wb.createSheet("Использование");
        headerRow(sheet, header, "Показатель", "Значение");
        int r = 1;
        r = keyValue(sheet, r, "Период, дней", String.valueOf(days));
        r = keyValue(sheet, r, "Пользователей", String.valueOf(s.users()));
        r = keyValue(sheet, r, "Запросов в веб-интерфейсе", String.valueOf(s.webRequests()));
        r = keyValue(sheet, r, "Запросов к API", String.valueOf(s.apiRequests()));
        r = keyValue(sheet, r, "Обращений к Telegram-боту", String.valueOf(s.botRequests()));
        r = keyValue(sheet, r, "Обращений к чат-боту", String.valueOf(s.chatRequests()));
        keyValue(sheet, r, "Ошибок", String.valueOf(s.errors()));
    }

    /** Точки выгружаем страницами. Если строк больше лимита листа, продолжаем на следующем листе. */
    private void writePoints(SXSSFWorkbook wb, CellStyle header, CatalogFilter filter) {
        int sheetNumber = 1;
        Sheet sheet = newPointSheet(wb, header, sheetNumber);
        int row = 1;
        int pageNumber = 0;
        Page<AccessPointView> page;
        do {
            page = accessPoints.search(filter, PageRequest.of(pageNumber++, PAGE_SIZE));
            for (AccessPointView p : page.getContent()) {
                if (row > MAX_ROWS_PER_SHEET) {
                    sheet = newPointSheet(wb, header, ++sheetNumber);
                    row = 1;
                }
                Row out = sheet.createRow(row++);
                text(out, 0, p.name());
                text(out, 1, p.address());
                text(out, 2, p.okrug());
                text(out, 3, p.district());
                text(out, 4, p.nearestTransport().label());
                number(out, 5, p.distanceToStopM());
                number(out, 6, Math.round(p.estimatedLoad()));
                text(out, 7, p.loadLevel().label());
            }
        } while (page.hasNext());
    }

    private static Sheet newPointSheet(SXSSFWorkbook wb, CellStyle header, int number) {
        Sheet sheet = wb.createSheet(number == 1 ? "ТД" : "ТД " + number);
        headerRow(sheet, header, "Название", "Адрес", "Округ", "Район", "Ближайший транспорт",
                "До остановки, м", "Нагрузка, чел. (оценка)", "Уровень (оценка)");
        return sheet;
    }

    private static CellStyle headerStyle(SXSSFWorkbook wb) {
        Font bold = wb.createFont();
        bold.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(bold);
        return style;
    }

    private static void headerRow(Sheet sheet, CellStyle style, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            row.createCell(i).setCellValue(titles[i]);
            row.getCell(i).setCellStyle(style);
            sheet.setColumnWidth(i, COLUMN_WIDTH);
        }
    }

    private static int keyValue(Sheet sheet, int rowIndex, String key, String value) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, key);
        text(row, 1, value);
        return rowIndex + 1;
    }

    private static void text(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private static void number(Row row, int column, double value) {
        row.createCell(column).setCellValue(value);
    }
}
