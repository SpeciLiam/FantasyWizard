# Sleeper League Advisor

_Empowering fantasy football players with insights, real-time matchup views, and instant trade advice using a modern full-stack web app._

---

## Table of Contents

- [Overview](#overview)
- [Monorepo Structure](#monorepo-structure)
- [Stack &amp; Architecture](#stack--architecture)
- [Quickstart](#quickstart)
- [Configuration &amp; Environment Variables](#configuration--environment-variables)
- [REST API Reference](#rest-api-reference)
- [Troubleshooting](#troubleshooting)
- [Useful Scripts &amp; Commands](#useful-scripts--commands)
- [Decisions &amp; Notes](#decisions--notes)
- [Security](#security)
- [License](#license)

---

## Overview

**Sleeper League Advisor** is a collaborative fantasy football platform that connects to the public [Sleeper API](https://docs.sleeper.app/) to provide advanced roster management, matchup breakdowns, projected scoring, and instant trade suggestions. The UI features a clean three-column layout: league members, weekly/roster details (live matchups), and advisor chat.

---

## Project Status & Recent Changes (August 2025)

- **Grid Layout Improvements:** Columns now shrink as needed—panels and cards stay fully contained in their grid tracks even with long content or very narrow screens.
- **Conditional Center Column:** The middle pane now shows either your roster or league matchups, based on the active tab (never both side by side). Matches current UI/UX best practices.
- **Accessibility:** Tab toggle buttons use `aria-selected` for clear screen reader support.
- **Live Data Only:** All matchups, rosters, and players are loaded dynamically from the API; no mocks.
- **Main Development:** The `fantasy-frontend/` project is under active development and is the recommended UI. `frontend/` exists for legacy/testing.
- See project `/frontend` and `/fantasy-frontend/public/styles.css` for latest CSS fixes.

---

## Monorepo Structure

```
/
├─ backend/           # Spring Boot (Java) REST API, caching, logic
├─ frontend/          # React + TypeScript Vite app (classic UI)
├─ fantasy-frontend/  # Alternative/experimental React frontend UI
├─ server/            # Node.js API proxy/microservices (dev tools, optional)
├─ shared/            # (Optional, future) Contract/OpenAPI/types
```

- Both `frontend/` and `fantasy-frontend/` have their own `node_modules`, config, and outputs.
- All subprojects are covered by a comprehensive `.gitignore`.

---

## Stack & Architecture

- **Frontend**: React + TypeScript (Vite), TailwindCSS, Framer Motion
  - UI features a member list, matchup details pane (shows ALL weekly matchups, fully API-driven), advisor chat.
  - The main fantasy football app is in `/fantasy-frontend`; `/frontend` is legacy/minimal or for alternate demos.
- **Backend**: Spring Boot 3 (Java 17), Caffeine cache, REST API, deterministic projections/trade advice.
- **Server layer (optional)**: Node.js/TypeScript microservices for API proxy/dev tools.
- **Live Data**: All matchup screens pull weekly matchups directly from the backend API; no mock matchup data is used anywhere in the UI.
- **LLM**: Integration optionally enabled (HuggingFace or local Ollama).

---

## Quickstart

### 1. Clone

```bash
git clone https://github.com/your-org/sleeper-league-advisor.git
cd sleeper-league-advisor
```

### 2. Start Backend (Spring Boot, Java 17)

```bash
cd backend
./gradlew bootRun           # Run in development mode OR
./gradlew build             # Then: java -jar build/libs/*.jar
```

The backend serves at [http://localhost:8080](http://localhost:8080) by default.

### 3. Start Frontend

```bash
cd ../fantasy-frontend      # or cd ../frontend for legacy/test UI
npm ci
npm run dev
```

Open your browser at [http://localhost:5173](http://localhost:5173).

---

## Configuration & Environment Variables

### Backend (`backend/`)

Create/Edit environment variables or set them in `src/main/resources/application.yml`:

- `HF_TOKEN` – HuggingFace LLM API token (optional)
- `OLLAMA_URL` – Ollama base URL (for local LLM, optional)
- `app.cors.origins` – Allowed frontend origins (default: `http://localhost:5173`)

### Frontend (`fantasy-frontend/`)

- `.env.local` or `.env` (not committed):
  - `VITE_API_BASE=http://localhost:8080`

_No sensitive keys or API tokens are required for basic usage._

---

## REST API Reference

All backend endpoints are prefixed with `/api`.

- **GET** `/api/user/{username}/leagues?season=2025`
- **GET** `/api/league/{leagueId}/members`
- **GET** `/api/league/{leagueId}/roster/{userId}?week=1`
- **GET** `/api/league/{leagueId}/matchups/{week}`_Returns all matchups for a week. The UI sorts and highlights the selected member’s matchup._
- **POST** `/api/explain` (LLM optional)
- **POST** `/api/trades`, **POST** `/api/generate/trades` (LLM optional)

See detailed shapes and response samples in [REST API Reference](#rest-api-reference).

All matchups, roster, and member data in the UI is loaded live from these endpoints—no frontend mock data.

---

## Troubleshooting

- **CORS Issues**: Set `app.cors.origins` in backend config. Default allows `http://localhost:5173`.
- open -n **"/Applications/Microsoft Edge.app"** --args --user-data-dir**=**"**$HOME**/msedge-dev-data" --disable-web-security
- **Java Version**: Requires Java 17 or later.
- **Port Conflicts**: Backend default is 8080, frontend is 5173.
- **Dependency Problems**: Ensure `node_modules` for UI, run `./gradlew` for backend, check `.gitignore` does not include lockfiles if you want reproducible installs.

---

## Useful Scripts & Commands

### Backend

```bash
cd backend
./gradlew bootRun          # Development server
./gradlew build            # Production JAR build
```

### Frontend

```bash
cd fantasy-frontend
npm ci
npm run dev
```

### Run Both Together

_On Unix/macOS:_

```bash
concurrently "cd backend && ./gradlew bootRun" "cd fantasy-frontend && npm run dev"
```

---

## Decisions & Notes

- All matchups shown in the UI are API-driven and sorted to the top for the selected member.
- No mock player or matchup data is used in the production UI.
- Full monorepo best practices: clear `.gitignore`, isolated builds per subproject.
- LLM chat and trade advice are optional and off by default.

---

## Security

- **Never commit secrets**: Use `.env` files locally (all are gitignored except `.env.example`).
- Frontend does not require any keys by default.

---

## License

MIT License (see [LICENSE](LICENSE)).
