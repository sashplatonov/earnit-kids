-- Consolidated schema for EarnIt Kids application.
-- Combines all Node.js migrations (001 through 015) into a single baseline.

-- Families table
CREATE TABLE IF NOT EXISTS families (
    id SERIAL PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    admin_password VARCHAR(255) NOT NULL,
    is_blocked BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT TRUE,
    verification_token VARCHAR(255),
    last_selected_child_id INTEGER, -- FK added after children table
    reset_token VARCHAR(64),
    reset_token_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_activity TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_families_email ON families(email);
CREATE INDEX IF NOT EXISTS idx_families_reset_token ON families(reset_token)
    WHERE reset_token IS NOT NULL;

-- Children table
CREATE TABLE IF NOT EXISTS children (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    token VARCHAR(255) UNIQUE,
    balance INTEGER DEFAULT 0,
    monthly_limit INTEGER DEFAULT 10000,
    daily_coin_limit INTEGER DEFAULT 0,
    theme VARCHAR(10) DEFAULT 'ocean' CHECK (theme IN ('mint', 'ocean', 'sun', 'coral', 'cosmos')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_children_family_id ON children(family_id);
CREATE INDEX IF NOT EXISTS idx_children_token ON children(token);

-- Add FK for last_selected_child_id
ALTER TABLE families ADD CONSTRAINT fk_families_last_selected_child
    FOREIGN KEY (last_selected_child_id) REFERENCES children(id) ON DELETE SET NULL;

-- Tasks table
CREATE TABLE IF NOT EXISTS tasks (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL,
    name VARCHAR(500) NOT NULL,
    coins INTEGER NOT NULL DEFAULT 0,
    group_name VARCHAR(255),
    frequency JSONB,
    comment TEXT,
    money_limit INTEGER,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT tasks_child_id_task_id_key UNIQUE (child_id, task_id)
);

CREATE INDEX IF NOT EXISTS idx_tasks_family_id ON tasks(family_id);

-- Shop items table
CREATE TABLE IF NOT EXISTS shop_items (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL,
    name VARCHAR(500) NOT NULL,
    price INTEGER NOT NULL DEFAULT 0,
    group_name VARCHAR(255),
    frequency JSONB,
    money_limit INTEGER,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT shop_items_child_id_item_id_key UNIQUE (child_id, item_id)
);

CREATE INDEX IF NOT EXISTS idx_shop_items_family_id ON shop_items(family_id);

-- History table
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
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_history_family_id ON history(family_id);
CREATE INDEX IF NOT EXISTS idx_history_child_id ON history(child_id);
CREATE INDEX IF NOT EXISTS idx_history_type ON history(type);
CREATE INDEX IF NOT EXISTS idx_history_created_at ON history(created_at);

-- Requests table
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
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_requests_family_id ON requests(family_id);
CREATE INDEX IF NOT EXISTS idx_requests_child_id ON requests(child_id);
CREATE INDEX IF NOT EXISTS idx_requests_status ON requests(status);

-- Friends table
CREATE TABLE IF NOT EXISTS friends (
    id SERIAL PRIMARY KEY,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    friend_child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT friends_child_id_friend_child_id_key UNIQUE (child_id, friend_child_id)
);

-- Device push tokens
CREATE TABLE IF NOT EXISTS device_push_tokens (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER REFERENCES children(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'child')),
    platform VARCHAR(20) NOT NULL DEFAULT 'unknown',
    token TEXT,
    push_type VARCHAR(10) NOT NULL DEFAULT 'fcm' CHECK (push_type IN ('fcm', 'web')),
    endpoint TEXT,
    key_p256dh TEXT,
    key_auth TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_seen_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_device_push_tokens_token
    ON device_push_tokens(token) WHERE token IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_device_push_tokens_endpoint
    ON device_push_tokens(endpoint) WHERE endpoint IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_device_push_tokens_family_id ON device_push_tokens(family_id);

-- Auto-update trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_families_updated_at
    BEFORE UPDATE ON families FOR EACH ROW
    EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_children_updated_at
    BEFORE UPDATE ON children FOR EACH ROW
    EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_tasks_updated_at
    BEFORE UPDATE ON tasks FOR EACH ROW
    EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_shop_items_updated_at
    BEFORE UPDATE ON shop_items FOR EACH ROW
    EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_requests_updated_at
    BEFORE UPDATE ON requests FOR EACH ROW
    EXECUTE PROCEDURE update_updated_at_column();
