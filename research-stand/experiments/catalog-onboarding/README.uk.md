# Запуск додаткових сценаріїв на Windows

## Окремий прогін 4.4 із двома MANUAL-маршрутами

З кореня Adaptive-Gateway у PowerShell виконайте команду нижче. Вона запускає тільки 10 пар порівняння 4.4. Кожен запуск створює власний каталог `results/4.4-methods-<UTC>`.

```powershell
python -B research-stand/experiments/catalog-onboarding/compare44.py --pg-bin "C:\Program Files\PostgreSQL\18\bin" --java "D:\java\jdk-21.0.10\bin\java.exe"
```

Перевірка збережених результатів без запуску стенду:

```powershell
python -B research-stand/experiments/catalog-onboarding/verify44.py "research-stand/experiments/catalog-onboarding/results/ID_ПРОГОНУ"
```

[Звіт 4.4](REPORT-4.4.uk.md) · [Інструкція для вимірювання часу оператора](ADMIN-PANEL-4.4.uk.md)

## Архівний комбінований прогін 4.3–4.5

Команда нижче відтворює попередню методику, включно із захищеним MANUAL-маршрутом на весь Catalog.

У корені локального Adaptive-Gateway мають бути зібрані виконувані JAR для Eureka, Auth, Gateway, Adaptive Manager та трьох сервісів дослідницького стенду. Потрібні Python 3.10+, Java 21 та локальні програми PostgreSQL 18 (`initdb`, `postgres`, `pg_ctl`, `pg_isready`, `psql`). Скрипт використовує стандартну бібліотеку Python. Приклад для PowerShell з кореня репозиторію:

```powershell
python -B research-stand/experiments/catalog-onboarding/study.py --pg-bin "C:\Program Files\PostgreSQL\18\bin" --java "D:\java\jdk-21.0.10\bin\java.exe"
```

Програма створює приватний тимчасовий кластер PostgreSQL, запускає власні процеси, виконує 10 первинних підключень, 10 пар MANUAL/AUTOMATIC і 20 пар циклів. Основні порти стенду — 38088, 38089, 38334–38336, 38761–38763, 38911–38914 і 39547 на loopback. Порт приватної БД можна змінити параметром `--pg-port`. Запускайте один примірник інструменту за раз. Скрипт завершить роботу при зайнятому порту.

Кожен запуск створює окремий каталог `results/<UTC-ідентифікатор>`. У `raw.jsonl` кожен рядок відповідає одному повтору. Часові позначки з суфіксом `_ns` записано в наносекундах монотонного годинника. Тривалості з суфіксом `_ms` наведено в мілісекундах. JSONL зберігається після кожного повтору, CSV і статистика — при завершенні. Першу помилку записано до відповідного рядка й умов прогону, подальші вимірювання припиняються. Окремий шлях для результатів задає `--output`.

Для незалежної перевірки завершеного прогону виконайте `python research-stand/experiments/catalog-onboarding/verify_results.py <каталог-прогону>`. Перевірка читає локальні файли з результатами.

[Результати та межі вимірювань](REPORT.uk.md)
