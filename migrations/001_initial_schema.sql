-- Migration: Create migrations tracking table
-- Created: 2026-02-03

CREATE TABLE IF NOT EXISTS migrations (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    executed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_migrations_name ON migrations(name);
-- Migration: Create families table
-- Created: 2026-02-03

CREATE TABLE IF NOT EXISTS families (
    id SERIAL PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    admin_password VARCHAR(255) NOT NULL,
    child_token VARCHAR(255) UNIQUE,
    monthly_limit INTEGER DEFAULT 10000,
    child_nickname VARCHAR(255),
    is_blocked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_activity TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_families_email ON families(email);
CREATE INDEX IF NOT EXISTS idx_families_child_token ON families(child_token);
CREATE INDEX IF NOT EXISTS idx_families_child_nickname ON families(child_nickname);
-- Migration: Create family_data table (balance)
-- Created: 2026-02-03

CREATE TABLE IF NOT EXISTS family_data (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    balance INTEGER DEFAULT 0,
    UNIQUE(family_id)
);

CREATE INDEX IF NOT EXISTS idx_family_data_family_id ON family_data(family_id);
-- Migration: Create tasks table
-- Created: 2026-02-03

CREATE TABLE IF NOT EXISTS tasks (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL,
    name VARCHAR(500) NOT NULL,
    coins INTEGER NOT NULL DEFAULT 0,
    group_name VARCHAR(255),
    frequency JSONB,
    comment TEXT,
    money_limit INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(family_id, task_id)
);

CREATE INDEX IF NOT EXISTS idx_tasks_family_id ON tasks(family_id);
-- Migration: Create shop_items table
-- Created: 2026-02-03

CREATE TABLE IF NOT EXISTS shop_items (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL,
    name VARCHAR(500) NOT NULL,
    price INTEGER NOT NULL DEFAULT 0,
    group_name VARCHAR(255),
    frequency JSONB,
    money_limit INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(family_id, item_id)
);

CREATE INDEX IF NOT EXISTS idx_shop_items_family_id ON shop_items(family_id);
-- Migration: Create history table
-- Created: 2026-02-03

CREATE TABLE IF NOT EXISTS history (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    data JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_history_family_id ON history(family_id);
CREATE INDEX IF NOT EXISTS idx_history_type ON history(type);
CREATE INDEX IF NOT EXISTS idx_history_created_at ON history(created_at);
-- Migration: Create requests table
-- Created: 2026-02-03

CREATE TABLE IF NOT EXISTS requests (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    data JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(50) DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_requests_family_id ON requests(family_id);
CREATE INDEX IF NOT EXISTS idx_requests_status ON requests(status);
-- Migration: Create friends table
-- Created: 2026-02-03

CREATE TABLE IF NOT EXISTS friends (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    friend_family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(family_id, friend_family_id)
);

CREATE INDEX IF NOT EXISTS idx_friends_family_id ON friends(family_id);
CREATE INDEX IF NOT EXISTS idx_friends_friend_family_id ON friends(friend_family_id);
-- Migration: Create super_admin table
-- Created: 2026-02-03

CREATE TABLE IF NOT EXISTS super_admin (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Insert default super admin if not exists (can be overridden by env vars at runtime)
INSERT INTO super_admin (email, password)
VALUES ('admin@admin.com', '000000')
ON CONFLICT (email) DO NOTHING;
