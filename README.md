# RideOn

A purpose-built platform for motorcyclists. Find routes worth riding, see exactly what the weather will be like along your route at the time you'll actually be there, plan group rides, and connect with other riders in your area.

Built as a long-term personal project — evolving from a monolith to microservices to a cloud-deployed production system.

## The problem

Riders currently juggle three tools that weren't built for them:
- **Google Maps** — routes for speed, not enjoyment
- **WhatsApp** — group coordination with no memory or structure
- **Windy.com** — weather at a single point, not along a 200km route over 4 hours

RideOn replaces all three for the specific context of motorcycle riding.

## Features (in progress)

- Route discovery — browse community routes scored for curvature, elevation, and road surface
- Weather along a route — conditions checked at each waypoint at the time you'll actually be there
- GPX import and export — bring routes from Garmin, Komoot, or Strava
- Group ride planning — create a ride, attach a route, manage join requests
- Rider profiles — bike info, ride history, and uploaded routes as a community trust signal

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3, Spring Security 6 |
| Database | PostgreSQL 16 + PostGIS, Flyway migrations |
| Testing | JUnit 5, Mockito, Testcontainers |
| Build | Maven |
| Local infra | Docker, Docker Compose |
| CI | GitHub Actions |
| External APIs | OpenRouteService, Open-Meteo *(planned)* |
| Cloud *(planned)* | AWS — RDS, ECS, S3, SQS, SES, Cognito |

## Running locally

**Prerequisites:** Java 21, Docker Desktop, Maven

```bash
# 1. Clone the repo
git clone https://github.com/yourusername/rideon.git
cd rideon

# 2. Copy the example env file and fill in your values
cp .env.example .env

# 3. Start the database
docker compose up -d

# 4. Run the app
./mvnw spring-boot:run

# 5. Run tests
./mvnw test
```

The app will be available at `http://localhost:8080`.

## Environment variables

Copy `.env.example` to `.env` and set the following:

| Variable | Description |
|---|---|
| `DB_NAME` | Postgres database name |
| `DB_USER` | Postgres username |
| `DB_PASSWORD` | Postgres password |
| `DB_HOST` | Database host (default: localhost) |
| `DB_PORT` | Database port (default: 5432) |

## Project structure

```
src/main/java/com/rideon/
├── config/        Spring configuration classes
├── controller/    REST endpoints
├── domain/        Entities and value objects
├── dto/           Request and response objects
├── exception/     Custom exceptions
├── repository/    JPA repositories
└── service/       Business logic
```

## Build phases

| Phase | Description | Status |
|---|---|---|
| 1 | Monolith — auth, profiles, routes, group rides, notifications | In progress |
| 2 | Route engine + weather overlay | Planned |
| 3 | Microservices split | Planned |
| 4 | Docker Compose orchestration + observability | Planned |
| 5 | Cloud migration to AWS | Planned |

## Author

Davit Barnabishvili — [GitHub](https://github.com/DavitBarnabishvili) · [LinkedIn](https://www.linkedin.com/in/davit-barnabishvili-3a8460294/)
