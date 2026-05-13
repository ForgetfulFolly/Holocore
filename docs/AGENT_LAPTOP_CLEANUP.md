# Agent Laptop — Cleanup & Hardening Runbook

**Audience:** Whoever runs the swarm/agent workflow on the laptop that has a local clone of this repo. **This laptop uses GitHub Copilot as its AI coding assistant.**

**Why this exists:** On 2026-05-11 the laptop's agent system accidentally pushed local working metadata (`.worktrees/`, `CLAIMED.md`, `BLOCKED.md`) and four `agent/task-*` branches to this public GitHub repo. The remote side has been cleaned (see commit `95a8f2445` and the `CLAUDE.md` at repo root). This runbook is the laptop-side work that completes the fix.

**Scope:** ~15 minutes of work, mostly mechanical. No code changes to the agent system are required to stop the leak — they're optional hardening at the end.

## Why this matters for Copilot specifically

GitHub Copilot can introduce changes through three paths that have different protection profiles:

| Copilot path | Local pre-commit hook fires? | GitHub Actions check fires? |
|--------------|------------------------------|------------------------------|
| Copilot Chat / inline suggestions you accept and commit locally | ✓ Yes | ✓ Yes (on push) |
| GitHub web UI commits via Copilot (`Code → Edit → Commit`) | ✗ No | ✓ Yes |
| GitHub Copilot Coding Agent (autonomous PR generation) | ✗ No | ✓ Yes |

The Actions check at `.github/workflows/no-agent-artifacts.yml` is **server-side** — it blocks any push or PR introducing agent metadata regardless of which Copilot path produced it. That's the durable fix.

The local pre-commit hook (`.githooks/pre-commit`) is **defense in depth** for the common path (local commits) and gives you a fast failure on the laptop before pushing anything.

The instructions in `.github/copilot-instructions.md` are read by GitHub Copilot Chat and the Copilot Coding Agent in modern versions, so they help at the suggestion-generation layer too.

---

## Step 0 — Preserve any in-flight agent work

The laptop's local `.worktrees/` directory may contain real in-progress work that the agent hasn't merged yet. **Do this before any cleanup commands:**

```bash
# On the laptop, in the Holocore clone:
cd /path/to/Holocore

# Move .worktrees/ OUT of the repo so cleanup doesn't touch it
mv .worktrees ~/.holocore_worktrees_backup_$(date +%Y%m%d)
```

If you don't care about preserving local agent state, skip the `mv` and just delete it:

```bash
rm -rf .worktrees
```

---

## Step 1 — Pull the latest master

Get the cleanup commit and the new guardrails (`CLAUDE.md`, `AGENTS.md`, `.gitignore`, `.githooks/`):

```bash
cd /path/to/Holocore
git fetch origin --prune
git checkout master
git pull --ff-only origin master
```

If `git pull --ff-only` fails because you have uncommitted local changes, stash or commit them first. If it fails because you have local commits diverging from master, that's a sign the laptop has been treating this clone as a workspace — see Step 5.

---

## Step 2 — Activate the pre-commit hook

The hook in `.githooks/pre-commit` blocks accidental staging of the same files that leaked last time. **Run this once per clone:**

```bash
cd /path/to/Holocore
git config core.hooksPath .githooks
```

Verify it's active:

```bash
git config --get core.hooksPath   # should print: .githooks
```

After this, any future commit attempting to add `.worktrees/`, `CLAIMED.md`, `BLOCKED.md`, `HEARTBEAT.md`, `REVIEW.md`, or any `STEER*.md` file is **blocked locally** with an error message. The agent can't accidentally push these even if `git add .` picks them up.

---

## Step 3 — Prune the deleted remote branches from your laptop

The 4 leak branches were deleted on GitHub. Your laptop still has stale local copies. Clean them up:

```bash
cd /path/to/Holocore

# Refresh remote-tracking refs (removes pointers to deleted branches)
git fetch origin --prune

# Delete the local copies of the leak branches if present
for b in \
  agent/task-601-triage-log-parser \
  agent/task-602-triage-tailer-daemon \
  agent/task-603-triage-llm-worker \
  agent/task-604-triage-systemd-install
do
  git branch -D "$b" 2>/dev/null && echo "deleted local $b" || echo "no local $b"
done
```

---

## Step 4 — Verify your git config doesn't auto-push agent branches

Some swarm systems are configured to `git push --all` or push every branch automatically. That's how the leak happened. Check yours:

```bash
cd /path/to/Holocore

# Look for any auto-push configuration
git config --get-all remote.origin.push
git config --get-all push.default
git config --get-all push.followTags
```

If `remote.origin.push` is set to something that pushes all branches or all refs (e.g. `refs/heads/*:refs/heads/*`), remove it:

```bash
git config --unset-all remote.origin.push
git config push.default current   # only push the current branch when 'git push' is used bare
```

Also search the swarm/agent code for `git push --all` or `git push --mirror` and replace with explicit branch pushes.

---

## Step 5 — Decide on the long-term remote setup (RECOMMENDED)

The root cause is that the laptop's `origin` for this repo points at the public `ForgetfulFolly/Holocore`. Anything the agent pushes is public by default. There are three honest ways to fix this:

### Option A — Private fork for agent work (most robust)

1. On GitHub, fork `ForgetfulFolly/Holocore` to a **private** repo under a personal account (call it e.g. `<you>/Holocore-agent`)
2. Repoint the laptop's `origin` to the private fork:
   ```bash
   git remote set-url origin git@github.com:<you>/Holocore-agent.git
   git remote add upstream https://github.com/ForgetfulFolly/Holocore.git
   ```
3. Now `git push` goes to the private fork. To pull SWG updates: `git fetch upstream && git merge upstream/master`. To contribute back: open a PR from `<you>/Holocore-agent` to `ForgetfulFolly/Holocore`.
4. Sync your swarm config so it knows `origin` is for the agent's private fork.

### Option B — Block push entirely on this clone (simpler, less flexible)

Configure git to refuse pushes from this clone:

```bash
git remote set-url --push origin no_push
```

This makes `git push` fail with a clear error. The agent then has to be explicit about where work goes. Combine with separate clones for contribution work if needed.

### Option C — Status quo + trust the .gitignore + hook (lowest effort, highest residual risk)

Don't touch the remote config; rely on the new `.gitignore` and the pre-commit hook to catch mistakes. This is what you have today after Steps 1-4. The risk is that any agent change that bypasses git hooks (e.g. `git commit --no-verify` or hooks not activated on this clone) can still leak.

**Recommendation: Option A.** It's a one-time setup that removes the entire class of "agent accidentally pushed to public repo" failures.

---

## Step 6 — Verify the local state is clean

After Steps 1-4:

```bash
cd /path/to/Holocore

# Should NOT show .worktrees/, CLAIMED.md, BLOCKED.md, etc.
git status

# Should print: .githooks
git config --get core.hooksPath

# Should show no local agent/* branches
git branch | grep '^  agent/' || echo "no agent branches — good"

# Should show only legitimate remote branches (no agent/*)
git branch -r | grep -v dependabot | grep -v 'HEAD ->'
```

Expected legitimate remote branches:
- `origin/master`
- `origin/feature/space-wars`
- `origin/fix/swg-server-local-changes`
- `origin/nge`
- `origin/packet_updates`
- `origin/dependabot/*` (auto-managed)

---

## Step 7 — (Optional) GitHub-side branch protection

Application-side, the `main-pollution guard` was already catching direct pushes to `master` (we saw it firing in BLOCKED.md before the leak). Adding **GitHub-side branch protection** is belt-and-suspenders:

1. Go to https://github.com/ForgetfulFolly/Holocore/settings/branches
2. Add a rule for `master`:
   - ✓ Require a pull request before merging
   - ✓ Require status checks to pass before merging
   - ✗ Allow force pushes (leave OFF)
   - ✗ Allow deletions (leave OFF)
3. Save

Requires admin access to the repo. The `ForgetfulFolly` org owner can set this.

---

## Step 8 — (Optional) Decide on history rewrite

The leaked files (`.worktrees/agent-task-*/CLAIMED.md` and `BLOCKED.md`) are no longer in the **current tree**, but their contents are still recoverable from older commits in git history. The leaked information is:

- Worker names: `moe`, `larry`, `happy`
- Team identifiers: `alpha`, `bravo`
- Timestamps (2026-05-10 / 2026-05-11)
- The `2026-04-15 main-pollution guard` mechanism

**No IPs, credentials, internal paths, or AudilyNow content was leaked** (independently verified).

If the worker/team identifier leak is acceptable: leave history alone. The leak is limited and a force-rewrite is disruptive for a public fork others may have cloned.

If you want history scrubbed:

```bash
# Install git-filter-repo first: pip install git-filter-repo

cd /tmp/holocore-rewrite
git clone https://github.com/ForgetfulFolly/Holocore.git --no-local
cd Holocore
git filter-repo --invert-paths --path .worktrees/

# Then force-push (BREAKING CHANGE for anyone who already cloned):
git push --force origin master --all
```

Be aware:
- This rewrites every commit hash that touched `.worktrees/` — any open PR, branch, or external clone gets desynced
- All open dependabot/feature branches need to be rebased on the new master
- The 4 agent/* branches are already deleted, so they don't need separate handling
- The forked-from-upstream history is preserved; only your local commits change hashes

**Recommendation: skip the rewrite unless the worker-name leak is genuinely sensitive.** The cost-benefit doesn't favor it.

---

## Done state

After Steps 0-4 (the mandatory ones):

- ✓ Latest master pulled
- ✓ Pre-commit hook active on the laptop clone
- ✓ Stale local agent branches deleted
- ✓ `push.default` set to `current` (no accidental all-branches pushes)
- ✓ `CLAUDE.md`, `AGENTS.md`, `.gitignore`, `.githooks/` all present and read by future agent invocations
- ✓ Original in-flight `.worktrees/` work (if any) preserved at `~/.holocore_worktrees_backup_*`

After Steps 5-7 (recommended hardening):

- ✓ `origin` points to a private fork, public `ForgetfulFolly/Holocore` is `upstream`
- ✓ GitHub branch protection on `master` blocks direct pushes and deletions
- ✓ History is unchanged (or rewritten if Step 8 was taken)

---

## If something goes wrong

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `git pull` fails with merge conflict in `.worktrees/` | You have uncommitted local files there | Step 0 first, then retry pull |
| Pre-commit hook doesn't fire | `core.hooksPath` not set | Run `git config core.hooksPath .githooks` |
| Pre-commit hook fires on legitimate work | Probably a false positive on the IP/path heuristic | The hook only WARNS on content — it still allows commit. The file-pattern check is the hard block. |
| Agent system pushes anyway | `pre-commit` hook only runs on `git commit`, not `git push` | Make sure the agent uses `git commit` not lower-level git plumbing. Or set up a `pre-push` hook too (write one if needed). |
| Need to commit an agent file legitimately | (You don't — but) | `git commit --no-verify` bypasses local hooks. Don't normalize this. |

---

## Verifying the cleanup landed

After running through this runbook, you can confirm the repo state is what you expect:

```bash
# All these commands should succeed and show clean state:

git ls-tree -r --name-only origin/master | grep '\.worktrees/' && echo "LEAK STILL PRESENT" || echo "no .worktrees in master — good"
git ls-tree -r --name-only origin/master | grep 'CLAIMED\.md\|BLOCKED\.md\|HEARTBEAT\.md' && echo "LEAK STILL PRESENT" || echo "no agent metadata in master — good"
git branch -r | grep 'agent/' && echo "LEAK BRANCH STILL PRESENT" || echo "no agent/* remote branches — good"
git ls-tree origin/master | grep -E '^.*\b(CLAUDE\.md|AGENTS\.md|\.gitignore|\.githooks)$' || echo "WARNING: guardrail files missing"
```

If any of these show `LEAK STILL PRESENT`, the cleanup didn't fully land — DM me (or whoever owns this work) before the next agent run.

---

## Contact / context

- Cleanup commit on remote: `95a8f2445` ("Remove agent/swarm artifacts from repo; add guardrails")
- See `CLAUDE.md` and `AGENTS.md` at the repo root for the durable rules going forward
- Pre-commit hook source: `.githooks/pre-commit`

If the swarm's git workflow has any non-obvious surfaces (custom scripts, programmatic git plumbing instead of porcelain commands, etc.), audit those for the same leak pattern.
