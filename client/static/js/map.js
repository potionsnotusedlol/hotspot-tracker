// Карты (Leaflet): карточка точки и карта с кластерами. Данные берём из data-атрибутов и JSON-ответа

// Цвет по уровню нагрузки. Уровень также выводится текстом (легенда и таблица), не только цветом
const LOAD_COLORS = {
    LOW: '#27ae60',
    MEDIUM: '#f39c12',
    HIGH: '#e74c3c'
};

// Центр Москвы, если точек ещё нет
const MOSCOW_CENTER = [55.7558, 37.6173];

function createMap(element) {
    const map = L.map(element);
    // ↑ Создаём карту внутри переданного элемента

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors',
        maxZoom: 19
    }).addTo(map);
    // ↑ Слой с картинками плиток (дороги, дома)

    return map;
}

// Карточка точки: одна метка в её координатах
function initPointMap(element) {
    const lat = Number(element.dataset.lat);
    const lon = Number(element.dataset.lon);

    const map = createMap(element).setView([lat, lon], 16);
    L.marker([lat, lon]).addTo(map).bindTooltip(element.dataset.name);
    // ↑ Подсказка с названием точки при наведении
}

// Карта каталога: точки группируются в кластеры, а цвет показывает нагрузку
function initClusterMap(element) {
    const map = createMap(element).setView(MOSCOW_CENTER, 11);
    const clusters = L.markerClusterGroup();
    map.addLayer(clusters);

    const counter = document.getElementById(element.dataset.counter);
    // ↑ Элемент, где пишем, сколько точек показано

    fetch(element.dataset.source, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
        .then(function(response) {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response.json();
        })
        .then(function(points) {
            points.forEach(function(point) {
                const marker = L.circleMarker([point.latitude, point.longitude], {
                    radius: 7,
                    color: '#ffffff',
                    weight: 1,
                    fillColor: LOAD_COLORS[point.loadLevel],
                    fillOpacity: 0.9
                });

                marker.bindPopup(buildPopup(point));
                clusters.addLayer(marker);
            });

            if (counter) {
                counter.textContent = 'Точек на карте: ' + points.length;
            }
        })
        .catch(function() {
            showNotification('Не удалось загрузить точки для карты', 'error');
        });
}

// Всплывающее окно строим через DOM: название точки не должно стать HTML
function buildPopup(point) {
    const box = document.createElement('div');

    const title = document.createElement('strong');
    title.textContent = point.name;
    box.appendChild(title);

    const link = document.createElement('a');
    link.href = '/access-points/' + encodeURIComponent(point.id);
    link.textContent = 'Открыть карточку';
    box.appendChild(document.createElement('br'));
    box.appendChild(link);

    return box;
}

// Инициализация карт внутри root. Каждую карту создаём один раз
function initMaps(root = document) {
    root.querySelectorAll('[data-map="point"]').forEach(function(element) {
        if (!element.dataset.ready) {
            element.dataset.ready = 'true';
            initPointMap(element);
        }
    });

    root.querySelectorAll('[data-map="cluster"]').forEach(function(element) {
        if (!element.dataset.ready) {
            element.dataset.ready = 'true';
            initClusterMap(element);
        }
    });
}

document.addEventListener('DOMContentLoaded', function() {
    initMaps();
});

document.body.addEventListener('htmx:after:settle', function(event) {
    initMaps(event.target);
});
