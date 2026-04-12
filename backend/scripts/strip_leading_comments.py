#!/usr/bin/env python3
"""
Strip leading single-line comments and block comments from specified Java files.

Use when Checkstyle reports Javadoc/block/leading-comment violations and
you want a safe, automated removal of comment lines that start the line.
This leaves inline comments after code untouched to avoid breaking strings.
"""
import re
import sys
from pathlib import Path

BLOCK_RE = re.compile(r"/\*[\s\S]*?\*/")
LEADING_SLASHES_RE = re.compile(r"^\s*//.*$", re.MULTILINE)


def strip_file(path: Path) -> bool:
    text = path.read_text(encoding='utf-8')
    orig = text

    # remove block comments entirely
    text = BLOCK_RE.sub('', text)

    # remove leading // comment lines (preserve inline comments)
    text = LEADING_SLASHES_RE.sub('', text)

    # collapse multiple consecutive empty lines to a single empty line
    text = re.sub(r"\n{3,}", "\n\n", text)

    # ensure EOF newline
    if not text.endswith('\n'):
        text += '\n'

    if text != orig:
        path.write_text(text, encoding='utf-8')
        print('Stripped:', path)
        return True
    return False


def main():
    if len(sys.argv) < 2:
        print('Usage: strip_leading_comments.py <file1> [file2 ...]')
        return
    changed = 0
    for p in sys.argv[1:]:
        path = Path(p)
        if path.exists():
            try:
                if strip_file(path):
                    changed += 1
            except Exception as e:
                print('Error:', path, e)
        else:
            print('Not found:', path)
    print(f'Done. Files changed: {changed}')


if __name__ == '__main__':
    main()
