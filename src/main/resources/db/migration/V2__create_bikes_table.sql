CREATE TABLE bikes (
                       id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                       make        VARCHAR(100) NOT NULL,
                       model       VARCHAR(100) NOT NULL,
                       year        INTEGER NOT NULL,
                       engine_cc   INTEGER,
                       type        VARCHAR(50),
                       nickname    VARCHAR(100),
                       photo_url   VARCHAR(500),
                       created_at  TIMESTAMPTZ DEFAULT NOW()
);