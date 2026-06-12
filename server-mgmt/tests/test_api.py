"""API 接口测试。"""

from __future__ import annotations

from tests.conftest import SAMPLE_SSH_KEY


class TestHealth:
    def test_health(self, client):
        resp = client.get("/api/health")
        assert resp.status_code == 200
        assert resp.json() == {"status": "ok"}


class TestServers:
    def test_list_servers(self, client):
        resp = client.get("/api/servers")
        assert resp.status_code == 200
        body = resp.json()
        assert body["ok"] is True
        assert len(body["data"]) == 2
        assert body["data"][0]["name"] in ("srv1", "srv2")

    def test_list_servers_mask_secrets(self, client):
        resp = client.get("/api/servers", params={"mask_secrets": True})
        assert resp.status_code == 200
        srv1 = next(r for r in resp.json()["data"] if r["name"] == "srv1")
        assert srv1["password"] == "******"
        assert srv1["sudo_password"] == "******"

    def test_server_options(self, client):
        resp = client.get("/api/servers/options")
        assert resp.status_code == 200
        assert set(resp.json()["data"]) == {"srv1", "srv2"}


class TestUsers:
    def test_create_user(self, client):
        resp = client.post(
            "/api/users",
            json={
                "name": "zhangsan",
                "server_name": "srv1",
                "ssh_key": SAMPLE_SSH_KEY,
                "state": "present",
            },
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["ok"] is True
        assert body["data"]["name"] == "zhangsan"
        assert body["data"]["server_name"] == "srv1"
        assert body["data"]["state"] == "present"
        assert body["data"]["groups"] == []

    def test_create_user_invalid_server(self, client):
        resp = client.post(
            "/api/users",
            json={
                "name": "lisi",
                "server_name": "srv99",
                "ssh_key": SAMPLE_SSH_KEY,
            },
        )
        assert resp.status_code == 400
        assert "不存在" in resp.json()["detail"]

    def test_create_user_invalid_username(self, client):
        resp = client.post(
            "/api/users",
            json={
                "name": "Bad-Name",
                "server_name": "srv1",
                "ssh_key": SAMPLE_SSH_KEY,
            },
        )
        assert resp.status_code == 422

    def test_list_users(self, client):
        client.post(
            "/api/users",
            json={"name": "wangwu", "server_name": "srv1", "ssh_key": SAMPLE_SSH_KEY},
        )
        client.post(
            "/api/users",
            json={"name": "wangwu", "server_name": "srv2", "ssh_key": SAMPLE_SSH_KEY},
        )
        resp = client.get("/api/users")
        assert resp.status_code == 200
        assert len(resp.json()["data"]) == 2

    def test_list_users_filter(self, client):
        client.post(
            "/api/users",
            json={"name": "filterme", "server_name": "srv1", "ssh_key": SAMPLE_SSH_KEY},
        )
        resp = client.get("/api/users", params={"name": "filterme", "server_name": "srv1"})
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert len(data) == 1
        assert data[0]["server_name"] == "srv1"

    def test_delete_user(self, client):
        client.post(
            "/api/users",
            json={"name": "todelete", "server_name": "srv1", "ssh_key": SAMPLE_SSH_KEY},
        )
        resp = client.request(
            "DELETE",
            "/api/users",
            json={"name": "todelete", "server_name": "srv1"},
        )
        assert resp.status_code == 200
        status = client.get(
            "/api/users/status",
            params={"name": "todelete", "server_name": "srv1"},
        )
        assert status.json()["data"]["exists"] is False

    def test_delete_user_not_found(self, client):
        resp = client.request(
            "DELETE",
            "/api/users",
            json={"name": "ghost", "server_name": "srv1"},
        )
        assert resp.status_code == 404

    def test_user_status_exists(self, client):
        client.post(
            "/api/users",
            json={"name": "statususer", "server_name": "srv2", "ssh_key": SAMPLE_SSH_KEY},
        )
        resp = client.get(
            "/api/users/status",
            params={"name": "statususer", "server_name": "srv2"},
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["exists"] is True
        assert data["status"] == "present"
        assert data["server_name"] == "srv2"

    def test_user_status_not_exists(self, client):
        resp = client.get(
            "/api/users/status",
            params={"name": "nobody", "server_name": "srv1"},
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["exists"] is False
        assert data["state"] is None


class TestGroups:
    def test_create_group(self, client):
        resp = client.post("/api/groups", json={"name": "developers"})
        assert resp.status_code == 200
        assert resp.json()["data"]["name"] == "developers"

    def test_create_group_duplicate(self, client):
        client.post("/api/groups", json={"name": "ops"})
        resp = client.post("/api/groups", json={"name": "ops"})
        assert resp.status_code == 409

    def test_list_groups(self, client):
        client.post("/api/groups", json={"name": "teamA"})
        resp = client.get("/api/groups")
        assert resp.status_code == 200
        names = [g["name"] for g in resp.json()["data"]]
        assert "teamA" in names

    def test_assign_group_to_user(self, client):
        client.post("/api/groups", json={"name": "devs"})
        client.post(
            "/api/users",
            json={"name": "member1", "server_name": "srv1", "ssh_key": SAMPLE_SSH_KEY},
        )
        resp = client.post(
            "/api/users/groups",
            json={"name": "member1", "server_name": "srv1", "group_name": "devs"},
        )
        assert resp.status_code == 200
        assert "devs" in resp.json()["data"]["groups"]

    def test_assign_group_user_not_found(self, client):
        client.post("/api/groups", json={"name": "devs2"})
        resp = client.post(
            "/api/users/groups",
            json={"name": "missing", "server_name": "srv1", "group_name": "devs2"},
        )
        assert resp.status_code == 404

    def test_assign_group_group_not_found(self, client):
        client.post(
            "/api/users",
            json={"name": "member2", "server_name": "srv1", "ssh_key": SAMPLE_SSH_KEY},
        )
        resp = client.post(
            "/api/users/groups",
            json={"name": "member2", "server_name": "srv1", "group_name": "nogroup"},
        )
        assert resp.status_code == 404
