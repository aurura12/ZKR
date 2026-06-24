"""数据库层测试。"""

from __future__ import annotations

import pytest

from db.database import (
    assign_user_group,
    create_group,
    export_users_yml,
    get_user,
    import_users_yml,
    init_db,
    list_users,
    upsert_server,
    upsert_user,
)
from tests.conftest import SAMPLE_SSH_KEY


@pytest.fixture
def conn(db_path):
    from db.database import get_connection

    with get_connection(db_path) as c:
        yield c


class TestDatabase:
    def test_upsert_user_one_record_per_server(self, conn):
        upsert_user(conn, name="alice", server_name="srv1", ssh_key=SAMPLE_SSH_KEY)
        upsert_user(conn, name="alice", server_name="srv2", ssh_key=SAMPLE_SSH_KEY)
        conn.commit()
        assert len(list_users(conn, name="alice")) == 2

    def test_assign_group(self, conn):
        upsert_user(conn, name="bob", server_name="srv1", ssh_key=SAMPLE_SSH_KEY)
        create_group(conn, "testgroup")
        assign_user_group(conn, name="bob", server_name="srv1", group_name="testgroup")
        conn.commit()
        row = get_user(conn, name="bob", server_name="srv1")
        assert row is not None

    def test_export_users_yml_with_groups(self, conn, tmp_path):
        upsert_user(conn, name="carol", server_name="srv1", ssh_key=SAMPLE_SSH_KEY)
        create_group(conn, "exportgroup")
        assign_user_group(conn, name="carol", server_name="srv1", group_name="exportgroup")
        conn.commit()
        out = tmp_path / "users.yml"
        export_users_yml(out, conn)
        text = out.read_text(encoding="utf-8")
        assert "carol" in text
        assert "exportgroup" in text
        assert "groups" in text

    def test_import_users_yml_expands_hosts(self, conn, tmp_path):
        upsert_server(conn, name="srv3", host="10.0.0.3", port=22, ops_user="admin")
        yml = tmp_path / "users.yml"
        yml.write_text(
            """
employees:
  - name: dave
    hosts: [srv1, srv3]
    ssh_keys:
      - "{key}"
    state: present
""".format(key=SAMPLE_SSH_KEY),
            encoding="utf-8",
        )
        n = import_users_yml(yml, conn)
        conn.commit()
        assert n == 2
        assert get_user(conn, name="dave", server_name="srv1")
        assert get_user(conn, name="dave", server_name="srv3")
