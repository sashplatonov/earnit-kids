-- H2-compatible schema for tests (PostgreSQL-specific features removed)

CREATE TABLE IF NOT EXISTS families (
    id SERIAL PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    admin_password VARCHAR(255) NOT NULL,
    is_blocked BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT TRUE,
    verification_token VARCHAR(255),
    last_selected_child_id INTEGER,
    reset_token VARCHAR(64),
    reset_token_expires_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_families_email ON families(email);

CREATE TABLE IF NOT EXISTS children (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    token VARCHAR(255) UNIQUE,
    balance INTEGER DEFAULT 0,
    monthly_limit INTEGER DEFAULT 10000,
    daily_coin_limit INTEGER DEFAULT 0,
    theme VARCHAR(10) DEFAULT 'ocean',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_children_family_id ON children(family_id);
CREATE INDEX IF NOT EXISTS idx_children_token ON children(token);

ALTER TABLE families ADD CONSTRAINT fk_families_last_selected_child
    FOREIGN KEY (last_selected_child_id) REFERENCES children(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS tasks (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL,
    name VARCHAR(500) NOT NULL,
    coins INTEGER NOT NULL DEFAULT 0,
    group_name VARCHAR(255),
    frequency VARCHAR(2000),
    comment TEXT,
    money_limit INTEGER,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tasks_child_id_task_id_key UNIQUE (child_id, task_id)
);

CREATE INDEX IF NOT EXISTS idx_tasks_family_id ON tasks(family_id);

CREATE TABLE IF NOT EXISTS shop_items (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL,
    name VARCHAR(500) NOT NULL,
    price INTEGER NOT NULL DEFAULT 0,
    group_name VARCHAR(255),
    frequency VARCHAR(2000),
    money_limit INTEGER,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT shop_items_child_id_item_id_key UNIQUE (child_id, item_id)
);

CREATE INDEX IF NOT EXISTS idx_shop_items_family_id ON shop_items(family_id);

CREATE TABLE IF NOT EXISTS history (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    external_id BIGINT,
    type VARCHAR(50) NOT NULL,
    amount INTEGER NOT NULL DEFAULT 0,
    description TEXT,
    money_amount INTEGER DEFAULT 0,
    related_id BIGINT,
    group_name VARCHAR(255),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_history_family_id ON history(family_id);
CREATE INDEX IF NOT EXISTS idx_history_child_id ON history(child_id);

CREATE TABLE IF NOT EXISTS requests (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    external_id BIGINT,
    task_id BIGINT,
    task_name TEXT,
    item_id BIGINT,
    coins INTEGER DEFAULT 0,
    status VARCHAR(50) DEFAULT 'pending',
    request_type VARCHAR(50) DEFAULT 'earn',
    money_amount INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_requests_family_id ON requests(family_id);
CREATE INDEX IF NOT EXISTS idx_requests_child_id ON requests(child_id);

CREATE TABLE IF NOT EXISTS friends (
    id SERIAL PRIMARY KEY,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    friend_child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT friends_child_id_friend_child_id_key UNIQUE (child_id, friend_child_id)
);

CREATE TABLE IF NOT EXISTS device_push_tokens (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER REFERENCES children(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    platform VARCHAR(20) NOT NULL DEFAULT 'unknown',
    token TEXT,
    push_type VARCHAR(10) NOT NULL DEFAULT 'fcm',
    endpoint TEXT,
    key_p256dh TEXT,
    key_auth TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_device_push_tokens_token
    ON device_push_tokens(token);
CREATE UNIQUE INDEX IF NOT EXISTS idx_device_push_tokens_endpoint
    ON device_push_tokens(endpoint);
CREATE INDEX IF NOT EXISTS idx_device_push_tokens_family_id ON device_push_tokens(family_id);

-- Auto-update trigger for updated_at
-- Triggers for auto-updating `updated_at` omitted for H2 tests
