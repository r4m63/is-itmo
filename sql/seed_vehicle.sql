-- Usage:
-- psql "postgresql://user:pass@host:port/db_name" -v seed_count=50 -v seed_coords=60 -f sql/seed_vehicle.sql

\if :{?seed_count}
\else
\set seed_count 50
\endif

\if :{?seed_coords}
\else
\set seed_coords 60
\endif

BEGIN;

-- 1) Админ, от имени которого создаём записи
INSERT INTO admin (login, pass_hash, salt)
VALUES ('seed_admin', 'seed_pass_hash_for_demo', 'seed_salt_for_demo')
ON CONFLICT (login) DO NOTHING;

-- 2) Подготовим справочник координат (N штук).
--    Требуется UNIQUE (x, y) в таблице coordinates (рекомендуется иметь такой индекс/ограничение).
--    Если уникального индекса нет, замените ON CONFLICT на проверку через NOT EXISTS.
WITH to_ins AS (
  SELECT
    -- X <= 613 (1 знак после запятой)
    ROUND((random() * 613)::numeric, 1)::float8 AS x,
    -- Y <= 962 (1 знак после запятой)
    ROUND((random() * 962)::numeric, 1)::float4 AS y
  FROM generate_series(1, :seed_coords)
)
INSERT INTO coordinates (x, y)
SELECT x, y
FROM to_ins
ON CONFLICT (x, y) DO NOTHING;

-- 2.1) Гарантируем, что в таблице coordinates есть хотя бы одна запись
INSERT INTO coordinates (x, y)
SELECT 0.0::float8, 0.0::float4
WHERE NOT EXISTS (SELECT 1 FROM coordinates);

-- 3) Вставляем машины и равномерно распределяем по справочнику coordinates
WITH coords AS (
  SELECT c.id,
         row_number() OVER (ORDER BY c.id) AS rn,
         count(*)      OVER ()            AS cnt
  FROM coordinates c
),
sa AS (
  SELECT id FROM admin WHERE login = 'seed_admin' LIMIT 1
)
INSERT INTO vehicle (
    name,
    type,
    engine_power,
    number_of_wheels,
    capacity,
    distance_travelled,
    fuel_consumption,
    fuel_type,
    admin_id,
    coordinates_id
)
SELECT
    'Seed #' || gs::text,

    -- type
    (ARRAY['CAR','HELICOPTER','MOTORCYCLE','CHOPPER'])[
      1 + floor(random()*4)::int
    ]::text,

    -- engine_power: 20% NULL, иначе 50..1049
    CASE WHEN random() < 0.20 THEN NULL
         ELSE (50 + floor(random()*1000))::int
    END,

    -- number_of_wheels: одно из {2,3,4,6}
    (ARRAY[2,3,4,6])[1 + floor(random()*4)::int],

    -- capacity: 30% NULL, иначе 1..6
    CASE WHEN random() < 0.30 THEN NULL
         ELSE (1 + floor(random()*6))::int
    END,

    -- distance_travelled: 25% NULL, иначе 500..100000
    CASE WHEN random() < 0.25 THEN NULL
         ELSE (500 + floor(random()*99501))::int
    END,

    -- fuel_consumption: 1..50 с 1 знаком после запятой
    ROUND((1 + random()*49)::numeric, 1)::float4,

    -- fuel_type
    (ARRAY['KEROSENE','MANPOWER','NUCLEAR'])[
      1 + floor(random()*3)::int
    ]::text,

    -- admin_id
    (SELECT id FROM sa),

    -- coordinates_id: равномерно по кругу по списку координат
    (
      SELECT c2.id
      FROM coords c2
      WHERE c2.rn = ((gs - 1) % c2.cnt) + 1
      LIMIT 1
    )
FROM generate_series(1, :seed_count) AS gs;

COMMIT;
