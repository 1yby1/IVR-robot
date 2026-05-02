---
name: ivr-project-structure
description: Enforce the IVR repository's existing project structure and module boundaries. Use when Codex is asked to add, modify, refactor, or generate code, tests, SQL, docs, frontend pages, backend APIs, IVR engine logic, AI/RAG logic, FreeSwitch call integration, or any new files in this IVR project; prevents ad hoc folders, misplaced classes, and unrelated structural churn.
---

# IVR Project Structure

## Core Rule

Before creating or moving files, inspect the existing tree and place work in the closest established module and package. Prefer extending existing folders over creating new top-level folders. Create a new directory only when an existing sibling pattern clearly supports it, or after asking the user.

Do not introduce unrelated framework structure, parallel module trees, generated scaffolds, duplicate package roots, or convenience files outside the established layout.

## Required Workflow

1. Run `git status --short` and inspect relevant folders before editing.
2. Identify the feature domain: admin API, engine runtime, AI/RAG, FreeSwitch call integration, frontend UI, SQL, docs, or tests.
3. Reuse the matching module and package listed below.
4. Keep files small and cohesive; do not mix backend, frontend, docs, and SQL changes unless the task explicitly needs them.
5. After backend code changes, run tests and then validate startup from `ivr-server/ivr-admin` with `java -jar target\ivr-admin-exec.jar`; fix startup errors and repeat until it stays running or only an external environment issue remains.

## Backend Modules

Use `ivr-server` as the only backend root. Do not create another backend root.

- `ivr-server/ivr-common`: shared exceptions, response wrappers, constants, utilities used by multiple server modules.
- `ivr-server/ivr-engine`: IVR flow runtime, graph parsing, sessions, node handler interfaces, built-in node handlers, call channel abstraction, flow events.
- `ivr-server/ivr-ai`: LLM, TTS/ASR abstractions, AI node handlers, RAG services, knowledge entities and mappers.
- `ivr-server/ivr-call`: FreeSwitch/ESL integration, call channel implementations, ESL event parsing, call gateway interfaces.
- `ivr-server/ivr-admin`: Spring Boot entrypoint, REST controllers, DTOs, admin services, persistence mappers/entities, security, config, listeners, resources.

### Admin Placement

Under `ivr-server/ivr-admin/src/main/java/com/ivr/admin`:

- `controller`: REST controllers only.
- `dto`: request/response DTOs only.
- `service`: admin application services and orchestration.
- `entity`: database entities owned by admin.
- `mapper`: MyBatis/MyBatis-Plus mapper interfaces owned by admin.
- `security`: JWT, permissions, security config, auth filters.
- `config`: Spring configuration beans.
- `listener`: event listeners bridging engine/runtime events to admin behavior.

Do not put controllers in `service`, database entities in `dto`, or SQL strings in controllers when a mapper/service already owns that behavior.

### Engine Placement

Under `ivr-server/ivr-engine/src/main/java/com/ivr/engine`:

- `node/handler`: built-in deterministic IVR node handlers such as play, dtmf, transfer, end, voicemail.
- `channel`: call media/control abstraction and default logging implementation.
- `graph`: flow graph models, parser, provider.
- `session`: flow session models and stores.
- `event`: engine event listener contracts/defaults.
- `cache`: engine-local cache helpers.

Put AI-specific nodes in `ivr-ai/node`, not in engine, unless they are generic and do not depend on AI services.

### AI/RAG Placement

Under `ivr-server/ivr-ai/src/main/java/com/ivr/ai`:

- Root package: service interfaces/classes such as `LlmService`, `TtsAsrService`, stub implementations.
- `node`: ASR, intent, RAG, or other AI-backed `NodeHandler` implementations.
- `rag`: knowledge retrieval services and text utilities.
- `rag/entity`: knowledge database entities.
- `rag/mapper`: knowledge MyBatis mapper interfaces.

Do not put knowledge-base admin CRUD here; admin CRUD belongs in `ivr-admin`.

### Call Integration Placement

Under `ivr-server/ivr-call/src/main/java/com/ivr/call`:

- `esl`: FreeSwitch ESL handlers, argument sanitizers, FreeSwitch call channel, gateway callback interfaces.
- `config`: call-module Spring config only.

Do not put business flow decisions in this module; call events should be forwarded to admin/engine services.

## Frontend Placement

Use `ivr-web/src` as the only frontend source root.

- `api`: typed API clients grouped by domain, e.g. `knowledge.ts`.
- `views/<domain>`: route-level pages such as `flow`, `knowledge`, `system`, `report`, `robot`, `login`.
- `components/<domain>`: reusable components; `components/flow/nodes` for flow-node UI pieces.
- `layouts`: app layout shells.
- `router`: route definitions and guards.
- `stores`: Pinia/global state.
- `styles`: global styles and design tokens.
- `utils`: frontend utility functions.

Do not create route pages under `components`, API clients under `views`, or new frontend roots outside `ivr-web/src`.

## SQL, Docs, And Generated Files

- Database schema and seed data belong in `docs/sql`.
- Project guides belong in `docs`.
- Runtime build output belongs only in existing `target`, `dist`, or tool-generated folders and should not be manually edited.
- Do not commit logs, local run-check files, caches, or generated dependency folders unless the repo already tracks that exact artifact type.

## Tests

Mirror production module placement:

- Backend tests go under `ivr-server/<module>/src/test/java` using the same package path as production code.
- Frontend tests, if added, must follow the existing frontend test convention; inspect `ivr-web` first.
- Do not create a separate `test`, `tests`, or `__tests__` root unless it already exists for that module.

## New File Decision Checklist

Before adding a new file, answer:

- Which existing module owns this behavior?
- Is there an existing package/folder for this file type?
- Is this file a route page, reusable component, API client, service, entity, mapper, node handler, or config?
- Will adding this file force unrelated structure changes?
- Can this be added by extending an existing class or package instead?

If ownership is ambiguous, ask the user before creating the file.

## Forbidden Patterns

- Creating new top-level app folders such as `backend`, `server`, `frontend`, `webapp`, `src`, or `app`.
- Creating duplicate Java package roots outside `com.ivr`.
- Mixing admin REST code into `ivr-engine`, `ivr-ai`, or `ivr-call`.
- Putting FreeSwitch ESL implementation in `ivr-admin` when `ivr-call` has an owner package.
- Putting RAG persistence CRUD pages or controllers in `ivr-ai`; admin-facing CRUD belongs in `ivr-admin` and `ivr-web`.
- Adding large generated scaffolds, placeholder examples, or unused directories.
- Moving existing files just to satisfy a new personal style.
