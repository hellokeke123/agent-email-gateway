#!/usr/bin/env python3
"""
步骤 1+2：发起授权并轮询直到 completed。
用法：python3 scripts/auth.py
完成后将 auth_code、role_name、role_desc 写入 AUTH_FILE
"""
import urllib.request
import json
import time
import sys
import os

BASE_URL = "{BASE_URL}"
AUTH_DIR = os.path.join(os.path.expanduser("~"), ".config", "agent-gateway")


def auth_file(session_id):
    return os.path.join(AUTH_DIR, f"auth-{session_id}.json")


def start_auth():
    req = urllib.request.Request(BASE_URL + "/api/auth/start", method="POST")
    resp = json.loads(urllib.request.urlopen(req).read().decode("utf-8"))
    session_id = resp["sessionId"]
    print("请在浏览器完成 TOTP 和角色选择：", flush=True)
    print(resp["pageUrl"], flush=True)
    print(f"AUTH_FILE={auth_file(session_id)}", flush=True)
    return session_id


def poll_auth(session_id):
    while True:
        resp = json.loads(urllib.request.urlopen(
            BASE_URL + "/api/auth/" + session_id
        ).read().decode("utf-8"))
        state = resp.get("state")
        if state == "completed":
            result = {
                "auth_code": resp["authCode"],
                "role_name": resp["role"]["name"],
                "role_desc": resp["role"]["description"],
            }
            f_path = auth_file(session_id)
            os.makedirs(os.path.dirname(f_path), exist_ok=True)
            with open(f_path, "w", encoding="utf-8") as f:
                json.dump(result, f, ensure_ascii=False)
            sys.stdout.reconfigure(encoding="utf-8")
            print("授权完成, role:", result["role_name"], flush=True)
            print("role_desc:", result["role_desc"], flush=True)
            return result
        elif state in ("waiting_totp", "verified"):
            time.sleep(4)
        else:
            print("SESSION_EXPIRED，需重新授权", flush=True)
            sys.exit(1)


if __name__ == "__main__":
    session_id = start_auth()
    poll_auth(session_id)

