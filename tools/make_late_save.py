#!/usr/bin/env python3
"""Membuat berkas simpanan akhir permainan (SEED, VER, lalu aksi sampai N giliran) dari fixture putar ulang.

Dipakai tools/ci-emulator.sh untuk mengukur muat simpanan panjang dari proses dingin di emulator. Format keluaran sama dengan
berkas simpanan aplikasi (kapital_save.txt). Pemakaian: make_late_save.py fixture.txt jumlah_giliran keluaran.txt
"""
import sys


def main():
    src, turns, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    version = None
    seed_line = None
    actions = []
    for raw in open(src, encoding="utf-8"):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#ENGINE_VERSION|"):
            version = line.split("|", 1)[1]
        elif line.startswith("#"):
            continue
        elif line.startswith("SEED:") and seed_line is None:
            seed_line = line
        else:
            actions.append(line)
    if version is None or seed_line is None:
        sys.exit("fixture tanpa #ENGINE_VERSION atau SEED")
    lines = [seed_line, "VER:" + version]
    ended = 0
    for a in actions:
        lines.append(a)
        if a == "END":
            ended += 1
            if ended >= turns:
                break
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"simpanan akhir permainan: {len(lines)} baris, {ended} giliran, versi mesin {version}")


if __name__ == "__main__":
    main()
