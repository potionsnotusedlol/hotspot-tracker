// Инициализация приложения
document.addEventListener('DOMContentLoaded', function() {
    // ↑ Ждём, пока всё в HTML загрузится, потом запускаем код
    
    console.log('App initialized');
    // ↑ Вывод в консоль браузера (F12 → Console)
    
    // HTMX конфигурация
    htmx.config.defaultIndicatorStyle = 'spinner';
    // ↑ Показывать крутящейся кружочек при загрузке
    
    htmx.config.defaultSwapStyle = 'innerHTML';
    // ↑ Заменять внутреннее содержимое (не весь элемент)
    
    // Слушаем HTMX события
    document.body.addEventListener('htmx:configRequest', function(event) {
        // ↑ Перед каждым запросом HTMX
        
        // Добавляем CSRF токен если есть
        const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
        // ↑ CSRF = защита от подделки запросов
        
        if (token && header) {
            event.detail.headers[header] = token;
            // ↑ Добавляем токен в заголовок запроса
        }
    });
});

// Утилита для показа сообщений
function showNotification(message, type = 'info') {
    // ↑ type может быть: 'info', 'success', 'error', 'warning'
    
    const notification = document.createElement('div');
    // ↑ Создаём новый элемент div
    
    notification.className = `notification notification-${type}`;
    // ↑ Добавляем CSS класс для стилизации
    
    notification.textContent = message;
    // ↑ Кладём текст сообщения
    
    document.body.appendChild(notification);
    // ↑ Добавляем элемент в страницу
    
    setTimeout(() => {
        notification.remove();
        // ↑ Через 3 секунды удаляем сообщение
    }, 3000);
}
