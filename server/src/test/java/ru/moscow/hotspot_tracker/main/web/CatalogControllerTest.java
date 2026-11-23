package ru.moscow.hotspot_tracker.main.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MvcResult;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.AccessPointView;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.CatalogFilter;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.DistrictCount;
import ru.moscow.hotspot_tracker.main.web.port.TransportType;

/**
 * Catalog page and its HTMX fragments (FR-CAT-01, FR-CAT-02, FR-CAT-06, FR-WEB-01).
 *
 * <p>Contract: the page size is limited so that one request cannot unload the whole catalog; blank filters do not
 * filter; {@code /catalog/rows} returns only the table and pushes the {@code /catalog} address with the filters;
 * values from the request and from the data are escaped.
 */
class CatalogControllerTest extends WebLayerTestSupport {

    private static final String CYRILLIC_OKRUG = "Центральный";

    @Test
    void defaultPageIsFirstPageOfTwentySortedByName() throws Exception {
        mvc.perform(get("/catalog")).andExpect(status().isOk());

        Pageable pageable = searchedPageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    void pageSizeIsLimitedToOneHundred() throws Exception {
        mvc.perform(get("/catalog/rows").param("size", "100000")).andExpect(status().isOk());

        assertThat(searchedPageable().getPageSize()).isEqualTo(100);
    }

    @Test
    void pageSizeAndNumberCannotBeNegative() throws Exception {
        mvc.perform(get("/catalog/rows").param("size", "-5").param("page", "-3"))
                .andExpect(status().isOk());

        Pageable pageable = searchedPageable();
        assertThat(pageable.getPageSize()).isEqualTo(1);
        assertThat(pageable.getPageNumber()).isZero();
    }

    @Test
    void sortByKnownFieldKeepsDirection() throws Exception {
        mvc.perform(get("/catalog/rows").param("sort", "estimatedLoad,desc")).andExpect(status().isOk());

        assertThat(searchedPageable().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "estimatedLoad"));
    }

    @Test
    void sortByUnknownFieldIsNotPassedToTheService() throws Exception {
        mvc.perform(get("/catalog/rows").param("sort", "passwordHash,desc")).andExpect(status().isOk());

        Sort sort = searchedPageable().getSort();
        assertThat(sort.getOrderFor("passwordHash")).isNull();
        assertThat(sort.getOrderFor("name")).isNotNull();
    }

    @Test
    void blankFiltersAreNotApplied() throws Exception {
        mvc.perform(get("/catalog")
                        .param("okrug", "")
                        .param("district", "   ")
                        .param("q", "")
                        .param("status", " "))
                .andExpect(status().isOk());

        assertThat(searchedFilter()).isEqualTo(new CatalogFilter(null, null, null, null, null, null, null, null, null));
    }

    @Test
    void filtersArePassedTrimmed() throws Exception {
        mvc.perform(get("/catalog")
                        .param("okrug", "  Okrug A ")
                        .param("district", "District A")
                        .param("q", "  library  ")
                        .param("transportType", "TRAM")
                        .param("distanceFrom", "100")
                        .param("distanceTo", "500")
                        .param("loadFrom", "10.5")
                        .param("loadTo", "99.5")
                        .param("status", "active"))
                .andExpect(status().isOk());

        assertThat(searchedFilter())
                .isEqualTo(new CatalogFilter(
                        "Okrug A", "District A", "library", TransportType.TRAM, 100, 500, 10.5, 99.5, "active"));
    }

    @Test
    void rowsFragmentPushesCatalogAddressWithFilters() throws Exception {
        MvcResult result = mvc.perform(get("/catalog/rows")
                        .param("okrug", CYRILLIC_OKRUG)
                        .param("q", "")
                        .param("size", "50")
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andReturn();

        String pushed = result.getResponse().getHeader("HX-Push-Url");
        assertThat(pushed).startsWith("/catalog?").doesNotContain("/catalog/rows");
        assertThat(pushed).contains("size=50").doesNotContain("q=");
        assertThat(pushed.chars()).as("header must be plain ASCII").allMatch(c -> c < 128);
        assertThat(java.net.URLDecoder.decode(pushed, StandardCharsets.UTF_8)).contains("okrug=" + CYRILLIC_OKRUG);
    }

    @Test
    void rowsFragmentIsNotAFullPage() throws Exception {
        mvc.perform(get("/catalog/rows"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void nextPageLinksKeepFiltersAndPreviousIsAbsentOnFirstPage() throws Exception {
        stubCatalog(0, 20, 45);

        mvc.perform(get("/catalog").param("okrug", "Okrug A"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("prevUrl", "prevRowsUrl"))
                .andExpect(model().attribute("nextUrl", containsString("page=1")))
                .andExpect(model().attribute("nextUrl", containsString("okrug=Okrug%20A")))
                .andExpect(model().attribute("nextRowsUrl", containsString("/catalog/rows?")));
    }

    @Test
    void lastPageHasOnlyPreviousLinks() throws Exception {
        stubCatalog(2, 20, 45);

        mvc.perform(get("/catalog").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("prevUrl", containsString("page=1")))
                .andExpect(model().attributeDoesNotExist("nextUrl", "nextRowsUrl"));
    }

    @Test
    void districtsAreLoadedOnlyForSelectedOkrug() throws Exception {
        mvc.perform(get("/catalog/districts").param("okrug", " "))
                .andExpect(status().isOk())
                .andExpect(model().attribute("districts", List.of()));
        verify(stats, never()).byDistrict(anyString());

        when(stats.byDistrict("Okrug A")).thenReturn(List.of(new DistrictCount("District A", 3)));
        mvc.perform(get("/catalog/districts").param("okrug", "Okrug A"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("districts", List.of(new DistrictCount("District A", 3))));
    }

    @Test
    void searchTextIsEscapedInThePage() throws Exception {
        mvc.perform(get("/catalog").param("q", "\"><script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }

    @Test
    void accessPointNamesAreEscapedInTheTable() throws Exception {
        doAnswer(inv -> new PageImpl<>(
                        List.of(point(1, "<img src=x onerror=alert(1)>")), inv.<Pageable>getArgument(1), 1))
                .when(accessPoints)
                .search(any(), any());

        mvc.perform(get("/catalog/rows"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<img src=x onerror=alert(1)>"))))
                .andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")));
    }

    private void stubCatalog(int page, int size, long total) {
        List<AccessPointView> content = LongStream.rangeClosed(1, Math.min(size, total - (long) page * size))
                .mapToObj(i -> point(i, "Point " + i))
                .toList();
        doAnswer(inv -> new PageImpl<>(content, inv.<Pageable>getArgument(1), total))
                .when(accessPoints)
                .search(any(), any());
    }

    private Pageable searchedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(accessPoints).search(any(), captor.capture());
        return captor.getValue();
    }

    private CatalogFilter searchedFilter() {
        ArgumentCaptor<CatalogFilter> captor = ArgumentCaptor.forClass(CatalogFilter.class);
        verify(accessPoints).search(captor.capture(), any());
        return captor.getValue();
    }
}
