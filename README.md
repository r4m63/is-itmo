# IS ITMO Lab

Учебная информационная система: веб‑админка для управления сущностями `Vehicle` и справочником `Coordinates`, выполнения спец‑запросов по БД и импорта данных из JSON с сохранением исходного файла в S3‑совместимом хранилище. Проект собран как Jakarta EE 11 приложение (`.war`) + отдельный фронтенд на React/Vite.

## Зачем это нужно
- CRUD по транспортным средствам и координатам с серверной пагинацией/фильтрацией.
- Встроенные «пресеты» спец‑операций (мин. пробег, поиск по типу/диапазону мощности, fuelConsumption > X) выполняются через SQL‑функции в PostgreSQL.
- Импорт JSON в один шаг: за одну транзакцию создаются сущности в БД и кладётся исходный файл в MinIO; при откате файл удаляется (двухфазный коммит через `TransactionSynchronizationRegistry`).
- Реактивные обновления таблиц через WebSocket broadcast («refresh»).
- История импортов с возможностью скачать исходный файл.
- Безопасное удаление координат: при наличии связанных `Vehicle` предлагается переназначить их на другие координаты.

## Архитектура и стек
**Backend (src/main/java)**
- Jakarta EE 11: JAX‑RS (REST API), CDI, Bean Validation, WebSocket.
- JPA/Hibernate 6.6 + Infinispan L2 cache (`infinispan-config-local.xml`, аннотация `@L2CacheStats` логирует хиты при включённом `-Dl2cache.stats.enabled=true`).
- PostgreSQL 16 (SQL схему и функции см. `sql/init.sql`).
- MinIO S3 для хранения файлов импорта (`MinioStorageService`).
- Двухфазный сценарий импорта: `VehicleImportTxService` регистрирует синхронизацию транзакции, сначала пишет в БД, затем в `beforeCompletion` грузит файл в MinIO; в `afterCompletion` фиксирует статус и чистит объект при rollback.
- WebSocket хаб `VehicleWsService` + endpoint `/ws/vehicles` рассылает событие `refresh` после CRUD/импорта.

**Frontend (frontend/)**
- React 19 + Vite 7, HeroUI, AG Grid (Infinite Row Model + серверные фильтры/сортировки), Sonner уведомления.
- Две страницы: `MainPage` (Vehicles) и `CoordinatesPage` (справочник). Модалки для создания/редактирования, import JSON, пресеты, история, выбор координат.
- API base настраивается в `frontend/cfg.js` (`API_BASE`).

**Инфраструктура**
- `docker-compose.yml` поднимает PostgreSQL и MinIO.
- Сборка backend: Gradle 8+, Java 17 (`./gradlew build` → `build/libs/app.war`).
- Деплой war на совместимый сервер приложений (WildFly / TomEE). `persistence.xml` ожидает JTA datasource `java:openejb/Resource/jdbc/MyDS` + необязательный non‑JTA (`MyDSUnmanaged`).

## Основные возможности
- Таблицы Vehicle/Coordinates с бесконечной прокруткой, сортировкой и фильтрами, выполняемыми на сервере (`GridTableRequest` → `GridTablePredicateBuilder`).
- CRUD с валидацией Bean Validation и оптимистичной версией (`@Version`).
- Спец‑операции через SQL‑функции (`fn_vehicle_min_distance`, `fn_vehicle_count_fuel_gt`, `fn_vehicle_list_fuel_gt`, `fn_vehicle_list_by_type`, `fn_vehicle_list_engine_between`) и отдельный REST (`/api/vehicle/special/*`).
- Импорт JSON массива `VehicleImportItemDto`: валидация до начала транзакции; логирование в `vehicle_import_operation`; файл кладётся в MinIO только при успешном коммите; история доступна по `/api/vehicle/import/history` и файл по `/api/vehicle/import/history/{id}/file`.
- История показывает статус, количество созданных записей, ссылку на исходный файл (если загрузка в MinIO прошла успешно).
- Удаление координат с проверкой внешних ключей: при конфликте возвращается 409 и фронт предлагает переназначить все связанные `Vehicle` на выбранные координаты (`/api/coordinates/{id}?reassignTo=...`).
- Кросс‑доменные запросы для локальной разработки настроены в `CorsFilter` (Origin whitelist `localhost:5173`, `localhost:22821`).

## Структура репозитория
- `src/main/java/ru/itmo/isitmolab` — backend (контроллеры, сервисы, DAO, DTO, WebSocket, утилиты, исключения).
- `src/main/resources` — `persistence.xml`, `beans.xml`, конфиг Infinispan.
- `src/main/webapp/WEB-INF/web.xml` — базовый web.xml.
- `sql/` — скрипты инициализации/сидов/миграций (`init.sql`, `seed_vehicle.sql`, `destroy.sql`, `migrate_add_import_file_columns.sql`).
- `frontend/` — исходники SPA, Vite конфиг, Tailwind 4, HeroUI, AG Grid.
- `docker-compose.yml` — PostgreSQL + MinIO.

## Быстрый старт локально
1) **Поднять БД и MinIO**
```bash
docker compose up -d
```
Переменные для compose: `DB_NAME`, `DB_USER`, `DB_PASS`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` (по умолчанию minioadmin/minioadmin). Консоль MinIO: http://localhost:9001.

2) **Инициализировать схему**
```bash
psql postgresql://$DB_USER:$DB_PASS@localhost:5432/$DB_NAME -f sql/init.sql
# необязательно: psql ... -f sql/seed_vehicle.sql
```

3) **Собрать backend**
```bash
./gradlew clean build
# артефакт: build/libs/app.war
```
Настройте JTA datasource с JNDI `java:openejb/Resource/jdbc/MyDS` на вашем сервере приложений и задеплойте `app.war` (WildFly/TomEE/Jakarta EE 11 совместимый). Hibernate L2 cache включён, статистика по хиту — `-Dl2cache.stats.enabled=true`.

4) **Запустить фронтенд**
```bash
cd frontend
npm install
npm run dev  # по умолчанию http://localhost:5173
```
При необходимости поменяйте `API_BASE` в `frontend/cfg.js` на адрес backend (пример: `http://localhost:8080/app`). Для продакшн‑сборки: `npm run build` → содержимое `frontend/dist` можно раздать nginx/httpd.

5) **Проверка**
- Откройте фронт, создайте Coordinates, затем Vehicle.
- Откройте два окна: при добавлении/редактировании должна прилетать команда `refresh` через WebSocket и таблица обновится.
- Импортируйте JSON (массив объектов `VehicleImportItemDto` с полями name, type, numberOfWheels, fuelConsumption, fuelType, coordinatesId, опциональными enginePower/capacity/distanceTravelled). История должна показать статус и дать скачать файл из MinIO.

## API
- `POST /api/vehicle` — создать Vehicle.
- `PUT /api/vehicle/{id}` — обновить.
- `DELETE /api/vehicle/{id}` — удалить.
- `GET /api/vehicle/{id}` — получить DTO.
- `POST /api/vehicle/query` — серверная таблица (body `GridTableRequest`).
- `POST /api/vehicle/import` — импорт JSON; заголовок `X-Filename` сохраняется в истории.
- `GET /api/vehicle/import/history[?limit=N]` — последние операции.
- `GET /api/vehicle/import/history/{id}/file` — скачать исходный файл из MinIO.
- `GET /api/vehicle/special/min-distance` | `count-fuel-gt` | `list-fuel-gt` | `by-type` | `by-engine-range` — спец‑запросы через SQL‑функции.
- `POST /api/coordinates/query` — таблица координат.
- `POST /api/coordinates` / `PUT /api/coordinates/{id}` / `DELETE /api/coordinates/{id}[?reassignTo=...]` — CRUD + переназначение.
- `GET /api/coordinates/search?q=...` — поиск для автокомплита.
- WebSocket: `/ws/vehicles` (broadcast `refresh`).
