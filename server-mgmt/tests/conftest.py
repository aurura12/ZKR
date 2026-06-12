"""pytest 公共 fixture。"""

from __future__ import annotations

import sqlite3
import sys
from collections.abc import Generator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

from api.deps import get_db  # noqa: E402
from api.main import app  # noqa: E402
from db.database import get_connection, init_db, upsert_server  # noqa: E402

SAMPLE_SSH_KEY = (
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAItestkeyfortestingonly test@example.com"
)


@pytest.fixture
def db_path(tmp_path: Path) -> Path:
    path = tmp_path / "test.db"
    init_db(path)
    with get_connection(path) as conn:
        upsert_server(
            conn,
            name="srv1",
            host="10.0.0.1",
            port=22,
            ops_user="admin",
            password="secret",
            sudo_password="sudo-secret",
        )
        upsert_server(
            conn,
            name="srv2",
            host="10.0.0.2",
            port=6000,
            ops_user="admin",
        )
        conn.commit()
    return path


@pytest.fixture
def client(db_path: Path) -> Generator[TestClient, None, None]:
    def override_get_db() -> Generator[sqlite3.Connection, None, None]:
        conn = get_connection(db_path)
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()
