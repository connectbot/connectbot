# Manual E2E checklist: tmux integration

Automated coverage lives in `app/src/test/kotlin/org/connectbot/service/tmux/`
(protocol parser against recorded `tmux -C` transcripts, session-manager state
machine, pane terminals, input encoding) and the Compose tests for the tab
strip, window strip, dialogs, and palette. The items below need a real device
and a real tmux server.

## Test rig

```sh
# Disposable sshd + tmux reachable from the device/emulator:
docker run -d --name cb-tmux -p 2222:22 \
  -e USER_NAME=test -e USER_PASSWORD=test \
  lscr.io/linuxserver/openssh-server:latest
docker exec cb-tmux apk add tmux
docker exec cb-tmux sed -i 's/AllowTcpForwarding no/AllowTcpForwarding yes/' \
  /etc/ssh/sshd_config && docker restart cb-tmux
```

Connect from ConnectBot to `test@<host-ip>:2222`. For version-tier testing,
repeat with an image pinning tmux 2.6 (e.g. Ubuntu 18.04) and 3.1
(Ubuntu 20.04) — below 2.6 the integration must silently disable.

## Checklist

### Discovery & attach
- [ ] Host without tmux: plain terminal, no tabs, no offer, nothing logged as an error.
- [ ] Host with tmux, no sessions: offer banner appears; **Start session** creates
      `connectbot`, its tab appears and attaches; dismiss (X) hides for this
      connection only; host-editor "tmux integration" off ⇒ no probe at all.
- [ ] Host with existing sessions: every session appears as a dimmed tab with a
      snapshot; tapping one attaches in ≲1.5 s and lands on the server-side
      active window/pane.

### Rendering & input
- [ ] vim/htop render correctly in an attached pane; server-side pane width is
      preserved with the font auto-fitted (letterboxed, not rewrapped).
- [ ] Typing round-trips (send-keys hex): shell editing, Ctrl-C, Esc in vim,
      sticky modifiers from the extra keyboard, IME word input, emoji + CJK output.
- [ ] Paste 100 KB into `vim` in paste-aware mode: arrives as one bracketed paste.
- [ ] Bell (`printf '\a'`) in the viewed session beeps; in another window it
      badges the strip.

### Navigation
- [ ] Window strip taps switch windows; edge swipe cycles windows; surface swipe
      cycles panes of a split window; dots reflect position; volume keys cycle
      panes (pref on) or fall through to windows on single-pane windows.
- [ ] Android back gesture still works on plain tabs; on tmux tabs it works
      outside the edge-swipe band.

### Persistence (the subway test)
- [ ] Airplane mode 30 s mid-vim, disable: connection reconnects and silently
      reattaches to the same session/window/pane; vim intact; no duplicate
      scrollback.
- [ ] Force-stop the app, relaunch, reconnect host: lands back in the last
      viewed target (Host.tmux_last_target).
- [ ] Closing a session tab (menu → Detach) leaves it running server-side;
      kill-session removes the tab after confirm.

### Multi-client
- [ ] Desktop `tmux attach` to the same session while the phone views it:
      desktop resize reflows and the phone re-fits the font without fighting;
      "Resize to my screen" warns first, then reflows the desktop too.
- [ ] `tmux kill-session` from the desktop: the phone tab disappears with no crash.

### Floods & flow control (tmux ≥ 3.2 and < 3.2)
- [ ] `yes` in a viewed pane: UI stays responsive; on ≥3.2 the pane pauses and
      resyncs (brief reset, then current content); on <3.2 the channel throttles
      but other tabs still switch.
- [ ] `yes` in a *background* window: viewed pane unaffected; activity badge
      appears.

### Alerts
- [ ] Bell in an unattached session: badge within ~10 s while the app is open;
      with the app backgrounded (and bell notifications enabled) a notification
      arrives within ~60 s and tapping it lands on that session and window.

### Management
- [ ] Rename session/window (incl. names with spaces, quotes, `$`), new window
      via [+], move left/right, kill window — all reflected server-side
      (`tmux list-windows` on the desktop).
- [ ] Palette: `list-windows` shows output; a bogus command shows the parse
      error in red; history tap refills the field.
- [ ] "Load earlier history" deepens scrollback (run `seq 1 5000` first;
      after loading, line 1 is reachable).
