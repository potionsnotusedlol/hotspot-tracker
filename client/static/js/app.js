// Инициализация приложения
document.addEventListener('DOMContentLoaded', function() {
    // ↑ Ждём, пока всё в HTML загрузится, потом запускаем код
    
    console.log('App initialized');
    // ↑ Вывод в консоль браузера (F12 → Console)
    
    // HTMX конфигурация
    htmx.config.includeIndicatorCSS = false;
    // ↑ htmx по умолчанию вставляет свой <style>, CSP его запрещает.
    //   Стили индикатора лежат в components.css. Замена по умолчанию (innerHTML) уже задана в htmx

    // Слушаем HTMX события
    document.body.addEventListener('htmx:config:request', function(event) {
        // ↑ Перед каждым запросом HTMX
        
        // Добавляем CSRF токен если есть
        const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
        // ↑ CSRF = защита от подделки запросов
        
        if (token && header) {
            event.detail.ctx.request.headers[header] = token;
            // ↑ Добавляем токен в заголовок запроса
        }
    });

    // Очищаем поле чата после успешной отправки
    document.body.addEventListener('htmx:after:request', function(event) {
        const form = event.target;
        // ↑ htmx 4 шлёт событие на элемент, который запустил запрос
        const response = event.detail.ctx.response;
        if (form.classList.contains('chat-form') && response && response.status < 400) {
            form.reset();
            // ↑ hx-on для этого CSP не разрешает, поэтому делаем здесь
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
