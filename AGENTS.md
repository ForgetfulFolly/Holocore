# AGENTS.md

This file is the alias for `CLAUDE.md` and serves the same role for any AI coding agent (Cursor, Aider, OpenAI Codex, Continue, etc.) that reads `AGENTS.md` by convention. **Read `CLAUDE.md` for the full rules.**

## TL;DR for any agent touching this repo

1. This is a PUBLIC GitHub repository. `git push` exposes content to the world.
2. Never commit `.worktrees/`, `CLAIMED.md`, `BLOCKED.md`, `HEARTBEAT.md`, `REVIEW.md`, `STEER*.md`, or anything else from the swarm workflow.
3. Never push `agent/*` branches.
4. Never push directly to `master` — open a PR.
5. Sanitize commits before pushing — no IPs, internal paths, credentials, worker names, or team identifiers.
6. Install the local pre-commit hook: `git config core.hooksPath .githooks`

Full reasoning and context in `CLAUDE.md`.
