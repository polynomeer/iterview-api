# Scripts

## Seed Dummy Data
Load local demo data after the app has created the schema and reference data.

Example:

```bash
PGPASSWORD=iterview psql -h localhost -p 5432 -U iterview -d iterview -f scripts/seed_dummy_data.sql
```

Seeded login:
- email: `demo@example.com`
- password: `password123`

Seeded data includes:
- one demo user with profile, settings, target companies, and active resume
- three questions with tags, companies, roles, and learning materials
- one archived progress item
- one retry-pending item with review queue data
- one daily card for today

## Import Maeil Mail Questions
Import sequential question pages from Maeil Mail into:
- `questions`
- `question_reference_answers`
- `learning_materials`
- `question_learning_materials`

The importer:
- scans from `--start-id` through `--max-id`
- skips ids that fail to fetch or parse
- skips questions already imported into `questions` with `source_type = 'external_import'`
- sleeps between requests to reduce the chance of being rate-limited

Defaults:
- `--start-id 1`
- `--max-id 5000`
- `--sleep-seconds 1.2`

Example:

```bash
python3 scripts/import_maeil_questions.py \
  --db-host localhost \
  --db-port 5432 \
  --db-name iterview \
  --db-user iterview \
  --db-password iterview
```

If `psql` is not on your `PATH`, pass it explicitly:

```bash
python3 scripts/import_maeil_questions.py \
  --psql-bin /opt/homebrew/bin/psql
```

Dry run:

```bash
python3 scripts/import_maeil_questions.py --dry-run --max-id 3
```

Slower import:

```bash
python3 scripts/import_maeil_questions.py --sleep-seconds 2.0
```
