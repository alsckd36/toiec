#!/usr/bin/env python3
"""Read the supplied workbook without editing it; DAY titles, never tab numbering, are authoritative."""
import argparse, hashlib, json, re, unicodedata
from collections import Counter
from pathlib import Path
import openpyxl


def extract(path):
    original_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    book = openpyxl.load_workbook(path, read_only=True, data_only=True)
    days, entries, excluded = [], [], []
    for sheet in book:
        rows = list(sheet.values)
        title = next((str(v).strip() for row in rows[:2] for v in row if v is not None), '')
        if re.search(r'\bTEST\b', title, re.I) or re.match(r'Test\b', sheet.title, re.I):
            excluded.append(sheet.title)
            continue
        match = re.fullmatch(r'DAY\s*(\d+)\s+(.+)', title, re.I)
        if not match:
            raise ValueError(f'Unrecognized title: {sheet.title}: {title}')
        day = int(match[1])
        count = 0
        for row_number, row in enumerate(rows[1:], 2):
            number, word, meaning = row[5:8]
            if word is None and meaning is None:
                continue
            if not isinstance(number, (int, float)) or not isinstance(word, str) or not isinstance(meaning, str) or not word.strip() or not meaning.strip():
                raise ValueError(f'Incomplete vocabulary row: {sheet.title}!{row_number}')
            word, meaning = word.strip(), meaning.strip()
            normalized = unicodedata.normalize('NFKC', word).casefold().replace('’', "'")
            identifier = hashlib.sha256(normalized.encode()).hexdigest()[:24]
            entries.append(dict(id=identifier, day=day, word=word, meaning=meaning, sourceSheet=sheet.title, sourceRow=row_number))
            count += 1
        days.append(dict(day=day, title=match[2], count=count, sourceSheet=sheet.title))
    book.close()
    assert original_hash == hashlib.sha256(path.read_bytes()).hexdigest(), 'Workbook modified'
    assert len(set(d['day'] for d in days)) == len(days)
    return dict(sourceSha256=original_hash, days=sorted(days, key=lambda d:d['day']), entries=entries, excludedSheets=excluded)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('workbook', type=Path)
    parser.add_argument('--output', type=Path, default=Path('android/app/src/main/assets/vocabulary.json'))
    args = parser.parse_args()
    result = extract(args.workbook)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    counts = Counter(e['id'] for e in result['entries'])
    report = ['# 단어 데이터 추출 결과', '', '실제 A1 DAY 제목으로 분류. F열 번호, G열 영어 단어·표현, H열 한글 뜻을 추출했습니다.', '빈 행 제외. Test 시트 전체 제외. 원문 표기·뜻은 앞뒤 공백만 제거하고 유지했습니다.', '', '| DAY | 제목 | 항목 수 | 원본 시트 |', '|---|---|---:|---|']
    report += [f"| {d['day']} | {d['title']} | {d['count']} | {d['sourceSheet']} |" for d in result['days']]
    report += ['', f"전체 {len(result['entries'])}항목 / 고유 영어 단어·표현 {len(counts)}개 / Test {len(result['excludedSheets'])}개 시트 제외.", 'DAY 26 은행은 시트 이름이 Day 25 (2)이지만 실제 제목을 기준으로 DAY 26에 분류했습니다.', 'DAY간 동일 영어 표현은 각 DAY의 학습 항목으로 유지하고, 오답노트에서는 동일 ID로 중복 없이 저장합니다.', '', '원본 SHA-256: '+result['sourceSha256']]
    Path('docs/vocabulary-report.md').write_text('\n'.join(report)+'\n')
    print('\n'.join(report))
