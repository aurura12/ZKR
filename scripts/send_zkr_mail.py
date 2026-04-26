#!/usr/bin/env python3
from pathlib import Path
import runpy


REPO_ROOT = Path(__file__).resolve().parents[1]


if __name__ == "__main__":
    runpy.run_path(str(REPO_ROOT / "skills" / "email" / "send.py"), run_name="__main__")
