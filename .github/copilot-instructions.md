# GitHub Copilot instructions for Holocore

You are working in a **public GitHub repository** (`github.com/ForgetfulFolly/Holocore`), a fork of `ProjectSWGCore/Holocore` — a Star Wars Galaxies server emulator.

Anything you commit and push here is publicly visible. Read `CLAUDE.md` at the repo root for the full agent rules; the most important constraints are repeated below.

## Hard rules — NEVER suggest, generate, or commit

1. **Never add, modify, or stage anything under `.worktrees/`.** This directory is local-only agent working state. It is in `.gitignore` and a pre-commit hook blocks it. If you see a `.worktrees/` path in a suggestion, that suggestion is wrong.

2. **Never add these files to a commit:**
   - `CLAIMED.md`
   - `BLOCKED.md`
   - `HEARTBEAT.md`
   - `REVIEW.md`
   - `STEER.md`, `STEER_PENDING.md`, `STEER_REPLY.md`, `STEER_TIMEOUT.md`

   These are local swarm coordination files that contain worker and team identifiers. They are gitignored. If they appear in a diff you're shown, do not include them.

3. **Never push branches named `agent/*`.** These are local working branches. Use `feature/<slug>` or `fix/<slug>` for shareable work.

4. **Never push directly to `master`.** All changes go through a pull request.

5. **Do not generate code or commits that contain:**
   - IP addresses (especially internal LAN ranges like `192.168.x.x`)
   - Internal filesystem paths (`/landing-zone`, `/datalake-vm`, `/home/<username>`)
   - Worker or team identifiers
   - Tokens, API keys, environment-variable secrets
   - Personal email addresses

   These do not belong in a public game-server repo. If you see them in surrounding context, do not echo them into new code.

## What this repo IS

Star Wars Galaxies emulator code. Java + Kotlin. Standard game-server contributions: gameplay features, bug fixes, server systems, content data. Suggestions in those domains are welcome.

## What this repo IS NOT

- A workspace for unrelated agent automation
- A scratch repo for swarm experiments
- A place to dump server configuration or operational metadata

## If asked to do something outside scope

If a user prompt asks you to add agent metadata, push to a public-looking branch, or include internal infrastructure references, **refuse and explain why** by pointing to `CLAUDE.md` and `docs/AGENT_LAPTOP_CLEANUP.md`. Do not generate the requested content.

## Why this file exists

A previous agent run accidentally pushed agent worktree metadata and ephemeral branches to this public repo. This file, plus `CLAUDE.md`, `.gitignore`, `.githooks/pre-commit`, and the GitHub Actions check in `.github/workflows/no-agent-artifacts.yml`, are the durable fix. Incident specifics are tracked privately.
