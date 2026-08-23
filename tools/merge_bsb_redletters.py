#!/usr/bin/env python3
"""Stamps words-of-Jesus sentinels into the BSB assets, derived from the WEB.

The BSB source USFM carries no \\wj markup, but our WEB assets do. Red-letter
verses transfer at verse granularity: a verse whose WEB text is (almost)
entirely red becomes entirely red in the BSB; a partially red verse maps its
red span through quotation-mark anchors (which curly quote opens the span,
which closes it, or the verse start/end). Verses whose anchors can't be
matched are left unmarked and reported.

Run from the repo root:  python3 tools/merge_bsb_redletters.py
"""

import json
import os

S1 = ""  # red-letter start sentinel
S2 = ""  # red-letter end sentinel
OPEN_Q = "“"   # left double quotation mark
CLOSE_Q = "”"  # right double quotation mark

NT_BOOKS = [
    "matthew", "mark", "luke", "john", "acts", "romans", "1corinthians",
    "2corinthians", "galatians", "ephesians", "philippians", "colossians",
    "1thessalonians", "2thessalonians", "1timothy", "2timothy", "titus",
    "philemon", "hebrews", "james", "1peter", "2peter", "1john", "2john",
    "3john", "jude", "revelation",
]

WEB_DIR = os.path.join("app", "src", "en", "assets", "bible")
BSB_DIR = os.path.join("app", "src", "en", "assets", "bible_bsb")


def red_spans(marked):
    """[(start, end)] into the plain text, plus the plain text itself."""
    spans = []
    plain = []
    start = None
    for ch in marked:
        if ch == S1:
            start = len(plain)
        elif ch == S2:
            if start is not None:
                spans.append((start, len(plain)))
                start = None
        else:
            plain.append(ch)
    if start is not None:
        spans.append((start, len(plain)))
    return spans, "".join(plain)


def positions(text, ch):
    return [i for i, c in enumerate(text) if c == ch]


def nearest_index(pos_list, offset, tolerance=2):
    for k, p in enumerate(pos_list):
        if abs(p - offset) <= tolerance:
            return k
    return None


def map_verse(web_marked, bsb_plain):
    """Returns the BSB verse with sentinels, or None when unmappable."""
    spans, web_plain = red_spans(web_marked)
    if not spans:
        return bsb_plain
    total_red = sum(e - s for s, e in spans)
    meaningful = len(web_plain.strip())
    if meaningful == 0:
        return bsb_plain
    if total_red >= meaningful - 3:
        return S1 + bsb_plain + S2  # effectively the whole verse

    web_open = positions(web_plain, OPEN_Q)
    web_close = positions(web_plain, CLOSE_Q)
    bsb_open = positions(bsb_plain, OPEN_Q)
    bsb_close = positions(bsb_plain, CLOSE_Q)

    out_spans = []
    for s, e in spans:
        # Anchor the start: verse start, or the k-th opening quote.
        if s <= 2:
            b_start = 0
        else:
            k = nearest_index(web_open, s)
            if k is None or k >= len(bsb_open):
                return None
            b_start = bsb_open[k]
        # Anchor the end: verse end, or just after the m-th closing quote.
        if e >= len(web_plain) - 2:
            b_end = len(bsb_plain)
        else:
            m = nearest_index(web_close, e - 1)
            if m is None:
                m = nearest_index(web_close, e)
            if m is None or m >= len(bsb_close):
                return None
            b_end = bsb_close[m] + 1
        if b_end <= b_start:
            return None
        out_spans.append((b_start, b_end))

    out = []
    last = 0
    for s, e in sorted(out_spans):
        if s < last:
            return None  # overlapping anchors; bail out
        out.append(bsb_plain[last:s])
        out.append(S1 + bsb_plain[s:e] + S2)
        last = e
    out.append(bsb_plain[last:])
    return "".join(out)


def main():
    stamped = 0
    skipped = []
    for book in NT_BOOKS:
        web_path = os.path.join(WEB_DIR, book + ".json")
        bsb_path = os.path.join(BSB_DIR, book + ".json")
        with open(web_path, encoding="utf-8") as f:
            web = json.load(f)
        with open(bsb_path, encoding="utf-8") as f:
            bsb = json.load(f)
        changed = False
        for c, (web_ch, bsb_ch) in enumerate(zip(web["chapters"], bsb["chapters"])):
            for v in range(min(len(web_ch), len(bsb_ch))):
                web_verse = web_ch[v]
                bsb_verse = bsb_ch[v]
                if not web_verse or not bsb_verse or S1 not in web_verse:
                    continue
                if S1 in bsb_verse:
                    continue  # already stamped
                mapped = map_verse(web_verse, bsb_verse)
                if mapped is None:
                    skipped.append(f"{book} {c + 1}:{v + 1}")
                elif mapped != bsb_verse:
                    bsb_ch[v] = mapped
                    stamped += 1
                    changed = True
        if changed:
            with open(bsb_path, "w", encoding="utf-8") as f:
                json.dump(bsb, f, ensure_ascii=True, separators=(",", ":"))
    print(f"stamped {stamped} verses; {len(skipped)} unmapped")
    for ref in skipped:
        print("  skipped:", ref)


if __name__ == "__main__":
    main()
