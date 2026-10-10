"""Tiny BlenderMCP socket client: bl.py code FILE | bl.py shot OUT.png [size]"""
import json, socket, sys
def call(cmd, params):
    s = socket.create_connection(("localhost", 9876), timeout=600)
    s.sendall(json.dumps({"type": cmd, "params": params}).encode())
    buf = b""
    while True:
        chunk = s.recv(65536)
        if not chunk: break
        buf += chunk
        try:
            return json.loads(buf)
        except ValueError:
            continue
    return json.loads(buf)
if sys.argv[1] == "code":
    r = call("execute_code", {"code": open(sys.argv[2], encoding="utf-8").read()})
elif sys.argv[1] == "shot":
    r = call("get_viewport_screenshot", {"filepath": sys.argv[2], "max_size": int(sys.argv[3]) if len(sys.argv) > 3 else 1000, "format": "png"})
else:
    r = call(sys.argv[1], {})
print(json.dumps(r)[:3000])
