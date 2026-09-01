# Calendar

Calendar is a full-stack scheduling workspace for people who need one place to plan their own time and coordinate with a team. It combines personal calendars, shared availability, meeting planning, invitation responses, search, and daily time insights in a single product.

The React interface uses the Spring Boot API for every product data flow. MongoDB is the source of truth for workspace accounts, profiles, calendars, events, guest responses, availability, and insights.

## Product capabilities

### Workspace access and navigation

- Sign in with a seeded workspace account, switch between profiles, resume a session, and sign out.
- Move between day, week, and month views using period controls, the mini calendar, Today, or keyboard shortcuts.

### Calendar and event management

- Create, view, edit, and delete events, tasks, focus time, out-of-office blocks, working locations, and appointment schedules.
- Schedule timed or all-day items with a calendar, color, location, description, guests, and recurring rules.
- Create, rename, recolor, show, hide, display exclusively, and delete empty calendars.

### Availability and collaboration

- Find coworkers, compare schedules and working hours, detect conflicts, and choose suggested meeting times.
- Review guest details and respond to invitations with Yes, Maybe, or No.

### Search and time insights

- Search by keywords, participant, location, calendar scope, excluded text, or date range.
- Review daily scheduled and remaining time by item type or calendar, then create a focus-time block.

## Technology stack

| Layer | Technology | Role |
|---|---|---|
| Frontend | React 19 | Product views, forms, dialogs, workspace state, and user interactions. |
| Frontend build | Vite 8 | Development server, API proxy, hot reload, and production bundling. |
| JavaScript runtime and packages | Bun | Root workspace installation, scripts, and frontend development. |
| Backend | JDK 21 and Spring Boot 3.4 | Versioned HTTP API, request handling, dependency injection, and server lifecycle. |
| Backend build | Gradle 9.7 wrapper | Reproducible backend compilation, execution, and seed commands. |
| Database | MongoDB with Spring Data MongoDB | Application documents, repositories, queries, and persistence. |
| Validation | Feature validators and shared exception handling | Request validation and consistent API errors. |
| Authentication | JSON Web Tokens and bcrypt | Workspace login, profile context, protected routes, and password verification. |
| Styling and assets | CSS and local Roboto font files | Responsive product styling without an external component library or CDN. |

## Project structure

```text
.
├── backend/
│   ├── src/main/java/com/calendar/
│   │   ├── auth/              Workspace login, session, profile switch, and logout
│   │   ├── availability/      Conflict detection and suggested meeting times
│   │   ├── calendars/         Calendar CRUD, visibility, colors, and display-only behavior
│   │   ├── events/            Event CRUD, search, recurrence, and invitation responses
│   │   ├── insights/          Daily scheduled-time aggregation
│   │   ├── people/            Profile listing and coworker search
│   │   ├── seed/              Deterministic MongoDB seed entrypoint and data
│   │   ├── shared/            MongoDB configuration, errors, web configuration, and utilities
│   │   └── CalendarApplication.java  Spring Boot application entrypoint
│   ├── src/main/resources/    Application configuration and backend-served local assets
│   ├── gradle/wrapper/        Pinned Gradle wrapper runtime
│   ├── .env.example           Backend environment contract
│   ├── build.gradle           Plugins, dependencies, Java version, and application tasks
│   ├── settings.gradle        Gradle project identity
│   ├── gradlew                Unix Gradle wrapper
│   └── gradlew.bat            Windows Gradle wrapper
├── frontend/
│   ├── src/
│   │   ├── features/
│   │   │   ├── auth/          Workspace sign-in
│   │   │   ├── calendar/      Calendar shell, navigation, views, and management
│   │   │   ├── events/        Editing, preview, recurrence, search, and responses
│   │   │   ├── insights/      Daily time summary and detailed drawer
│   │   │   ├── people/        People picker, availability comparison, and suggestions
│   │   │   └── profiles/      Workspace profile selection and avatars
│   │   ├── shared/            API client, reusable controls, and shared utilities
│   │   ├── App.jsx            Product orchestration and feature state
│   │   ├── main.jsx           React entrypoint
│   │   └── styles.css         Responsive application styling
│   ├── public/                Local static assets
│   ├── .env.example           Frontend environment contract
│   ├── package.json           Frontend scripts and pinned dependencies
│   └── vite.config.js         Port, proxy, and build configuration
├── docs/
│   └── HackerRank-Code-Repo-Guidelines.md  Product and acceptance requirements
├── skills/code-repo-validate/
│   ├── SKILL.md                         Validation workflow
│   ├── SKILL-MANUAL.md                  Adoption and usage guide
│   └── references/                      Static, runtime, and report instructions
├── .vscode/launch.json        Spring Boot debugger configuration
├── .gitattributes             HackerRank archive exclusions
├── hackerrank.yml             HackerRank install, run, protection, and IDE configuration
├── setup.sh                   Environment, backend, MongoDB, and seed setup
├── package.json               Root Bun workspace and application commands
└── bun.lock                   Pinned JavaScript dependency graph
```

Frontend and backend code are organized by the same product domains. Spring controllers handle HTTP concerns, services hold business rules, and repositories own MongoDB operations.

## Run the application

### Prerequisites

- Bun
- JDK 21
- MongoDB Community Server

`bun start` checks MongoDB and attempts to start a local instance when needed. If startup reports that MongoDB is unreachable, start the installed service manually:

| Platform | Command |
|---|---|
| macOS with Homebrew | `brew services start mongodb-community` |
| Linux with systemd | `sudo systemctl start mongod` |
| Windows service | Run `net start MongoDB` from Command Prompt as Administrator. |

To verify the local connection manually:

```bash
mongosh "mongodb://127.0.0.1:27017" --eval "db.runCommand({ ping: 1 })"
```

### Install and start

From the repository root:

```bash
bun install
bun start
```

After startup:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8000`
- API health: `http://localhost:8000/api/v1/health`

HackerRank uses `bun install && bash setup.sh --seed` for installation and `bun start` for the complete application run flow.

### Command reference

| Command | Purpose |
|---|---|
| `bun install` | Installs the pinned root and frontend workspaces from `bun.lock`. |
| `bun start` | Builds the backend when needed, verifies MongoDB, resets seed data, and starts Spring Boot and Vite together. |
| `bun run seed` | Clears the application collections and restores the deterministic seed baseline without starting the servers. |
| `bun run dev:backend` | Starts only the Spring Boot API on port `8000` through the Gradle wrapper. |
| `bun run dev:frontend` | Starts only Vite on port `3000`. Use it when the backend is already running separately. |

## Demo workspace

Use the seeded Alex Morgan account:

```text
Email: alex.morgan@calendar.com
Password: password123
```

After login, choose any seeded profile to open that person's calendar.

## Create and validate a Code Repo application

This repository includes an internal authoring guideline and a read-only validation skill for maintainers creating complete Code Repo applications.

### While creating an application

Use the [HackerRank Code Repo Guidelines](docs/HackerRank-Code-Repo-Guidelines.md) as the acceptance contract. It explains product completeness, stack preservation, dependency restrictions, repository structure, HackerRank runtime behavior, README requirements, and evidence needed for completion.

Write the new application's README as its product contract. Document features that are implemented across frontend, API, and persistence, and describe the actual stack and commands used by that repository.

### After implementation is complete

Open the repository in Codex or Claude Code and use this prompt:

```text
Read and follow skills/code-repo-validate/SKILL.md to validate this complete Code Repo application against docs/HackerRank-Code-Repo-Guidelines.md. Run the in-scope static, install, build, start, API, and MongoDB checks, then write the report outside the repository.
```

The validator first applies the required README gate, then checks repository structure, stack and dependency consistency, declared commands, frontend-to-backend feature coverage, live API behavior, persistence, seed reset, and archive contents. It never creates missing documentation or repairs the repository during the audit.

See the [validation manual](skills/code-repo-validate/SKILL-MANUAL.md) for adoption, invocation, verdict meanings, and the rerun workflow.
