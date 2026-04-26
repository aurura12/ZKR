#!/usr/bin/env python3
import json
import re
import sys
from urllib.request import Request, urlopen


def fetch_tags(registry: str, repo: str):
    url = f"http://{registry}/v2/{repo}/tags/list"
    req = Request(url)
    with urlopen(req, timeout=10) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    return payload.get("tags") or []


def next_version(tags):
    nums = []
    for tag in tags:
        m = re.fullmatch(r"v1\.(\d+)", str(tag))
        if m:
            nums.append(int(m.group(1)))
    if not nums:
        return "v1.1"
    return f"v1.{max(nums) + 1}"


def main():
    if len(sys.argv) != 3:
        print("Usage: next_image_version.py <registry_host:port> <repo>")
        sys.exit(2)
    registry, repo = sys.argv[1], sys.argv[2]
    tags = fetch_tags(registry, repo)
    print(next_version(tags))


if __name__ == "__main__":
    main()
