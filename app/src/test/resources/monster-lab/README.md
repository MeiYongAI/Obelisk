# Monster Lab fixtures

Extracted on 2026-11-21 from the supplied `The HentaiVerse（ml）.html`,
`The HentaiVerse（ml-create）.html`, the thirteen `ml-create-{species}` pages,
`The HentaiVerse（ml-slot）.html` and `The HentaiVerse（ml-pane=skills）.html`.
The added `unnamed-slot.html` and `unnamed-list.html` come from
`The HentaiVerse（新创建未命名怪物）.html` (`?s=Bazaar&ss=ml&slot=59`) and
`The HentaiVerse（新创建未命名怪物1）.html` (`?s=Bazaar&ss=ml`).

Only `#monster_outer`, its external operation forms, and relevant inline
handlers are retained. Image paths are local placeholders. Monster names are
replaced by `Fixture Monster {slot}` (the server's `Unnamed Celestial` label is retained); the deletion secret is replaced
by `fixture-delete-token`. Embedded background images and unrelated navigation,
user status, recovery forms and scripts are removed. Slots, class IDs, combat
stats and costs remain unchanged so parser assertions reflect the supplied DOM.

Verified protocol evidence:

- Class selection posts the existing `create_form` with `selected_class`; its
  `selected_patk` remains empty. Creation posts the selected attack from the
  actual `create_monster(...)` control. Each race keeps its supplied choices.
- Rename uses `rename_form` / `rename_monster`; feeding uses `food_form` /
  `food_action`. The supplied stats page has an enabled food button and a
  disabled Happy Pills image. The original named monster's page has a deletion
  form and function but no delete control, so no delete action is exposed there.
- The new monster occupies slot 59 at level 0; slot 60 remains empty. Its
  activation notice requires powerlevel 25 and a name. There is no rendered
  rename control yet, despite a hidden rename form and handler, so the native
  page keeps naming unavailable. Its actual skills link retains slot 59 and
  `pane=skills`; the destination's contents have not been supplied.
- The new monster has a real delete control. Its action requires that control,
  the POST form, and the captured `do_delete()` assignment and confirmation;
  the irreversible confirmation is preserved. No deletion is performed by tests.
- Crystal and Chaos upgrades submit their actual stat/perk key and count.
  The form supplies count 1; the inline Shift/Ctrl handlers supply 5 and 10.
  Displayed costs are for the next upgrade only; no batch total is inferred.
  The new monster's Scavenging cost uses singular `Chaos Token`; other perks
  remain disabled until their displayed powerlevel requirements are met.
- Skills are one `skillform` with `update_skills=1`, three name fields and six
  type/element values. Only properties with a real `show_properties(...)`
  control are editable; options come from their `set_property(...)` handlers.
  Fixed hidden values and blank names (which preserve game defaults) are kept.

This covers the supplied named and newly created monsters' detail layouts and
the original named monster's skills layout. Other species' skill-option layouts,
the unnamed monster's skills contents, its first-name control after reaching
powerlevel 25, and operation response pages have not been supplied. Tests alter selected
handlers and URLs only to check parsing boundaries; those are synthetic cases.
No real creation, rename, feeding, upgrade, skill save or deletion was submitted.
