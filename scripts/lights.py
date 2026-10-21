#!/usr/bin/env python3
"""lights.py — find, pair and test smart lights for Liftoff, from this computer.

  lights.py find govee                 list Govee lights with LAN Control on
  lights.py find hue                   list Philips Hue bridges on this network
  lights.py pair hue <bridge-ip>       get a bridge token (press the bridge's round button first)
  lights.py list hue <bridge-ip> <token>                 list the bridge's lights and their ids
  lights.py list homeassistant <url> <token>             list Home Assistant light entities
  lights.py test                       flash every light in liftoff.json: red, blue, then warm white
  lights.py on|off                     all lights in liftoff.json on or off

WLED: find its IP in the WLED app or your router; nothing to pair.
Home Assistant: the user makes a long-lived token (Profile → Security → Long-lived access tokens)
and pastes it into liftoff.json themselves.
"""
import json, os, socket, sys, time, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def http(method, url, body=None, token=None):
    req = urllib.request.Request(url, method=method, data=None if body is None else json.dumps(body).encode())
    req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=5) as r: return json.loads(r.read() or b"null")

def govee_find():
    r = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); r.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    r.bind(("", 4002)); r.settimeout(3)
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
    s.sendto(json.dumps({"msg": {"cmd": "scan", "data": {"account_topic": "reserve"}}}).encode(), ("239.255.255.250", 4001))
    found = 0
    try:
        while True:
            raw, (ip, _) = r.recvfrom(4096); d = json.loads(raw)["msg"]["data"]; found += 1
            print(d.get("ip") or ip, d.get("sku"), d.get("device"))
    except socket.timeout:
        if not found: print("No Govee lights answered. Turn on LAN Control for each light in the Govee Home app.")

def hue_find():
    # SSDP: Hue bridges answer M-SEARCH with a header naming the bridge.
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.settimeout(3)
    s.sendto(b'M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: "ssdp:discover"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n', ("239.255.255.250", 1900))
    seen = set()
    try:
        while True:
            data, (ip, _) = s.recvfrom(4096)
            if b"hue-bridgeid" in data.lower() and ip not in seen: seen.add(ip); print(ip, "Hue bridge")
    except socket.timeout:
        if not seen: print("No Hue bridge answered. Find its IP in the Hue app: Settings → My Hue System → Bridge.")

class Lights:
    """Mirror of app/src/.../Lights.java, for testing from the computer."""
    def __init__(self, c):
        self.p = (c.get("provider") or "govee").lower().replace(" ", "").replace("-", "").replace("_", "")
        self.host, self.token, self.devices = c.get("host", ""), c.get("token", ""), c.get("devices") or c.get("govee") or []
    def govee(self, ip, cmd, data):
        socket.socket(socket.AF_INET, socket.SOCK_DGRAM).sendto(json.dumps({"msg": {"cmd": cmd, "data": data}}).encode(), (ip, 4003))
    def power(self, d, on):
        if self.p == "govee": self.govee(d, "turn", {"value": 1 if on else 0})
        elif self.p == "hue": http("PUT", f"http://{self.host}/api/{self.token}/lights/{d}/state", {"on": on})
        elif self.p == "wled": http("POST", f"http://{d}/json/state", {"on": on})
        else: http("POST", self.host.rstrip("/") + "/api/services/light/" + ("turn_on" if on else "turn_off"), {"entity_id": d}, self.token)
    def color(self, d, r, g, b):
        if self.p == "govee": self.govee(d, "colorwc", {"color": {"r": r, "g": g, "b": b}, "colorTemInKelvin": 0})
        elif self.p == "hue":
            import colorsys; h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            http("PUT", f"http://{self.host}/api/{self.token}/lights/{d}/state", {"on": True, "hue": int(h * 65535), "sat": int(s * 254), "bri": max(1, int(v * 254))})
        elif self.p == "wled": http("POST", f"http://{d}/json/state", {"on": True, "seg": [{"fx": 0, "col": [[r, g, b]]}]})
        else: http("POST", self.host.rstrip("/") + "/api/services/light/turn_on", {"entity_id": d, "rgb_color": [r, g, b]}, self.token)
    def warm(self, d):
        if self.p == "govee": self.govee(d, "colorwc", {"color": {"r": 0, "g": 0, "b": 0}, "colorTemInKelvin": 2700})
        elif self.p == "hue": http("PUT", f"http://{self.host}/api/{self.token}/lights/{d}/state", {"on": True, "ct": 370})
        elif self.p == "wled": self.color(d, 255, 169, 87)
        else: http("POST", self.host.rstrip("/") + "/api/services/light/turn_on", {"entity_id": d, "color_temp_kelvin": 2700}, self.token)

def configured():
    path = os.environ.get("LIFTOFF_CONFIG", os.path.join(ROOT, "liftoff.json"))
    c = (json.load(open(path)).get("lights") or {})
    l = Lights(c)
    if not l.devices: sys.exit("No lights in liftoff.json (lights.devices is empty), so the Lights entry stays hidden.")
    return l

def main(a):
    if not a: sys.exit(__doc__)
    if a[0] == "find" and a[1:] == ["govee"]: govee_find()
    elif a[0] == "find" and a[1:] == ["hue"]: hue_find()
    elif a[0] == "pair" and len(a) == 3 and a[1] == "hue":
        r = http("POST", f"http://{a[2]}/api", {"devicetype": "liftoff#tv"})[0]
        print(r["success"]["username"] if "success" in r else "Press the round button on the bridge, then run this again within 30 seconds.")
    elif a[0] == "list" and len(a) == 4 and a[1] == "hue":
        for i, l in http("GET", f"http://{a[2]}/api/{a[3]}/lights").items(): print(i, l["name"], l["type"])
    elif a[0] == "list" and len(a) == 4 and a[1] == "homeassistant":
        for e in http("GET", a[2].rstrip("/") + "/api/states", token=a[3]):
            if e["entity_id"].startswith("light."): print(e["entity_id"], e["attributes"].get("friendly_name", ""))
    elif a[0] == "test":
        l = configured()
        for step, fn in (("red", lambda d: l.color(d, 255, 0, 0)), ("blue", lambda d: l.color(d, 0, 60, 255)), ("warm white", l.warm)):
            print(step)
            for d in l.devices: l.power(d, True); fn(d)
            time.sleep(2)
        print(f"Did all {len(l.devices)} light(s) go red, blue, then warm white?")
    elif a[0] in ("on", "off"):
        l = configured()
        for d in l.devices: l.power(d, a[0] == "on")
    else: sys.exit(__doc__)

if __name__ == "__main__":
    main(sys.argv[1:])
