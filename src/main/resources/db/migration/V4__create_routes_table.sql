CREATE TABLE routes (
                        id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id          UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    canonical_id     UUID REFERENCES routes(id) ON DELETE SET NULL,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    path             GEOMETRY(LINESTRING, 4326) NOT NULL,
    distance_m       DOUBLE PRECISION,
    visibility       VARCHAR(20) NOT NULL DEFAULT 'public',
    is_protected     BOOLEAN NOT NULL DEFAULT false,
    popularity_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX routes_user_id_idx ON routes(user_id);
CREATE INDEX routes_canonical_id_idx ON routes(canonical_id);
CREATE INDEX routes_path_idx ON routes USING GIST(path);