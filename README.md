# Sleeper League Advisor

_Empowering fantasy football players with insights and instant trade advice powered by a modern full-stack web app._

---

## Table of Contents

- [Overview](#overview)
- [Stack & Architecture](#stack--architecture)
- [Quickstart](#quickstart)
- [Backend Configuration & Environment Variables](#backend-configuration--environment-variables)
- [Frontend Configuration & Environment Variables](#frontend-configuration--environment-variables)
- [REST API Reference](#rest-api-reference)
- [Caching Policy](#caching-policy)
- [LLM Integration: Setup & Offline Mode](#llm-integration-setup--offline-mode)
- [Troubleshooting](#troubleshooting)
- [Useful Scripts & Commands](#useful-scripts--commands)
- [Architecture & Decisions](#architecture--decisions)
- [OpenAPI Contract](#openapi-contract)
- [Security](#security)
- [License](#license)

---

## Overview

**Sleeper League Advisor** is a full-stack fantasy football companion app. It connects to the public [Sleeper API](https://docs.sleeper.app/) and empowers league members with advanced roster views, projected scoring, and instant trade proposal advice. The intuitive three-column UI (members, details, chat/explain) makes it easy to manage your rosters, evaluate moves, and collaborate on trades—with or without AI.

---

## Stack & Architecture

- **Frontend**:  
  - React + TypeScript, Vite, TailwindCSS (utility-first styling), framer-motion (animations), lucide-react (icons)
  - Core layout: left (members), center (roster, picks, trade ideas), right (chat/explain)
- **Backend**:  
  - Java 17, Spring Boot 3 (MVC, actuation via /actuator), blocking RestTemplate for API calls
  - Caffeine + Spring Cache for data caching (players, leagues, etc.)
  - Deterministic `ProjectionStore` for stub projections; `TradeEngine` for fair trade suggestions
  - Secured CORS (API serves at http://localhost:8080, UI at http://localhost:5173)
- **Data Source**:  
  - Direct integration with Sleeper’s public read-only API endpoints (see [API Reference](#rest-api-reference))
- **LLM Integration (optional)**:  
  - Hugging Face Inference API _or_ local Ollama, for natural-language explanations/trade ideas (works offline or with LLM disabled)
- **Monorepo**:  
  - `/backend` (Java service), `/frontend` (React app)

---

## Quickstart

### 1. Clone the repository

```bash
git clone https://github.com/your-org/sleeper-league-advisor.git
cd sleeper-league-advisor
```

### 2. Run the Backend

```bash
cd backend
./mvnw spring-boot:run
# or, to build:
./mvnw -U clean package
```

### 3. Run the Frontend

```bash
cd ../frontend
npm ci
npm run dev
```

Visit the app at [http://localhost:5173](http://localhost:5173).

---

## Backend Configuration & Environment Variables

Set up these environment variables as needed (all are optional unless specified):

- `HF_TOKEN` – Hugging Face API token (for cloud LLM, optional)
- `HF_MODEL` – Hugging Face model name (optional)
- `OLLAMA_URL` – Base URL for local Ollama (e.g., `http://localhost:11434`, optional)
- `OLLAMA_MODEL` – Ollama model name (e.g., `llama3`, optional)
- `app.cors.origins` – Allowed frontend origins (default: `http://localhost:5173`)
- `app.cache.playersTtlMinutes` – TTL for player data (default: 1440)
- `app.cache.leagueTtlMinutes` – TTL for leagues/members (default: 5)

Application config is managed via `backend/src/main/resources/application.yml` or environment variables.

---

## Frontend Configuration & Environment Variables

- `VITE_API_BASE` (default: `http://localhost:8080`)

Adjust in your `/frontend/.env` if you proxy/back the API differently.

---

## REST API Reference

All backend endpoints are prefixed with `/api`.

### GET `/api/user/{username}/leagues?season=2025`

Returns a list of leagues for a given username.

**Response:**

```json
{
  "leagues": [
    { "leagueId": "1234", "name": "Cool League" }
  ]
}
```

---

### GET `/api/league/{leagueId}/members`

List league members.

**Response:**

```json
[
  { "userId": "324", "displayName": "Pat", "avatar": "url", "isMe": false }
]
```

---

### GET `/api/league/{leagueId}/roster/{userId}?week=1`

Returns a full roster for the user in the given league and week.

**Response:**

```json
{
  "starters": [
    { "id": "p_1", "name": "Player 1", "pos": "QB", "team": "KC", "proj": 17.1, "value": 120 }
  ],
  "bench": [
    { "id": "p_2", "name": "Player 2", "pos": "WR", "team": "MIN", "proj": 11.2, "value": 87 }
  ],
  "taxi": [
    { "id": "p_3", "name": "Rookie 1", "pos": "RB", "team": "CHI", "proj": 7.8, "value": 60 }
  ],
  "picks": [
    { "season": 2025, "round": 1, "originalOwner": "324", "owner": "324", "traded": false }
  ]
}
```

---

### POST `/api/explain`

**Body:**

```json
{ "prompt": "Why is my WR underperforming?", "context": { ... } }
```
**Response:**

```json
{ "answer": "Based on recent box scores..." }
```

_If no LLM is configured, answer will indicate chat is disabled._

---

### POST `/api/trades`

Returns deterministic trade proposals.

**Body:**

```json
{ "yourTeam": [...], "otherTeam": [...] }
```

**Response:**

```json
[
  { "youSend": [...], "youReceive": [...], "delta": -2.5, "yourGain": 10.2, "reason": "Improves RB depth" }
]
```

---

### POST `/api/generate/trades` _(optional, with LLM)_

**Response:**

```json
{
  "enabled": true,
  "trades": [
    { "youSend": [...], "youReceive": [...], "delta": 0.3, "yourGain": 5.7, "reason": "Fair swap" }
  ]
}
```

---

## Caching Policy

- **Players**: 24h (Caffeine-based; `playersMap`)
- **Leagues, members, rosters, etc.**: ~5m (configurable)
  - See `application.yml` for all cache TTLs.

---

## LLM Integration: Setup & Offline Mode

Enable LLM-powered explanations or keep the app fully offline:

### Hugging Face Inference API

1. Get a HF account + token.
2. Set `HF_TOKEN` and (optionally) `HF_MODEL`.
3. Start the backend.

### Local Ollama

1. Install [ollama](https://ollama.com/), e.g.:
   ```bash
   ollama pull llama3
   ```
2. Set environment:
   ```bash
   export OLLAMA_URL=http://localhost:11434
   export OLLAMA_MODEL=llama3
   ```
3. Start the backend.

### Offline/No Config

- If no LLM config is set, the API/chat endpoints work and simply return “disabled” messages or stubs.

---

## Troubleshooting

**1. Corporate Maven mirror blocking dependencies**

- If you see missing dependencies, check for company-wide `.mvn/settings.xml` or `~/.m2/settings.xml` overriding central.
- Comment out custom `<mirror>` and/or `<repository>` sections, or add a project-local `.mvn/settings.xml`.

**2. Java 17 / Spring Boot 3 migration**

- Ensure imports use `jakarta.*` rather than `javax.*`.

**3. CORS Failures**

- Make sure `app.cors.origins` in backend matches your frontend origin (default: `http://localhost:5173`).
- See browser network console for error details.

**4. Port conflicts (8080/5173)**

- Backend defaults to 8080, frontend to 5173. Either adjust the ports or stop conflicting services.

---

## Useful Scripts & Commands

### Backend

```bash
cd backend
./mvnw spring-boot:run    # Start server
./mvnw -U clean package   # Build JAR
```

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

### Monorepo / Both Together

**With `concurrently` (optional, requires install):**
```bash
npm install -g concurrently
concurrently "cd backend && ./mvnw spring-boot:run" "cd frontend && npm run dev"
```

---

## Architecture & Decisions

- **Spring MVC + RestTemplate**: Prioritizes simplicity and compatibility (especially for Caffeine/Spring caching, actuator, and seamless local debugging) over async complexity; WebFlux is overkill for read-only proxies and complicates cache layers.
- **Deterministic Stub Services**: `ProjectionStore` and `TradeEngine` ensure stable, reproducible UI values and demos even if the live API is flakey or LLMs are disabled.
- **Monorepo**: Simplifies coordinated deployment and local development. Can be split later (move contracts to `/shared`, split CI scripts, publish OpenAPI file).
- **LLM Gating**: LLM integration is opt-in; critical user flows and trading features do not require LLM access or tokens.

---

## OpenAPI Contract

_If/when provided, the canonical OpenAPI contract will be published at:_

```
/shared/openapi.yaml
```

---

## Security

- **DO NOT COMMIT SECRETS**.  
  - Do not store any tokens or credentials in the repo.
  - Use local `.env` files (_not_ committed) for all secrets.
  - Application config (tokens, origins) belongs in environment or application.yml.

---

## License

MIT License (see [LICENSE](LICENSE)).
