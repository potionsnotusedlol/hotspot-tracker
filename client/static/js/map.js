// Утилиты для работы с картами (Leaflet)

let map;
// ↑ Глобальная переменная для хранения объекта карты

function initMap(containerId, latitude = 55.7558, longitude = 37.6173, zoom = 11) {
    // ↑ containerId = id элемента в HTML где карта
    //   latitude = широта (по умолчанию Москва)
    //   longitude = долгота (по умолчанию Москва)
    //   zoom = уровень приближения (11 = хороший для города)
    
    map = L.map(containerId).setView([latitude, longitude], zoom);
    // ↑ Создаём карту и центрируем на координаты
    
    // OpenStreetMap слой
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors',
        maxZoom: 19
    }).addTo(map);
    // ↑ Добавляем слой карты (картинки плиток с дорогами)
    
    return map;
}

function addMarker(lat, lng, title, popupText) {
    // ↑ Добавить маркер (флажок) на карту
    
    const marker = L.marker([lat, lng]).addTo(map);
    // ↑ Создаём маркер и добавляем на карту
    
    if (popupText) {
        marker.bindPopup(popupText);
        // ↑ При клике на маркер показывается окно с текстом
    }
    
    if (title) {
        marker.bindTooltip(title);
        // ↑ При наведении на маркер показывается подсказка
    }
    
    return marker;
}

function addCluster(markers) {
    // ↑ Группировать маркеры (если много маркеров, собрать их в один круг)
    // Пока в коде ничего нет, потому что нужна отдельная библиотека
}
