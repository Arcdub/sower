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


def quote_regions(text):
    """Regions inside curly double quotes, inclusive of the marks themselves.
    A closer with no opener means the verse began mid-quote; an unclosed
    opener runs to the end of the verse."""
    regions = []
    start = None
    for i, c in enumerate(text):
        if c == OPEN_Q:
            if start is None:
                start = i
        elif c == CLOSE_Q:
            regions.append((0 if start is None else start, i + 1))
            start = None
    if start is not None:
        regions.append((start, len(text)))
    return regions


def clip_to_quotes(spans, text):
    """Speech lives inside quotes: narration such as 'said Jesus' between or
    around quoted stretches is trimmed out of the red spans. Verses without
    any quote mark (mid-discourse continuations) pass through untouched."""
    if OPEN_Q not in text and CLOSE_Q not in text:
        return spans
    out = []
    for s, e in spans:
        for qs, qe in quote_regions(text):
            a, b = max(s, qs), min(e, qe)
            if b > a:
                out.append((a, b))
    return sorted(out)


def emit(bsb_plain, spans):
    out = []
    last = 0
    for s, e in spans:
        if s < last:
            return None
        out.append(bsb_plain[last:s])
        out.append(S1 + bsb_plain[s:e] + S2)
        last = e
    out.append(bsb_plain[last:])
    return "".join(out)


def map_verse(web_marked, bsb_plain):
    """Returns the BSB verse with sentinels, or None when unmappable.

    Partial-red verses transfer at quote-region granularity: the WEB tells us
    which quoted stretches are Jesus speaking, and the matching BSB regions are
    marked, anchored from the first and last red region so an inversion that
    splits one quote into two ("...," He said, "...") still maps whole while
    the narration between the regions stays black.
    """
    spans, web_plain = red_spans(web_marked)
    if not spans:
        return bsb_plain
    total_red = sum(e - s for s, e in spans)
    meaningful = len(web_plain.strip())
    if meaningful == 0:
        return bsb_plain
    if total_red >= meaningful - 3:
        # The whole WEB verse is speech; any BSB narration sits outside quotes.
        clipped = clip_to_quotes([(0, len(bsb_plain))], bsb_plain)
        return emit(bsb_plain, clipped) if clipped else None

    regions_w = quote_regions(web_plain)
    regions_b = quote_regions(bsb_plain)
    if not regions_w or not regions_b:
        return None
    red_idx = set()
    for k, (rs, re_) in enumerate(regions_w):
        covered = sum(max(0, min(e, re_) - max(s, rs)) for s, e in spans)
        if re_ > rs and covered / (re_ - rs) > 0.5:
            red_idx.add(k)
    if not red_idx:
        return None
    first = min(red_idx)
    last = max(red_idx)
    if set(range(first, last + 1)) != red_idx:
        return None  # non-contiguous red regions; too risky to map
    # Keep the first red region's index from the front and the last one's
    # distance from the back, absorbing any extra regions the BSB split off.
    b_last = len(regions_b) - 1 - (len(regions_w) - 1 - last)
    if first > b_last or b_last > len(regions_b) - 1:
        return None
    return emit(bsb_plain, [regions_b[k] for k in range(first, b_last + 1)])


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
