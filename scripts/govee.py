#!/usr/bin/env python3
"""govee.py <ip> on|off|status|bright <1-100>|color <r> <g> <b>|temp <2000-9000>

Control a Govee light over the local Govee LAN API (UDP 4003, replies on 4002). No account or cloud.
Turn on "LAN Control" for the light in the Govee Home app first. Find lights with: govee.py scan
"""
import sys, json, socket
if len(sys.argv) > 1 and sys.argv[1] == 'scan':
    # Ask every Govee light on the network to announce itself (multicast 239.255.255.250:4001).
    r = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); r.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    r.bind(('', 4002)); r.settimeout(3)
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
    s.sendto(json.dumps({'msg': {'cmd': 'scan', 'data': {'account_topic': 'reserve'}}}).encode(), ('239.255.255.250', 4001))
    try:
        while True:
            d = json.loads(r.recvfrom(4096)[0])['msg']['data']; print(d.get('ip'), d.get('sku'), d.get('device'))
    except socket.timeout: sys.exit(0)
if len(sys.argv) < 3: sys.exit(__doc__)
ip, cmd, *a = sys.argv[1:]
m = {'on': ('turn', {'value': 1}), 'off': ('turn', {'value': 0}), 'status': ('devStatus', {})}
if cmd in m: c, d = m[cmd]
elif cmd == 'bright': c, d = 'brightness', {'value': int(a[0])}
elif cmd == 'color': c, d = 'colorwc', {'color': {'r': int(a[0]), 'g': int(a[1]), 'b': int(a[2])}, 'colorTemInKelvin': 0}
elif cmd == 'temp': c, d = 'colorwc', {'color': {'r': 0, 'g': 0, 'b': 0}, 'colorTemInKelvin': int(a[0])}
else: sys.exit(__doc__)
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
if cmd == 'status':
    r = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); r.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); r.bind(('', 4002)); r.settimeout(3)
s.sendto(json.dumps({'msg': {'cmd': c, 'data': d}}).encode(), (ip, 4003))
if cmd == 'status':
    try: print(json.loads(r.recvfrom(4096)[0])['msg']['data'])
    except socket.timeout: print('no reply')
