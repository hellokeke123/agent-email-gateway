#!/usr/bin/env python3
"""
消息操作工具：发送、回复、标记已读/完成、列出角色。
用法：
  python3 scripts/gateway.py send <toRoleId> <subject> <body>
  python3 scripts/gateway.py reply <messageId> <body>
  python3 scripts/gateway.py read <messageId>
  python3 scripts/gateway.py complete <messageId>
  python3 scripts/gateway.py roles
  python3 scripts/gateway.py inbox
  python3 scripts/gateway.py sent
"""
import urllib.request
import urllib.error
import json
import sys
import os

BASE_URL = "{BASE_URL}"
AUTH_FILE = os.path.join(os.path.expanduser("~"), ".config", "agent-gateway", "auth.json")


def load_auth_code():
    with open(AUTH_FILE, "r", encoding="utf-8") as f:
        return json.load(f)["auth_code"]


def get(path, auth_code):
    req = urllib.request.Request(
        BASE_URL + path,
        headers={"X-Auth-Code": auth_code},
    )
    return json.loads(urllib.request.urlopen(req).read().decode("utf-8"))


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


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    auth_code = load_auth_code()
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)

    cmd = args[0]

    if cmd == "send" and len(args) >= 4:
        result = post("/api/messages/send", {
            "toRoleId": int(args[1]),
            "subject": args[2],
            "body": args[3],
        }, auth_code)
    elif cmd == "reply" and len(args) >= 3:
        result = post(f"/api/messages/{args[1]}/reply", {
            "body": args[2],
        }, auth_code)
    elif cmd == "read" and len(args) >= 2:
        result = post(f"/api/messages/{args[1]}/read", {"read": True}, auth_code)
    elif cmd == "complete" and len(args) >= 2:
        result = post(f"/api/messages/{args[1]}/complete", {"completed": True}, auth_code)
    elif cmd == "roles":
        result = get("/api/roles", auth_code)
    elif cmd == "inbox":
        result = get("/api/messages/inbox?read=false&completed=false&limit=20", auth_code)
    elif cmd == "sent":
        result = get("/api/messages/sent?limit=20", auth_code)
    else:
        print(__doc__)
        sys.exit(1)

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
