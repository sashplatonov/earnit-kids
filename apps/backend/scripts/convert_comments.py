#!/usr/bin/env python3
"""
Convert block comments (/* ... */ and /** ... */) and single-line comments
to project-approved single-line comments starting with 'EXPLAIN:' where needed.

Use with caution: this performs a best-effort transformation of comments in
`src/main/java` files. Review changes before committing.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src' / 'main' / 'java'

BLOCK_RE = re.compile(r"/\*[\s\S]*?\*/")
ALLOWED_INLINE = re.compile(r"^\s*//\s*(EXPLAIN:|EXPLAIN|FIXME|BUGFIX|HACK|NOTE)")
HTTP_PATTERN = re.compile(r"https?://")


def convert_block_comment(match: re.Match) -> str:
    text = match.group(0)
    inner = text[2:-2].strip()
    lines = inner.splitlines()
    out_lines = []
    for ln in lines:
        ln = ln.strip()
        if ln.startswith('*'):
            ln = ln[1:].strip()
        if ln == '':
            continue
        out_lines.append('// EXPLAIN: ' + ln)
    if not out_lines:
        return '// EXPLAIN:'
    return '\n'.join(out_lines)


def convert_file(path: Path) -> bool:
    text = path.read_text(encoding='utf-8')
    original = text

    # Replace block comments with EXPLAIN single-line comments
    text = BLOCK_RE.sub(lambda m: convert_block_comment(m), text)

    # Normalize single-line comments that are not allowed
    def repl_inline(line: str) -> str:
        if '//' not in line:
            return line
        # don't touch lines that look like URLs
        if HTTP_PATTERN.search(line):
            return line
        parts = line.split('//', 1)
        prefix, comment = parts[0], parts[1]
        if ALLOWED_INLINE.match('//' + comment):
            return line
        # preserve leading whitespace before comment
        return prefix + '// EXPLAIN: ' + comment.strip()

    new_lines = []
    for ln in text.splitlines():
        new_lines.append(repl_inline(ln))
    text = '\n'.join(new_lines)

    # Replace tabs with 4 spaces
    if '\t' in text:
        text = text.replace('\t', '    ')

    # Ensure trailing newline
    if not text.endswith('\n'):
        text += '\n'

    if text != original:
        path.write_text(text, encoding='utf-8')
        return True
    return False


def main():
    changed = 0
    for file in sorted(ROOT.rglob('*.java')):
        if 'target' in file.parts:
            continue
        try:
            if convert_file(file):
                print('Converted:', file)
                changed += 1
        except Exception as e:
            print('Error processing', file, e)
    print(f'Done. Files changed: {changed}')


if __name__ == '__main__':
    main()
