#!/usr/bin/env python3
"""
消息操作工具：发送、回复、标记已读/完成、列出角色。
用法：
  python3 scripts/gateway.py [auth_file.json] send <toRoleId> <subject> <body>
  python3 scripts/gateway.py [auth_file.json] reply <messageId> <body>
  python3 scripts/gateway.py [auth_file.json] read <messageId>
  python3 scripts/gateway.py [auth_file.json] complete <messageId>
  python3 scripts/gateway.py [auth_file.json] roles
  python3 scripts/gateway.py [auth_file.json] inbox
  python3 scripts/gateway.py [auth_file.json] sent
"""
import urllib.request
import urllib.error
import json
import sys
import os

BASE_URL = "{BASE_URL}"
AUTH_FILE = os.path.join(os.path.expanduser("~"), ".config", "agent-gateway", "auth.json")


def load_auth_code(auth_file_path):
    with open(auth_file_path, "r", encoding="utf-8") as f:
        return json.load(f)["auth_code"]


def refresh_auth_code(old_code, auth_file_path):
    req = urllib.request.Request(
        BASE_URL + "/api/auth/refresh",
        method="POST",
        headers={"X-Auth-Code": old_code},
    )
    resp = json.loads(urllib.request.urlopen(req).read().decode("utf-8"))
    new_code = resp["authCode"]
    with open(auth_file_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data["auth_code"] = new_code
    with open(auth_file_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    return new_code


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


def request_with_refresh(fn, auth_code, auth_file_path):
    try:
        return fn(auth_code)
    except urllib.error.HTTPError as e:
        body = json.loads(e.read().decode("utf-8"))
        if body.get("error") in ("AUTH_CODE_EXPIRED", "AUTH_CODE_INVALID"):
            new_code = refresh_auth_code(auth_code, auth_file_path)
            return fn(new_code)
        raise


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)

    # 第一个参数如果是文件路径则作为 auth_file，否则用默认
    if args[0].endswith(".json"):
        auth_file_path = args[0]
        args = args[1:]
    else:
        auth_file_path = AUTH_FILE

    auth_code = load_auth_code(auth_file_path)
    if not args:
        print(__doc__)
        sys.exit(1)

    cmd = args[0]

    if cmd == "send" and len(args) >= 4:
        result = request_with_refresh(lambda c: post("/api/messages/send", {
            "toRoleId": int(args[1]),
            "subject": args[2],
            "body": args[3],
        }, c), auth_code, auth_file_path)
    elif cmd == "reply" and len(args) >= 3:
        result = request_with_refresh(lambda c: post(f"/api/messages/{args[1]}/reply", {
            "body": args[2],
        }, c), auth_code, auth_file_path)
    elif cmd == "read" and len(args) >= 2:
        result = request_with_refresh(lambda c: post(f"/api/messages/{args[1]}/read", {"read": True}, c), auth_code, auth_file_path)
    elif cmd == "complete" and len(args) >= 2:
        result = request_with_refresh(lambda c: post(f"/api/messages/{args[1]}/complete", {"completed": True}, c), auth_code, auth_file_path)
    elif cmd == "roles":
        result = request_with_refresh(lambda c: get("/api/roles", c), auth_code, auth_file_path)
    elif cmd == "inbox":
        result = request_with_refresh(lambda c: get("/api/messages/inbox?read=false&completed=false&limit=20", c), auth_code, auth_file_path)
    elif cmd == "sent":
        result = request_with_refresh(lambda c: get("/api/messages/sent?limit=20", c), auth_code, auth_file_path)
    else:
        print(__doc__)
        sys.exit(1)

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()