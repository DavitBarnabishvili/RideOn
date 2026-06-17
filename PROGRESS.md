# RideOn — Project Plan & Progress

> Keep this file updated as you complete work. Share it at the start of any new chat to restore full context instantly.
>
> Structure: the strategic plan (goal, scope, phases, shipping) is up top. The detailed decision log, tech-debt table, and environment gotchas are below — paste the whole file, or just the top half, depending on what a session needs.

---

## Plan at a glance

- The monolith is built to **feature-complete for V1** — auth, profiles, routes, ratings/reviews, roles & admin, ride planning (`Trip`), community hazards, and the weather overlay — then gets a frontend and is **deployed as V1 before any microservices work begins**.
- Everything from the microservices split onward is done on a **live, deployed system under CI/CD**. Deployment and continuous delivery are explicit learning goals introduced at the ship gate.
- The frontend is a **collaboration track** (mobile-first, map-centric) and the slowest leg. Backend work on later phases runs in parallel, so the next phase will likely begin before V1 is publicly live — by design.
- Each version ships against a **done-bar committed in advance**, so "perfect" never becomes the reason it isn't shipped.

---

## V1 scope — locked

The single source of truth for what V1 is. Decided across planning; do not silently re-expand.

### In V1
- **Routes:** create / import / export (GPX), browse near, browse a user's public routes. Geometry **immutable** post-create.
- **Ratings & reviews:** community star ratings → `popularity_score`. **This *is* the "is this route fun" signal** — entirely community-driven, no algorithmic curvature/scenic scoring.
- **Trip planning (static):** pick a route + departure time + **stops** (where, how long, how many) + **optional return leg**. The app estimates **arrival time at each sampled point** and shows the **raw weather at that arrival time** per point. Estimates **recompute when the app is reopened and re-planned** — not live while riding.
- **Arrival-time / speed engine (geometry-derived, no external routing):** a base open-road average speed per **bike type + cc**, reduced per section by **curvature** (bearing-change between GPX points), **gradient** (elevation delta), and **active hazards**, summed into a section-by-section time estimate. Rough by nature (rider skill swamps it) — set that expectation.
- **Community hazards / info markers (the "colored circles"):** rider-reported **and** admin-seeded "known" points. A **type taxonomy + custom fallback**, **optional photo**. Other riders can **corroborate (upvote/affirm)**, post a **timestamped update**, or **declare it gone** (system trusts the declaration). **Type-based expiry** as the no-effort safety net. Authenticated to report; public to view.
- **Map — progressive disclosure:** hazard/info dots **cluster when zoomed out, resolve into individual click-popup markers when zoomed in**. Backed by a query-time clustering endpoint.
- **Roles & admin:** `USER` / `ADMIN` (`MODERATOR` if needed). Admin can mark routes **protected**, **moderate** reviews/reports, and **seed/maintain known hazards** (Georgia-wide seeding is the cold-start fix).
- **Weather:** Open-Meteo, per-waypoint at arrival time, sampled, cached (Redis), resilient (Resilience4j).
- **Elevation backfill (OpenTopoData):** now **load-bearing** — feeds the gradient term of the speed engine, not just cosmetic.

### Out of V1 (deferred / fast-follow / post-ship)
- **Route-line difficulty coloring** (red/amber/green by road surface, recurring traffic, recurring bad-weather difficulty, etc.) — deferred. *When it returns it can reuse V1's curvature computation + the known-hazard/segment data, adding surface/traffic/recurring-weather inputs V1 doesn't have.*
- **Live in-ride re-estimation** (recompute remaining arrivals/weather from the rider's current GPS + time while riding) — fast-follow; needs GPS projection onto the route (`ST_LineLocatePoint`), off-route handling, live re-fetch, device location streaming over spotty mountain signal.
- **Weather-based condition scoring** (weather → safe/caution/avoid) — post-ship.
- **OpenRouteService entirely** — scenic routing, curvature-as-fun scoring, accurate segment durations. V1 uses the geometry-derived speed engine instead.
- **Smart Departure Planner** — optimal departure *window*, round-trips optimized as a unit. The headline differentiator, fast-follow after V1. V1 weather is the *forward* calc for a chosen departure time, not a search over departure times.
- **Notifications** — minimal at most for V1; rich channels deferred.

---

## What this project is

A purpose-built platform for motorcyclists. Find routes worth riding, see exactly what the weather will be like along your route **at the time you'll actually be there** (not just at the start city), plan rides, and connect with other riders.

**The core problem:** Riders juggle Google Maps (routes for speed not enjoyment), WhatsApp (no memory, no structure), and Windy.com (weather at a single point, not along a 200km route over 4 hours). RideOn replaces all three for the specific context of motorcycle riding.

**Repo:** https://github.com/DavitBarnabishvili/RideOn

---

## Goal & success metric

The goal is **not** adoption. The goal is to take a real, non-trivial system from **0 → the messy middle → shipped**, built to a standard I'm proud of, *learning every layer along the way* — regardless of whether anyone ends up using it.

- Product-oriented thinking is practiced **as a discipline** (build with the user in mind), not because adoption is the scoreboard.
- Aspiration: if the end state is the way I'd like it ("perfect"), it *might* be good enough to be adopted. That's a hope, not the metric.
- **Explicit learning target:** deployment, CI/CD, and operating + evolving a system that is already live. I have never deployed or worked on a running system; the plan deliberately creates that experience at the ship gate.
- **Definition-of-done discipline:** "perfect" is unfalsifiable and fights the "reach shipping" goal, so each ship has a **bar committed to in advance** (see V1 done-bar). A version is shipped when its bar is true — not when it stops feeling improvable.

---

## Architecture philosophy

The project deliberately evolves through stages rather than starting cloud-native. The goal is to *experience* each migration and understand *why* each architectural decision exists. A ship checkpoint sits before the microservices split so that every later migration is performed on a live system.

| Phase | Description | Built / run where |
|-------|-------------|-------------------|
| Phase 1 | Monolith — auth, profiles, routes, ratings/reviews, roles & admin, `Trip` planning, community hazards, notifications | Local |
| Phase 2 | Route engine — geometry speed engine + weather overlay (the differentiator) + display clustering | Local |
| Phase 3 | Frontend + Ship V1 + CI/CD — first public deploy | Simple host (pre-cloud) |
| Phase 4 | Microservices split — of a deployed, live system | Live, via CI/CD |
| Phase 5 | Orchestration + observability — on the running system | Live |
| Phase 6 | Migrate everything to AWS — motivated by felt ops pain | Cloud |

The ship checkpoint is what makes Phases 4–6 mean something: you split, instrument, and migrate a system that real riders can reach, with a deploy pipeline already under you — not a local toy.

---

## Current status

**Phase:** 1 — Monolith
**Current week:** Week 7 complete — post-review hardening + Step 0 weather spike (throwaway, done & deleted)
**Last completed:** Step 0 weather spike — validated arrival-time-along-route + live Open-Meteo and locked the weather decisions (see *Arrival-time & weather engine*). Prior: full code-review hardening (visibility on create, GPX no-elevation import, framework-exception mapping, 409 for protected, public `GET /routes/{id}`, JWT secret externalized, multipart limits, export `<ele>` guard)
**Test count:** **105 test methods** (0 disabled) — verify with `./mvnw test` (the spike was throwaway, untested, deleted)
**Active branch:** `main` (the spike ran on a throwaway `spike/weather-open-meteo` branch, now deleted — never merged)
**Next:** **Roles & admin** (first real build) → `Trip` + weather + speed engine → community hazards → ratings & reviews → clustering → (minimal) notifications

> Endpoints live: `POST /routes`, `GET /routes/{id}` (public for public routes), `PATCH /routes/{id}`, `POST /routes/import`, `GET /routes/{id}/export`, `GET /routes/my`, `GET /routes?userId=`, `GET /routes/near`, `DELETE /routes/{id}`. Shared `validateVisibility()` + `requireVisible()`. `UpdateRouteRequest` intentionally has no coordinates field (geometry immutable).

---

## Immediate next steps (start here)

A fresh session should direct work in this order. It front-loads de-risking, then builds foundation-first, and supersedes any terser ordering elsewhere in this file.

**Step 0 — Weather spike — ✅ DONE (throwaway, deleted).** Hardcoded Tbilisi→Gudauri (then re-checked against a 350-point Tbilisi→Shovi GPX), crude distance-based arrival times, live Open-Meteo per sampled point, compared point-by-point against Windy. Outcome: the arrival-time-along-route + live-weather premise holds for the high-confidence signals, with specifics that are now **locked decisions** (see *Arrival-time & weather engine*): model = Open-Meteo `gfs_seamless`; temperature is governed by elevation downscaling, which Open-Meteo does automatically against its 90 m DEM (proven — forcing `&elevation=1700` → 13.2 °C, matching Windy's grid-level value); rain is uncertain → present as condition + band, not precise mm/%; `precipitation_probability` is available but soft/nullable; timezone Asia/Tbilisi confirmed (UTC+4). The spike was never production code and has been deleted.

Then, in dependency order:
1. **Roles & admin** — foundational, low-risk; unblocks moderation (hazards, reviews) and route-protection, and resolves the hardcoded `ROLE_USER`. Decide single `role` column vs join table; seed the first admin.
2. **`Trip` + weather + speed engine** — the differentiator, de-risked by Step 0. `Trip` (route + departure + stops + optional return) needs the geometry speed engine for arrival times; weather hangs off those times via Open-Meteo `gfs_seamless`. Introduce Redis + Resilience4j here. Weather does **not** block on the elevation backfill — Open-Meteo's own DEM handles temperature; pass `&elevation=` per point only when we already hold one (GPX now). OpenTopoData backfill is slotted for the speed engine's **gradient term**, not weather.
3. **Community hazards** — needs roles (moderation). Also feeds the engine's `hazardFactor`; the engine can default that to 1 until hazards exist, so this can safely follow the engine.
4. **Ratings & reviews** — needs roles (moderation); fills `popularity_score` (Bayesian).
5. **Display clustering endpoint** — needs routes + hazards to exist.
6. **Notifications (minimal)** — last, only what the done-bar needs; likely deferrable past V1.

Throughout: stub external APIs in tests (no live calls in CI), and check every change against **V1 scope — locked** above before adding it.

---

## Completed work (summary)

Class-by-class detail lives in the repo; the *why* is in **Key decisions** below.

- **Foundation (Wk 1):** Spring Boot 3 / Java 21 / Maven, Docker Compose + Postgres 16, Flyway (`ddl-auto: validate`), GitHub Actions CI, Testcontainers, branch protection, `User` + `UserRepository`.
- **Auth (Wk 2):** Spring Security 6, JWT (jjwt 0.12.6), register/login/me, filter chain + entry point, `GlobalExceptionHandler`, OpenAPI/Swagger with bearer auth.
- **Rider profiles (Wk 3):** `Bike` entity (incl. `type` + `engineCc` — will drive the speed engine), `FileStorageService` interface + Cloudinary impl, photo upload, ownership-scoped queries.
- **Route foundation (Wk 4):** PostGIS (`postgis/postgis:16-3.4`), Hibernate Spatial + JTS, V3/V4 migrations, `LINESTRING` path, `ST_DWithin` proximity, GiST index, create/my/near/delete.
- **Route discovery + enrichment (Wk 5–6):** GPX import/export (jpx, Douglas-Peucker), V5 `LINESTRINGZ` elevation migration (gain/loss), `RouteResponse` coordinates as `List<Double[]>` with explicit null ele, `PATCH /routes/{id}`, public `GET /routes?userId=`, shared `validateVisibility()`, `GeometryFactory` bean, `UserService.requireUser()`, `@Transactional` everywhere. **86 tests passing.**
- **Post-review hardening (Wk 7):** external code review of the full repo, then fixes: visibility validated on `createRoute`; GPX import without `<ele>` fixed (all-or-nothing elevation, Z=0.0 placeholder — NaN broke `LINESTRINGZ` inserts); export `<ele>` guard switched to `elevationGainM != null` (placeholder routes no longer export fake zero elevation); `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` so framework 4xx no longer become 500s, with generic client messages per status family; dedicated `InvalidVisibilityException` (400) + `ProtectedRouteException` (**409**); new public `GET /routes/{id}`; JWT secret externalized to `JWT_SECRET_KEY`; 20MB multipart limit with 413 mapping; JWT parsed once per request; coordinate pairs validated as exactly `[lon, lat]`. **105 tests passing.**

---

## V1 done-bar (committed in advance)

V1 is **shipped** when a rider can, on their phone:
1. sign up / log in and add a bike (type + cc — used for speed estimation),
2. find public routes near them or browse another rider's public routes,
3. import a GPX or create a route,
4. **plan a `Trip`** — a route + departure time + stops (where / how long / how many) + optional return — and see the **estimated arrival time at each point** (bike-and-geometry speed model) with the **raw weather at that arrival time**; estimates recompute on reopen / re-plan,
5. see **hazard/info markers** along the road (rider-reported and admin-seeded "known"), read the map by **progressive disclosure** (dots cluster out / resolve into click-popup markers in),
6. **report a hazard/info point** (type from a list or custom, optional photo), and **corroborate / post a timestamped update / declare gone** on others' reports,
7. **rate and review routes and see others'** (community "is this route fun" → `popularity_score`),

and the platform has:

8. a **role system** (`USER` / `ADMIN`, `MODERATOR` if needed) — admin can **mark routes protected**, **moderate** reviews/reports, and **seed/maintain known hazards**,

…all served from a public URL, over HTTPS, deployed via an automated pipeline.

**Explicitly out of V1** (see *V1 scope — locked → Out of V1*): live in-ride re-estimation; route-line difficulty coloring; weather condition scoring; ORS; the Smart Departure Planner; rich notifications.

---

## Key decisions

### Architecture & persistence
- **Flyway exclusively owns schema; `ddl-auto: validate`.**
- **Testcontainers over H2** — requires `postgis/postgis:16-3.4` with `asCompatibleSubstituteFor("postgres")` + `withInitScript("postgis-init.sql")`; plain `postgres:16` fails the V3 migration.
- **DTOs are Java records.**
- **`FileStorageService` is an interface from day 1** — S3 swap in Phase 6 with no service-layer changes.
- **`PasswordEncoder` lives in `PasswordEncoderConfig`, not `SecurityConfig`** — avoids a cycle. A `@Configuration` class that both provides and consumes beans is a cycle risk — split it.
- **`JwtUtil` uses constructor injection for `@Value`** — instantiable in tests without Spring.
- **`DaoAuthenticationProvider`**: `UserDetailsService` via constructor, `PasswordEncoder` via setter — non-deprecated API.
- **`UserService` implements `UserDetailsService` directly** — becomes the service contract boundary at the Phase 4 split.
- **`UserService.requireUser()` returns the `User` entity** for internal service use — centralizes lookup; `RouteService`/`BikeService` use it.
- **`GeometryFactory` is a Spring bean (`GeometryConfig`)** — stateless, SRID 4326.
- **`@MockitoBean` not `@MockBean`** — Spring Boot 3.4+.
- **`@Transactional` discipline** — every DB-touching service method; read-only uses `readOnly = true`.

### Auth & security
- **`BadCredentialsException` → 401, vague message** — never reveal which half was wrong.
- **Public routes listed explicitly** — wildcard `/auth/**` let `GET /auth/me` through.
- **`JwtAuthEntryPoint` returns structured 401 JSON**, injects the Spring `ObjectMapper`.
- **`GlobalExceptionHandler` logs 500s at ERROR level**; maps 409/400/401/404 by exception type, catch-all → 500.
- **`GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`** — framework exceptions (missing param, wrong method, malformed JSON, oversized upload) map to their correct 4xx instead of falling into the catch-all as 500s. Specific-over-generic handler resolution keeps the catch-all safe for true unknowns.
- **Client-facing error messages are generic per status family** — raw `ex.getMessage()` from framework exceptions leaks Jackson parser positions and class names. One exception: missing request params get a safe, self-built `"Missing required parameter: <name>"`.
- **Domain errors use dedicated exception types** — `InvalidVisibilityException` → 400, `ProtectedRouteException` → **409** (deliberate upgrade from 400: the request is well-formed, the resource state forbids it). Broad `IllegalState`/`IllegalArgument` handlers no longer carry domain semantics.
- **JWT secret is env-only (`JWT_SECRET_KEY`), no fallback — fail-fast at boot.** The previously committed key sits in public git history → permanently compromised, never reuse anywhere. Test profile carries its own random key.
- **JWT is parsed/verified once per request** in the filter (`parseClaims`); expired/invalid → request proceeds unauthenticated.

### Spatial / geometry
- **Longitude first, latitude second** — PostGIS/JTS convention.
- **GiST index mandatory** on geometry columns.
- **`ST_DWithin` over `geography`** for proximity — metres on a spherical earth. Matches any point on the route within the radius (correct for RideOn).
- **`/routes/near` is public** — discovery needs no auth.
- **`LINESTRING` → `LINESTRINGZ` (V5)** for GPX elevation. A `LINESTRINGZ` column rejects plain 2D geometry — must use `Coordinate(x, y, z)` with a real number; `0.0` is the placeholder (NaN insufficient).
- **`columnDefinition` removed from `Route.path`** — Hibernate-Spatial maps the type; Flyway owns the definition.
- **Coordinates exposed as `List<Double[]>` in DTOs** — JTS never leaks; boxed so ele can be null.
- **`elevationGainM != null` is the authoritative elevation signal** in `toResponse` — Z in the geometry is an implementation detail.
- **Manual routes store Z=0.0** (placeholder); **manual creation accepts 2-element `[lon, lat]`** — elevation added later by OpenTopoData.

### Routes domain model
- **`Route` is a reusable plan/path *definition*, not a ride record.** Time-bound data (arrival estimates, forecast weather) attaches to **`Trip`**, never to `Route`. A timestamped **`Ride`** record is a future additive entity.
- **Route geometry is immutable post-creation (V1).** `UpdateRouteRequest` has no coordinates field by design. *Future (deferred):* editable while `private`, lock on publish — when the frontend needs path editing.
- **PATCH semantics:** only non-null fields applied; present-but-blank rejected (service layer). Protected routes block all metadata edits (mirrors delete).
- **`canonical_id` self-reference** — null = standalone/canonical; non-null = variant.
- **`is_protected`** — admin-set; blocks deletion/edits regardless of ownership.
- **`ON DELETE RESTRICT` on `routes.user_id`** — never cascade-delete community routes.
- **`visibility` / `bike_type` are `VARCHAR`, not enums** — validation in the service layer; no migration to add values.
- **Non-owner access returns 404, not 403** (delete + private export + detail) — never confirm existence to a non-owner, including anonymous viewers.
- **`GET /routes/{id}` is public for public routes** — consistent with `/near`; anonymous or non-owner access to a private route → 404. In `SecurityConfig`, `/routes/my` is matched `authenticated()` *before* the `/routes/*` wildcard (a test guards the ordering); `/{id}/export` (two segments) is not matched by `/*` and stays authenticated.
- **`findByIdAndUserId` scopes owned-resource ops** to the authenticated user.

### GPX & validation
- **GPX import uses `Mode.LENIENT`.**
- **Douglas-Peucker applied only to GPX imports** (ε=0.0001°); user coordinates are intentional. Z preserved (`SimplificationTest`).
- **GPX import without `<ele>` stores Z=0.0** — the same placeholder convention as manual routes; elevation is all-or-nothing per file. Geolatte (under Hibernate Spatial) decides 2D vs 3D from the *first* coordinate's NaN, so NaN Z values break `LINESTRINGZ` inserts.
- **GPX export includes `<ele>` only when `elevationGainM != null`** — the authoritative signal, *not* a Z scan: placeholder routes store Z=0.0, which a Z-based check would export as fake zero elevation. Guard retired by the OpenTopoData backfill.
- **Multipart upload limit is 20MB** (`max-file-size` + `max-request-size`) — multi-hour 1Hz GPX recordings exceed Spring's 1MB default; oversize → 413 via the inherited handler. Tested with a dedicated context pinning a 1KB limit + `TestRestTemplate` (MockMvc doesn't exercise multipart limits).
- **`@Validated` + `@Pattern` on `@RequestParam` avoided** — AOP proxy breaks exception propagation through Spring Security's `ExceptionTranslationFilter`; validate in the service layer.
- **`produces` removed from binary `@GetMapping` (export)** — caused content-negotiation failure on JSON error; `Content-Type` set manually on the success path only.

### Arrival-time & weather engine (V1, no external routing)
- **Fun is community-driven, not algorithmic** — there is no curvature-as-fun scoring API. "Is this route fun" = star ratings (`popularity_score`). *(See Ratings.)*
- **Curvature + gradient are computed from the geometry and used *only* for speed estimation** — not for route difficulty or fun. Curvature = rate of bearing change between consecutive points; gradient = elevation delta (needs elevation → see backfill).
- **Speed model:** `base = openRoadSpeed(bikeType, cc)`; per sampled section `speed = base × curvatureFactor × gradientFactor × hazardFactor` (factors ≤ 1); `time = length / speed`, cumulative + stop dwell → arrival clock time per sample. **Rough by design** — rider skill swamps it. **Default profile** required for users without a bike.
- **Elevation backfill (OpenTopoData) is for the speed engine's gradient term, not weather** (corrected after Step 0). The gradient — elevation delta between consecutive points — is computed in *our* code, so we must hold the data; Open-Meteo cannot compute our route's gradient for us. Routes without elevation get a flat-road speed assumption until backfilled. **Weather temperature does *not* need this backfill** (see the temperature bullet below), so weather work does not block on it.
- **Weather provider + model (Step 0): Open-Meteo, `models=gfs_seamless`.** No API key, free non-commercial (CC BY 4.0 — attribution due at the Phase 3 frontend), ~16-day hourly horizon. GFS chosen because its wind tracked Windy's for this region; temperature is governed by elevation, not model choice (next bullet). Stub it in CI (debt #25) — never call live Open-Meteo from tests.
- **Temperature elevation correction is Open-Meteo's job, by default.** A model grid cell carries one averaged terrain height; in a ridge-ringed valley that average sits far above the road, making the raw temp too cold. Open-Meteo downscales to the true point elevation against its own 90 m Copernicus DEM via lapse rate — **proven in Step 0** (forcing `&elevation=1700` dropped Pasanauri to 13.2 °C vs ~17 °C at the valley floor, matching Windy's grid value). So **trust the DEM by default; pass `&elevation=` per sampled point only when we already hold a real elevation** (GPX on import now; the OpenTopoData backfill later) — for fidelity on gorge/bridge terrain and consistency with the displayed elevation profile. GPX elevation noise (±~30 m → ±~0.2 °C) is negligible for weather; it matters for the gradient term (needs smoothing), not here.
- **Weather is per-waypoint at arrival time, sampled** (~every 15–30 min of travel; Open-Meteo is hourly-resolution anyway), matched to the arrival hour. Handle: departure beyond forecast horizon (degrade gracefully), past departures, rides crossing hour boundaries. **Timezone explicit** — `timezone=Asia/Tbilisi`, confirmed in Step 0 via the echoed `utc_offset_seconds=14400` (UTC+4, no DST); match against the API's local hourly timestamps.
- **Precipitation is presented as condition + coarse intensity band, never precise mm or a false %.** It is the genuinely uncertain field and the magnitudes sit at the noise floor — Windy and Open-Meteo disagreed even GFS-to-GFS in Step 0, because precip is stored as accumulations disaggregated differently, runs differ, and convective rain is spatially noisy. `weather_code` → icon (clear/cloud/showers/rain/snow); mm → light/moderate/heavy; honest language ("possible showers").
- **`precipitation_probability` is captured but soft and *nullable* — not load-bearing.** Step 0 confirmed GFS returns it for our routes (10–34 % across Tbilisi→Gudauri; no blanks on a 350-point Tbilisi→Shovi GPX). It is ensemble-derived (GFS members), so it can disagree with the deterministic `weather_code` — that divergence *is* the uncertainty; show it as a hint, not a headline. Tolerate it being absent (Open-Meteo's support here has been in flux). A rigorous ensemble-derived probability via `/v1/ensemble` is **post-V1**, pairing with deferred condition scoring.
- **`Trip` weather is computed live + cached, recomputed on reopen/re-plan** — not snapshotted as truth. (Snapshot only if/when a ride is later "committed.")
- **Stops are first-class on `Trip`** (location + duration); a stop shifts every downstream arrival. **Optional return leg** — V1 default: reverse of the outbound, departing after outbound arrival + a dwell.
- **External calls are resilient from the first line** (Resilience4j: timeout + retry/backoff + circuit-breaker + fallback — show the route without weather rather than 500).
- **Cache keys are designed for hit-rate** (grid-rounded lat/lon + hour bucket) — per-exact-coordinate keys get ~0% hits. Redis introduced *to back the weather calls*.

### Community hazards / info markers
- **Trust-by-default, low friction** — at launch scale the enemy is an empty map, not abuse. Reports show immediately; no corroboration gate.
- **Authenticated to report, public to view** — accountability + moderation; anonymous invites abuse.
- **Type taxonomy + custom fallback** (pothole / gravel / fallen rocks / closure / camera / police / …). The **type sets the default expiry**.
- **Optional photo** (Cloudinary) — raises credibility when present; never required (you can't safely photograph mid-ride).
- **Corroboration is additive, not gating** — upvote/affirm raises a confidence count; a **timestamped update** appends to the report; a **"gone" declaration is trusted** and clears it. Accept that one mistaken "gone" can clear a real hazard — no conflict-resolution in V1.
- **Lifecycle = community clearing + type-based expiry + visible age** — riders can't be relied on to mark things gone on low-traffic roads, so type-based expiry is the no-effort safety net; old/unconfirmed reports fade and drop out at low zoom.
- **Admin-seeded "known" hazards/info** — admin pre-loads recurring Georgian-road hazards. Doubles as the cold-start fix.
- **Hazards feed two things** — the map dots *and* the speed engine's `hazardFactor`.

### Ratings / reviews
- **`popularity_score` = community star ratings = the "is this route fun" signal.** No algorithmic scoring.
- **Decided (Bayesian average):** shrink each route's average toward the global mean until enough distinct riders have rated, so one lucky 5★ doesn't outrank a proven 4.7. Formula: `score = (v/(v+m))·R + (m/(v+m))·C` (R = route average, v = vote count, m = min-votes weight, C = global mean).
- **One review per user per route** (uniqueness). Gates the future auto-protection threshold.

### Scope & product
- **V1 scope is locked** (see the dedicated section). The differentiator that ships in V1 is per-waypoint weather at estimated arrival; the optimizer is post-ship.
- **Roles are introduced minimally but well-defined before V1** — resolves the hardcoded `ROLE_USER`. Shipping a community product without moderation is irresponsible.

### Shipping & operations
- **Ship gate between Phase 2 and the microservices split.** Feature-complete (incl. the differentiator) before going public; the split is far more instructive on a live system.
- **First deploy is deliberately humble (pre-cloud)** — a single VPS running the existing Docker Compose stack, domain + TLS. Manual ops pain motivates Phases 5–6.
- **CI/CD is a first-class learning objective at the ship gate** — extend GitHub Actions into build → test → image → deploy; Flyway migrations against real data; rollback thinking.
- **Frontend is a collaboration track.** Tracks run in parallel; the gate blocks the *public V1 milestone*, not all forward work.

---

## Shipping & deployment strategy

### Where V1 ships
At the **end of Phase 2**, once the monolith can do the full V1 loop including the weather overlay, ratings/reviews, hazards, and admin. Last moment before the architecture fragments — ship the coherent whole, then evolve it live.

### Deploy target (Phase 3, pre-cloud)
- Single VPS (Hetzner / DigitalOcean), the **existing Docker Compose** stack (Spring Boot + Postgres/PostGIS).
- Frontend served as static assets (same box or a CDN); Cloudinary already external.
- Domain + TLS via Caddy (auto-certs). Documented Postgres-volume backups.
- Basic uptime + error visibility (logs + a simple monitor) — full observability is Phase 5.

### CI/CD (the skill this gate exists to teach)
- Extend GitHub Actions: on merge to `main` → build, test, build image, push, deploy to the VPS.
- Learn: PR-against-live workflow, **Flyway migrations against production data** (forward-only), config/secrets separated from dev, a rollback path.

### Learning ledger (what operating live teaches)
Prod vs dev data · secrets management · low-downtime deploys · migrating a schema with real rows · reading remote logs · "don't break prod" discipline · backups & restore · basic monitoring.

---

## Frontend track

The slow, uncertain leg, and where working alone hurts most.

- **Shape:** mobile-first, map-centric, installable. **React PWA**, map via **MapLibre GL or Leaflet**, UI on a component library (shadcn/ui).
- **Why React/PWA:** transferable, strongest map ecosystem, no app-store friction for a first ship. Native deferred.
- **Map interaction (V1):** plain route line (no difficulty coloring), **hazard/info dots** that cluster at low zoom and resolve into click-popup markers (what it warns/informs + age) as you zoom in. Powered by the Phase 2 clustering endpoint.
- **Collaboration:** bring in a frontend-capable partner.
- **Parallelism:** runs partly concurrent with late backend work.
- **Backend readiness:** the weather/hazard/clustering/`Trip` response DTOs are consumed by a collaborator — design them as deliberately as the route DTOs and keep them stable. CORS hardcoded to `localhost:3000` becomes real config when the frontend gets a domain.

---

## Full phase breakdown

### Phase 1 — Monolith
| Week(s) | Feature | Status |
|---------|---------|--------|
| 1 | Skeleton, CI, Flyway, Docker | ✅ Done |
| 2 | Auth — register, login, JWT, Spring Security 6 | ✅ Done |
| 3 | Rider profile — bikes, photo, FileStorageService | ✅ Done |
| 4 | Routes — PostGIS, spatial queries, foundation | ✅ Done |
| 5–6 | Route discovery — GPX, simplification, enrichment, update + browse | ✅ Done |
| — | **`Trip`** — route + departure + stops + optional return (no ride state-machine/join choreography in V1) | ⬜ Planned |
| — | **Roles & admin** — `USER`/`ADMIN`/`MODERATOR`, route-protection, moderation, first-admin bootstrap, known-hazard seeding | ⬜ Planned |
| — | **Ratings & reviews** — community stars → `popularity_score` (Bayesian) | ⬜ Planned |
| — | **Community hazards** — types + custom, optional photo, corroborate/update/declare-gone, type-based expiry, admin-seeded known | ⬜ Planned |
| — | **Notifications (minimal)** — likely deferrable past V1 | ⬜ Planned |

### Phase 2 — Route engine + weather overlay (the differentiator)
| Feature | Notes |
|---------|-------|
| Geometry speed engine | Base open-road speed by bike type/cc; per-section reduction from curvature + gradient + active hazards → arrival estimates |
| Weather along route | Open-Meteo — per-waypoint at arrival time (attaches to `Trip`); recompute on reopen/re-plan |
| OpenTopoData elevation | Backfill all routes — **feeds the gradient term**; retires the Z=0.0 placeholder + GPX export guard |
| Display clustering endpoint | Query-time clustering of hazard/info dots by proximity; cluster out / resolve in by zoom |
| Redis caching | Cache weather (grid + hour keys) and other expensive calcs |
| (post-ship) Smart Departure Planner | Optimal departure *window*, round-trips as a unit — fast-follow after V1 |

### Phase 3 — Frontend + Ship V1 + CI/CD
| Track | Notes |
|-------|-------|
| Frontend | React PWA + map (plain line + clustered hazard dots) — collaboration track |
| Deploy | Single VPS, existing Docker Compose, domain + TLS, backups |
| CI/CD | GitHub Actions → build/test/image/deploy on merge; Flyway against prod data |
| **Milestone** | **V1 publicly shipped** (done-bar above) |

### Phase 4 — Microservices split (of a live system)
- Extract: User · Route (routes/GPX/weather/scoring) · Social (rides/hazards/ratings/notifications) · API gateway.
- New dimension: split a **deployed** system with CI/CD — rolling changes, splitting a DB with real data, no-downtime cutover.

### Phase 5 — Orchestration + observability
- Containerize/orchestrate all services; structured JSON logging (Logback+SLF4J); distributed tracing (OpenTelemetry) — on a system already serving traffic.

### Phase 6 — AWS migration
| Local | AWS replacement |
|-------|-----------------|
| VPS Postgres | RDS |
| Cloudinary | S3 + presigned URLs |
| Mailpit | SES |
| Custom JWT | Cognito |
| Docker Compose / VPS | ECS or EKS |
| — | SQS for async messaging |

Full CI/CD: GitHub Actions → ECR → ECS/EKS. Motivated by ops pain felt since Phase 3.

---

## Technical debt & deferred decisions

Numbered for stable reference. PATCH + browse-by-user are done and removed.

| # | Location | What & why | When to address |
|---|----------|------------|-----------------|
| 1 | `UserService.loadUserByUsername` | `ROLE_USER` hardcoded; a test asserts it so it breaks intentionally when roles arrive. | **Phase 1 — roles** |
| 2 | `application*.yml` | ✅ Resolved early (Wk 7) — secret externalized to `JWT_SECRET_KEY` env var, no fallback (fail-fast); test profile has its own key. Old committed key is compromised — never reuse. | Done |
| 3 | `SecurityConfig` | CORS hardcoded to `localhost:3000` → config property. | **Phase 3** (frontend domain) |
| 4 | Auth | No refresh token (24h access only) — need refresh/rotation/revocation. | **Before public V1 (Phase 3)** |
| 5 | `UserService` | No `UserDetailsService` interface — becomes the contract boundary at the split. | **Phase 4** |
| 6 | `RegisterRequest` | Password length-only; email format-only. | **Before public V1 (Phase 3)** |
| 7 | `CloudinaryFileStorageService` | Cloudinary is dev convenience; prod is S3 — single-class swap. | **Phase 6** |
| 8 | `BikeService` / hazards | Private files unsupported — all Cloudinary URLs public. Hazard photos are public too (fine for V1). | When private content is introduced |
| 9 | API versioning | No side-by-side v1/v2 strategy. | When breaking changes / frontend begins (Phase 3) |
| 10 | Domain — roles | No role system — add a `role` (recommended single VARCHAR column over a join table unless multi-role is real), update `loadUserByUsername` + the asserting test, **bootstrap the first admin via a Flyway/startup seed**. | **Phase 1 — roles** |
| 11 | Domain — admin | No way to set `is_protected` / seed known hazards except via DB. Blocked by #10. | **Phase 1 — roles/admin** |
| 12 | `routes` | Auto-protection threshold (popularity from N distinct riders → auto-protect) needs ratings + a background job. | After ratings + roles |
| 13 | `RouteRepository`/`RouteService` | Route dedup — `ST_HausdorffDistance` at create + import; offer to link as a variant (`canonical_id`). | **Phase 2** |
| 14 | clustering | Query-time clustering endpoint for hazard/info dots (`ST_ClusterDBSCAN`/grid); needs bbox + zoom from the client. | **Phase 2** |
| 15 | `RouteService` distance | `getLength() * 111_320` approximation → `ST_Length(::geography)`. | **Phase 2** |
| 16 | `RouteService.toLineString2D` | Z=0.0 placeholder; `elevationGainM != null` is the signal. | **Phase 2** (OpenTopoData) |
| 17 | `RouteService.exportGpx` | `<ele>` omitted when any point lacks elevation — dead code after backfill. | **Phase 2** (OpenTopoData) |
| 18 | `popularity_score` ordering | `/routes/near` orders by score, all 0.0 until ratings exist. | **Phase 1 — ratings** |
| 19 | Logging | Debug logging in `application.yml` — remove before deploy; structured logging is Phase 5. | Remove: **Phase 3** / structured: Phase 5 |
| 20 | `SecurityConfig` | ✅ Resolved — `/auth/register` + `/auth/login` each listed once in `permitAll()`. | Done |
| 21 | Domain — `Ride` record | `Route` is a plan, not a record; add `Ride` when timestamped logs are ingested. | **Phase 2+** |
| 22 | `Route` geometry editing | V1 immutable. Future: editable while private, lock on publish. | When the frontend needs path editing |
| 23 | Speed engine | Default speed profile needed for users without a bike; the model is rough (rider skill dominates) — communicate estimate uncertainty in the UI. | **Phase 2 — speed engine** |
| 24 | Elevation strategy | OpenTopoData public limits are strict (~1 req/sec, ~100 locations/req, ~1000/day — verify current). A full backfill is slow. Decide: self-host (Docker + SRTM) vs new-routes-only vs slow batch. **For the speed engine's gradient only** — Step 0 confirmed weather temperature uses Open-Meteo's own 90 m DEM, so weather does not depend on this. | **Phase 2 — speed engine** (weather does not block on it) |
| 25 | External-API tests | CI must never hit live Open-Meteo/OpenTopoData — stub with MockWebServer/WireMock; one manual contract check outside CI. | **Phase 2** (with the first external call) |
| 26 | Hazard lifecycle | Type-based expiry windows are a per-type tuning decision; conflict resolution (corroboration vs "gone") deliberately omitted in V1. | When abuse/volume warrants |
| 27 | `RouteController.exportGpx` | Export still requires auth even for public routes — now inconsistent with public `GET /routes/{id}` and `/near`; deliberate for V1 (download = heavier action, mild abuse surface). | If/when the frontend wants anonymous GPX download |
| — | **Deferred features** (not debt — out of V1 by decision) | Route-line difficulty coloring · live in-ride re-estimation · weather condition scoring · ORS · Smart Departure Planner · rich notifications. | Post-ship / fast-follow |

---

## Coding standards

- **@Transactional discipline** — every DB-touching service method; read-only uses `readOnly = true` (`org.springframework.transaction.annotation.Transactional`).
- **TDD** — Mockito units first, then Testcontainers integration against real PostgreSQL/PostGIS; external APIs stubbed (no live calls in CI). Every edge case also manually tested via Swagger + dev tools.
- **Decision-first** — forks resolved before code; this file is the decision record (the *why*, plus the trigger to revisit each placeholder).

---

## Tech stack reference

| Layer | Technology |
|-------|------------|
| Language / Framework | Java 21, Spring Boot 3, Spring Security 6 |
| ORM / DB / Geometry | Hibernate + JPA + Hibernate Spatial, PostgreSQL 16 + PostGIS, JTS |
| Migrations / Build | Flyway, Maven |
| File storage | Cloudinary (dev) → S3 (prod) |
| Testing | JUnit 5, Mockito, Testcontainers, MockWebServer/WireMock (external APIs); TDD |
| External APIs | Open-Meteo (weather), OpenTopoData (elevation), GPX/jpx. *(OpenRouteService deferred — V1 uses a geometry-derived speed engine.)* |
| Resilience / Caching | Resilience4j, Redis |
| Frontend | React, PWA, MapLibre GL / Leaflet, component library (shadcn/ui) |
| Deployment | VPS (Hetzner/DO), Docker Compose, Caddy (TLS), domain |
| CI/CD | GitHub Actions (test → build → deploy), Flyway-against-prod discipline |
| Observability | OpenTelemetry, structured logging |
| Cloud | AWS (RDS, S3, SQS, SES, Cognito, ECS/EKS) |

---

## Environment notes

- **JAVA_HOME:** `C:\Users\Barni\.jdks\openjdk-21.0.2` (Windows system env, `\bin` on PATH).
- **IntelliJ project root:** inner `rideon` folder where `pom.xml` lives.
- **DB name / username / password:** all `rideon`.
- Docker must be running for tests and local dev.
- IntelliJ run config uses the EnvFile plugin → `.env`. `.env` holds `DB_*` + `CLOUDINARY_*` + `JWT_SECRET_KEY`, is in `.gitignore`.
- **`JWT_SECRET_KEY` is required** — the app fails fast without it. Generate with `openssl rand -hex 32`.
- **Terminal tests:** `./mvnw test` from Git Bash (test profile + YAML fallbacks; Cloudinary uses a `:test` fallback).
- GitHub: branch protection on main; auto-delete head branches on merge.
- **Testcontainers** needs `postgis/postgis:16-3.4` with `asCompatibleSubstituteFor("postgres")` + `withInitScript("postgis-init.sql")`; plain `postgres:16` fails V3. Random container names are expected.
- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html` — Authorize to paste a JWT. **Swagger can misreport status on binary endpoints — verify with the browser network tab.**
- **PostGIS:** longitude first, latitude second.
- **`LINESTRINGZ` rejects 2D geometry** — use `Coordinate(x, y, 0.0)`; NaN insufficient.
- Data persists via the named volume `postgres_data` (survives `docker compose down`; lost with `-v`).
- YAML schema header added to `application*.yml` to silence false IntelliJ warnings.
- Test GPX loaded from the classpath (`getResourceAsStream`), not a filesystem path.
- **External-API note (Phase 2):** Open-Meteo needs no key but is rate-limited; OpenTopoData public limits are strict (verify current); both require timezone-correct requests. Keys/secrets become real config at the Phase 3 ship gate.

---

## How to use this file

Update the checkboxes and status as you complete work. At the start of a new chat, paste this file (or link it) and say "pick up where we left off." For class-by-class detail, point the session at the repo; this file carries the plan and the *why*.

*Last updated: June 2026 — Week 7 post-review hardening complete (105 tests) + Step 0 weather spike done & deleted (throwaway). **Weather decisions locked from the spike:** provider Open-Meteo, model `gfs_seamless`; temperature elevation-corrected by Open-Meteo's own 90 m DEM by default (pass `&elevation=` only when we hold one — GPX/backfill); the OpenTopoData backfill serves the speed engine's gradient, not weather, so weather does not block on it; rain presented as condition + intensity band (not precise mm/%); `precipitation_probability` captured as a soft/nullable hint; timezone `Asia/Tbilisi` (UTC+4) confirmed. **Next: roles & admin** (first real build). **V1 scope locked:** create/import/export routes; community star ratings (= the "fun" signal); static `Trip` planning with stops + optional return; geometry-derived bike-aware speed engine (curvature + gradient + hazards, no ORS); per-waypoint weather at arrival (recompute on reopen); community hazards with corroborate/update/declare-gone + admin-seeded known hazards; progressive-disclosure hazard map. Deferred: route-line difficulty coloring, live in-ride re-estimation, weather condition scoring, ensemble-based precipitation probability, ORS, the Smart Departure Planner. Ship V1 from a simple host with CI/CD before the microservices split, then evolve the live system.*