# REST API для системы бронирования отелей на базе Spring Boot с использованием микросервисной архитектуры

Цель проекта — создать распределённое приложение с несколькими сервисами, реализующими основные функции бронирования, управления гостиницами и маршрутизации запросов через API Gateway.

## Компоненты:
- **API Gateway** —  шлюз, осуществляющий маршрутизацию запросов, прокси-передачу JWT в backend-сервисы, проверку доступа на входе и/или на сервисах согласно выбранной схеме
- **Booking Service** — создание бронирований и управление ими, интеграция с Hotel Service, регистрация и авторизация пользователей, администрирование. Booking Service создаёт бронирование в статусе PENDING,
   запрашивает подтверждение доступности номера у Hotel Service. При успешном ответе переводит бронирование в CONFIRMED, при сбое выполняет компенсацию (отменяет бронирование)
- **Hotel Management Service** — управление отелями и номерами (CRUD), агрегации по загруженности
- **Eureka Server** — сервер регистрации и динамического обнаружения сервисов

Каждый сервис должен быть самостоятельным Spring Boot приложением с in-memory базой данных (H2).

---

## Функциональные требования

- Регистрация и вход пользователей (JWT) через Booking Service  
- Создание бронирований с двухшаговой согласованностью (`PENDING → CONFIRMED/CANCELLED` с компенсацией)  
- Идемпотентность запросов с `requestId`  
- Повторы с экспоненциальной паузой и таймауты при удалённых вызовах  
- Подсказки по выбору номера (сортировка по `timesBooked`, затем по `id`)  
- Администрирование пользователей (CRUD) и отелей/номеров (CRUD) для админов  
- Агрегации: популярность номеров по `timesBooked`  
- Сквозная корреляция с заголовком `X-Correlation-Id`  

---

## Архитектура и порты

| Сервис            | Порт           | Примечание |
|-------------------|----------------|------------|
| eureka-server     | 8761           | Service Registry |
| gateway-service   | 8080           | Маршрутизирует запросы через Eureka и проксирует `Authorization` |
| hotel-service     | случайный (0)  | Регистрируется как `hotel-service` |
| booking-service   | случайный (0)  | Регистрируется как `booking-service` |

---

## Требования

- Java 17+.
- Spring Boot 3.5.x.
- Spring Cloud (релиз-трейн, совместимый со Spring Boot по BOM).
- Spring Data JPA + H2 (in-memory).
- Spring Security + JWT (реализация на усмотрение, совместимая со Spring Security).
- Spring Cloud Eureka (Service Discovery).
- Spring Cloud Gateway (API Gateway).
- Lombok, MapStruct (для DTO и маппинга).

---

## Сборка и запуск

1. Запустить Eureka:

```
mvn -pl eureka-server spring-boot:run
```
2. Запустить API Gateway:
```
mvn -pl gateway-service spring-boot:run
```
3. Запустить Hotel Service и Booking Service (в отдельных терминалах):
```
mvn -pl hotel-service spring-boot:run
mvn -pl booking-service spring-boot:run
```
---

## Конфигурация JWT

Для демонстрации используется симметричный ключ HMAC, секрет задаётся свойством:
```
security:
  jwt:
    secret: <your-secret>
```
---

## Быстрый сценарий (через Gateway на 8080, Postman)
1. Регистрация пользователя
```
POST /auth/register
Content-Type: application/json

{
  "username": "user1",
  "password": "pass"
}
```
Для админа добавьте "admin": true

2. Вход и получение JWT
```
POST /auth/login
Content-Type: application/json

{
  "username": "user1",
  "password": "pass"
}
```
Скопируйте access_token из ответа и используйте в заголовке Authorization: Bearer <token>

3. Создание отеля и номера (только admin)
Отель
```
POST /hotels
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Hotel A",
  "city": "Moscow",
  "address": "Red Square, 1"
}
```

4. Комната
```
POST /rooms
Authorization: Bearer <token>
Content-Type: application/json

{
  "number": "101",
  "capacity": 2,
  "available": true
}
```

5. Подсказки по номерам
```
GET /bookings/suggestions
Authorization: Bearer <token>
```
6. Создание бронирования (идемпотентно по requestId)
```
POST /bookings
Authorization: Bearer <token>
Content-Type: application/json

{
  "roomId": 1,
  "startDate": "2025-10-20",
  "endDate": "2025-10-22",
  "requestId": "req-123"
}
```
7. История бронирований пользователя

```
GET /bookings
Authorization: Bearer <token>
```

---
## Основные эндпойнты через Gateway

1. Аутентификация (Booking)
```
POST /auth/register — регистрация

POST /auth/login — получение JWT
```
2. Бронирования (Booking)
```
GET /bookings — мои бронирования

POST /bookings — создать бронирование (PENDING → CONFIRMED / RELEASE)

GET /bookings/suggestions — подсказки по комнатам

GET /bookings/all — все бронирования (admin)
```
3.Пользователи (Booking, admin)
```
GET /admin/users, GET /admin/users/{id}

PUT /admin/users/{id}, DELETE /admin/users/{id}
```
4.Отели и номера (Hotel)
```
GET /hotels, GET /hotels/{id}

POST /hotels, PUT /hotels/{id}, DELETE /hotels/{id} (admin)

GET /rooms/{id}, POST /rooms, PUT /rooms/{id}, DELETE /rooms/{id} (admin)

POST /rooms/{id}/hold — удержание слота (идемпотентно)

POST /rooms/{id}/confirm — подтверждение удержания

POST /rooms/{id}/release — освобождение удержания (компенсация)
```
5.Статистика (Hotel)
```
GET /stats/rooms/popular — популярность номеров по timesBooked
```

---
## Согласованность и надёжность

- Локальные транзакции внутри сервисов (@Transactional)

- Двухшаговый процесс бронирования: PENDING → hold → CONFIRM → CONFIRMED / RELEASE

- Идемпотентность по requestId

- Повторы с backoff и таймауты при вызовах к Hotel через WebClient

- Сквозная корреляция через X-Correlation-Id

---
## Консоль H2

- Включена для Hotel Service: /h2-console

- Схема JDBC и логика таблиц задаются через @Entity + ddl-auto=update

---
## Swagger / OpenAPI

- Booking Service UI: http://localhost:<booking-port>/swagger-ui.html

- Hotel Service UI: http://localhost:<hotel-port>/swagger-ui.html

- Gateway UI: http://localhost:8080/swagger-ui.html (выбор спецификаций)

 ---
## Тестирование

- **Минимальные тесты**: HotelAvailabilityTests — идемпотентность hold/confirm/release

- **Интеграционные тесты**:

  - Booking Service: BookingHttpIT — успешное бронирование, компенсации, таймауты, идемпотентность

  - Hotel Service: HotelHttpIT — админ может создавать отели, HotelMoreTests — занятость по датам, available-флаг, статистика

Запуск всех тестов:
```
mvn -q -DskipTests=false test
```
