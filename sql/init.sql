BEGIN;
SET session_replication_role = 'replica';
TRUNCATE TABLE vehicle_import_operation, vehicle, coordinates
    RESTART IDENTITY CASCADE;
SET session_replication_role = 'origin';
COMMIT;


CREATE TABLE IF NOT EXISTS coordinates
(
    id BIGSERIAL PRIMARY KEY,
    x  DOUBLE PRECISION NOT NULL CHECK (x <= 613),
    y  REAL             NOT NULL CHECK (y <= 962),
    CONSTRAINT coordinates_xy_uniq UNIQUE (x, y)
);
CREATE TABLE IF NOT EXISTS vehicle
(
    id                 BIGSERIAL PRIMARY KEY,
    version            INT                DEFAULT 0,
    name               TEXT      NOT NULL CHECK (length(btrim(name)) > 0),
    creation_time      TIMESTAMP NOT NULL DEFAULT now(),
    type               TEXT      NOT NULL CHECK (type IN ('CAR', 'HELICOPTER', 'MOTORCYCLE', 'CHOPPER')),
    engine_power       INTEGER CHECK (engine_power IS NULL OR engine_power > 0),
    number_of_wheels   INTEGER   NOT NULL CHECK (number_of_wheels > 0),
    capacity           INTEGER CHECK (capacity IS NULL OR capacity > 0),
    distance_travelled INTEGER CHECK (distance_travelled IS NULL OR distance_travelled > 0),
    fuel_consumption   REAL      NOT NULL CHECK (fuel_consumption > 0),
    fuel_type          TEXT      NOT NULL CHECK (fuel_type IN ('KEROSENE', 'MANPOWER', 'NUCLEAR')),
    coordinates_id     BIGINT    NOT NULL REFERENCES coordinates (id) ON DELETE RESTRICT
);
CREATE TABLE IF NOT EXISTS vehicle_import_operation
(
    id             BIGSERIAL PRIMARY KEY,
    status         BOOLEAN,
    imported_count INTEGER,
    creation_time  TIMESTAMP NOT NULL DEFAULT now()
);


-- 1) Любой объект с минимальным distance_travelled (среди NOT NULL)
CREATE OR REPLACE FUNCTION fn_vehicle_min_distance()
    RETURNS SETOF vehicle
    LANGUAGE sql
    STABLE
AS
$$
SELECT *
FROM vehicle
WHERE distance_travelled IS NOT NULL
ORDER BY distance_travelled ASC
LIMIT 1
$$;

-- 2) Кол-во с fuel_consumption > заданного
CREATE OR REPLACE FUNCTION fn_vehicle_count_fuel_gt(p_value real)
    RETURNS bigint
    LANGUAGE sql
    STABLE
AS
$$
SELECT COUNT(*)::bigint
FROM vehicle
WHERE fuel_consumption > p_value
$$;

-- 3) Список с fuel_consumption > заданного
CREATE OR REPLACE FUNCTION fn_vehicle_list_fuel_gt(p_value real)
    RETURNS SETOF vehicle
    LANGUAGE sql
    STABLE
AS
$$
SELECT *
FROM vehicle
WHERE fuel_consumption > p_value
ORDER BY id
$$;

-- 4) Найти все ТС заданного типа (поле type хранится как TEXT)
CREATE OR REPLACE FUNCTION fn_vehicle_list_by_type(p_type text)
    RETURNS SETOF vehicle
    LANGUAGE sql
    STABLE
AS
$$
SELECT *
FROM vehicle
WHERE type = p_type
ORDER BY id
$$;

-- 5) Найти все ТС с мощностью в диапазоне [min, max] (границы включительно, null-ы игнорим)
CREATE OR REPLACE FUNCTION fn_vehicle_list_engine_between(p_min int, p_max int)
    RETURNS SETOF vehicle
    LANGUAGE sql
    STABLE
AS
$$
SELECT *
FROM vehicle
WHERE engine_power IS NOT NULL
  AND engine_power >= p_min
  AND engine_power <= p_max
ORDER BY engine_power, id
$$;

-- 1
select *
from fn_vehicle_min_distance();

-- 2
select fn_vehicle_count_fuel_gt(10.0);

-- 3
select *
from fn_vehicle_list_fuel_gt(15.5);

-- 4
select *
from fn_vehicle_list_by_type('CAR');

-- 5
select *
from fn_vehicle_list_engine_between(100, 200);