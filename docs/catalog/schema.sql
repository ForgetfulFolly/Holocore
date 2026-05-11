-- holocore_catalog schema (reference copy — authoritative version is on trading-node)
-- Applied by: /home/trader/bin/holocore-catalog-seed.py
-- Database: holocore_catalog (owner: trader)

CREATE TABLE IF NOT EXISTS classes (
    id          SERIAL PRIMARY KEY,
    fqn         TEXT NOT NULL,
    short_name  TEXT NOT NULL,
    kind        TEXT NOT NULL,           -- intent | service | manager | callback | object | packet | utility | other
    language    TEXT NOT NULL,           -- kotlin | java
    file_path   TEXT NOT NULL,           -- relative to Holocore repo root
    package     TEXT NOT NULL,
    parent_name TEXT,
    indexed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(fqn)
);
CREATE INDEX IF NOT EXISTS idx_classes_short  ON classes(lower(short_name));
CREATE INDEX IF NOT EXISTS idx_classes_kind   ON classes(kind);
CREATE INDEX IF NOT EXISTS idx_classes_pkg    ON classes(package);

-- Key domain class fields (PlayerObject, CreatureObject, etc.)
CREATE TABLE IF NOT EXISTS class_fields (
    id          SERIAL PRIMARY KEY,
    class_id    INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    field_name  TEXT NOT NULL,
    field_type  TEXT,
    is_mutable  BOOLEAN NOT NULL DEFAULT true,  -- false = val / final
    notes       TEXT,
    UNIQUE(class_id, field_name)
);
CREATE INDEX IF NOT EXISTS idx_cf_class ON class_fields(class_id);

-- pswgcommon packet classes with constructor signatures
CREATE TABLE IF NOT EXISTS packets (
    id               SERIAL PRIMARY KEY,
    class_name       TEXT NOT NULL,
    fqn              TEXT NOT NULL,
    constructor_sig  TEXT NOT NULL,
    file_path        TEXT NOT NULL,
    indexed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(class_name)
);
CREATE INDEX IF NOT EXISTS idx_packets_name ON packets(lower(class_name));

-- Registered cpp callbacks (from CommandExecutionService.java)
CREATE TABLE IF NOT EXISTS commands (
    id              SERIAL PRIMARY KEY,
    command_name    TEXT NOT NULL,
    callback_class  TEXT NOT NULL,
    callback_fqn    TEXT,
    sdb_row_exists  BOOLEAN NOT NULL DEFAULT false,
    indexed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(command_name)
);

-- serverdata files (.sdb, .cfg, .json, etc.)
CREATE TABLE IF NOT EXISTS data_files (
    id          SERIAL PRIMARY KEY,
    file_path   TEXT NOT NULL,
    format      TEXT NOT NULL,
    row_count   INTEGER,
    columns     TEXT[],
    description TEXT,
    indexed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(file_path)
);
CREATE INDEX IF NOT EXISTS idx_df_format ON data_files(format);

CREATE TABLE IF NOT EXISTS schema_migrations (
    version    TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
