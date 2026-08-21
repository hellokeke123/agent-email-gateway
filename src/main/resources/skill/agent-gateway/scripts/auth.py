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
AUTH_FILE = os.path.join(os.path.expanduser("~"), ".config", "agent-gateway", "auth.json")


def start_auth():
    req = urllib.request.Request(BASE_URL + "/api/auth/start", method="POST")
    resp = json.loads(urllib.request.urlopen(req).read().decode("utf-8"))
    print("请在浏览器完成 TOTP 和角色选择：", flush=True)
    print(resp["pageUrl"], flush=True)
    return resp["sessionId"]


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
            os.makedirs(os.path.dirname(AUTH_FILE), exist_ok=True)
            with open(AUTH_FILE, "w", encoding="utf-8") as f:
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


def refresh_auth(auth_code):
    req = urllib.request.Request(
        BASE_URL + "/api/auth/refresh",
        method="POST",
        headers={"X-Auth-Code": auth_code},
    )
    resp = json.loads(urllib.request.urlopen(req).read().decode("utf-8"))
    new_code = resp["authCode"]
    try:
        with open(AUTH_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        data["auth_code"] = new_code
        with open(AUTH_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
    except Exception:
        pass
    print("auth_code 已刷新:", new_code, flush=True)
    return new_code


if __name__ == "__main__":
    session_id = start_auth()
    poll_auth(session_id)

