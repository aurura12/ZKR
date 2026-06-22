"""服务器连通状态检测接口测试。"""

from __future__ import annotations

from unittest.mock import patch

from tests.conftest import SAMPLE_SSH_KEY


def _mock_run_all_ok(cmd, cwd, capture_output, text, timeout):
    """模拟 ansible ping 全部在线。"""

    class Proc:
        returncode = 0
        stdout = (
            "PLAY RECAP *********************************************************************\n"
            "srv1                       : ok=1    changed=0    unreachable=0    failed=0\n"
            "srv2                       : ok=1    changed=0    unreachable=0    failed=0\n"
        )
        stderr = ""

    return Proc()


def _mock_run_one_unreachable(cmd, cwd, capture_output, text, timeout):
    """模拟 srv1 离线。"""

    class Proc:
        returncode = 3
        stdout = (
            "PLAY [Test SSH connectivity] **********************************************\n"
            "TASK [Ping servers] *******************************************************\n"
            "srv1 | UNREACHABLE! => {\"changed\": false, \"msg\": "
            "\"Failed to connect to the host via ssh: Connection refused\", "
            "\"unreachable\": true}\n"
            "ok: [srv2]\n"
            "PLAY RECAP *****************************************************************\n"
            "srv1    : ok=0    changed=0    unreachable=1    failed=0\n"
            "srv2    : ok=1    changed=0    unreachable=0    failed=0\n"
        )
        stderr = ""

    return Proc()


def _mock_run_auth_failure(cmd, cwd, capture_output, text, timeout):
    """模拟 srv2 认证失败。"""

    class Proc:
        returncode = 3
        stdout = (
            "PLAY [Test SSH connectivity] **********************************************\n"
            "TASK [Ping servers] *******************************************************\n"
            "ok: [srv1]\n"
            "srv2 | UNREACHABLE! => {\"changed\": false, \"msg\": "
            "\"Invalid/incorrect password\", \"unreachable\": true}\n"
            "PLAY RECAP *****************************************************************\n"
            "srv1    : ok=1    changed=0    unreachable=0    failed=0\n"
            "srv2    : ok=0    changed=0    unreachable=1    failed=0\n"
        )
        stderr = ""

    return Proc()


def _mock_run_host_key_error(cmd, cwd, capture_output, text, timeout):
    """模拟 Host key verification failed。"""

    class Proc:
        returncode = 3
        stdout = (
            "PLAY [Test SSH connectivity] **********************************************\n"
            "TASK [Ping servers] *******************************************************\n"
            "srv1 | UNREACHABLE! => {\"changed\": false, \"msg\": "
            "\"Host key verification failed.\"}\n"
            "PLAY RECAP *****************************************************************\n"
            "srv1    : ok=0    changed=0    unreachable=1    failed=0\n"
            "srv2    : ok=1    changed=0    unreachable=0    failed=0\n"
        )
        stderr = ""

    return Proc()


def _mock_run_timeout(cmd, cwd, capture_output, text, timeout):
    """模拟 subprocess 超时。"""
    import subprocess

    raise subprocess.TimeoutExpired(cmd, timeout)


class TestServerStatus:
    def test_all_online(self, client):
        with patch("api.main.run_export", return_value=None), \
             patch("api.main.subprocess.run", side_effect=_mock_run_all_ok):
            resp = client.get("/api/servers/status")
        assert resp.status_code == 200
        body = resp.json()
        assert body["ok"] is True
        data = {s["name"]: s for s in body["data"]}
        assert data["srv1"]["status"] == "ok"
        assert data["srv2"]["status"] == "ok"

    def test_one_unreachable(self, client):
        with patch("api.main.run_export", return_value=None), \
             patch("api.main.subprocess.run", side_effect=_mock_run_one_unreachable):
            resp = client.get("/api/servers/status")
        assert resp.status_code == 200
        data = {s["name"]: s for s in resp.json()["data"]}
        assert data["srv1"]["status"] == "unreachable"
        assert data["srv2"]["status"] == "ok"

    def test_auth_failure(self, client):
        with patch("api.main.run_export", return_value=None), \
             patch("api.main.subprocess.run", side_effect=_mock_run_auth_failure):
            resp = client.get("/api/servers/status")
        assert resp.status_code == 200
        data = {s["name"]: s for s in resp.json()["data"]}
        assert data["srv2"]["status"] == "auth_failed"
        assert data["srv1"]["status"] == "ok"

    def test_host_key_error_is_unreachable(self, client):
        with patch("api.main.run_export", return_value=None), \
             patch("api.main.subprocess.run", side_effect=_mock_run_host_key_error):
            resp = client.get("/api/servers/status")
        assert resp.status_code == 200
        data = {s["name"]: s for s in resp.json()["data"]}
        assert data["srv1"]["status"] == "unreachable"

    def test_filter_single_server(self, client):
        with patch("api.main.run_export", return_value=None), \
             patch("api.main.subprocess.run", side_effect=_mock_run_all_ok):
            resp = client.get("/api/servers/status", params={"server_name": "srv1"})
        assert resp.status_code == 200
        assert len(resp.json()["data"]) == 1
        assert resp.json()["data"][0]["name"] == "srv1"

    def test_filter_invalid_server(self, client):
        with patch("api.main.run_export", return_value=None), \
             patch("api.main.subprocess.run", side_effect=_mock_run_all_ok):
            resp = client.get("/api/servers/status", params={"server_name": "srv99"})
        assert resp.status_code == 200
        assert resp.json()["data"] == []

    def test_timeout(self, client):
        with patch("api.main.run_export", return_value=None), \
             patch("api.main.subprocess.run", side_effect=_mock_run_timeout):
            resp = client.get("/api/servers/status")
        assert resp.status_code == 200
        for s in resp.json()["data"]:
            assert s["status"] == "unknown"
            assert "超时" in s["detail"]
