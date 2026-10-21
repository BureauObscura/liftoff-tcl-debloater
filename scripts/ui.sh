#!/usr/bin/env bash
# ui.sh — print every labelled thing on the TV screen; [*] marks the focused one. Handy for agents
# navigating menus with key presses (scripts/key.sh) instead of guessing from screenshots.
source "$(dirname "$0")/lib.sh"
need_tv
X="$(mktemp)"
sh_tv uiautomator dump /sdcard/liftoff-ui.xml >/dev/null && adbtv pull /sdcard/liftoff-ui.xml "$X" >/dev/null
python3 -I - "$X" <<'PY'
import sys, xml.etree.ElementTree as E
for n in E.parse(sys.argv[1]).iter('node'):
    l = n.get('text') or n.get('content-desc')
    f = n.get('focused') == 'true'
    if l or f: print(('[*] ' if f else '    ') + (l or '(unlabelled)').replace('\n', ' | ') + '  ' + n.get('bounds'))
PY
rm -f "$X"
