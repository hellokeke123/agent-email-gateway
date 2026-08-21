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
import os

BASE_URL = "{BASE_URL}"
AUTH_FILE = os.path.join(os.path.expanduser("~"), ".config", "agent-gateway", "auth.json")
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
        return json.loads(urllib.request.urlopen(req).read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = json.loads(e.read().decode("utf-8"))
        if body.get("error") in ("AUTH_CODE_EXPIRED", "AUTH_CODE_INVALID"):
            return None
        raise


def post(path, body, auth_code):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        BASE_URL + path,
        data=data,
        headers={
            "X-Auth-Code": auth_code,
            "Content-Type": "application/json; charset=UTF-8",
        },
        method="POST",
    )
    return json.loads(urllib.request.urlopen(req).read().decode("utf-8"))


def handle_message(msg, auth):
    msg_id = msg["id"]
    print(f"收到消息 {msg_id}：{msg.get('subject')}", flush=True)
    # 标记已读
    post(f"/api/messages/{msg_id}/read", {"read": True}, auth["auth_code"])
    # 回复收到，等待 agent 处理
    post(f"/api/messages/{msg_id}/reply", {
        "body": f"已收到，正在按职责处理。"
    }, auth["auth_code"])


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
