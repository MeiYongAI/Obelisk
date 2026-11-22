Fixtures retain the `#mainpane` and relevant inline scripts from the user-supplied
`The HentaiVerse（rb/gr/iw/tw）.html` pages. Form tokens are replaced with
`fixture-post-token`, equipment identifiers with 1001–1006, and the unrelated
credit balance with a synthetic value. No account Cookie is included.

Request semantics come from the supplied scripts:

- rb: `init_battle(id, entrycost)` writes `initid` and submits `#initform`.
- gr/tw: `init_battle(id)` writes `initid` and submits `#initform`.
- iw: `hveqc.js.下载` uses single equipment selection, checks each material against
  `itemdata`, then submits `#equipform`; `eqitems` provides battle details and costs.

The Tower fixture has only `startchallenge_d.png` and no actionable `onclick`.
Tests verify that the app does not invent an entry action for that state.
No test sends network requests or starts an actual challenge.

`ar-low-level.html` and `rb-low-level.html` retain only `#mainpane` from
read-only GET responses obtained on 2026-11-21 using the emulator's existing
session. The form tokens are replaced with `fixture-post-token`. Arena has a
real First Blood challenge encoded with bitmap-font elements; Ring of Blood
has a full-width server message indicating no challenges at the current level.
