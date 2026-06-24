"""FastAPI 依赖。"""

from __future__ import annotations

import sqlite3
from collections.abc import Generator

from db.database import get_connection, init_db


def get_db() -> Generator[sqlite3.Connection, None, None]:
    init_db()
    conn = get_connection()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
