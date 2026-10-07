# Third party notices

The app's own code is MIT licensed; see `LICENSE`. The Bible texts bundled with
it are separate works under their own terms, listed here. This file used to be
two lines inside `LICENSE`, which both broke licence detection and understated
the position: not every bundled translation is public domain.

Each edition ships exactly one translation, except the English edition, which
ships two.

| Edition | Translation | Terms |
| --- | --- | --- |
| `en` | World English Bible | Public domain |
| `en` | Berean Standard Bible | Public domain |
| `es` | Reina-Valera 1909 | Public domain |
| `fr` | Louis Segond 1910 | Public domain |
| `pt` | Bíblia Livre 2018 | CC BY 4.0 |
| `ru` | Синодальный перевод (Synodal) | Public domain |
| `ar` | ترجمة فان دايك (Van Dyck) | Public domain |
| `hi` | Indian Revised Version 2017 | CC BY-SA 4.0 |
| `sw` | Unlocked Literal Bible (Kiswahili) | CC BY-SA 4.0 |
| `zh` | 和合本 1919 (Chinese Union Version) | Public domain |

The two CC BY-SA 4.0 texts carry a share-alike condition. It attaches to the
text itself, not to the app's code, so adapting those translations means
releasing the adaptation under the same licence. Redistributing the app as it
stands is fine.

CC BY 4.0 and CC BY-SA 4.0 both require attribution. Attribution is shown in
the app on the Pass it on screen, and is set per edition by the
`about_translation` string in `app/build.gradle`.

The texts are converted from their upstream USFM distributions by
`tools/transform.js`, which does not alter the wording.
