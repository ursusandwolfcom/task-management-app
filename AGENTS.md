# Project working rules

Communicate in Russian. The project is developed by one developer with one AI agent.
Do not spawn sub-agents unless the user explicitly requests them.

## Required branch and verification cycle

The user selected this workflow: `sprint/sNN` → `dev` → `main`.

- Create each sprint branch from current `dev`; initialise `dev` from `main` when needed.
- Keep exactly three working branches locally and on origin: main, dev and the current
  sprint branch. Once work is preserved in the current sprint or integrated into main/dev,
  close superseded PRs, remove obsolete clean worktrees and delete obsolete branches.
  Delete the completed sprint branch before creating the next one; a brief transition
  with two branches is expected. Never delete a branch with unpreserved unique work.
- Work in the sprint branch with small meaningful commits and a sprint PR targeting `dev`.
- Acceleration (user-approved): batch fixes into one push before opening/updating a PR;
  run targeted Maven/npm checks while iterating and one full `clean verify` on the
  sprint SHA before merge; overlap local verify with CI; merge to `dev` as soon as the
  latest sprint SHA is green. Release `dev` → `main` in batches of 2–3 sprints (or when
  a blocking hotfix needs main), not after every sprint. Still require green checks on
  the exact SHA being merged; never use success from an older SHA.
- Before merging to `dev`, run Maven Wrapper `clean verify` and all applicable database,
  concurrency, startup/container and frontend checks. Verify CI on the latest sprint SHA
  and the proposed merge result against current `dev`.
- After merging, rerun the required checks on the actual latest `dev` commit (smoke or
  full verify as appropriate); do not open a release PR until the batch is ready.
- Only after those repeated checks succeed for the release candidate, merge a release PR
  from `dev` into `main`. Ensure the main merge result contains exactly the tested code;
  integrate divergent main changes into dev and rerun checks before release.
- Check `main` CI/smoke after merge, then synchronise `dev` with `main` for the next sprint.
- Use merge commits for sprint/release integration. Never rewrite shared branch history,
  force-push, bypass branch protections, skip required checks, or use success from an older SHA.
- The user has authorised merges that satisfy this cycle; do not request redundant approval.
  Respect any externally required reviewer/approval. Merge is not authorisation for production deploy.
- When a check fails, fix in a sprint branch and repeat the cycle; do not push an untested fix
  directly to `dev` or `main`.

## Workspace and source requirements

- Preserve user changes and untracked files. Use a clean worktree if checkout/update would
  collide with the copied application files; do not use destructive cleanup to change branches.
- Never print or commit `.env`. `/docs/` is intentionally ignored and contains local assignment,
  architecture and sprint plans. If available, read `docs/UNIFIED_PLAN.md`, `docs/ARCHITECTURE.md`,
  `docs/DEVELOPMENT_SPRINTS.md` and `docs/WORKFLOW.md` before relevant implementation.
- Keep deliverable README/OpenAPI current without links to ignored local files.
- The early alpha interface is explicitly user-approved as a browser-storage prototype.
  Keep alpha assets/endpoint behind the alpha profile and loopback defaults. Alpha task
  states/IDs are prototype data, not completed PostgreSQL CRUD, authentication, lifecycle
  history or resource guards. Route graph calculation to planning.core, not a JS copy.
  Update the frontend npm type/storage checks when changing alpha behavior.
- Preserve Java 21, UTC startup/tests, Liquibase tracking in `public`, and ddl-auto=validate.
- Keep the algorithm core independent of Spring/JPA/HTTP. Introduce it before CRUD; verify
  the graph invariants and concurrency against the merged requirements.
- Apply relevant installed skills for requirements, architecture, implementation, review,
  debugging, performance and frontend work; avoid unnecessary dependencies and patterns.
  The current global set is: `requirements-spec-writer`, `java-architect`, `java-developer`,
  `java-code-reviewer`, `java-bug-hunter`, `java-performance`, `frontend-react`.
  The earlier generic skills from `files.zip` were removed and must not be invoked.
  Archive examples do not override this project's selected Java 21, Liquibase or workflow.
- After each significant implementation iteration, update local docs/CHANGELOG.md and
  docs/CHANGELOG_UML.puml with matching iteration IDs, implemented behavior, validation,
  commit/PR references and remaining decisions. Preserve history; do not portray planned
  features as implemented. Both changelogs stay ignored along with docs.
- Ask the user when a business rule or consequential unresolved choice is needed;
  continue independent work while awaiting the answer. Do not silently turn assumptions
  into API behavior.

For the current transition, carry the existing feature-branch work into the first sprint
without losing or duplicating changes. The prior feature → main approach is superseded
by the user's explicit sprint → dev → main rule.
