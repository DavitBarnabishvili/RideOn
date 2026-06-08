-- Promote path from 2D LINESTRING to 3D LINESTRINGZ.
-- Existing rows have their Z filled with 0 by ST_Force3D during the cast —
-- this is expected. All pre-migration routes lack elevation data and will
-- be backfilled when the OpenTopoData elevation API is integrated in Phase 2.
ALTER TABLE routes
    ALTER COLUMN path TYPE GEOMETRY(LINESTRINGZ, 4326)
        USING ST_Force3D(path);

-- Elevation gain and loss in metres, computed from Z coordinates at
-- insert/import time. NULL until a route has valid elevation data.
ALTER TABLE routes
    ADD COLUMN elevation_gain_m  DOUBLE PRECISION,
        ADD COLUMN elevation_loss_m  DOUBLE PRECISION;