#!/usr/bin/env python3
"""Laporan hasil uji untuk CI: anotasi GitHub untuk tiap uji gagal, tabel ringkas (nama, status, detik) dan baris keluaran penting
(diawali UI-, REPLAY-, ART-) ke ringkasan langkah. Pemakaian: ci_test_report.py "<glob xml>" [judul]"""
import glob
import sys
import xml.etree.ElementTree as ET


def esc(s):
    return s.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


def main():
    pattern = sys.argv[1]
    title = sys.argv[2] if len(sys.argv) > 2 else "Hasil uji"
    rows = []
    notes = []
    for f in sorted(glob.glob(pattern, recursive=True)):
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        for tc in root.iter('testcase'):
            name = f"{tc.get('classname')}.{tc.get('name')}"
            secs = float(tc.get('time') or 0)
            fails = list(tc.findall('failure')) + list(tc.findall('error'))
            skipped = tc.find('skipped') is not None
            status = 'GAGAL' if fails else ('lewati' if skipped else 'lulus')
            rows.append((name, status, secs))
            for fail in fails:
                msg = (fail.get('message') or '') + '\n' + (fail.text or '')[:1200]
                print(f"::error title=Uji gagal: {name}::{esc(msg[:3500])}")
        for so in root.iter('system-out'):
            for line in (so.text or '').splitlines():
                if line.startswith(('UI-', 'REPLAY-', 'ART-')):
                    notes.append(line.strip())
    total = len(rows)
    bad = sum(1 for r in rows if r[1] == 'GAGAL')
    out = [f"### {title}", f"{total} uji, {bad} gagal.", "", "| uji | status | detik |", "|---|---|---|"]
    out += [f"| {n} | {s} | {t:.1f} |" for n, s, t in rows]
    if notes:
        out += ["", "Keluaran penting:", "```"] + notes + ["```"]
    text = "\n".join(out) + "\n"
    print(text)
    summary = None
    try:
        import os
        summary = os.environ.get('GITHUB_STEP_SUMMARY')
    except Exception:
        pass
    if summary:
        with open(summary, 'a', encoding='utf-8') as fh:
            fh.write(text)


if __name__ == '__main__':
    main()
