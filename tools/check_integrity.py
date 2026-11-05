#!/usr/bin/env python3
"""Check delivered backend/protected files against the recorded source inventory."""
import csv
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / "docs/verification/backend-inventory.csv"
failures = []
rows = list(csv.DictReader(INVENTORY.open()))
for row in rows:
    path = ROOT / row["path"]
    actual = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else "MISSING"
    if actual != row["current_sha256"]:
        failures.append(row["path"])
if failures:
    raise SystemExit("FAIL: source differs from verified delivery:\n" + "\n".join(failures))
print(f"PASS: {len(rows)} backend/protected files match the delivery inventory")
print("The CSV also records baseline hashes; AUTHORIZED_CHANGE is not a byte-identical-backend claim.")
