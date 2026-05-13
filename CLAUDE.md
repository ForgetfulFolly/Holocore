# Holocore — Agent Working Rules

> **THIS IS A PUBLIC GITHUB REPOSITORY:** `github.com/ForgetfulFolly/Holocore`
> Anything committed here is visible to the world. Read this file before touching git.

This repo is a fork of [ProjectSWGCore/Holocore](https://github.com/ProjectSWGCore/Holocore) — a Star Wars Galaxies server emulator. It is NOT a private workspace for agent/swarm work, and never has been. This file exists because an automated agent previously pushed local working metadata to this public repository.

---

## Hard rules (NEVER violate)

### 1. Never commit `.worktrees/`

The `.worktrees/` directory is local-only git worktree storage used by the agent workflow. It must never be added to git, even by accident. Already in `.gitignore`. If you see `.worktrees/` in `git status`, stop and figure out why.

### 2. Never commit agent phase-marker files

These files coordinate the swarm and contain worker identifiers, team names, and operational timestamps. They are NOT for public consumption:

- `CLAIMED.md`
- `BLOCKED.md`
- `HEARTBEAT.md`
- `REVIEW.md`
- `STEER.md`, `STEER_PENDING.md`, `STEER_REPLY.md`, `STEER_TIMEOUT.md`

All are in `.gitignore`. If one shows up staged, you have a bug.

### 3. Never push an `agent/*` branch to this repo

Agent task branches (named `agent/task-NNN-*`) live on the working laptop. They are working state, not contributions. If you want to share work publicly, rename to `feature/<slug>` or `fix/<slug>` with a descriptive name and sanitize the contents first.

### 4. Never push directly to `master`

All changes via PR. Master is protected. If your agent encounters a "refusing to push protected branch 'master'" error, that's the main-pollution guard working as intended — do not bypass it.

### 5. Sanitize before every commit

Before pushing, scan staged files for anything that looks like:

- IP addresses (internal LAN ranges especially)
- Internal paths (`/landing-zone`, `/datalake-vm`, `/home/<username>`, server-specific mount points)
- Worker or team identifiers from the swarm
- Tokens, API keys, environment-variable secrets
- Personal emails

If found, remove from the staged change before committing. If a commit already happened, follow the recovery procedure in `docs/agent-cleanup.md` (or ask before force-pushing).

---

## What this repo IS

Star Wars Galaxies emulator code. Standard game-server contributions are welcome via PR. The upstream is `ProjectSWGCore/Holocore`.

## What this repo IS NOT

- Your agent's working directory (use `~/work/`, `/tmp/`, or an out-of-tree path)
- A scratch space for swarm experiments
- A place to track which worker is doing what task
- A place to dump server config, secrets, or internal infrastructure docs

---

## If you're an agent running on the laptop

Your local git remote for this repo probably points at `https://github.com/ForgetfulFolly/Holocore.git`. **That is the public origin.** Pushing anything to `origin` makes it public.

If you need a place to push agent working state, do one of:

1. Use a private fork under a personal GitHub account, and re-point `origin` to it. The public `ForgetfulFolly/Holocore` becomes `upstream` for pulling SWG changes only.
2. Use a local-only branch and never push it. `git config branch.<name>.pushRemote no_push` to be safe.
3. Use an out-of-tree path entirely — your worktree state doesn't need to live inside a git repo at all.

---

## Pre-commit hook (recommended)

A pre-commit hook script in `.githooks/pre-commit` blocks the agent metadata patterns above. Install it on your local clone with:

```bash
git config core.hooksPath .githooks
```

This makes the hook active for this repo. The hook rejects commits that include `.worktrees/` or any of the phase-marker files. If you genuinely need to commit one (you don't), use `--no-verify` and write down why.

---

## Why this file exists

A previous agent run accidentally pushed working-directory metadata and ephemeral branches to this public repository. The cleanup commit and these guardrails (`.gitignore`, `.githooks/pre-commit`, `.github/workflows/no-agent-artifacts.yml`, `.github/copilot-instructions.md`, `AGENTS.md`, this file) are the durable fix.

Incident details and the laptop-side remediation runbook are kept in the private parent repository, not here.
