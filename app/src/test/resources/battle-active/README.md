# Active battle fixtures

Extracted from the user-supplied `The HentaiVerse（实际战斗页rb1）.html` and
`The HentaiVerse（遭遇战实际战斗页）.html` on 2026-11-21. The complete `#mainpane`
is retained, including `#battle_main` and its `#battle_top` sibling; image paths
are local placeholders, and the runtime token is replaced. `#battle_top` holds
the player effects and completion panes and must not be dropped from snapshots.

The saved original `hvc.js.下载` lines 29–44 define tooltip arguments, action
modes, JSON pane updates, log insertion order and `battle_continue` (reload).
Neither active snapshot contains a completed-round pane. Real Item World
completion HTML is preserved separately in `../battle-completion/`; JSON merge
tests still use a synthetic envelope to verify the documented update contract
and are not captured live responses. No real challenge, item use or battle was
performed.
