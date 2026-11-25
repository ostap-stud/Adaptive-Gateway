# Дослідницький стенд Adaptive Gateway

Стенд моделює одне замовлення з кількох товарів. Усі клієнтські запити надходять через Gateway на порт 8088. Eureka знаходить сервіси, Manager отримує їхні OpenAPI й передає бажану конфігурацію в Auth. Три бізнес-застосунки запускаються незалежними JVM. Спільна бібліотека `stand-support` містить декларацію доступу, побудову розширення OpenAPI й перевірки вхідних даних.

| Застосунок | Локальний порт | Схема й дані |
|---|---:|---|
| catalog-service | 8911 | `catalog.category`, `catalog.product`. Назва, категорія, поточна ціна у гривнях, ознака активності. Початкові дані — 3 категорії та 6 товарів. |
| inventory-service | 8912 | `inventory.stock`, `reservation`, `reservation_item`. Фізичний залишок, зарезервована й доступна кількості, стан резервування за UUID замовлення. |
| order-service | 8913 | `orders.purchase`, `orders.line`. UUID, дата створення, стан, кількість і ціна кожної позиції на момент оформлення. Підсумок — сума добутків кількості на ціну. |

### Що відбувається під час замовлення

1. Клієнт генерує UUID і надсилає позиції до Order через Gateway. Order перевіряє кошик та читає актуальні ціни за прямим HTTP з Catalog. Зберігає замовлення у стані `PENDING` разом зі знімками цін. Повтор із тим самим UUID використовує збережені ціни.
2. Order звертається безпосередньо до Inventory. Одне умовне оновлення PostgreSQL для кожної позиції перевіряє кількість і збільшує резерв. Позиції впорядковано за ID. Нестача хоча б одного товару відкочує весь резерв і повертає 409.
3. Після підтвердження Inventory стан замовлення стає `RESERVED`. Адміністратор може перевести його в `CONFIRMED` або скасувати. Скасування фіксує `CANCELLING` перед віддаленим викликом і `CANCELLED` після звільнення запасів. Фізична відвантажка виходить за межі моделі.
4. Таймаути з’єднання й читання становлять 1,5 і 3 секунди на один запит. При 503 клієнт повторює той самий POST з тим самим UUID та кошиком або адміністратор повторює скасування. Збережений ідентифікатор, блокування рядка та позначка звільнення дозволяють пережити втрату відповіді. Якщо облік запасів недоступний, `PENDING` або `CANCELLING` можуть зберігатися до повтору. Фонового узгодження немає. Для нової покупки після скасування потрібен новий UUID.
5. Прикладні сервіси слухають loopback. Довіра між процесами ґрунтується на ізольованому хості. Власника замовлення модель не зберігає. Право `orders.read` дає доступ до всіх замовлень. `INTERNAL` блокує маршрут на Gateway. Процес із доступом до локального backend може викликати його прямо.

### Запуск у Windows та IntelliJ IDEA

1. Відкрийте кореневий Maven-проєкт Adaptive Gateway з JDK 21. У вікні **Maven → Link Maven Projects** додайте `research-stand/pom.xml`. Новий агрегатор має власне дерево модулів. Його параметри відповідають стеку Auth у цій копії — Spring Boot 3.3.5, Spring Cloud 2023.0.4, Kotlin 1.9.25, Springdoc 2.6.0, цільовий байткод Java 17.
2. В одному локальному екземплярі PostgreSQL створіть окрему порожню БД `adaptive_commerce` поряд з інфраструктурною БД. Наприклад, у консолі БД `postgres` виконайте `CREATE DATABASE adaptive_commerce;`. Користувачу стенду потрібні права на створення схем і таблиць у цій БД. Кожен застосунок звертається лише до таблиць своєї схеми. Спільний обліковий запис БД є спрощенням локального запуску.
3. Скопіюйте `research-stand/local.properties.example` до `research-stand/local.properties`, заповніть користувача й пароль PostgreSQL. Для нестандартної адреси додайте `STAND_PG_HOST`; порт задає `STAND_PG_PORT`. Сервіси самі створюють таблиці й заповнюють демонстраційні рядки під час першого запуску. Наступні запуски зберігають відредаговані ціни, залишки й замовлення.
4. Підготуйте інфраструктурні `config/local.properties` і БД за [інструкцією основного проєкту](../docs/operations.uk.md). Запустіть наявні конфігурації IDEA у порядку **Eureka → Auth → Gateway → Adaptive Manager**. Для першого досліду з OBSERVE запустіть Manager у цьому режимі ще до запуску трьох сервісів. Наявний демонстраційний сервіс можна запускати окремо.
5. Після Maven-імпорту запустіть **Research - catalog-service**, **Research - inventory-service**, **Research - order-service**. Робочий каталог для всіх — корінь репозиторію. Без IDEA виконайте з кореня `mvn -f research-stand/pom.xml package` та запустіть три JAR за прикладом `java -jar research-stand/catalog-service/target/catalog-service-1.0.0.jar` (замініть назву для решти). Таймаути й прямі адреси дозволяють одразу діагностувати недоступний backend. Змінні `STAND_INVENTORY_URL`, `STAND_CATALOG_URL`, `STAND_EUREKA` задають інші локальні адреси.
6. Перевірте Eureka на `http://localhost:8761` і `http://localhost:8335/observations`. Для кожного сервісу потрібен один UP-екземпляр. Metadata містить `gateway.enabled=true`, `gateway.base-path=/<service>`, `gateway.openapi-path=/<service>/v3/api-docs`. Імена — lowercase, бізнес-шляхи мають той самий префікс на backend і Gateway. Manager обробляє JSON OpenAPI 3.0/3.1 з підтримуваними шляхами та методами, без префікса в `servers`.
7. У локальному APPLY/DISCOVERY дочекайтеся синхронізації. Перевірте `http://localhost:8088/catalog-service/products`, `/swagger-ui.html`, `/swagger/config` та `/swagger/services/catalog-service`. У режимі OBSERVE запис і публікацію відкладають до APPLY.
8. Запустіть наявну адмінпанель з каталогу `admin-panel` через `npm ci` (перший запуск) і `npm run dev`. На `http://127.0.0.1:5173` увійдіть адміністратором. Відкрийте **Services**, **Operations**, **Permissions** і **Adaptive**. Окрема інструкція для інтерфейсу — [README адмінпанелі](../admin-panel/README.md).

### REST API

Префікс кожного рядка — назва відповідного сервісу, наприклад `/catalog-service/products`. У таблиці `P(…)` означає декларацію `x-access: {"type":"PERMISSION","permissions":[…]}`. Для решти типів об’єкт має лише поле `type`.

| Сервіс | Метод і шлях | Дія | Доступ |
|---|---|---|---|
| Catalog | GET /products | Список, `page` від 0, `size` 1–100, `q`, `categoryId`, `minPrice`, `maxPrice` | PUBLIC |
| Catalog | GET /products/{id} | Активний товар | PUBLIC |
| Catalog | GET /categories | Категорії | PUBLIC |
| Catalog | POST /products | Створення, 201 | P(catalog.write) |
| Catalog | PUT /products/{id} | Заміна назви, ціни, категорії | P(catalog.write) |
| Catalog | DELETE /products/{id} | Деактивація, 204 | P(catalog.write) |
| Catalog | GET /selection | До 5 товарів до 1000 грн. Наявність та доступ залежать від профілю | Див. експерименти |
| Order | POST /orders | Створення або повтор за UUID, 201 | AUTHENTICATED |
| Order | GET /orders/{id} | Замовлення та ціни позицій | P(orders.read) |
| Order | GET /orders | Список за часом створення, `page`, `size` | P(orders.read) |
| Order | POST /orders/{id}/cancel | Скасування та звільнення товарів | P(orders.manage) |
| Order | PATCH /orders/{id}/status | Тіло `{"status":"CONFIRMED"}` або `CANCELLED` | P(orders.manage) |
| Inventory | GET /stock/{id} | Фізична, зарезервована й доступна кількості | P(inventory.manage) |
| Inventory | GET /stock/{id}/availability?quantity=2 | Перевірка можливості резерву | AUTHENTICATED |
| Inventory | PUT /stock/{id} | Встановлення фізичної кількості, тіло `{"onHand":20}` | P(inventory.manage) |
| Inventory | PUT /reservations/{id} | Атомарне резервування, 204 | INTERNAL |
| Inventory | DELETE /reservations/{id} | Ідемпотентне звільнення, 204 | INTERNAL |

Приклад створення товару — `{"name":"Док-станція","categoryId":1,"price":850.50}`. Для нового товару задайте запас через Inventory. Приклад тіла POST замовлення нижче. UUID генерує клієнт для кожної нової покупки. Запит має містити від 1 до 100 різних товарів.

```json
{"id":"91054200-c48e-4450-9d27-c602262388db","items":[{"productId":1,"quantity":2},{"productId":2,"quantity":1}]}
```

Валідація повертає 400, пошук відсутнього активного товару — 404, конфлікт резерву або стану — 409. Якщо кошик неможливо зарезервувати, після поповнення складу можна повторити початковий запит.

### Доступ і скидання даних

У панелі **Users** створіть роль і призначте її тестовому користувачу. У **Permissions** зіставте назву дозволу з повною назвою ролі, наприклад `catalog.write → ROLE_COMMERCE_EDITOR`. Те саме робить адміністратор через Gateway запитом `PUT /auth-service/policy/permissions/catalog.write` з тілом `{"roles":["ROLE_COMMERCE_EDITOR"],"reason":"Редагування каталогу стенду"}`. Окремо налаштуйте `orders.read`, `orders.manage`, `inventory.manage` та експериментальний `catalog.selection`. Порожній mapping блокує операцію. Менеджер представляє PERMISSION як AUTHENTICATED із переліком permissions, INTERNAL як BLOCKED. JWT видає наявний Auth.

Для повного скидання бізнес-даних зупиніть три застосунки, підключіться **до БД adaptive_commerce** й виконайте [reset-demo.sql](reset-demo.sql). Скрипт перевіряє ім’я БД та видаляє в транзакції лише сім відомих таблиць, без CASCADE. Запустіть сервіси знову. Ця дія знищує всі замовлення, резерви й правки каталогу. Для повтору первинної реєстрації з порожньою інфраструктурою використовуйте ізольований приймальний прогін з окремою БД Auth.

[Готові запити IntelliJ HTTP Client](commerce.http) · [Вісім дослідницьких сценаріїв](EXPERIMENTS.uk.md) · [Перевірки й фактичні результати](VERIFICATION.uk.md)
