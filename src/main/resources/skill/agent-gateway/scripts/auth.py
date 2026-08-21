#!/usr/bin/env python3
"""
步骤 1+2：发起授权并轮询直到 completed。
用法：python3 scripts/auth.py
完成后将 auth_code、role_name、role_desc 写入 /tmp/agent_gateway_auth.json
"""
import urllib.request
import json
import time
import sys

BASE_URL = "{BASE_URL}"


def start_auth():
    req = urllib.request.Request(BASE_URL + "/api/auth/start", method="POST")
    resp = json.loads(urllib.request.urlopen(req).read())
    print("请在浏览器完成 TOTP 和角色选择：")
    print(resp["pageUrl"])
    return resp["sessionId"]


def poll_auth(session_id):
    while True:
        resp = json.loads(urllib.request.urlopen(
            BASE_URL + "/api/auth/" + session_id
        ).read())
        state = resp.get("state")
        if state == "completed":
            result = {
                "auth_code": resp["authCode"],
                "role_name": resp["role"]["name"],
                "role_desc": resp["role"]["description"],
            }
            with open("/tmp/agent_gateway_auth.json", "w", encoding="utf-8") as f:
                json.dump(result, f, ensure_ascii=False)
            print("授权完成, role:", result["role_name"])
            print("role_desc:", result["role_desc"])
            return result
        elif state in ("waiting_totp", "verified"):
            time.sleep(4)
        else:
            print("SESSION_EXPIRED，需重新授权")
            sys.exit(1)


def refresh_auth(auth_code):
    req = urllib.request.Request(
        BASE_URL + "/api/auth/refresh",
        method="POST",
        headers={"X-Auth-Code": auth_code},
    )
    resp = json.loads(urllib.request.urlopen(req).read())
    new_code = resp["authCode"]
    # 更新缓存文件
    try:
        with open("/tmp/agent_gateway_auth.json", "r", encoding="utf-8") as f:
            data = json.load(f)
        data["auth_code"] = new_code
        with open("/tmp/agent_gateway_auth.json", "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
    except Exception:
        pass
    print("auth_code 已刷新:", new_code)
    return new_code


if __name__ == "__main__":
    session_id = start_auth()
    poll_auth(session_id)
