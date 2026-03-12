#!/usr/bin/env python3
"""
Import Maeil Mail question pages into the local Postgres database.

The script fetches sequential question pages starting at question 1 and stops
at the first HTTP or parsing error unless a maximum id is provided.

Imported data mapping:
- question title/body -> questions
- editorial answer content -> question_reference_answers
- source page link -> learning_materials + question_learning_materials
"""

from __future__ import annotations

import argparse
import html
from html.parser import HTMLParser
import os
import shutil
import subprocess
import sys
import tempfile
import textwrap
import time
from dataclasses import dataclass
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BASE_URL = "https://www.maeil-mail.kr/question/{question_id}"
DEFAULT_HEADERS = {
    "User-Agent": "Mozilla/5.0 (compatible; IterviewImporter/1.0; +https://localhost)",
    "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
}
FOOTER_MARKERS = (
    "공유하기",
    "Copyright",
    "Contact",
    "Socials",
    "Etc",
    "팀 소개",
    "서비스 피드백",
    "컨텐츠 피드백",
    "team.maeilmail@gmail.com",
)


@dataclass
class ImportedQuestion:
    question_id: int
    url: str
    title: str
    category_label: str
    category_name: str
    question_type: str
    body: str
    reference_answer_title: str
    reference_answer_text: str


class MaeilMailTextParser(HTMLParser):
    BLOCK_TAGS = {
        "article",
        "div",
        "section",
        "main",
        "header",
        "footer",
        "h1",
        "h2",
        "h3",
        "h4",
        "p",
        "li",
        "ul",
        "ol",
        "br",
    }
    SKIP_TAGS = {"script", "style", "svg", "path", "noscript"}

    def __init__(self) -> None:
        super().__init__()
        self._skip_depth = 0
        self._capture_title = False
        self.page_title = ""
        self.lines: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in self.SKIP_TAGS:
            self._skip_depth += 1
            return
        if tag == "title":
            self._capture_title = True
        if tag in self.BLOCK_TAGS:
            self.lines.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in self.SKIP_TAGS and self._skip_depth > 0:
            self._skip_depth -= 1
            return
        if tag == "title":
            self._capture_title = False
        if tag in self.BLOCK_TAGS:
            self.lines.append("\n")

    def handle_data(self, data: str) -> None:
        if self._skip_depth > 0:
            return
        text = html.unescape(data).strip()
        if not text:
            return
        if self._capture_title and not self.page_title:
            self.page_title = text
        self.lines.append(text)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Import Maeil Mail question pages into the Iterview database.",
    )
    parser.add_argument("--start-id", type=int, default=1, help="First question id to fetch.")
    parser.add_argument(
        "--max-id",
        type=int,
        default=5000,
        help="Inclusive upper bound for question ids to fetch. Defaults to 5000.",
    )
    parser.add_argument(
        "--sleep-seconds",
        type=float,
        default=1.2,
        help="Delay between requests to reduce load on the source site.",
    )
    parser.add_argument("--db-host", default=os.getenv("DB_HOST", "localhost"))
    parser.add_argument("--db-port", default=os.getenv("DB_PORT", "5432"))
    parser.add_argument("--db-name", default=os.getenv("DB_NAME", "iterview"))
    parser.add_argument("--db-user", default=os.getenv("DB_USERNAME", os.getenv("DB_USER", "iterview")))
    parser.add_argument("--db-password", default=os.getenv("DB_PASSWORD", "iterview"))
    parser.add_argument(
        "--psql-bin",
        default=os.getenv("PSQL_BIN", "psql"),
        help="psql executable name or absolute path.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the generated SQL instead of executing it with psql.",
    )
    return parser


def fetch_question_page(question_id: int) -> str:
    url = BASE_URL.format(question_id=question_id)
    request = Request(url, headers=DEFAULT_HEADERS)
    with urlopen(request, timeout=20) as response:
        if response.status != 200:
            raise HTTPError(url, response.status, f"Unexpected status: {response.status}", response.headers, None)
        return response.read().decode("utf-8")


def normalize_lines(raw_lines: Iterable[str]) -> list[str]:
    joined = "\n".join(raw_lines)
    lines = []
    for raw in joined.splitlines():
        line = " ".join(raw.split()).strip()
        if not line:
            continue
        if any(marker in line for marker in FOOTER_MARKERS):
            break
        lines.append(line)
    return dedupe_consecutive(lines)


def dedupe_consecutive(lines: list[str]) -> list[str]:
    deduped: list[str] = []
    for line in lines:
        if deduped and deduped[-1] == line:
            continue
        deduped.append(line)
    return deduped


def parse_question(question_id: int, html_text: str) -> ImportedQuestion:
    parser = MaeilMailTextParser()
    parser.feed(html_text)
    lines = normalize_lines(parser.lines)
    if not lines:
        raise ValueError("No readable text found on page")

    title = extract_title(lines, parser.page_title)
    category_label = extract_category_label(lines)
    category_name = map_category_name(category_label)
    question_type = "behavioral" if category_name in {"Behavioral", "Leadership"} else "technical"
    reference_lines = extract_reference_lines(lines, title, category_label)
    if not reference_lines:
        raise ValueError("No reference answer content found on page")

    body = title
    reference_answer_title = "Maeil Mail imported answer"
    reference_answer_text = "\n\n".join(reference_lines).strip()

    return ImportedQuestion(
        question_id=question_id,
        url=BASE_URL.format(question_id=question_id),
        title=title,
        category_label=category_label,
        category_name=category_name,
        question_type=question_type,
        body=body,
        reference_answer_title=reference_answer_title,
        reference_answer_text=reference_answer_text,
    )


def extract_title(lines: list[str], page_title: str) -> str:
    candidate = lines[0]
    if candidate.startswith("매일메일 - "):
        candidate = candidate.removeprefix("매일메일 - ").strip()
    if candidate:
        return candidate
    if page_title:
        return page_title.removeprefix("매일메일 - ").strip()
    raise ValueError("Could not determine question title")


def extract_category_label(lines: list[str]) -> str:
    for line in lines[1:5]:
        if "관련된 질문" in line:
            return line
    return "백엔드 와 관련된 질문이에요."


def map_category_name(category_label: str) -> str:
    label = category_label.replace(" ", "")
    mappings = {
        "백엔드와관련된질문이에요.": "Backend Engineering",
        "데이터베이스와관련된질문이에요.": "Database",
        "자료구조와관련된질문이에요.": "Data Structures",
        "테스트와관련된질문이에요.": "Testing",
        "아키텍처와관련된질문이에요.": "Architecture",
        "컴퓨터사이언스와관련된질문이에요.": "Computer Science",
        "CS와관련된질문이에요.": "Computer Science",
        "시스템디자인과관련된질문이에요.": "System Design",
        "행동과관련된질문이에요.": "Behavioral",
        "리더십과관련된질문이에요.": "Leadership",
    }
    return mappings.get(label, "Backend Engineering")


def extract_reference_lines(lines: list[str], title: str, category_label: str) -> list[str]:
    filtered: list[str] = []
    started = False
    for line in lines:
        if line == title or line == category_label:
            continue
        if not started and line.startswith("##"):
            started = True
        if not started:
            if line and line != title:
                started = True
            else:
                continue
        cleaned = line.lstrip("#").strip()
        if not cleaned or cleaned == "충분히 고민해보신 다음에 펼쳐보세요!":
            continue
        filtered.append(cleaned)
    return filtered


def sql_literal(value: str | None) -> str:
    if value is None:
        return "NULL"
    delimiter = "$sql$"
    while delimiter in value:
        delimiter = f"${delimiter.strip('$')}_x$"
    return f"{delimiter}{value}{delimiter}"


def build_question_sql(question: ImportedQuestion) -> str:
    title = sql_literal(question.title)
    body = sql_literal(question.body)
    category_name = sql_literal(question.category_name)
    question_type = sql_literal(question.question_type)
    page_url = sql_literal(question.url)
    reference_title = sql_literal(question.reference_answer_title)
    reference_answer = sql_literal(question.reference_answer_text)
    learning_material_title = sql_literal(f"Maeil Mail: {question.title}")
    learning_material_description = sql_literal("Imported source page from Maeil Mail.")

    return textwrap.dedent(
        f"""
        INSERT INTO categories (name, parent_id, created_at)
        VALUES ({category_name}, NULL, now())
        ON CONFLICT (name) DO NOTHING;

        WITH target_category AS (
            SELECT id
            FROM categories
            WHERE name = {category_name}
        ),
        inserted_question AS (
            INSERT INTO questions (
                author_user_id,
                category_id,
                title,
                body,
                question_type,
                difficulty_level,
                source_type,
                quality_status,
                visibility,
                expected_answer_seconds,
                is_active,
                created_at,
                updated_at
            )
            SELECT
                NULL,
                tc.id,
                {title},
                {body},
                {question_type},
                'MEDIUM',
                'external_import',
                'approved',
                'public',
                180,
                TRUE,
                now(),
                now()
            FROM target_category tc
            WHERE NOT EXISTS (
                SELECT 1
                FROM questions q
                WHERE q.title = {title}
                  AND q.source_type = 'external_import'
            )
            RETURNING id
        ),
        target_question AS (
            SELECT id FROM inserted_question
            UNION ALL
            SELECT q.id
            FROM questions q
            WHERE q.title = {title}
              AND q.source_type = 'external_import'
            LIMIT 1
        ),
        inserted_material AS (
            INSERT INTO learning_materials (
                title,
                material_type,
                content_text,
                content_url,
                source_name,
                description,
                difficulty_level,
                estimated_minutes,
                is_official,
                display_order_hint,
                created_at,
                updated_at
            )
            SELECT
                {learning_material_title},
                'article',
                NULL,
                {page_url},
                'Maeil Mail',
                {learning_material_description},
                'intermediate',
                8,
                FALSE,
                1,
                now(),
                now()
            WHERE NOT EXISTS (
                SELECT 1
                FROM learning_materials lm
                WHERE lm.title = {learning_material_title}
            )
            RETURNING id
        ),
        target_material AS (
            SELECT id FROM inserted_material
            UNION ALL
            SELECT lm.id
            FROM learning_materials lm
            WHERE lm.title = {learning_material_title}
            LIMIT 1
        )
        INSERT INTO question_reference_answers (
            question_id,
            title,
            answer_text,
            answer_format,
            source_type,
            is_official,
            display_order,
            created_at,
            updated_at
        )
        SELECT
            tq.id,
            {reference_title},
            {reference_answer},
            'full_answer',
            'external_import',
            FALSE,
            1,
            now(),
            now()
        FROM target_question tq
        WHERE NOT EXISTS (
            SELECT 1
            FROM question_reference_answers qra
            WHERE qra.question_id = tq.id
              AND qra.title = {reference_title}
        );

        WITH target_question AS (
            SELECT q.id
            FROM questions q
            WHERE q.title = {title}
              AND q.source_type = 'external_import'
            LIMIT 1
        ),
        target_material AS (
            SELECT lm.id
            FROM learning_materials lm
            WHERE lm.title = {learning_material_title}
            LIMIT 1
        )
        INSERT INTO question_learning_materials (
            question_id,
            learning_material_id,
            relevance_score,
            display_order,
            relationship_type,
            label_override,
            created_at
        )
        SELECT
            tq.id,
            tm.id,
            1.00,
            1,
            'reference_answer_support',
            'Original Maeil Mail page',
            now()
        FROM target_question tq
        CROSS JOIN target_material tm
        WHERE NOT EXISTS (
            SELECT 1
            FROM question_learning_materials qlm
            WHERE qlm.question_id = tq.id
              AND qlm.learning_material_id = tm.id
        );
        """
    ).strip()


def execute_sql(sql: str, args: argparse.Namespace) -> None:
    if args.dry_run:
        print(sql)
        return

    psql_bin = resolve_psql_bin(args.psql_bin)
    env = os.environ.copy()
    env["PGPASSWORD"] = args.db_password

    with tempfile.NamedTemporaryFile("w", suffix=".sql", encoding="utf-8", delete=False) as handle:
        handle.write("BEGIN;\n")
        handle.write(sql)
        handle.write("\nCOMMIT;\n")
        temp_path = handle.name

    try:
        subprocess.run(
            [
                psql_bin,
                "-v",
                "ON_ERROR_STOP=1",
                "-h",
                str(args.db_host),
                "-p",
                str(args.db_port),
                "-U",
                str(args.db_user),
                "-d",
                str(args.db_name),
                "-f",
                temp_path,
            ],
            check=True,
            env=env,
        )
    finally:
        try:
            os.remove(temp_path)
        except FileNotFoundError:
            pass


def resolve_psql_bin(configured_value: str) -> str:
    if os.path.isabs(configured_value):
        if os.path.exists(configured_value):
            return configured_value
        raise FileNotFoundError(
            f"Configured psql binary does not exist: {configured_value}",
        )

    resolved = shutil.which(configured_value)
    if resolved:
        return resolved

    common_candidates = [
        "/opt/homebrew/bin/psql",
        "/usr/local/bin/psql",
        "/Applications/Postgres.app/Contents/Versions/latest/bin/psql",
    ]
    for candidate in common_candidates:
        if os.path.exists(candidate):
            return candidate

    raise FileNotFoundError(
        "psql executable not found. Install PostgreSQL client tools or rerun with "
        "--psql-bin /absolute/path/to/psql",
    )


def question_already_imported(question: ImportedQuestion, args: argparse.Namespace) -> bool:
    title_literal = sql_literal(question.title)
    sql = textwrap.dedent(
        f"""
        SELECT EXISTS (
            SELECT 1
            FROM questions
            WHERE title = {title_literal}
              AND source_type = 'external_import'
        );
        """
    ).strip()

    if args.dry_run:
        return False

    psql_bin = resolve_psql_bin(args.psql_bin)
    env = os.environ.copy()
    env["PGPASSWORD"] = args.db_password
    result = subprocess.run(
        [
            psql_bin,
            "-t",
            "-A",
            "-h",
            str(args.db_host),
            "-p",
            str(args.db_port),
            "-U",
            str(args.db_user),
            "-d",
            str(args.db_name),
            "-c",
            sql,
        ],
        check=True,
        capture_output=True,
        text=True,
        env=env,
    )
    return result.stdout.strip().lower() == "t"


def main() -> int:
    args = build_parser().parse_args()

    imported = 0
    skipped_existing = 0
    failed = 0
    current_id = args.start_id
    while True:
        if current_id > args.max_id:
            break

        url = BASE_URL.format(question_id=current_id)
        try:
            html_text = fetch_question_page(current_id)
            question = parse_question(current_id, html_text)
        except (HTTPError, URLError, TimeoutError, ValueError) as exc:
            failed += 1
            print(f"Skipping question {current_id}: {exc}", file=sys.stderr)
            current_id += 1
            if args.sleep_seconds > 0:
                time.sleep(args.sleep_seconds)
            continue
        except FileNotFoundError as exc:
            print(str(exc), file=sys.stderr)
            return 1

        try:
            if question_already_imported(question, args):
                skipped_existing += 1
                print(f"Skipping question {current_id}: already imported ({question.title})")
                current_id += 1
                if args.sleep_seconds > 0:
                    time.sleep(args.sleep_seconds)
                continue

            sql = build_question_sql(question)
            execute_sql(sql, args)
        except subprocess.CalledProcessError as exc:
            failed += 1
            stderr = exc.stderr.strip() if exc.stderr else str(exc)
            print(f"Skipping question {current_id}: database command failed: {stderr}", file=sys.stderr)
            current_id += 1
            if args.sleep_seconds > 0:
                time.sleep(args.sleep_seconds)
            continue
        except FileNotFoundError as exc:
            print(str(exc), file=sys.stderr)
            return 1
        imported += 1
        print(f"Imported question {current_id}: {question.title}")

        current_id += 1
        if args.sleep_seconds > 0:
            time.sleep(args.sleep_seconds)

    print(
        f"Completed import scan through question {args.max_id}. "
        f"imported={imported} skipped_existing={skipped_existing} failed={failed}",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
