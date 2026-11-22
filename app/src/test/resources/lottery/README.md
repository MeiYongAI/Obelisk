# Lottery fixtures

`past-weapon.html` and `past-armor.html` are reduced, token-sanitized excerpts of the supplied previous-period weapon and armor lottery pages. Their reward, winner, and navigation values are preserved.

`after-purchase.html` contains the form and purchase, opt-out, and golden-ticket controls from `The HentaiVerse（购买彩票后）.html`. Authentication tokens are replaced with `fixture-lottery-token`; local asset directory prefixes and presentation styles are omitted.

The supplied after-purchase page was saved after JPX had replaced the lottery text with Chinese. For this fixture only, these three fields are normalized back to their corresponding English templates in `JPX-PLUS/HentaiVerse-Chinese.user.js`, keeping the captured numbers:

- `你目前拥有 3,985,289 GP.` → `You currently have 3,985,289 GP.`
- `购买一张彩票将花费 1,000 GP.` → `Each ticket costs 1,000 GP.`
- `你拥有 10 / 13,002 张已售出的彩票.` → `You hold 10 of 13,002 sold tickets.`

Other retained text remains as captured. This normalization is test-fixture preparation, not a production-parser rule or a claim that the saved file contains unmodified English server HTML. The tests verify the existing submission fields and enabled controls without making a purchase.

The zero-GP/no-ticket case in `BazaarSectionsParserTest` uses the observed server values and the original disabled opt-out tooltip, `You cannot opt out unless you have at least one ticket.`
