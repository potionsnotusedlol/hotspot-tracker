// Утилиты для работы с графиками (Chart.js)

function createLineChart(ctx, labels, data, label) {
    // ↑ ctx = элемент canvas в HTML
    //   labels = названия точек на оси X (часы, дни и т.д.)
    //   data = массив значений для графика
    //   label = название линии графика
    
    return new Chart(ctx, {
        type: 'line',
        // ↑ Линейный график (не столбики, не кольцо и т.д.)
        
        data: {
            labels: labels,
            datasets: [{
                label: label,
                data: data,
                borderColor: '#0066cc',
                // ↑ Синий цвет линии
                
                backgroundColor: 'rgba(0, 102, 204, 0.1)',
                // ↑ Светло-синий цвет области под линией
                
                fill: true,
                // ↑ Заполнять область под линией
                
                tension: 0.4
                // ↑ 0 = прямые линии, 1 = волнистые линии, 0.4 = красиво
            }]
        },
        options: {
            responsive: true,
            // ↑ Граффик автоматически масштабируется
            
            plugins: {
                legend: {
                    display: true,
                    position: 'top'
                    // ↑ Легенда вверху
                }
            },
            scales: {
                y: {
                    beginAtZero: true
                    // ↑ Ось Y начинается с нуля
                }
            }
        }
    });
}

function createBarChart(ctx, labels, data, label) {
    // ↑ Столбчатый график (как гистограмма)
    
    return new Chart(ctx, {
        type: 'bar',
        // ↑ Столбики вместо линии
        
        data: {
            labels: labels,
            datasets: [{
                label: label,
                data: data,
                backgroundColor: '#0066cc'
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: {
                    display: true,
                    position: 'top'
                }
            },
            scales: {
                y: {
                    beginAtZero: true
                }
            }
        }
    });
}
