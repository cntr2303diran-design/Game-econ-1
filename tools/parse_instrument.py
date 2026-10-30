#!/usr/bin/env python3
"""Mengurai keluaran mentah `adb shell am instrument -w -r` menjadi ringkasan: jumlah uji, daftar gagal, dan kode keluar
(0 = semua lulus). Pemakaian: parse_instrument.py instrument.txt [ringkasan.md]"""
import re
import sys

CODES = {1: 'mulai', 0: 'lulus', -1: 'galat', -2: 'GAGAL', -3: 'diabaikan', -4: 'asumsi gagal'}


def main():
    text = open(sys.argv[1], encoding='utf-8', errors='replace').read()
    blocks = re.split(r'(?m)^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$', text)
    tests = {}
    order = []
    # blocks: [pre, code, body, code, body...]; isi status berada SEBELUM kodenya
    pending = blocks[0]
    for i in range(1, len(blocks), 2):
        code = int(blocks[i])
        body = pending
        pending = blocks[i + 1] if i + 1 < len(blocks) else ''
        cls = re.findall(r'(?m)^INSTRUMENTATION_STATUS: class=(.*)$', body)
        name = re.findall(r'(?m)^INSTRUMENTATION_STATUS: test=(.*)$', body)
        if not cls or not name:
            continue
        key = f"{cls[-1].strip()}.{name[-1].strip()}"
        stack = ''
        m = re.search(r'(?ms)^INSTRUMENTATION_STATUS: stack=(.*?)(?=^INSTRUMENTATION_STATUS: |\Z)', body)
        if m:
            stack = m.group(1).strip()
        if key not in tests:
            order.append(key)
        if code != 1 or key not in tests:
            tests[key] = (code, stack)
    crashed = 'Process crashed' in text or re.search(r'(?m)^INSTRUMENTATION_RESULT: shortMsg=', text) is not None
    bad = [k for k in order if tests[k][0] in (-1, -2)]
    lines = ["### Uji instrumented di emulator (ART)", f"{len(order)} uji, {len(bad)} gagal" + (", proses aplikasi macet" if crashed else ""), "",
             "| uji | status |", "|---|---|"]
    for k in order:
        lines.append(f"| {k} | {CODES.get(tests[k][0], tests[k][0])} |")
    for k in bad:
        lines += ["", f"**{k}**", "```", tests[k][1][:2500], "```"]
    out = "\n".join(lines) + "\n"
    print(out)
    if len(sys.argv) > 2:
        open(sys.argv[2], 'w', encoding='utf-8').write(out)
    sys.exit(0 if (order and not bad and not crashed) else 1)


if __name__ == '__main__':
    main()
