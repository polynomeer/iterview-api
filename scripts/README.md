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

The importer stops at the first fetch or parse error unless `--max-id` is provided.

Example:

```bash
python3 scripts/import_maeil_questions.py \
  --db-host localhost \
  --db-port 5432 \
  --db-name iterview \
  --db-user iterview \
  --db-password iterview
```

Dry run:

```bash
python3 scripts/import_maeil_questions.py --dry-run --max-id 3
```
