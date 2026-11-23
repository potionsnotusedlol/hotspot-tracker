package ru.moscow.hotspot_tracker.main.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.CatalogFilter;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.HourlyLoad;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.MapPoint;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.NearestStop;
import ru.moscow.hotspot_tracker.main.web.port.DayType;
import ru.moscow.hotspot_tracker.main.web.port.LoadLevel;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.DistanceBucket;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.DistrictCount;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.OkrugCount;
import ru.moscow.hotspot_tracker.main.web.port.TransportType;

/**
 * Home page, access point card, map and statistics (FR-WEB-02, FR-WEB-03, FR-CAT-04, FR-STAT-01\...04).
 *
 * <p>Contract: the web shows what the ports return without recalculating it; load is labelled as an estimate;
 * the map gets at most 2000 points; statistics by district are requested only for a selected okrug.
 */
class PublicPagesTest extends WebLayerTestSupport {

    /** "Estimate" in Russian: every load figure on the card is marked as a model estimate (FR-LOAD). */
    private static final String ESTIMATE = "расчётная оценка";

    private static final String CYRILLIC_OKRUG = "Центральный";

    @Test
    void homeShowsTotalOfAllOkrugs() throws Exception {
        when(stats.byOkrug()).thenReturn(List.of(new OkrugCount("A", 3), new OkrugCount("B", 4)));

        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(model().attribute("total", 7L));
    }

    @Test
    void unknownAccessPointIsNotFound() throws Exception {
        when(accessPoints.findById(404)).thenReturn(Optional.empty());

        mvc.perform(get("/access-points/404")).andExpect(status().isNotFound());
    }

    @Test
    void cardShowsBothDayTypesAndMarksLoadAsEstimate() throws Exception {
        when(accessPoints.findById(5)).thenReturn(Optional.of(point(5, "Point 5")));
        when(accessPoints.loadProfile(5, DayType.WEEKDAY)).thenReturn(profileWithPeakAt(18, 300));
        when(accessPoints.loadProfile(5, DayType.WEEKEND)).thenReturn(profileWithPeakAt(13, 120));
        when(accessPoints.nearestStops(5))
                .thenReturn(List.of(
                        new NearestStop(TransportType.TRAM, "Stop T", 300, 1),
                        new NearestStop(TransportType.BUS, "Stop B", 100, 1)));

        mvc.perform(get("/access-points/5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("weekdayPeak", startsWith("18:00")))
                .andExpect(model().attribute("weekendPeak", startsWith("13:00")))
                .andExpect(model().attribute("weekdayValues", hourlyValues(18, 300)))
                .andExpect(content().string(containsString(ESTIMATE)))
                .andExpect(content().string(containsString("Stop T")))
                .andExpect(content().string(containsString("Stop B")));
    }

    @Test
    void cardHasTwentyFourHourLabels() throws Exception {
        when(accessPoints.findById(5)).thenReturn(Optional.of(point(5, "Point 5")));
        when(accessPoints.loadProfile(eq(5L), any())).thenReturn(profileWithPeakAt(0, 1));

        mvc.perform(get("/access-points/5"))
                .andExpect(model().attribute(
                                "hourLabels",
                                String.join(
                                        "|",
                                        IntStream.range(0, 24)
                                                .mapToObj(h -> h + ":00")
                                                .toList())));
    }

    @Test
    void mapPointsAreJsonAndLimitedTo2000() throws Exception {
        when(accessPoints.mapPoints(any(), eq(2000)))
                .thenReturn(List.of(new MapPoint(7, "Point 7", 55.7, 37.6, 150.0, LoadLevel.HIGH)));

        mvc.perform(get("/map/points").param("okrug", "Okrug A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].name").value("Point 7"))
                .andExpect(jsonPath("$[0].latitude").value(55.7))
                .andExpect(jsonPath("$[0].longitude").value(37.6))
                .andExpect(jsonPath("$[0].estimatedLoad").value(150.0))
                .andExpect(jsonPath("$[0].loadLevel").value("HIGH"));

        assertThat(mapFilter()).isEqualTo(new CatalogFilter("Okrug A", null, null, null, null, null, null, null, null));
    }

    @Test
    void mapPointsWithBlankOkrugAreNotFiltered() throws Exception {
        mvc.perform(get("/map/points").param("okrug", " ")).andExpect(status().isOk());

        assertThat(mapFilter()).isEqualTo(new CatalogFilter(null, null, null, null, null, null, null, null, null));
    }

    @Test
    void mapPageLoadsPointsOfSelectedOkrugByEncodedUrl() throws Exception {
        mvc.perform(get("/map").param("okrug", CYRILLIC_OKRUG))
                .andExpect(status().isOk())
                .andExpect(model().attribute("source", startsWith("/map/points?okrug=%D0%A6")));
        mvc.perform(get("/map")).andExpect(model().attribute("source", "/map/points"));
    }

    @Test
    void statsWithoutOkrugDoNotAskForDistricts() throws Exception {
        when(stats.byOkrug()).thenReturn(List.of(new OkrugCount("A", 3), new OkrugCount("B", 4)));

        mvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("total", 7L))
                .andExpect(model().attribute("okrugLabels", "A|B"))
                .andExpect(model().attribute("okrugValues", "3,4"))
                .andExpect(model().attribute("districts", List.of()));
        verify(stats, never()).byDistrict(anyString());
        verify(stats).byStopDistance(null);
        verify(stats).topByLoad(10);
    }

    @Test
    void statsForOkrugShowDistrictsAndTransportBuckets() throws Exception {
        when(stats.byDistrict("A")).thenReturn(List.of(new DistrictCount("D1", 2), new DistrictCount("D2", 1)));
        when(stats.byStopDistance(TransportType.METRO))
                .thenReturn(List.of(new DistanceBucket("0-100", 5), new DistanceBucket("100-250", 6)));

        mvc.perform(get("/stats").param("okrug", "A").param("transportType", "METRO"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("districtLabels", "D1|D2"))
                .andExpect(model().attribute("districtValues", "2,1"))
                .andExpect(model().attribute("bucketLabels", "0-100|100-250"))
                .andExpect(model().attribute("bucketValues", "5,6"))
                .andExpect(model().attribute("selectedTransport", TransportType.METRO));
    }

    private CatalogFilter mapFilter() {
        ArgumentCaptor<CatalogFilter> captor = ArgumentCaptor.forClass(CatalogFilter.class);
        verify(accessPoints).mapPoints(captor.capture(), eq(2000));
        return captor.getValue();
    }

    private static List<HourlyLoad> profileWithPeakAt(int peakHour, double peak) {
        return IntStream.range(0, 24)
                .mapToObj(h -> new HourlyLoad(h, h == peakHour ? peak : 10))
                .toList();
    }

    private static String hourlyValues(int peakHour, long peak) {
        return String.join(
                ",",
                IntStream.range(0, 24)
                        .mapToObj(h -> String.valueOf(h == peakHour ? peak : 10))
                        .toList());
    }
}
