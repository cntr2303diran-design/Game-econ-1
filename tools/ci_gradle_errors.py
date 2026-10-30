#!/usr/bin/env python3
"""Ringkas kegagalan Gradle dari berkas log menjadi anotasi GitHub.

GitHub hanya menampilkan sekitar 10 anotasi galat per langkah, jadi yang dicetak adalah galat kompilasi Kotlin/Java yang unik
(path dipersingkat), lalu blok "What went wrong" dari Gradle. Anotasi inilah yang bisa dibaca dari luar CI (log mentah tidak
selalu terjangkau); log lengkap ikut diunggah sebagai artifact.

Pemakaian: ci_gradle_errors.py <berkas-log> [jumlah-maksimum]
"""
import re
import sys


def main() -> int:
    path = sys.argv[1]
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 9
    try:
        with open(path, errors="replace") as fh:
            log = fh.read().splitlines()
    except OSError as e:
        print(f"::error::tidak bisa membaca {path}: {e}")
        return 0

    found: list[str] = []
    seen: set[str] = set()

    def add(msg: str) -> None:
        msg = re.sub(r"file:///[^ ]*?/work/[^/]+/[^/]+/", "", msg).strip()
        if msg and msg not in seen:
            seen.add(msg)
            found.append(msg[:480])

    for line in log:
        if line.startswith("e: "):
            add(line)
    for line in log:
        if re.search(r"\.java:\d+: error:", line):
            add(line)
    for i, line in enumerate(log):
        if line.startswith("* What went wrong:"):
            block = []
            for x in log[i + 1 : i + 8]:
                if x.startswith("* "):
                    break
                if x.strip():
                    block.append(x.strip())
            add("Gradle: " + " | ".join(block))
            break

    for msg in found[:limit]:
        safe = msg.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print(f"::error::{safe}")
    print(f"(ringkasan galat: {len(found)} unik dari {len(log)} baris log)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
