# Lineage 2 Telegram Mini App Bot

Java/Spring Boot бот для Telegram Mini App. Первая реализованная функция - калькулятор крафта по данным из `datapack/aCis/data/xml/recipes.xml`, item XML, `wiki_items.db` и локальных иконок.

## Что уже есть

- Telegram bot polling через Bot API.
- Кнопка Mini App "Открыть калькулятор" на `/start` и `/craft`.
- Mini App UI с фильтром по grade, поиском рецептов и деревом материалов.
- API:
  - `GET /api/craft/grades`
  - `GET /api/craft/recipes?grade=C&q=sword`
  - `GET /api/craft/tree/{itemId}?count=1`
- Dockerfile и `docker-compose.yml`.

## Локальный запуск

```bash
mvn spring-boot:run
```

Открыть Mini App локально: [http://localhost:8080](http://localhost:8080)

## Запуск в контейнере

```bash
docker compose up --build
```

## Переменные окружения

```bash
TELEGRAM_BOT_TOKEN=123:telegram-token
PUBLIC_URL=https://your-public-mini-app-url
PORT=8080
```

Для Telegram Mini App `PUBLIC_URL` должен быть публичным HTTPS URL. Для локальной разработки можно использовать туннель и передать его как `PUBLIC_URL`.
