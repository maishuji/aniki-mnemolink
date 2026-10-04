#!/usr/bin/env python3
"""Verify the committed Gradle 8.13 wrapper against its published SHA-256."""

from hashlib import sha256
from pathlib import Path

EXPECTED_SHA256 = "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"
ROOT = Path(__file__).resolve().parent.parent


def main() -> None:
    wrapper = ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"
    actual = sha256(wrapper.read_bytes()).hexdigest()
    if actual != EXPECTED_SHA256:
        raise SystemExit(f"Gradle wrapper checksum mismatch: {actual}")
    print("Gradle 8.13 wrapper SHA-256 verified.")


if __name__ == "__main__":
    main()
