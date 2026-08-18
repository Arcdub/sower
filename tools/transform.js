// Generates the app's assets/bible/ files from the World English Bible USFM
// distribution (https://ebible.org/Scriptures/engwebp_usfm.zip, public domain).
//
// Words of Jesus (\wj ... \wj*) are preserved as sentinel characters in the verse
// strings — U+0001 opens a red span, U+0002 closes it — which the app renders as
// red-letter text (see RedLetter.java).
//
// usage: node transform.js <usfmDir> <outDir>
const fs = require("fs");
const path = require("path");

const usfmDir = process.argv[2];
const outDir = process.argv[3];
if (!usfmDir || !outDir) {
  console.error("usage: node transform.js <usfmDir> <outDir>");
  process.exit(1);
}
fs.mkdirSync(outDir, { recursive: true });

const RED_OPEN = "\u0001";
const RED_CLOSE = "\u0002";

// Canonical order: [USFM book code, asset file stem, display name, isNewTestament]
const books = [
  ["GEN","genesis","Genesis",0],["EXO","exodus","Exodus",0],["LEV","leviticus","Leviticus",0],
  ["NUM","numbers","Numbers",0],["DEU","deuteronomy","Deuteronomy",0],["JOS","joshua","Joshua",0],
  ["JDG","judges","Judges",0],["RUT","ruth","Ruth",0],["1SA","1samuel","1 Samuel",0],
  ["2SA","2samuel","2 Samuel",0],["1KI","1kings","1 Kings",0],["2KI","2kings","2 Kings",0],
  ["1CH","1chronicles","1 Chronicles",0],["2CH","2chronicles","2 Chronicles",0],
  ["EZR","ezra","Ezra",0],["NEH","nehemiah","Nehemiah",0],["EST","esther","Esther",0],
  ["JOB","job","Job",0],["PSA","psalms","Psalms",0],["PRO","proverbs","Proverbs",0],
  ["ECC","ecclesiastes","Ecclesiastes",0],["SNG","songofsolomon","Song of Solomon",0],
  ["ISA","isaiah","Isaiah",0],["JER","jeremiah","Jeremiah",0],["LAM","lamentations","Lamentations",0],
  ["EZK","ezekiel","Ezekiel",0],["DAN","daniel","Daniel",0],["HOS","hosea","Hosea",0],
  ["JOL","joel","Joel",0],["AMO","amos","Amos",0],["OBA","obadiah","Obadiah",0],
  ["JON","jonah","Jonah",0],["MIC","micah","Micah",0],["NAM","nahum","Nahum",0],
  ["HAB","habakkuk","Habakkuk",0],["ZEP","zephaniah","Zephaniah",0],["HAG","haggai","Haggai",0],
  ["ZEC","zechariah","Zechariah",0],["MAL","malachi","Malachi",0],
  ["MAT","matthew","Matthew",1],["MRK","mark","Mark",1],["LUK","luke","Luke",1],
  ["JHN","john","John",1],["ACT","acts","Acts",1],["ROM","romans","Romans",1],
  ["1CO","1corinthians","1 Corinthians",1],["2CO","2corinthians","2 Corinthians",1],
  ["GAL","galatians","Galatians",1],["EPH","ephesians","Ephesians",1],
  ["PHP","philippians","Philippians",1],["COL","colossians","Colossians",1],
  ["1TH","1thessalonians","1 Thessalonians",1],["2TH","2thessalonians","2 Thessalonians",1],
  ["1TI","1timothy","1 Timothy",1],["2TI","2timothy","2 Timothy",1],["TIT","titus","Titus",1],
  ["PHM","philemon","Philemon",1],["HEB","hebrews","Hebrews",1],["JAS","james","James",1],
  ["1PE","1peter","1 Peter",1],["2PE","2peter","2 Peter",1],["1JN","1john","1 John",1],
  ["2JN","2john","2 John",1],["3JN","3john","3 John",1],["JUD","jude","Jude",1],
  ["REV","revelation","Revelation",1],
];

// Map USFM code -> file on disk (files are named like 70-MATengwebp.usfm,
// 70-MATspaRV1909.usfm, ... — 3-char book code, then the translation id).
const filesByCode = {};
for (const f of fs.readdirSync(usfmDir)) {
  const m = f.match(/^\d+-([0-9A-Z]{3})[A-Za-z0-9-]*\.usfm$/);
  if (m) filesByCode[m[1]] = path.join(usfmDir, f);
}

// Line-level markers whose entire line is not verse text
const SKIP_LINE = /^\\(id|ide|h|toc\d|mt\d?|ms\d?|mr|s\d?|sr|r|d|sp|cl|cp|rem|ie|b|periph)\b/;
// Paragraph/poetry markers: drop the marker, keep any trailing text in the current verse
const PARA = /^\\(p|m|po|pr|pc|pi\d?|mi|nb|q\d?|qr|qc|qm\d?|li\d?|lim\d?|ph\d?|tr)\b\s*/;

function parseBook(code) {
  let text = fs.readFileSync(filesByCode[code], "utf8").replace(/^﻿/, "");

  // The translation's own name for the book (e.g. "San Mateo") from the \h header.
  const header = text.match(/^\\h\s+(.+?)\s*$/m);
  const localName = header ? header[1].trim() : null;

  // Footnotes and cross-references are reader's-aid content, not verse text.
  text = text.replace(/\\f\s[\s\S]*?\\f\*/g, "");
  text = text.replace(/\\fe\s[\s\S]*?\\fe\*/g, "");
  text = text.replace(/\\x\s[\s\S]*?\\x\*/g, "");
  // Strong's-number word markup: \w word|strong="G123"\w*  ->  word
  text = text.replace(/\\\+?w\s([^|\\]*)\|[^\\]*?\\\+?w\*/g, "$1");
  text = text.replace(/\\\+?w\s([^\\]*?)\\\+?w\*/g, "$1");

  const chapters = [];
  let chapter = 0;
  let verse = 0;

  const append = (s) => {
    if (chapter < 1 || verse < 1 || !s) return;
    const verses = chapters[chapter - 1];
    verses[verse - 1] = (verses[verse - 1] ? verses[verse - 1] + " " : "") + s;
  };

  for (let line of text.split(/\r?\n/)) {
    line = line.trim();
    if (!line) continue;
    let m;
    if ((m = line.match(/^\\c\s+(\d+)/))) {
      chapter = parseInt(m[1], 10);
      verse = 0;
      chapters[chapter - 1] = chapters[chapter - 1] || [];
    } else if ((m = line.match(/^\\v\s+(\d+)(?:-\d+)?\s*(.*)$/))) {
      verse = parseInt(m[1], 10);
      append(m[2]);
    } else if (SKIP_LINE.test(line)) {
      // headings, titles, metadata — not verse text
    } else if ((m = line.match(PARA))) {
      append(line.slice(m[0].length));
    } else {
      append(line); // plain continuation or unknown in-verse marker; cleaned up below
    }
  }

  // Convert \wj spans to sentinels, tracking spans that cross verse boundaries.
  let inRed = false;
  let redVerses = 0;
  for (const verses of chapters) {
    for (let i = 0; i < verses.length; i++) {
      let v = verses[i];
      if (v === undefined) { verses[i] = ""; continue; }
      let out = inRed ? RED_OPEN : "";
      for (const token of v.split(/(\\\+?wj\*?)/)) {
        if (/^\\\+?wj\*$/.test(token)) {
          if (inRed) { out += RED_CLOSE; inRed = false; }
        } else if (/^\\\+?wj$/.test(token)) {
          if (!inRed) { out += RED_OPEN; inRed = true; }
        } else {
          out += token;
        }
      }
      if (inRed) out += RED_CLOSE; // span continues; reopened on the next verse
      // Strip any leftover character markers (\add, \nd, \sc, \it, \bk, \tl, ...)
      out = out.replace(/\\\+?[a-z]+\d?\*/g, "").replace(/\\\+?[a-z]+\d?\s?/g, "");
      out = out.replace(/\u0001\s*\u0002/g, ""); // drop empty red spans
      out = out.replace(/\u0002(\s*)\u0001/g, "$1"); // merge adjacent red spans
      // Keep whitespace outside the markers so spans hug the words
      out = out.replace(/\u0001\s+/g, " \u0001").replace(/\s+\u0002/g, "\u0002 ");
      out = out.replace(/[ \t]+/g, " ").trim();
      verses[i] = out;
      if (out.includes(RED_OPEN)) redVerses++;
    }
  }
  return { chapters, redVerses, localName };
}

const index = [];
let totalVerses = 0;
let totalRed = 0;

for (const [code, file, fallbackName, nt] of books) {
  if (!filesByCode[code]) throw new Error("missing USFM for " + code);
  const { chapters, redVerses, localName } = parseBook(code);
  const name = localName || fallbackName;
  for (const verses of chapters) for (const v of verses) if (v) totalVerses++;
  totalRed += redVerses;
  fs.writeFileSync(path.join(outDir, file + ".json"), JSON.stringify({ name, chapters }));
  index.push({ file, name, chapters: chapters.length, nt: !!nt });
}

fs.writeFileSync(path.join(outDir, "index.json"), JSON.stringify(index));
console.log("books:", index.length, "verses:", totalVerses, "redLetterVerses:", totalRed);
