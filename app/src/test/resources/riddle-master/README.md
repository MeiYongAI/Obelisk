# RiddleMaster form and response fixtures

Extracted on 2026-11-21 from the user-supplied
`The HentaiVerse （小马提交表单）.html`. Only the actual `#riddlemaster`
subtree and the first original inline timer/selection-rule script are kept.
The later injected script variant, prior battle DOM and unrelated user scripts
are excluded. Absolute script times are replaced by fixture values that retain
their original 30-second difference; the saved visible counter remains 8.

The form posts to `https://alt.hentaiverse.org/?s=Battle&ss=ar`, with repeated
`riddleanswer[]` checkboxes and `riddlesubmit` equal to the actual submit input's
value. This captured form has no hidden token. Manual button submission permits
one to three selected checkboxes. Parsing and model methods do not pick answers,
execute scripts, trigger a timer submission or perform any HTTP request.

The original image source was rewritten by browser Save to
`./The HentaiVerse_files/riddlemaster`. Its accompanying file is a JPEG, not a
base64 URI. No original remote URL can be recovered from that saved path, so
the parser retains it as an offline source and does not invent an endpoint.
The challenge image is not duplicated in the fixture. Arbitrary option values
used in request tests check encoding only and are not answers to the image.
The supplied official reference image URL is retained.

Two further fixtures come from the actual Isekai GrindFest sequence:

| Fixture | Source |
| --- | --- |
| `isekai-manual-form.html` | `The HentaiVerse小马1.html` |
| `isekai-after-submit.html` | `The HentaiVerse提交小马1后.html` |

The new form posts to `https://alt.hentaiverse.org/isekai/?s=Battle&ss=gr` and
contains no prior battle root. It keeps the original form and selection/timer
script; user-script images attached to option labels are omitted. Its challenge
image still has a browser-rewritten local path, so no remote image URL is inferred.

The captured response is an active battle page with an empty completion pane,
enabled battle controls and the `Blessing of the RiddleMaster` effect. The
response fixture keeps the relevant action, effect and log panes and substitutes
`fixture-battle-token` for the runtime token. A user-script `hv-battle-text`
wrapper in the first log line contained Chinese text in a closed shadow-root
template and the original English in its light DOM. The fixture removes that
translation wrapper/template and retains the English source log. The Chinese
translation is not treated as part of the server response.

These samples establish the returned battle state without selecting answers or
submitting the form. Incorrect-answer and timeout responses still need actual
HTML. The earlier Persistent document contains an old `#battle_main` alongside
the RiddleMaster page, so page dispatch must continue to check RiddleMaster first.
Alternate image URLs, additional query parameters, hidden fields, disabled
states and malformed cases in tests remain derived protocol checks.
