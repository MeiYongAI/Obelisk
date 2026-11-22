# Battle completion fixtures

Extracted on 2026-11-21 from these user-supplied battle pages:

| Fixture | Source |
| --- | --- |
| `item-world-next-round.html` | `The HentaiVerse（一轮战斗结束的页面-显示下一轮提示）.html` |
| `item-world-victory.html` | `The HentaiVerse（道具界战斗胜利结束页）.html` |
| `item-world-defeat.html` | `The HentaiVerse（死亡-战斗失败页）.html` |
| `item-world-flee.html` | `The HentaiVerse（逃离成功页）.html` |
| `grindfest-defeat.html` | `The HentaiVerse（GF战败）.html` |

For the four Item World fixtures, the entire `#mainpane` is retained because `#pane_completion` is inside the
`#battle_top` sibling of `#battle_main`. Scripts were removed, the runtime token
was replaced by `fixture-battle-token`, local image paths were reduced to
placeholders, and equipment IDs in completion URLs were replaced by `100001`
and `100002`. The control syntax and query structure are unchanged.

The next-round control calls `battle.battle_continue()`. The corresponding
saved `hvc.js.下载` defines this as reloading the current document. Victory,
defeat and flee controls instead assign the literal `document.location` URL,
which returns to the Armory equipment modification page in these samples.
The parser must read each actual control; Armory is not a global completion
destination. No form or POST is present in these completion controls.

The four Item World pages preserve their original completion controls. The
separate GrindFest defeat fixture extracts the actual completion and action
panes into a minimal battle document. Its original control calls
`common.goto_grindfest()`: the matching saved `hvc.js.下载` assigns
`MAIN_URL + "?s=Battle&ss=gr"`, and this sample's `MAIN_URL` is
`https://alt.hentaiverse.org/isekai/`. The expected return therefore remains
Isekai GrindFest. The injected `#jpx-final-revenue` report and its plugin layout
attributes are removed when extracting the fixture; they are not server
content or an application parsing requirement. The saved completion text was
already translated by a userscript and is retained as captured, not described
as original server wording. The battle token and resource paths are sanitized
as above.

The user separately confirmed defeat returns to the same mode for Arena and
Ring of Blood. Their route cases still replace only the Item World fixture's
target and are derived protocol checks, not actual result HTML. The older
derived GrindFest route check is now supplemented by the real defeat fixture.
Arena and Ring of Blood result HTML and Tower's destinations still need actual
samples. JSON pane
merge tests use these real HTML fragments inside a synthetic envelope based on
the saved script contract; no live battle JSON response was captured. No real
battle, item use or challenge was performed during this work.
