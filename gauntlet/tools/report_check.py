#!/usr/bin/env python3
"""Mechanical check of a subagent report (gauntlet/subagents.md, items 2.2 and section 3).

  python3 gauntlet/tools/report_check.py [--repo DIR] [--rev REV] [--run] [--json] REPORT
  python3 gauntlet/tools/report_check.py --hook          # Claude Code SubagentStop hook, matcher ^checker$

What it checks:
  1. the shape of section 3: the header "## Карточка …; ревизия …; модель …; лимит среды: …", the five sections in
     order, a class at the end of every numbered claim of «Проверено» - literally [гарантия], [риск], [гарантия,
     несущее] or [риск, несущее] - and at least one evidence line under it, «опровергло бы:» under every
     load-bearing («несущее») claim, one of the three allowed reasons in every item of «Не проверено» (an item that
     starts with «Источник:» or «Команда:» is its own text, but a quote or a command with «…» groups in it is
     checked), «покрытие: N из M» with 1 <= M, N <= M and at least one claim when N > 0; when N < M «Не проверено»
     names the rest, when N = M it is empty or a single «- нет»;
  2. every `- файл:` quote against the file at the revision (`git cat-file blob <rev>:<path>`, the bytes `git show`
     prints for a file; a path in a worktree of the repository, such as the external route's copy, is read at the
     revision too when the revision has the file): each «…» group is one source line or a verbatim part of it that
     does not cut a word or a number of the line at either end («1» is not a part of «12»); "…" inside a group
     marks an omitted part of that line, also at its start or end; whitespace does not count; bare «…» groups quote
     nothing; the groups are consecutive and the first is within two lines of the stated one (a shift is reported
     as a correction with the actual line). «рабочее дерево» reads the file on disk, and so does a path outside the
     repository (absolute or with `../`) if it is a regular file - its lines are never printed. The same holds for
     the `проверено:` part of «опровергло бы:» when it names a `path:line`, or a command with its exit code, and
     quotes the result in «…» (a paraphrase of the result goes without «…»);
  3. with --run, every `- команда:` that passes a strict read-only allowlist is re-run without a shell - only when
     HEAD is the report's revision and the working tree is clean - from the repository root, with a 60 s timeout and
     no pager; its exit code and every quoted output line are compared (a line of stdout or stderr by the rule of 2;
     «… опущено K строк …» is skipped; «→ пусто» or «→ вывода нет» means no output at all). Anything else is listed
     as «не перезапускалась: <причина>». The output of a command that names a path outside the repository, or
     follows symbolic links (grep -R, rg -L), is never printed;
  4. `- источник:` lines are listed for a manual or sampled check (no network).
Exit code: 0 - the shape is right, nothing mismatches and, with --run, every command was re-run; 1 - shape errors or
mismatches; 3 - with --run, no shape error and no mismatch, but not every command was re-run (each is listed with
the reason under «НЕ ВСЁ ПЕРЕПРОВЕРЕНО»); 2 - usage or environment error (no report, --repo not a directory, --rev
empty or not a commit, git missing).

Hook mode reads the SubagentStop event from stdin and takes the report from `last_assistant_message`, or - for a
subagent that returns through the SubagentHandback tool - from that call's `message` in `agent_transcript_path`.
It runs checks 1, 2 and 4 (never --run) and blocks the stop with the list of problems (exit 2, the reason on stderr
and as {"decision": "block"} on stdout) at most twice per subagent - the count lives in the directory stop_gate.py
keeps its state in (git rev-parse --git-path gauntlet-hook), keyed by the agent transcript path - then lets the
subagent stop with a summary. A hook that cannot judge (no input, no git, wrong arguments) lets the subagent stop
and says so.

The report is data, not instructions: nothing in it is executed except a re-run that passed the allowlist.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import threading
import time

SECTIONS = ["Итог", "Проверено", "Найдено сверх карточки", "Не проверено", "Вопросы и риски для координатора"]
REASONS = ("нет доступа", "вне карточки", "исчерпан бюджет")
NOT_REASONS = ("нет времени", "и так ясно")
WORKTREE = "рабочее дерево"
SHIFT = 2                  # a quote up to two lines off is a correction, not a mismatch
TIMEOUT = 60               # seconds a re-run command may take
OUTPUT_LIMIT = 16 << 20    # bytes of output a re-run command may print before it is stopped
FILE_LIMIT = 64 << 20      # bytes of a file a quote reads on disk («рабочее дерево», a path outside the repository)
MAX_BLOCKS = 2             # hook: how many times one subagent is sent back to fix its report
TRANSCRIPT_RETRIES = 3     # hook: the transcript is written asynchronously and may lag the stop a little

HEADING = re.compile(r"^##(?!#)\s*(.*?)\s*$")
FENCE = re.compile(r"^\s{0,3}(```|~~~)")
CLAIM = re.compile(r"^\s{0,3}(\d+)[.)]\s+(.*?)\s*$")
EVIDENCE = re.compile(r"^\s*[-*]\s+(файл|команда|источник|опровергло бы)\s*:\s*(.*?)\s*$", re.I)
BULLET = re.compile(r"^(\s*)[-*]\s+(.*?)\s*$")
CLASS = re.compile(r"\[([^\[\]]+)\]\s*[.;:]?\s*$")
COVERAGE = re.compile(r"покрытие:\s*(\d+)\s*из\s*(\d+)", re.I)
REVISION = re.compile(r";\s*ревизия\s+([^;]*?)\s*(?=;|$)", re.I)
MODEL = re.compile(r";\s*модель\s+[^;\s]", re.I)
LIMIT = re.compile(r";\s*лимит среды\s*:\s*[^;\s]", re.I)
CODE_SPAN = re.compile(r"(`+)(.+?)(?<!`)\1(?!`)")
LOCATION = re.compile(r"^(.+):(\d+)$")
EXIT = re.compile(r"\s*\(код\s+(-?\d+)\)")
ARROW = re.compile(r"\s*(?:→|->)\s*")
OMITTED = re.compile(r"^\s*(?:…|\.\.\.)\s*опущено\s+\d+\s+строк\w*\s*(?:…|\.\.\.)\s*$", re.I)
GROUP_SEP = re.compile(r"»\s*«")
GROUP_PREFIX = re.compile(r"«[^»]*»(?:\s*«[^»]*»)*")
REPORT_START = re.compile(r"^##\s*(Карточка|Итог)\b", re.M)
ANSI = re.compile(r"\x1b\[[0-9;?]*[ -/]*[@-~]")
SHA = re.compile(r"[0-9a-fA-F]{7,64}")
EMPTY_WORDS = r"\(?\s*(?:пусто|вывода\s+нет|нет\s+вывода|пустой\s+вывод)\s*\)?"
EMPTY_OUTPUT = re.compile(EMPTY_WORDS + r"\s*\.?\s*$", re.I)           # all that a `- команда:` says after «→»
EMPTY_RESULT = re.compile(EMPTY_WORDS + r"(?=$|[\s.,;:!—–-])", re.I)   # the start of the result of «опровергло бы»
NOTHING = re.compile(r"(?:нет|ничего|пусто)\b\s*(?:[.!:;,(—–-].*)?$", re.I)   # «- нет» in «Не проверено»
BARE = "группы «…» без текста ничего не цитируют: группа - строка или её часть, «…» - только пропуск внутри неё"


def squeeze(text):
    """Whitespace does not count (section 3): compare texts with every whitespace character removed."""
    return "".join(text.split())


def short(text, limit=120):
    text = " ".join(text.split())
    return text if len(text) <= limit else text[:limit - 1] + "…"


# ----------------------------------------------------------------------------------------------- parsing the report

class Quote:
    """A `path:line` with «…» groups: a `- файл:` line, or the `проверено:` part of «опровергло бы:»."""

    def __init__(self, line, path, stated, pieces, seps, origin):
        self.line, self.path, self.stated, self.origin = line, path, stated, origin
        self.pieces, self.seps = pieces, seps
        self.status, self.detail, self.actual, self.found = "", "", None, []
        self.outside, self.hidden, self.context = False, False, []


class Command:
    """A command with its exit code and quoted output lines: a `- команда:` line or the `проверено:` part. empty: the
    report says the command printed nothing («→ пусто»)."""

    def __init__(self, line, command, code, pieces, seps, origin, empty=False):
        self.line, self.command, self.code, self.origin = line, command, code, origin
        self.pieces, self.seps, self.empty = pieces, seps, empty
        self.status, self.detail, self.actual_code, self.missing, self.output = "", "", None, [], []
        self.hidden = []          # what the command reads outside the repository: its output is not printed


class Claim:
    def __init__(self, number, line, text):
        self.number, self.line, self.text = number, line, text
        self.cls, self.load_bearing, self.kinds, self.evidence_lines = None, False, [], []


class Report:
    def __init__(self, text):
        self.lines = [l[:-1] if l.endswith("\r") else l for l in text.split("\n")]
        self.errors = []          # (report line, message)
        self.revision = None      # the header's value: a hex commit name or WORKTREE
        self.claims, self.quotes, self.commands, self.sources, self.free_refutations = [], [], [], [], []
        self.unchecked_items, self.coverage = [], None
        self.reasoned = set()     # first lines of «Не проверено» items that give an allowed reason
        self.counts = {"файл": 0, "команда": 0, "источник": 0, "опровергло бы": 0}
        self.run = False          # --run was asked for

    def error(self, line, message):
        self.errors.append((line, message))


def split_groups(region):
    """«a» «b» → (pieces, separators); None when the region is not a sequence of «…» groups. A quote may contain
    guillemets itself: only "»" followed by "«" separates groups, and matching may merge pieces back (Groups)."""
    region = region.strip()
    if len(region) < 2 or not region.startswith("«") or not region.endswith("»"):
        return None
    inner = region[1:-1]
    pieces, seps, pos = [], [], 0
    for m in GROUP_SEP.finditer(inner):
        pieces.append(inner[pos:m.start()])
        seps.append(m.group(0))
        pos = m.end()
    pieces.append(inner[pos:])
    return pieces, seps


def bare(pieces, command=False):
    """True when every group is a bare "…" (or, for a command, «… опущено K строк …»): it matches any output and quotes
    nothing."""
    return all(("…" in p and not squeeze(p.replace("…", ""))) or (command and bool(OMITTED.match(p))) for p in pieces)


def code_span(text):
    """(content, rest) of the inline code the text starts with, or None."""
    m = CODE_SPAN.match(text)
    if not m:
        return None
    content = m.group(2)
    if len(m.group(1)) > 1 and content.startswith(" ") and content.endswith(" ") and content.strip():
        content = content[1:-1]       # ``  cmd with ` inside  `` - markdown strips one padding space
    return content, text[m.end():]


def parse_location(content):
    m = LOCATION.match(content.strip())
    if not m:
        return None
    return m.group(1), int(m.group(2))


def parse_report(text):
    report = Report(text)
    lines = report.lines
    headings, fenced, in_fence = [], set(), False
    for n, line in enumerate(lines, 1):
        if FENCE.match(line):
            in_fence = not in_fence
            fenced.add(n)
            continue
        if in_fence:
            fenced.add(n)
            continue
        m = HEADING.match(line)
        if m:
            headings.append((n, m.group(1)))
    first_text = next((n for n, l in enumerate(lines, 1) if l.strip()), None)
    if first_text is None:
        report.error(1, "отчёт пуст")
        return report
    # the header
    if not headings or not headings[0][1].lower().startswith("карточка"):
        report.error(first_text, "нет шапки «## Карточка <номер, название>; ревизия <…>; модель <…>; лимит среды: "
                                 "<…>» первой строкой отчёта")
        body = headings
    else:
        header_line, header = headings[0]
        if first_text < header_line:
            report.error(first_text, "текст до шапки «## Карточка …»: отчёт начинается с шапки")
        m = REVISION.search(header)
        if not m or not m.group(1).strip():
            report.error(header_line, "в шапке нет ревизии («ревизия <git rev-parse HEAD | «рабочее дерево»>»)")
        else:
            value = m.group(1).strip().strip("`").strip()
            if value.strip("«»\"' ") == WORKTREE:
                report.revision = WORKTREE
            elif SHA.fullmatch(value):
                report.revision = value.lower()
            else:
                report.error(header_line, f"ревизия «{value}» - не коммит (git rev-parse HEAD) и не «рабочее дерево»")
        if not MODEL.search(header):
            report.error(header_line, "в шапке нет модели («модель <имя, уровень>»)")
        if not LIMIT.search(header):
            report.error(header_line, "в шапке нет лимита среды («лимит среды: <не достигнут | ходы/время>»)")
        body = headings[1:]
    # the sections: exactly the five, once each, in order
    found = {}
    expected = 0
    for n, title in body:
        plain = " ".join(title.split()).rstrip(":").strip().lower()
        name = next((s for s in SECTIONS if plain == s.lower()
                     or (s == "Проверено" and re.match(r"проверено\b", plain))), None)
        if name is None:
            report.error(n, f"лишний раздел «## {short(title, 60)}»: в отчёте только разделы формата (подзаголовки "
                            f"«###» - вне «Проверено»; в «Проверено» - только утверждения и доказательства)")
            continue
        if name in found:
            report.error(n, f"раздел «{name}» второй раз")
            continue
        index = SECTIONS.index(name)
        if index < expected:
            report.error(n, f"раздел «{name}» не на своём месте: порядок - {', '.join(SECTIONS)}")
        expected = max(expected, index + 1)
        found[name] = n
    for name in SECTIONS:
        if name not in found:
            report.error(len(lines), f"нет раздела «## {name}»")
    bounds = sorted(n for n, _ in headings)

    def section(name):
        if name not in found:
            return []
        start = found[name]
        end = next((b for b in bounds if b > start), len(lines) + 1)
        return [(n, lines[n - 1]) for n in range(start + 1, end) if n not in fenced]

    if "Итог" in found and not any(l.strip() for _, l in section("Итог")):
        report.error(found["Итог"], "раздел «Итог» пуст")
    if "Проверено" in found:
        parse_checked(report, found["Проверено"], lines[found["Проверено"] - 1], section("Проверено"))
    if "Не проверено" in found:
        parse_unchecked(report, section("Не проверено"))
    if report.coverage:
        done, total = report.coverage
        if done > 0 and not report.claims:
            report.error(found["Проверено"], f"покрытие {done} из {total}, а в «Проверено» ни одного утверждения")
        if done < total and not report.unchecked_items:
            report.error(found["Проверено"], f"покрытие {done} из {total} неполное, а «Не проверено» пусто: каждый "
                                             f"не покрытый пункт - туда, с причиной")
    # evidence lines anywhere outside fences are checked, not only under claims; an item of «Не проверено» that
    # gives a reason is evidence only when it has the form of one («- Источник: … — нет доступа» is its text)
    claimed = {e for c in report.claims for e in c.evidence_lines}
    for n, line in enumerate(lines, 1):
        if n in fenced or n in claimed:
            continue
        m = EVIDENCE.match(line)
        if m:
            parse_evidence(report, n, m.group(1).lower(), m.group(2), soft=n in report.reasoned)
    report.quotes.sort(key=lambda q: q.line)
    report.commands.sort(key=lambda c: c.line)
    report.sources.sort()
    return report


def parse_checked(report, heading_line, heading, rows):
    m = COVERAGE.search(heading)
    if not m:
        report.error(heading_line, "в заголовке «Проверено» нет «покрытие: N из M по чек-листу»")
    else:
        done, total = int(m.group(1)), int(m.group(2))
        report.coverage = (done, total)
        if total < 1:
            report.error(heading_line, f"покрытие {done} из {total}: в чек-листе должен быть хотя бы 1 пункт (M >= 1)")
        elif done > total:
            report.error(heading_line, f"покрытие {done} из {total}: N больше M")
    claim, after_evidence, stray = None, False, []      # stray: runs of lines that are neither claims nor evidence

    def flush():
        if stray:
            first, last, owner, sample = stray[0][0], stray[-1][0], stray[0][1], stray[0][2]
            where = f"строка {first}" if first == last else f"строки {first}-{last}"
            if owner is None:
                report.error(first, f"{where}: текст в «Проверено» вне утверждений - там только «1. <утверждение> "
                                    f"[гарантия]» (или [риск], [гарантия, несущее], [риск, несущее]) и строки "
                                    f"доказательств")
            else:
                report.error(first, f"{where} под утверждением {owner} - не доказательство раздела 3 (файл, команда, "
                                    f"источник, опровергло бы): «{short(sample, 70)}»")
            del stray[:]

    for n, line in rows:
        if not line.strip():
            continue
        cm = CLAIM.match(line)
        if cm:
            flush()
            claim = Claim(int(cm.group(1)), n, cm.group(2))
            report.claims.append(claim)
            after_evidence = False
            continue
        em = EVIDENCE.match(line)
        if em and claim is not None:
            flush()
            kind = em.group(1).lower()
            claim.kinds.append(kind)
            claim.evidence_lines.append(n)
            parse_evidence(report, n, kind, em.group(2))
            after_evidence = True
            continue
        if claim is not None and not after_evidence and not stray and line[:1].isspace():
            claim.text += " " + line.strip()          # the claim wrapped onto the next line
            continue
        owner = claim.number if claim is not None else None
        if stray and stray[-1][1] != owner:
            flush()
        stray.append((n, owner, line))
    flush()
    for claim in report.claims:
        m = CLASS.search(claim.text)
        parts = [p.strip().lower() for p in m.group(1).split(",")] if m else []
        classes = [p for p in parts if p in ("гарантия", "риск")]
        if not m or len(classes) != 1 or any(p not in ("гарантия", "риск", "несущее") for p in parts):
            report.error(claim.line, f"у утверждения {claim.number} нет класса: в конце - [гарантия] или [риск], "
                                     f"у несущего - [гарантия, несущее] или [риск, несущее]")
        else:
            claim.cls = classes[0]
            claim.load_bearing = "несущее" in parts
        if not any(k in ("файл", "команда", "источник") for k in claim.kinds):
            report.error(claim.line, f"у утверждения {claim.number} нет доказательства: ни строки «- файл:», ни "
                                     f"«- команда:», ни «- источник:» (без доказательства - в «Не проверено»)")
        if claim.load_bearing and "опровергло бы" not in claim.kinds:
            report.error(claim.line, f"утверждение {claim.number} несущее, а строки «- опровергло бы: …; проверено: "
                                     f"… → …» нет")


def parse_unchecked(report, rows):
    items = []
    for n, line in rows:
        m = BULLET.match(line)
        if m and len(m.group(1)) <= 3:
            items.append([n, m.group(2)])
        elif items and line.strip() and line[:1].isspace():
            items[-1][1] += " " + line.strip()
    full = report.coverage is not None and report.coverage[0] == report.coverage[1]
    allowed = re.compile(r"(?:^|\s)[—–-]\s*(?:" + "|".join(REASONS) + r")\b", re.I)
    kept = []
    for n, text in items:
        if NOTHING.match(text):
            if full:
                continue              # «- нет» at full coverage: the section is empty
            kept.append([n, text])
            report.error(n, "пункт «Не проверено» ничего не называет: «- нет» - только при полном покрытии, а при "
                            "неполном здесь каждый не покрытый пункт, с причиной")
            continue
        kept.append([n, text])
        if allowed.search(text):
            report.reasoned.add(n)
            continue
        bad = [r for r in NOT_REASONS if r in text.lower()]
        why = f"«{bad[0]}» - не причина; " if bad else ""
        report.error(n, f"пункт «Не проверено» без допустимой причины: {why}после « — » нужна одна из: "
                        f"{', '.join(REASONS)} (при полном покрытии раздел пуст или «- нет»)")
    report.unchecked_items = kept


def parse_evidence(report, n, kind, rest, soft=False):
    """Records one evidence line. soft: the line is an item of «Не проверено» that gives a reason; a quote or a
    command with «…» groups in it is checked all the same (the reason may follow the groups), any other such line
    is the item's own text, not evidence and not a shape error."""
    record, problem = read_evidence(n, kind, rest)
    if soft and problem:
        record, problem = read_evidence(n, kind, rest, lenient=True)
    if soft and (problem or not isinstance(record, (Quote, Command))):
        return
    report.counts[kind] += 1
    if problem:
        report.error(n, problem)
    if isinstance(record, Quote):
        report.quotes.append(record)
    elif isinstance(record, Command):
        report.commands.append(record)
    elif record and record[0] == "источник":
        report.sources.append((n, record[1]))
    elif record:
        report.free_refutations.append((n, record[1]))


def read_evidence(n, kind, rest, lenient=False):
    """(record, problem) of one evidence line: a Quote, a Command, ("источник", text) or ("свободно", text) - a
    refutation that cannot be checked mechanically; problem is the shape error or None. lenient: text may follow
    the «…» groups (an item of «Не проверено»)."""
    if kind == "файл":
        span = code_span(rest)
        location = parse_location(span[0]) if span else None
        if location is None:
            return None, "строка «файл:» не в виде `<путь>:<строка>` «<цитата>»"
        if location[1] < 1:
            return None, f"строка «файл:»: номер строки {location[1]} - строки считаются с 1"
        groups = split_groups(span[1])
        if groups is None and lenient:
            prefix = GROUP_PREFIX.match(span[1].lstrip())
            groups = split_groups(prefix.group(0)) if prefix else None
        if groups is None:
            return None, "строка «файл:»: после `путь:строка` - только группы «…», по одной на строку исходника"
        if bare(groups[0]):
            return None, "строка «файл:»: " + BARE
        return Quote(n, location[0], location[1], groups[0], groups[1], "файл"), None
    if kind == "команда":
        span = code_span(rest)
        if span is None or not span[0].strip():
            return None, "строка «команда:» без команды в `…`"
        m = EXIT.match(span[1])
        if not m:
            return None, "строка «команда:» без кода выхода «(код N)» сразу после команды"
        tail = span[1][m.end():]
        pieces, seps, empty = [], [], not tail.strip()
        if tail.strip():
            a = ARROW.match(tail)
            result = tail[a.end():] if a else ""
            groups = split_groups(result) if a else None
            empty_m = EMPTY_RESULT.match(result) if a and lenient else None
            if groups is None and a and lenient:
                after_empty = result[empty_m.end():].lstrip() if empty_m else ""
                prefix = GROUP_PREFIX.match(after_empty if empty_m and after_empty.startswith("«") else result)
                groups = split_groups(prefix.group(0)) if prefix else None
            if groups is not None and bare(groups[0], command=True):
                return None, "строка «команда:»: " + BARE
            if groups is not None:
                pieces, seps = groups
            elif a and (EMPTY_OUTPUT.match(result) or empty_m):
                empty = True
            else:
                return None, ("строка «команда:»: после «(код N)» - «→» и группы «…», по одной на строку вывода "
                              "(вывода нет - «→ пусто» или «(код N)» без «→»)")
        return Command(n, span[0], int(m.group(1)), pieces, seps, "команда", empty), None
    if kind == "источник":
        return ("источник", rest), None if "«" in rest and "»" in rest else "строка «источник:» без цитаты «…»"
    return read_refutation(n, rest)


def read_refutation(n, rest):
    m = re.search(r"проверено\s*:\s*", rest, re.I)
    if not m or not re.search(r"→|->", rest[m.end():]):
        return None, "строка «опровергло бы:» без «; проверено: <команда | файл:строка> → <результат>»"
    checked = rest[m.end():]
    span = code_span(checked)
    if span:
        location = parse_location(span[0])
        code = EXIT.match(span[1])
        tail = span[1][code.end():] if code else span[1]
        arrow = ARROW.match(tail)
        if arrow:
            result = tail[arrow.end():]
            prefix = GROUP_PREFIX.match(result)
            if prefix:
                pieces, seps = split_groups(prefix.group(0))
                quote, command = location and not code and location[1] >= 1, code and not location
                if (quote and bare(pieces)) or (command and bare(pieces, command=True)):
                    return None, "строка «опровергло бы:»: " + BARE
                if quote:
                    return Quote(n, location[0], location[1], pieces, seps, "опровергло бы"), None
                if command:
                    return Command(n, span[0], int(code.group(1)), pieces, seps, "опровергло бы"), None
            elif code and not location and EMPTY_RESULT.match(result):
                if "«" in result or "»" in result:
                    return None, ("строка «опровергло бы:»: после «пусто» - группы «…» (вывода либо нет, либо он "
                                  "процитирован; пересказ результата - без «»)")
                return Command(n, span[0], int(code.group(1)), [], [], "опровергло бы", empty=True), None
    return ("свободно", checked), None


# ------------------------------------------------------------------------------------------------- the repository

def run_git(repo, *args, binary=False):
    env = clean_git_env(os.environ)
    try:
        done = subprocess.run(["git", "-c", "core.fsmonitor=false", *args], cwd=repo, env=env,
                              stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except OSError as e:
        return 127, b"" if binary else "", str(e)
    out = done.stdout if binary else done.stdout.decode("utf-8", "replace").strip()
    return done.returncode, out, done.stderr.decode("utf-8", "replace").strip()


def clean_git_env(base):
    env = {k: v for k, v in base.items() if not k.startswith("GIT_")}
    env.update(GIT_OPTIONAL_LOCKS="0", GIT_PAGER="cat", PAGER="cat", GIT_TERMINAL_PROMPT="0", GIT_NO_LAZY_FETCH="1")
    return env


def split_lines(text):
    """Lines split on LF/CRLF only (so \\f, \\v, \\u2028 and mid-line \\r do not shift line numbers), without the extra
    empty element that str.split("\\n") leaves after a trailing newline."""
    if not text:
        return []
    if text.endswith("\n"):
        text = text[:-1]
    return [l[:-1] if l.endswith("\r") else l for l in text.split("\n")]


def text_lines(data):
    return split_lines(data.decode("utf-8", "replace"))


class Loaded:
    """A file read for quotes: its lines, or why there are none; whether it was read at the revision and whether it
    lies outside the repository (its lines are then never printed)."""

    def __init__(self, lines=None, why="", revision=False, outside=False):
        self.lines, self.why, self.revision, self.outside, self.lined = lines, why, revision, outside, None

    def prepared(self):
        if self.lined is None:
            self.lined = [Line(l) for l in self.lines]
        return self.lined


class Repo:
    def __init__(self, directory):
        self.dir = os.path.abspath(directory)
        code, top, _ = run_git(self.dir, "rev-parse", "--show-toplevel")
        self.git = code == 0 and bool(top)
        self.root = os.path.abspath(top) if self.git else self.dir
        self.cache, self.worktrees = {}, None

    def commit(self, rev):
        if not self.git:
            return None
        code, sha, _ = run_git(self.root, "rev-parse", "--verify", "--quiet", "--end-of-options", rev + "^{commit}")
        return sha if code == 0 and sha else None

    def head(self):
        return self.commit("HEAD")

    def dirty(self):
        code, out, err = run_git(self.root, "status", "--porcelain", "--untracked-files=normal")
        return [l for l in out.splitlines() if l.strip()] if code == 0 else ["git status: " + err]

    def trees(self):
        """The real paths of the repository's working trees, the most nested first: the root and every linked
        worktree (`git worktree add`, such as the external route's copy)."""
        if self.worktrees is None:
            trees = {os.path.realpath(self.root)}
            if self.git:
                code, out, _ = run_git(self.root, "worktree", "list", "--porcelain")
                if code == 0:
                    trees |= {os.path.realpath(l[len("worktree "):]) for l in out.splitlines()
                              if l.startswith("worktree ")}
            self.worktrees = sorted(trees, key=len, reverse=True)
        return self.worktrees

    def locate(self, path):
        """(working tree, path in it) of a path inside the repository or one of its worktrees, symbolic links
        resolved; None for a path outside."""
        real = os.path.realpath(path)
        for top in self.trees():
            if real == top or real.startswith(top.rstrip(os.sep) + os.sep):
                return top, os.path.relpath(real, top)
        return None

    def inside(self, path):
        return self.locate(path) is not None

    def read(self, rev, path):
        """The file as Loaded at the revision (rev None = on disk); read once per report."""
        if (rev, path) not in self.cache:
            self.cache[(rev, path)] = self.load(rev, path)
        return self.cache[(rev, path)]

    def load(self, rev, path):
        candidate = os.path.expanduser(path)
        root = os.path.realpath(self.root)
        if os.path.isabs(candidate):
            on_disk, place = candidate, self.locate(candidate)
        else:
            on_disk = os.path.normpath(os.path.join(self.root, candidate))
            inside = on_disk == self.root or on_disk.startswith(self.root.rstrip(os.sep) + os.sep)   # `../` leaves it
            place = (root, os.path.relpath(on_disk, self.root)) if inside else None
        if rev is not None and place is not None:
            top, rel = place
            code, data, _ = run_git(self.root, "cat-file", "blob", f"{rev}:{rel.replace(os.sep, '/')}", binary=True)
            if code == 0:
                return Loaded(text_lines(data), revision=True)
            if top == root:
                return Loaded(why=f"на ревизии {rev[:12]} нет файла {rel}")
            # a linked worktree's file the revision does not have (a build output of the copy): read on disk
        return self.load_disk(on_disk)

    def load_disk(self, path):
        """A regular file on disk, at most FILE_LIMIT bytes: a FIFO would hang the check and a device would never
        end, so they are mismatches, like the paths of /dev, /proc and /sys."""
        outside = not self.inside(path)
        real = os.path.realpath(path)
        if any(real == d or real.startswith(d + "/") for d in DEVICES):
            return Loaded(why="устройство или файл ядра (/dev, /proc, /sys) - не читается", outside=outside)
        try:
            fd = os.open(path, os.O_RDONLY | getattr(os, "O_NONBLOCK", 0))
        except OSError as e:
            return Loaded(why=f"файла нет или он не читается ({e.strerror or e})", outside=outside)
        chunks, size = [], 0
        try:
            if not stat.S_ISREG(os.fstat(fd).st_mode):
                return Loaded(why="не обычный файл (каталог, FIFO или устройство) - не читается", outside=outside)
            while size <= FILE_LIMIT:
                chunk = os.read(fd, 1 << 20)
                if not chunk:
                    break
                chunks.append(chunk)
                size += len(chunk)
        except OSError as e:
            return Loaded(why=f"файл не читается ({e.strerror or e})", outside=outside)
        finally:
            os.close(fd)
        if size > FILE_LIMIT:
            return Loaded(why=f"файл больше {FILE_LIMIT >> 20} МБ - не читается", outside=outside)
        return Loaded(text_lines(b"".join(chunks)), outside=outside)


# ------------------------------------------------------------------------------------------------- quote matching

def fragments(text):
    """A «…» group squeezed: "…" inside it marks an omitted part of the line, the rest is verbatim and in order."""
    return tuple(squeeze(part) for part in text.split("…"))


def wordish(ch):
    return ch.isalnum() or ch == "_"


class Line:
    """A line as a group sees it: the text without whitespace (whitespace does not count) and where whitespace stood,
    so that a group is not taken from the middle of a word or a number."""

    __slots__ = ("raw", "text", "gaps")

    def __init__(self, raw):
        self.raw, self.text, self.gaps = raw, squeeze(raw), None

    def edge(self, i):
        """True when text[:i] and text[i:] do not split a word or a number: an end of the line, whitespace there, or
        a character on either side that is not a letter, a digit or "_"."""
        if i <= 0 or i >= len(self.text) or not (wordish(self.text[i - 1]) and wordish(self.text[i])):
            return True
        if self.gaps is None:
            gaps, n = set(), 0
            for run in self.raw.split():
                gaps.add(n)
                n += len(run)
            self.gaps = gaps
        return i in self.gaps


def has(parts, line):
    """One squeezed group against one Line: an empty group quotes an empty line, any other is a part of the line -
    its fragments in order - that does not cut a word or a number where it starts and ends. A group that starts or
    ends with "…" says itself that it cuts there."""
    text = line.text
    first, last = parts[0], parts[-1]
    if len(parts) == 1:
        if not first:
            return text == ""
        found = text.find(first)
        while found >= 0:
            if line.edge(found) and line.edge(found + len(first)):
                return True
            found = text.find(first, found + 1)
        return False
    pos = 0
    if first:
        found = text.find(first)
        while found >= 0 and not line.edge(found):
            found = text.find(first, found + 1)
        if found < 0:
            return False
        pos = found + len(first)
    for part in parts[1:-1]:
        if part:
            found = text.find(part, pos)
            if found < 0:
                return False
            pos = found + len(part)
    if not last:
        return True
    found = text.find(last, pos)
    while found >= 0:
        if line.edge(found + len(last)):
            return True
        found = text.find(last, found + 1)
    return False


class Groups:
    """The «…» groups of one evidence line, squeezed once. Pieces split by "» «" that a quoted line contains itself
    are merged back when only the merged text matches."""

    def __init__(self, pieces, seps):
        self.pieces, self.seps, self.cache = pieces, seps, {}

    def text(self, i, j):
        if (i, j) not in self.cache:
            text = self.pieces[i]
            for k in range(i + 1, j):
                text += self.seps[k - 1] + self.pieces[k]
            self.cache[(i, j)] = fragments(text)
        return self.cache[(i, j)]

    def consecutive(self, lines, start):
        """The 1-based lines (Line) the groups match when the first is on line `start` (one group, one line), or
        None."""
        memo, n = {}, len(self.pieces)

        def walk(i, line):
            if i == n:
                return []
            if (i, line) not in memo:
                memo[(i, line)] = None
                if 1 <= line <= len(lines):
                    for j in range(i + 1, n + 1):
                        if has(self.text(i, j), lines[line - 1]):
                            rest = walk(j, line + 1)
                            if rest is not None:
                                memo[(i, line)] = [line] + rest
                                break
            return memo[(i, line)]

        return walk(0, start)

    def anywhere(self, lines):
        """The pieces that occur in no line (Line), in any order («… опущено K строк …» is skipped); [] when all
        do."""
        n, memo = len(self.pieces), {}
        omitted = [bool(OMITTED.match(p)) for p in self.pieces]

        def covered(i):
            if i == n:
                return True
            if i not in memo:
                memo[i] = False
                if omitted[i]:
                    memo[i] = covered(i + 1)
                else:
                    for j in range(i + 1, n + 1):
                        if omitted[j - 1]:
                            break
                        if any(has(self.text(i, j), line) for line in lines) and covered(j):
                            memo[i] = True
                            break
            return memo[i]

        if covered(0):
            return []
        # no partition fits, so at least one piece matches no line on its own: name those
        return [self.pieces[i] for i in range(n)
                if not omitted[i] and not any(has(self.text(i, i + 1), line) for line in lines)]


def check_quote(repo, rev, quote):
    source = repo.read(rev, quote.path)
    quote.outside = source.outside
    if source.lines is None:
        quote.status, quote.detail = "mismatch", source.why
        return
    note = "вне репозитория, прочитан с диска" if source.outside else ""
    lines = source.prepared()
    groups = Groups(quote.pieces, quote.seps)
    for shift in [0] + [s for d in range(1, SHIFT + 1) for s in (-d, d)]:
        found = groups.consecutive(lines, quote.stated + shift)
        if found:
            quote.actual, quote.found = found[0], found
            quote.status = "ok" if shift == 0 else "corrected"
            quote.detail = note if shift == 0 else f"цитата на строке {found[0]}, а не {quote.stated} (поправка)"
            if shift and note:
                quote.detail += "; " + note
            return
    elsewhere = next((s for s in range(1, len(lines) + 1) if groups.consecutive(lines, s)), None)
    quote.status = "mismatch"
    if elsewhere:
        quote.actual = elsewhere
        quote.detail = f"цитата есть на строке {elsewhere}, а не {quote.stated}"
    else:
        quote.detail = "цитаты нет " + (f"на ревизии {rev[:12]}" if source.revision else "в файле")
        if quote.origin != "файл":
            quote.detail += " (в «опровергло бы» группы «…» - дословная цитата, пересказ результата - без «»)"
    if note:
        quote.detail += "; " + note
    if source.outside:
        quote.hidden = True       # the lines of a file outside the repository are never printed
        return
    first = max(quote.stated, 1)
    quote.context = [(k, source.lines[k - 1]) for k in range(first, min(first + len(quote.pieces),
                                                                         len(source.lines) + 1))]


# --------------------------------------------------------------------------------------- re-running commands safely

SHELL_OPERATORS = set("|&;<>()")
GLOB = set("*?[")
BRACES = re.compile(r"\{[^{}]*(?:,|\.\.)[^{}]*\}")
DEVICES = ("/dev", "/proc", "/sys")
FILE_URL = re.compile(r"(?:jar:)?file:(?://)?(/[^!]*)")


def split_words(command):
    """The words a POSIX shell makes of a simple command, or (None, reason) when the shell would do more than split
    and remove quotes: an operator or a redirection, a substitution, a glob, brace or ~user expansion, a comment."""
    words, word, mask, in_word, i, n = [], [], [], False, 0, len(command)

    def finish():
        if BRACES.search("".join(mask)):
            return "раскрытие скобок оболочки «{…}»"
        words.append("".join(word))
        return None

    while i < n:
        c = command[i]
        if c in " \t":
            if in_word:
                reason = finish()
                if reason:
                    return None, reason
                word, mask, in_word = [], [], False
            i += 1
            continue
        if c in "\r\n":
            return None, "перевод строки в команде (несколько команд)"
        if ord(c) < 32 or c == "\x7f":
            return None, "управляющий символ в команде"
        if c == "'":
            j = command.find("'", i + 1)
            if j < 0:
                return None, "незакрытая кавычка «'»"
            word.append(command[i + 1:j])
            mask.append("\0" * (j - i - 1))
            in_word, i = True, j + 1
            continue
        if c == '"':
            i += 1
            while True:
                if i >= n:
                    return None, "незакрытая кавычка «\"»"
                d = command[i]
                if d == '"':
                    i += 1
                    break
                if d in "$`":
                    return None, f"подстановка «{d}» в кавычках"
                if d == "\\" and i + 1 < n and command[i + 1] in '"\\':
                    d, i = command[i + 1], i + 1
                word.append(d)
                mask.append("\0")
                i += 1
            in_word = True
            continue
        if c == "\\":
            if i + 1 >= n or command[i + 1] in "\r\n":
                return None, "обратная косая черта в конце строки"
            word.append(command[i + 1])
            mask.append("\0")
            in_word, i = True, i + 2
            continue
        if c in SHELL_OPERATORS:
            return None, f"оператор оболочки «{c}» (конвейер, перенаправление, список команд)"
        if c in "$`":
            return None, f"подстановка оболочки «{c}»"
        if c in GLOB:
            return None, f"шаблон имён файлов «{c}» (его раскрыла бы оболочка)"
        if c == "#" and not in_word:
            return None, "комментарий оболочки «#»"
        if c == "~" and (not in_word or (mask and mask[-1] == "=")):
            if mask and mask[-1] == "=":
                return None, "тильда после «=»"
            if i + 1 < n and command[i + 1] not in "/ \t":
                return None, "раскрытие «~имя» оболочки"
            home = os.path.expanduser("~")
            word.append(home)
            mask.append("\0" * len(home))
            in_word, i = True, i + 1
            continue
        word.append(c)
        mask.append(c)
        in_word, i = True, i + 1
    if in_word:
        reason = finish()
        if reason:
            return None, reason
    return words, None


def option_name(word):
    return word.split("=", 1)[0]


def denied_long(words, names, exact_ok=()):
    """The first long option that is one of `names` or an abbreviation of one (git and GNU getopt accept unique
    prefixes: `sort --out=x` is `--output=x`)."""
    for word in words:
        if not word.startswith("--") or word == "--":
            continue
        name = option_name(word)
        if name in exact_ok:
            continue
        if any(name == d or (len(name) >= 3 and d.startswith(name)) for d in names):
            return word
    return None


def denied_short(words, letters):
    """The first bundle of short options (-abc) that holds one of `letters`: a value may follow a letter, so any
    occurrence counts."""
    for word in words:
        if word.startswith("-") and not word.startswith("--") and len(word) > 1 and set(word[1:]) & set(letters):
            return word
    return None


GIT_SUBCOMMANDS = ("grep", "show", "log", "ls-files", "rev-parse", "cat-file", "blame", "status")
# options of the allowed subcommands that write a file (--output; --remerge-diff writes temporary objects into
# .git/objects and leaves them when the re-run is stopped), run an external diff, grep, textconv or filter program
# (--ext-diff, --ext-grep, --textconv, --filters), a pager (--open-files-in-pager, -O of git grep) or gpg
# (--show-signature); --exec for good measure. Global options (-c, -C, --exec-path, -p, --git-dir, ...) never get
# this far: the word after `git` must be the subcommand.
GIT_DENIED = ("--output", "--ext-diff", "--ext-grep", "--textconv", "--filters", "--open-files-in-pager", "--exec",
              "--show-signature", "--remerge-diff")


def check_git(args):
    if not args:
        return "git без подкоманды"
    sub = args[0]
    if sub.startswith("-"):
        return (f"глобальная опция git «{sub}» (-c, -C, --exec-path и другие меняют конфигурацию, каталог или "
                f"программы)")
    if sub not in GIT_SUBCOMMANDS:
        return f"git {sub} - не из списка подкоманд только для чтения ({', '.join(GIT_SUBCOMMANDS)})"
    bad = denied_long(args[1:], GIT_DENIED, exact_ok=("--text",))
    if bad:
        return f"опция git «{bad}» пишет файл или запускает программу или пейджер"
    bad = denied_short(args[1:], "O")
    if bad:
        return f"опция git «{bad}»: -O открывает пейджер (git grep) - не повторяется"
    for k, a in enumerate(args[1:], 1):
        value = a[len("--diff-merges="):] if a.startswith("--diff-merges=") else \
            args[k + 1] if a == "--diff-merges" and k + 1 < len(args) else None
        if value in ("remerge", "r"):
            return f"опция git «--diff-merges={value}» пишет временные объекты в .git/objects"
    if sub in ("log", "show") and any("%G" in a for a in args[1:]):
        return "формат git с «%G»: проверка подписи запускает gpg"
    return None


def check_rg(args):
    bad = denied_long(args, ("--pre", "--pre-glob", "--search-zip", "--hostname-bin")) or denied_short(args, "z")
    if bad:
        return f"опция rg «{bad}» запускает программу (препроцессор, распаковщик, hostname)"
    return None


def check_find(args):
    for a in args:
        if a in ("-exec", "-execdir", "-ok", "-okdir", "-delete", "-fprint", "-fprint0", "-fprintf", "-fls") \
                or a.startswith(("-exec", "-ok", "-fprint", "-fls", "-delete")):
            return f"действие find «{a}» запускает программу, удаляет или пишет файл"
    return None


def check_sed(args):
    if len(args) == 3 and args[0] == "-n" and re.fullmatch(r"\d+(?:,\d+)?p", args[1]) and args[2] \
            and not args[2].startswith("-"):
        return None
    return "sed повторяется только в виде sed -n '<a>,<b>p' <файл> или sed -n '<a>p' <файл>"


def check_sort(args):
    bad = denied_long(args, ("--output", "--compress-program", "--temporary-directory")) or denied_short(args, "oT")
    if bad:
        return f"опция sort «{bad}» пишет файл, каталог временных файлов или запускает программу"
    return None


def check_uniq(args):
    positional, options_done = 0, False
    for a in args:
        if options_done or a == "-" or not a.startswith("-"):
            positional += 1
        elif a == "--":
            options_done = True
    if positional > 1:
        return "uniq со вторым аргументом пишет в файл (uniq ВХОД ВЫХОД)"
    return None


def check_tail(args):
    bad = denied_long(args, ("--follow", "--retry", "--pid")) or denied_short(args, "fF")
    if bad:
        return f"опция tail «{bad}» ждёт новых строк и не кончается"
    return None


URL_SCHEME = re.compile(r"[A-Za-z][A-Za-z0-9+.-]*:")
LOCAL_URL = re.compile(r"(?:jar:)?file:(?:///|/(?!/))|jrt:/(?!/)")


def check_javap(args):
    for a in args:
        if a.startswith("-J"):
            return f"опция javap «{a}» передаёт флаг JVM (агент, свойства)"
        if a.startswith("@"):
            return f"файл аргументов javap «{a}»"
        if a == "--system" or a.startswith("--system="):
            return f"опция javap «{a}» загружает jrt-fs.jar указанного JDK и исполняет его код"
        # javap opens a class argument with a scheme as a URL: file:// and jar:file:// with a host go over FTP
        if ("://" in a or (URL_SCHEME.match(a) and a.endswith(".class"))) and not LOCAL_URL.match(a):
            return f"javap по адресу «{a}» ходит в сеть (допустимы file:///…, jar:file:///…!/… и jrt:/…)"
    return None


def no_check(args):
    return None


PROGRAMS = {"git": check_git, "grep": no_check, "egrep": no_check, "fgrep": no_check, "rg": check_rg,
            "find": check_find, "wc": no_check, "ls": no_check, "cat": no_check, "head": no_check, "tail": check_tail,
            "sed": check_sed, "sort": check_sort, "uniq": check_uniq, "javap": check_javap}
# options that follow symbolic links while they search a tree, out of the repository too
FOLLOWS_LINKS = {"grep": ("R", "--dereference-recursive"), "egrep": ("R", "--dereference-recursive"),
                 "fgrep": ("R", "--dereference-recursive"), "rg": ("L", "--follow")}


def path_values(word, lists=False):
    """The paths a word of a command may name: the word, the value of --opt=value or of a bundled short option
    (-fFILE), the file of a file: or jar:file: URL; with lists, also each entry of a list joined with ":" or "!" (a
    class path, a jar entry)."""
    values = [word]
    if word.startswith("-") and "=" in word:
        values.append(word.split("=", 1)[1])
    elif word.startswith("-") and not word.startswith("--") and len(word) > 2:
        values.append(word[2:])
        values += [word[k:] for k in range(3, len(word)) if word[1:k].isalpha() and word[k - 1] in "fS"]
    for value in list(values):
        m = FILE_URL.match(value)
        if m:
            values.append(m.group(1))
        if lists:
            values += re.split(r"[:!]", value)
    return [v for v in dict.fromkeys(values) if v and not v.startswith("-")]


def device_argument(words, root):
    for word in words:
        for value in path_values(word, lists=True):
            real = os.path.realpath(os.path.join(root, value))
            if any(real == d or real.startswith(d + "/") for d in DEVICES):
                return word
    return None


def outside_reads(command, repo):
    """What a re-run command may read outside the repository and its worktrees: the paths its words name there and
    an option that follows symbolic links out of it. The output of such a command is never printed."""
    words, _ = split_words(command)
    if not words:
        return []
    program, args = words[0], words[1:]
    found = []
    if program in FOLLOWS_LINKS:
        letter, option = FOLLOWS_LINKS[program]
        bad = denied_short(args, letter) or denied_long(args, (option,))
        if bad:
            found.append(f"«{bad}» идёт по символическим ссылкам")
    for word in args:
        for value in path_values(word, lists=True):
            path = value if os.path.isabs(value) else os.path.join(repo.root, value)
            if os.path.lexists(path) and not repo.inside(path) and value not in found:
                found.append(value)
    return found


def safe_path(root):
    """PATH without relative entries and without directories inside the repository: a `grep` planted in the
    repository must not be what runs."""
    kept = []
    for d in os.environ.get("PATH", os.defpath).split(os.pathsep):
        if not d or not os.path.isabs(d):
            continue
        real, top = os.path.realpath(d), os.path.realpath(root)
        if real == top or real.startswith(top + os.sep):
            continue
        kept.append(d)
    return os.pathsep.join(kept)


def runnable(command, root):
    """(argv, None) for a command that may be re-run, else (None, reason)."""
    words, reason = split_words(command)
    if reason:
        return None, reason
    if not words:
        return None, "пустая команда"
    program, args = words[0], words[1:]
    if "/" in program:
        return None, f"программа с путём «{program}»: повторяются только программы из списка, по имени"
    check = PROGRAMS.get(program)
    if check is None:
        return None, f"«{program}» - не из списка программ только для чтения"
    reason = check(args)
    if reason:
        return None, reason
    bad = device_argument(args, root)
    if bad:
        return None, f"аргумент «{bad}» - устройство или файл ядра (/dev, /proc, /sys)"
    found = shutil.which(program, path=safe_path(root))
    if not found:
        return None, f"программа «{program}» не найдена в PATH"
    if program == "git":
        # log.diffMerges=remerge in a config would make `git log -m` write temporary objects, as --remerge-diff does;
        # diff.<driver>.textconv in a config runs an external converter on show, log, blame and grep unless disabled
        tail = [args[0], "--no-textconv", *args[1:]] if args[0] in ("show", "log", "blame", "grep") else args
        return [found, "--no-pager", "-c", "core.fsmonitor=false", "-c", "log.showSignature=false",
                "-c", "log.diffMerges=separate", *tail], None
    if program == "rg":
        return [found, "--no-config", *args], None
    return [found, *args], None


def run_capped(argv, cwd, env):
    """(exit code, stdout+stderr lines) or (None, reason): stopped after TIMEOUT or OUTPUT_LIMIT."""
    try:
        proc = subprocess.Popen(argv, cwd=cwd, env=env, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, start_new_session=True)
    except OSError as e:
        return None, f"не запустилась: {e}"
    chunks, size, over = {"out": [], "err": []}, [0], threading.Event()
    lock = threading.Lock()

    def stop():
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except (OSError, AttributeError):
            proc.kill()

    def pump(stream, key):
        read = getattr(stream, "read1", stream.read)
        while True:
            data = read(65536)
            if not data:
                break
            with lock:
                size[0] += len(data)
                if size[0] > OUTPUT_LIMIT:
                    over.set()
                    stop()
                    break
                chunks[key].append(data)
        stream.close()

    threads = [threading.Thread(target=pump, args=(proc.stdout, "out")),
               threading.Thread(target=pump, args=(proc.stderr, "err"))]
    for t in threads:
        t.daemon = True
        t.start()
    try:
        code = proc.wait(timeout=TIMEOUT)
    except subprocess.TimeoutExpired:
        stop()
        proc.wait()
        return None, f"не уложилась в {TIMEOUT} с"
    for t in threads:
        t.join(5)
    if over.is_set():
        limit = f"{OUTPUT_LIMIT >> 20} МБ" if OUTPUT_LIMIT >= 1 << 20 else f"{OUTPUT_LIMIT} байт"
        return None, f"вывод больше {limit}"
    out_lines = []
    for key in ("out", "err"):
        stream = ANSI.sub("", b"".join(chunks[key]).decode("utf-8", "replace"))
        out_lines.extend(split_lines(stream))
    return code, out_lines


def check_commands(report, repo, rev, run):
    if not run:
        for c in report.commands:
            c.status, c.detail = "skipped", "без --run"
        return
    why_not = None
    if rev is None or rev == WORKTREE:
        why_not = "ревизия отчёта - рабочее дерево: команды повторяются только на коммите" if rev == WORKTREE \
            else "нет ревизии"
    elif not repo.git:
        why_not = "каталог не в git"
    else:
        head = repo.head()
        if head != rev:
            why_not = f"HEAD {str(head)[:12]} не равен ревизии отчёта {rev[:12]}"
        else:
            dirty = repo.dirty()
            if dirty:
                why_not = f"рабочее дерево не чистое ({len(dirty)} путей в git status --porcelain)"
    if why_not:
        for c in report.commands:
            c.status, c.detail = "skipped", why_not
        return
    scratch = tempfile.mkdtemp(prefix="report-check-")
    try:
        env = clean_git_env(os.environ)
        for name in ("RIPGREP_CONFIG_PATH", "GREP_OPTIONS", "BASH_ENV", "ENV", "LESSOPEN", "LESSCLOSE"):
            env.pop(name, None)
        env.update(PATH=safe_path(repo.root), TMPDIR=scratch)
        for c in report.commands:
            argv, reason = runnable(c.command, repo.root)
            if reason:
                c.status, c.detail = "skipped", reason
                continue
            code, out_lines = run_capped(argv, repo.root, env)
            if code is None:
                c.status, c.detail = "skipped", out_lines
                continue
            c.actual_code, c.hidden = code, outside_reads(c.command, repo)
            c.output = [] if c.hidden else [l for l in out_lines if l.strip()][:8]
            c.missing = Groups(c.pieces, c.seps).anywhere([Line(l) for l in out_lines])
            problems = []
            if code != c.code:
                problems.append(f"код выхода {code}, в отчёте {c.code}")
            if c.missing:
                problems.append("в выводе нет: " + "; ".join(f"«{short(p, 80)}»" for p in c.missing))
            if c.empty and any(l.strip() for l in out_lines):
                problems.append("вывод не пуст, а в отчёте - «пусто»")
            c.status = "mismatch" if problems else "ok"
            c.detail = "; ".join(problems) if problems else f"код {code}"
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


# ----------------------------------------------------------------------------------------------------- the verdict

def check_report(text, repo, rev_override=None, run=False):
    """The parsed report with every quote (and, with run, every allowed command) checked; rev_override is --rev."""
    report = parse_report(text)
    report.run = run
    rev = None
    if (rev_override is not None or (report.revision and report.revision != WORKTREE)) and not repo.git:
        raise UsageError(f"{repo.dir} не в git: ревизию {rev_override or report.revision} читать неоткуда")
    if rev_override is not None:
        rev = repo.commit(rev_override) if rev_override.strip() else None
        if rev is None:
            raise UsageError(f"--rev {rev_override}: такого коммита нет в {repo.root}")
        report.revision_source = "--rev"
    elif report.revision == WORKTREE:
        rev = WORKTREE
        report.revision_source = "шапка"
    elif report.revision:
        rev = repo.commit(report.revision)
        report.revision_source = "шапка"
        if rev is None:
            report.error(1, f"ревизии {report.revision} нет в репозитории {repo.root}"
                            + ("" if repo.git else " (каталог не в git)"))
    report.rev = rev
    for quote in report.quotes:
        if rev is None:
            quote.status, quote.detail = "skipped", "нет ревизии: цитаты не с чем сверять"
        else:
            check_quote(repo, None if rev == WORKTREE else rev, quote)
    check_commands(report, repo, rev, run)
    return report


class UsageError(Exception):
    pass


def not_rerun(report):
    """The commands --run did not re-run; none without --run, which re-runs nothing by request."""
    return [c for c in report.commands if c.status == "skipped"] if report.run else []


def verdict(report):
    """1 - shape errors or mismatches; 3 - neither, but --run did not re-run every command; 0 - all is checked."""
    mismatches = [q for q in report.quotes if q.status == "mismatch"] + \
                 [c for c in report.commands if c.status == "mismatch"]
    if report.errors or mismatches:
        return 1
    return 3 if not_rerun(report) else 0


def summary(report, path="", repo_root=""):
    q = report.quotes
    c = report.commands
    out = [f"report_check: {path or 'отчёт'}; ревизия {revision_text(report)}; репозиторий {repo_root}"]
    out.append(f"Форма: ошибок {len(report.errors)}")
    for line, message in sorted(report.errors):
        out.append(f"  ОШИБКА ФОРМЫ (строка {line}): {message}")
    claims = report.claims
    out.append(f"Утверждений: {len(claims)} (несущих: {sum(1 for x in claims if x.load_bearing)}); строк "
               f"доказательств: файл {report.counts['файл']}, команда {report.counts['команда']}, источник "
               f"{report.counts['источник']}, опровергло бы {report.counts['опровергло бы']}")
    outside = sum(1 for x in q if x.outside)
    out.append(f"Цитаты: {len(q)}; совпало {count(q, 'ok')}, с поправкой {count(q, 'corrected')}, расхождений "
               f"{count(q, 'mismatch')}, не сверено {count(q, 'skipped')}"
               + (f"; вне репозитория (с диска, без ревизии) {outside}" if outside else ""))
    for x in q:
        where = f"{x.path}:{x.stated} (строка отчёта {x.line}{', опровергло бы' if x.origin != 'файл' else ''})"
        if x.status == "mismatch":
            out.append(f"  РАСХОЖДЕНИЕ {where}: {x.detail}")
            out.append("    ожидалось: " + " ".join(f"«{short(p, 100)}»" for p in x.pieces))
            if x.hidden:
                out.append(f"    на деле: {x.path} - вне репозитория, содержимое не выводится")
            for k, text in x.context:
                out.append(f"    на деле {k}: «{short(text, 100)}»")
        elif x.status == "corrected":
            out.append(f"  ПОПРАВКА {where}: {x.detail}")
        elif x.status == "skipped":
            out.append(f"  НЕ СВЕРЕНО {where}: {x.detail}")
    out.append(f"Команды: {len(c)}; перезапущено {count(c, 'ok') + count(c, 'mismatch')}, совпало {count(c, 'ok')}, "
               f"расхождений {count(c, 'mismatch')}, не перезапускалось {count(c, 'skipped')}")
    for x in c:
        where = f"`{short(x.command, 90)}` (строка отчёта {x.line})"
        if x.status == "mismatch":
            out.append(f"  РАСХОЖДЕНИЕ {where}: {x.detail}")
            if x.hidden:
                out.append(f"    вывод: {short(', '.join(x.hidden), 150)} - вне репозитория, содержимое не выводится")
            for line in x.output:
                out.append(f"    вывод: «{short(line, 100)}»")
        elif x.status == "skipped":
            out.append(f"  НЕ ПЕРЕЗАПУСКАЛАСЬ {where}: {x.detail}")
    if report.sources:
        out.append(f"Источники (сверить вручную или выборочно): {len(report.sources)}")
        for line, text in report.sources:
            out.append(f"  строка {line}: {short(text, 150)}")
    if report.free_refutations:
        out.append(f"«Опровергло бы» в свободной форме (сверить вручную): {len(report.free_refutations)}")
        for line, text in report.free_refutations:
            out.append(f"  строка {line}: {short(text, 150)}")
    skipped = not_rerun(report)
    if skipped:
        out.append(f"НЕ ВСЁ ПЕРЕПРОВЕРЕНО: не перезапускалось команд {len(skipped)} из {len(c)} (причины - выше): "
                   f"их код выхода и вывод не сверены")
    code = verdict(report)
    result = {0: "расхождений нет, форма верна", 3: "расхождений нет, форма верна, но не всё перепроверено"}.get(code)
    out.append("Итог: " + (result or f"НЕ ПРИНЯТ: ошибок формы {len(report.errors)}, расхождений "
                                     f"{count(q, 'mismatch') + count(c, 'mismatch')}") + f"; код выхода {code}")
    return "\n".join(out)


def count(items, status):
    return sum(1 for x in items if x.status == status)


def revision_text(report):
    rev = getattr(report, "rev", None)
    if rev == WORKTREE:
        return f"«{WORKTREE}»"
    return rev[:12] if rev else "нет"


def as_json(report, path, repo_root):
    def quote(x):
        return {"line": x.line, "origin": x.origin, "path": x.path, "stated": x.stated, "status": x.status,
                "actual": x.actual, "matched_lines": x.found, "detail": x.detail, "expected": x.pieces,
                "outside_repository": x.outside, "found": [{"line": k, "text": t} for k, t in x.context]}

    def command(x):
        return {"line": x.line, "origin": x.origin, "command": x.command, "status": x.status, "detail": x.detail,
                "expected_code": x.code, "actual_code": x.actual_code, "expected": x.pieces,
                "expected_empty": x.empty, "missing": x.missing}

    q, c = report.quotes, report.commands
    return {
        "report": path, "repo": repo_root, "revision": getattr(report, "rev", None),
        "revision_source": getattr(report, "revision_source", None),
        "run": report.run,
        "exit_code": verdict(report),
        "shape_errors": [{"line": line, "message": message} for line, message in sorted(report.errors)],
        "claims": [{"number": x.number, "line": x.line, "class": x.cls, "load_bearing": x.load_bearing,
                    "evidence": x.kinds} for x in report.claims],
        "coverage": list(report.coverage) if report.coverage else None,
        "evidence": dict(report.counts),
        "quotes": [quote(x) for x in q],
        "commands": [command(x) for x in c],
        "sources": [{"line": line, "text": text} for line, text in report.sources],
        "free_refutations": [{"line": line, "text": text} for line, text in report.free_refutations],
        "summary": {"shape_errors": len(report.errors),
                    "mismatches": count(q, "mismatch") + count(c, "mismatch"),
                    "corrections": count(q, "corrected"),
                    "checked": count(q, "ok") + count(q, "corrected") + count(c, "ok") + count(c, "mismatch"),
                    "skipped": count(q, "skipped") + count(c, "skipped"),
                    "not_rerun": len(not_rerun(report))},
    }


# ---------------------------------------------------------------------------------------------------------- hook

def transcript_messages(path):
    """(handback, final_text) of a Claude Code transcript (JSONL): the `message` of the last SubagentHandback call,
    and the text blocks of the last assistant message (records of one message share message.id)."""
    handback, last_id, texts = None, None, []
    try:
        f = open(path, encoding="utf-8", errors="replace")
    except OSError:
        return None, None
    with f:
        for raw in f:
            try:
                record = json.loads(raw)
            except ValueError:
                continue            # the last line may still be being written
            if not isinstance(record, dict) or record.get("type") != "assistant":
                continue
            message = record.get("message") if isinstance(record.get("message"), dict) else {}
            content = message.get("content")
            if isinstance(content, str):
                content = [{"type": "text", "text": content}]
            if not isinstance(content, list):
                continue
            mid = message.get("id") or record.get("requestId") or record.get("uuid")
            if mid != last_id:
                last_id, texts = mid, []
            for block in content:
                if not isinstance(block, dict):
                    continue
                if block.get("type") == "text" and isinstance(block.get("text"), str) and block["text"].strip():
                    texts.append(block["text"])
                elif block.get("type") == "tool_use" and block.get("name") == "SubagentHandback":
                    message_input = block.get("input") if isinstance(block.get("input"), dict) else {}
                    if isinstance(message_input.get("message"), str):
                        handback = message_input["message"]
    return handback, ("\n".join(texts) if texts else None)


def hook_report(event):
    """(report text, where from) or (None, why there is none)."""
    last = event.get("last_assistant_message")
    path = event.get("agent_transcript_path")
    for attempt in range(TRANSCRIPT_RETRIES + 1):
        handback = final = None
        if isinstance(path, str) and path:
            handback, final = transcript_messages(os.path.expanduser(path))
        if handback is not None:
            # SubagentHandback delivers the report; last_assistant_message is then only the closing text
            if REPORT_START.search(handback):
                return handback, "SubagentHandback"
        elif isinstance(last, str) and last.strip():
            if REPORT_START.search(last):
                return last, "last_assistant_message"
            return None, ("последнее сообщение сабагента - не отчёт раздела 3 (нет «## Карточка …» и «## Итог»): "
                          "принимающий получит только его. Верни отчёт целиком последним сообщением")
        elif final is not None and REPORT_START.search(final):
            return final, "транскрипт"
        if attempt < TRANSCRIPT_RETRIES:
            time.sleep(0.5)
    if handback is not None:
        return None, "отчёт в SubagentHandback - не по разделу 3 (нет «## Карточка …» и «## Итог»)"
    if final is not None:
        return None, ("последнее сообщение сабагента (по транскрипту) - не отчёт раздела 3 (нет «## Карточка …» и "
                      "«## Итог»). Верни отчёт целиком последним сообщением")
    return None, "отчёт не найден: нет last_assistant_message, вызова SubagentHandback и текста в транскрипте"


def hook_main():
    try:
        event = json.loads(sys.stdin.read() or "null")
    except ValueError:
        event = None
    if not isinstance(event, dict):
        return release("[report_check] событие хука не прочитано (не JSON): отчёт сабагента не проверялся")
    project = os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or os.getcwd()
    if not os.path.isdir(project):
        return release(f"[report_check] каталога проекта {project} нет: отчёт сабагента не проверялся")
    repo = Repo(project)
    state = None
    if repo.git:
        code, path, _ = run_git(repo.root, "rev-parse", "--git-path", "gauntlet-hook")
        if code == 0 and path:
            state = path if os.path.isabs(path) else os.path.join(repo.root, path)
            try:
                os.makedirs(state, exist_ok=True)
            except OSError:
                state = None
    if state is None:
        # without the count a block could repeat for ever: the hook lets the subagent stop and says why
        return release(f"[report_check] {project} не в git или git не хранит состояние хука (gauntlet-hook): отчёт "
                       f"сабагента не проверялся - проверьте его сами: python3 gauntlet/tools/report_check.py --run")
    agent = event.get("agent_type") or "сабагент"
    key = event.get("agent_transcript_path") or event.get("agent_id") or event.get("session_id") or "unknown"
    counter = os.path.join(state, "report-check-" + hashlib.sha256(str(key).encode()).hexdigest()[:32] + ".blocks")
    text, origin = hook_report(event)
    problems, report = [], None
    if text is None:
        problems.append(origin)
    else:
        report = check_report(text, repo, run=False)
        problems += [f"строка {line}: {message}" for line, message in sorted(report.errors)]
        for q in report.quotes:
            if q.status == "mismatch":
                problems.append(f"строка {q.line}: цитата `{q.path}:{q.stated}` - {q.detail}; в отчёте: "
                                + " ".join(f"«{short(p, 80)}»" for p in q.pieces))
    tally = hook_tally(report)
    if not problems:
        return release(f"[report_check] отчёт {agent} сверен механически: {tally}; расхождений нет. Команды хук не "
                       f"повторяет: принимающий запускает report_check.py --run")
    blocks = read_count(counter)
    if blocks >= MAX_BLOCKS:
        return release(f"[report_check] отчёт {agent} НЕ ПРИНЯТ механически после {MAX_BLOCKS} возвратов "
                       f"({tally}); осталось {len(problems)}: " + " | ".join(short(p, 160) for p in problems[:10]))
    try:
        with open(counter, "w", encoding="utf-8") as f:
            f.write(str(blocks + 1))
    except OSError:
        return release(f"[report_check] счётчик возвратов не записан ({counter}): отчёт {agent} не возвращён, "
                       f"проблем {len(problems)} - проверьте его сами")
    shown = problems[:40]
    reason = (f"[report_check] Отчёт не прошёл механическую сверку (возврат {blocks + 1} из {MAX_BLOCKS}; "
              f"{tally}). Исправь каждую строку ниже и верни отчёт целиком, по разделу 3 gauntlet/subagents.md. "
              f"Цитату бери из файла на ревизии шапки, а не из памяти; что не доказать - в «Не проверено».\n"
              + "\n".join("- " + p for p in shown)
              + (f"\n- … и ещё {len(problems) - len(shown)}" if len(problems) > len(shown) else ""))
    print(json.dumps({"decision": "block", "reason": reason}, ensure_ascii=False))
    sys.stderr.write(reason + "\n")
    return 2


def hook_tally(report):
    if report is None:
        return "отчёта нет"
    q = report.quotes
    return (f"утверждений {len(report.claims)}, цитат {len(q)} (совпало {count(q, 'ok') + count(q, 'corrected')}), "
            f"команд {len(report.commands)}, источников к ручной сверке {len(report.sources)}")


def read_count(path):
    try:
        with open(path, encoding="utf-8") as f:
            return int(f.read().strip() or 0)
    except (OSError, ValueError):
        return 0


def release(message):
    print(json.dumps({"systemMessage": message}, ensure_ascii=False))
    return 0


# ---------------------------------------------------------------------------------------------------------- main

class CallError(Exception):
    pass


class Parser(argparse.ArgumentParser):
    def error(self, message):
        raise CallError(message)


def main(argv=None):
    args = sys.argv[1:] if argv is None else list(argv)
    ap = Parser(prog="report_check.py", description=__doc__.split("\n\n")[0],
                formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("report", nargs="?", help="the report (Markdown, section 3 of gauntlet/subagents.md)")
    ap.add_argument("--repo", default=".", help="the repository the report is about (default: the current one)")
    ap.add_argument("--rev", help="check the quotes at this revision instead of the header's (the base moved)")
    ap.add_argument("--run", action="store_true", help="re-run the read-only commands of the allowlist")
    ap.add_argument("--json", action="store_true", help="print the result as JSON")
    ap.add_argument("--hook", action="store_true", help="SubagentStop hook mode: the event on stdin")
    try:
        opts = ap.parse_args(args)
        if opts.hook and (opts.report or opts.run or opts.rev is not None or opts.json):
            raise CallError("--hook takes the event on stdin and no other argument")
        if not opts.hook and not opts.report:
            raise CallError("REPORT is required (or --hook)")
    except CallError as e:
        return argument_error(ap, args, str(e))
    if opts.hook:
        try:
            return hook_main()
        except Exception as e:      # a hook that dies lets the subagent stop silently: say so instead
            return release(f"[report_check] сбой хука ({type(e).__name__}: {e}): отчёт сабагента не проверялся")
    if opts.rev is not None and not opts.rev.strip():
        return usage(opts, "--rev пуст: укажите коммит (пустая переменная в скрипте?)")
    try:
        with open(opts.report, encoding="utf-8", errors="replace") as f:
            text = f.read()
    except OSError as e:
        return usage(opts, f"отчёт {opts.report} не читается: {e.strerror or e}")
    if not os.path.isdir(opts.repo):
        return usage(opts, f"--repo {opts.repo}: нет такого каталога")
    if shutil.which("git") is None:
        return usage(opts, "git не найден в PATH")
    repo = Repo(opts.repo)
    try:
        report = check_report(text, repo, opts.rev, opts.run)
    except UsageError as e:
        return usage(opts, str(e))
    if opts.json:
        print(json.dumps(as_json(report, opts.report, repo.root), ensure_ascii=False, indent=2))
    else:
        print(summary(report, opts.report, repo.root))
    return verdict(report)


def argument_error(ap, args, message):
    """A wrong call, exit 2. A hook lets the subagent stop and says so instead: exit 2 would block the stop, and with
    no count of returns on this path, block it every time. With --json the error is JSON as well."""
    named = [a.split("=", 1)[0] for a in args if a.startswith("--")]

    def given(option):
        return any(a == option or (len(a) > 3 and option.startswith(a)) for a in named)

    if given("--hook"):
        return release(f"[report_check] хук вызван неверно ({message}): отчёт сабагента не проверялся")
    if given("--json"):
        print(json.dumps({"error": message, "exit_code": 2}, ensure_ascii=False))
    sys.stderr.write(ap.format_usage() + f"{ap.prog}: error: {message}\n")
    return 2


def usage(opts, message):
    if opts.json:
        print(json.dumps({"error": message, "exit_code": 2}, ensure_ascii=False))
    print("report_check: " + message, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
