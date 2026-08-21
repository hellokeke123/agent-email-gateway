#!/usr/bin/env python3
"""
持续轮询收件箱，收到消息后调用 claude CLI 处理。
用法：python3 scripts/inbox.py
后台运行：nohup python3 scripts/inbox.py &
"""
import urllib.request
import urllib.error
import json
import sys
import time
import subprocess
import os

BASE_URL = "{BASE_URL}"
AUTH_FILE = "/tmp/agent_gateway_auth.json"
POLL_INTERVAL = 10


def load_auth():
    with open(AUTH_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def fetch_inbox(auth_code):
    req = urllib.request.Request(
        BASE_URL + "/api/messages/inbox?read=false&completed=false&limit=20",
        headers={"X-Auth-Code": auth_code},
    )
    try:
        return json.loads(urllib.request.urlopen(req).read())
    except urllib.error.HTTPError as e:
        body = json.loads(e.read())
        if body.get("error") in ("AUTH_CODE_EXPIRED", "AUTH_CODE_INVALID"):
            return None
        raise


def handle_message(msg, auth):
    msg_id = msg["id"]
    prompt = (
        f"你是 {auth['role_name']}。\n"
        f"职责：{auth['role_desc']}\n\n"
        f"你收到一条消息（ID: {msg_id}）：\n"
        f"发件人角色ID: {msg.get('fromRoleId')}\n"
        f"主题: {msg.get('subject')}\n"
        f"内容: {msg.get('body')}\n\n"
        f"按职责处理这条消息。处理完成后运行：\n"
        f"python3 scripts/gateway.py reply {msg_id} <你的回复>\n"
        f"python3 scripts/gateway.py complete {msg_id}\n"
        f"skill 文件在 ~/.config/agent-gateway/skill/agent-gateway/ 目录下。"
    )
    subprocess.run(
        ["claude", "-p", prompt],
        cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    )


def main():
    while True:
        try:
            auth = load_auth()
            inbox = fetch_inbox(auth["auth_code"])
            if inbox is None:
                # auth_code 过期，重新授权
                subprocess.run(["python3", os.path.join(os.path.dirname(__file__), "auth.py")])
                continue
            for msg in inbox.get("messages", []):
                handle_message(msg, auth)
        except Exception as e:
            print(f"error: {e}", file=sys.stderr)
        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()
