# Git hooks for Holocore

This directory contains versioned git hooks for this repo. Activate them on your local clone with:

```bash
git config core.hooksPath .githooks
```

That tells git to look for hooks here instead of in `.git/hooks/`. Run it once per clone.

## What's installed

- **`pre-commit`** — Blocks commits that try to add agent/swarm metadata (`.worktrees/`, `CLAIMED.md`, `BLOCKED.md`, etc.) to this public repo. Warns on content that looks like internal infrastructure references. See `CLAUDE.md` for context on why this exists.

## Why hooks are in `.githooks/` not `.git/hooks/`

`.git/hooks/` lives inside the local git directory and isn't versioned. Anything there only applies to your clone. By putting the hook in `.githooks/` at the repo root and pointing `core.hooksPath` at it, the hook ships with the repo and every contributor gets the same protections after running the one-time `git config` command.
