package ru.moscow.hotspot_tracker.main.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.AccessPointView;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.CatalogFilter;
import ru.moscow.hotspot_tracker.main.web.port.TransportType;
import ru.moscow.hotspot_tracker.main.web.port.StatsService;

/**
 * Каталог точек доступа (FR-CAT, FR-WEB-02). Полная страница открывается по /catalog,
 * а HTMX обновляет только таблицу через /catalog/rows и меняет адрес в адресной строке.
 */
@Controller
class CatalogController {

    private static final Set<String> SORT_FIELDS = Set.of("name", "district", "distanceToStopM", "estimatedLoad");
    private static final String DEFAULT_SORT = "name,asc";

    private final AccessPointService accessPoints;
    private final StatsService stats;

    CatalogController(AccessPointService accessPoints, StatsService stats) {
        this.accessPoints = accessPoints;
        this.stats = stats;
    }

    @GetMapping("/catalog")
    String catalog(CatalogQuery query, Model model) {
        addCatalogModel(query, model);
        return "catalog/index";
    }

    @GetMapping("/catalog/rows")
    String rows(CatalogQuery query, Model model, HttpServletResponse response) {
        addCatalogModel(query, model);
        // Адрес в строке браузера остаётся страницей каталога, а не /catalog/rows
        response.setHeader("HX-Push-Url", catalogUrl("/catalog", query, null));
        return "catalog/rows :: rows";
    }

    /** Зависимый список районов: выбор округа сбрасывает район. */
    @GetMapping("/catalog/districts")
    String districts(@RequestParam(required = false) String okrug, Model model) {
        String selected = StringUtils.hasText(okrug) ? okrug : null;
        model.addAttribute("districts", selected == null ? List.of() : stats.byDistrict(selected));
        model.addAttribute("selectedDistrict", null);
        return "catalog/filters :: district-field";
    }

    private void addCatalogModel(CatalogQuery query, Model model) {
        Pageable pageable = query.pageable();
        Page<AccessPointView> page = accessPoints.search(query.filter(), pageable);

        model.addAttribute("query", query);
        model.addAttribute("page", page);
        model.addAttribute("okrugs", stats.byOkrug());
        model.addAttribute("districts", query.okrug() == null ? List.of() : stats.byDistrict(query.okrug()));
        model.addAttribute("selectedDistrict", query.district());
        model.addAttribute("transportTypes", TransportType.values());
        model.addAttribute("pageSizes", List.of(10, 20, 50));

        if (page.hasPrevious()) {
            model.addAttribute("prevUrl", catalogUrl("/catalog", query, page.getNumber() - 1));
            model.addAttribute("prevRowsUrl", catalogUrl("/catalog/rows", query, page.getNumber() - 1));
        }
        if (page.hasNext()) {
            model.addAttribute("nextUrl", catalogUrl("/catalog", query, page.getNumber() + 1));
            model.addAttribute("nextRowsUrl", catalogUrl("/catalog/rows", query, page.getNumber() + 1));
        }
    }

    /** Ссылка с сохранёнными фильтрами: пустые поля в адрес не попадают. */
    private static String catalogUrl(String path, CatalogQuery query, Integer page) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        put(params, "okrug", query.okrug());
        put(params, "district", query.district());
        put(params, "q", query.q());
        put(params, "transportType", query.transportType());
        put(params, "distanceFrom", query.distanceFrom());
        put(params, "distanceTo", query.distanceTo());
        put(params, "loadFrom", query.loadFrom());
        put(params, "loadTo", query.loadTo());
        put(params, "status", query.status());
        put(params, "size", query.size());
        put(params, "sort", query.sort());
        put(params, "page", page);
        return UriComponentsBuilder.fromPath(path).queryParams(params).build().toUriString();
    }

    private static void put(MultiValueMap<String, String> params, String key, Object value) {
        if (value != null && StringUtils.hasText(value.toString())) {
            params.add(key, value.toString());
        }
    }

    /**
     * Параметры каталога из запроса. Строки пустые означают «не фильтровать».
     * Размер страницы ограничен, чтобы один запрос не выгрузил весь каталог (FR-CAT-01).
     */
    record CatalogQuery(
            String okrug,
            String district,
            String q,
            TransportType transportType,
            Integer distanceFrom,
            Integer distanceTo,
            Double loadFrom,
            Double loadTo,
            String status,
            Integer page,
            Integer size,
            String sort) {

        private static final int DEFAULT_SIZE = 20;
        private static final int MAX_SIZE = 100;

        CatalogFilter filter() {
            return new CatalogFilter(
                    blankToNull(okrug),
                    blankToNull(district),
                    blankToNull(q),
                    transportType,
                    distanceFrom,
                    distanceTo,
                    loadFrom,
                    loadTo,
                    blankToNull(status));
        }

        Pageable pageable() {
            int pageSize = size == null ? DEFAULT_SIZE : Math.clamp(size, 1, MAX_SIZE);
            int pageNumber = page == null ? 0 : Math.max(page, 0);
            return PageRequest.of(pageNumber, pageSize, parseSort());
        }

        private Sort parseSort() {
            String[] parts = (sort == null ? DEFAULT_SORT : sort).split(",");
            String property = SORT_FIELDS.contains(parts[0]) ? parts[0] : "name";
            Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            return Sort.by(direction, property);
        }

        private static String blankToNull(String value) {
            return StringUtils.hasText(value) ? value.trim() : null;
        }
    }
}
