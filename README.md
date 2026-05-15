# RideOn

A purpose-built platform for motorcyclists. Find routes worth riding, see exactly what the weather will be like along your route at the time you'll actually be there, plan group rides, and connect with other riders in your area.

Built as a long-term personal project — evolving from a monolith to microservices to a cloud-deployed production system.

## The problem

Riders currently juggle three tools that weren't built for them:
- **Google Maps** — routes for speed, not enjoyment
- **WhatsApp** — group coordination with no memory or structure
- **Windy.com** — weather at a single point, not along a 200km route over 4 hours

RideOn replaces all three for the specific context of motorcycle riding.

## Why this exists

This project started because I actually ride motorcycles and the problem is real - I want a tool that tells me what the weather will be like at a specific point on a route at the specific time I'll be there.

I want to know relevant information about road surface, or even how fun the route is - something that no one but a rider who's been there would be able to say.

And with all of that, I also want a tool to connect to people in the community, plan rides together, share routes, help each other.

The project is also deliberately built to be a learning journey. I want to take something from zero to a real, released product — not a tutorial clone, not a throwaway — and understand every layer along the way.

The architecture evolves intentionally through stages: starting as a simple local monolith, splitting into microservices, containerizing with full observability, and eventually migrating to a cloud-deployed production system on AWS. Each migration is done manually and deliberately, not skipped to the end state, because the point is to understand *why* each architectural decision exists — not just that it does.

This means the codebase at any given point reflects where I am in that journey, not where it's going. The foundation is built to support what comes next without having to tear down what came before.

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
├── service/       Business logic
└── security/      JWT filter, entry point, utilities
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
