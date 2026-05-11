# Holocore Data Catalog — Agent Guide

The `holocore_catalog` PostgreSQL database on `trading-node` indexes every class,
packet, command callback, and data file in this codebase. **All agents must query it
before writing class references into a spec.** This is the primary control for AFP-011.

## Quick Start

From trading-node:
```bash
# Does this class exist?
hc-catalog exists CraftingSessionService
# exit 0 = exists, exit 1 = does not exist

# What is the constructor for this packet?
hc-catalog lookup MessageQueueDraftSchematics

# List all intent classes
hc-catalog list --kind intent

# What fields does PlayerObject have?
hc-catalog fields PlayerObject

# What cpp callbacks are registered?
hc-catalog commands
```

## AFP-011 Pre-Flight Checklist

Before referencing ANY class in a spec:

1. **Run lookup:** `hc-catalog lookup <ClassName>`
   - If no results → the class does not yet exist in master
   - If the task creates it, say so explicitly in the spec
   - If the task does NOT create it, STOP — you have an AFP-011 violation

2. **For packet constructors:** Always copy the `Constructor:` line from the lookup output.
   Do not paraphrase or guess parameter names.

3. **For commands:** Run `hc-catalog commands` to see what is already registered.
   Do not add a command that already exists.

4. **For data files:** Run `hc-catalog file commands_ground` to see the SDB schema.

## Catalog Contents

| Table        | Contents                                          | ~Rows |
|--------------|---------------------------------------------------|-------|
| `classes`    | All Kotlin/Java classes (kind-classified)         | 1060+ |
| `class_fields` | Key fields on PlayerObject, CreatureObject, etc | varies |
| `packets`    | pswgcommon packet classes + constructor signatures | 260+  |
| `commands`   | Registered cpp callbacks in CommandExecutionService | 56+ |
| `data_files` | All .sdb / .cfg / .json files in serverdata/      | 5500+ |

### Class Kinds

| Kind       | Parent Class      | Example                          |
|------------|-------------------|----------------------------------|
| `intent`   | Intent            | RequestCraftingSessionIntent     |
| `service`  | Service           | CraftingSessionService           |
| `manager`  | Manager           | CraftingManager                  |
| `callback` | ICmdCallback      | CmdRequestCraftingSession        |
| `object`   | SWGObject (etc.)  | PlayerCreatureObject             |
| `packet`   | *Packet / Message | MessageQueueDraftSchematics      |
| `utility`  | (none)            | StaticItemCreator                |

## Direct SQL Queries

```bash
psql -U trader -d holocore_catalog
```

```sql
-- Find all classes in a package
SELECT short_name, kind FROM classes
WHERE package LIKE '%crafting%'
ORDER BY kind, short_name;

-- Check if a command name is taken
SELECT * FROM commands WHERE command_name = 'requestcraftingsession';

-- Find all SDB files for a subsystem
SELECT file_path, row_count FROM data_files
WHERE file_path LIKE '%command%';

-- Get packet constructor
SELECT class_name, constructor_sig FROM packets
WHERE class_name ILIKE '%crafting%';
```

## Keeping the Catalog Fresh

A systemd timer on trading-node runs `holocore-catalog-seed.py` hourly:
- Does `git pull origin master` on the Holocore repo
- Re-seeds all tables (UPSERT, safe to re-run)

After merging new code, the catalog updates within **1 hour**. To trigger immediately:
```bash
ssh trading-node 'sudo systemctl start holocore-catalog-seed.service'
```

Check the last seed time:
```bash
ssh trading-node 'psql -U trader -d holocore_catalog -c "SELECT * FROM schema_migrations ORDER BY applied_at DESC LIMIT 5"'
```

## Adding Fields to class_fields

If you implement a new class with important fields, add them:
```sql
INSERT INTO class_fields(class_id, field_name, field_type, is_mutable, notes)
SELECT id, 'myField', 'Int', true, 'Brief description'
FROM classes WHERE short_name = 'MyClassName'
ON CONFLICT DO NOTHING;
```

Or by extending `KNOWN_FIELDS` dict in `/home/trader/bin/holocore-catalog-seed.py`
and re-running the seeder.

## Source

- Seeder:  `/home/trader/bin/holocore-catalog-seed.py` on trading-node
- CLI:     `/home/trader/bin/hc-catalog` on trading-node
- Timer:   `systemctl status holocore-catalog-seed.timer` on trading-node
- DB:      `psql -U trader -d holocore_catalog` on trading-node
