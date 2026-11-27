# Design

What the app is made of, and why. The protocol is not described here: it is `docs/REMOTE.md` in the
Roost repository, and that document is normative for this app (its `RR-n` are cited in the code).

## 1. Shape

One module, `app`. Kotlin, Jetpack Compose with Material 3, minimum Android 10 (API 29), target API
36, compiled against API 37.2, which Compose and Coil require.

| | |
|---|---|
| `net/Wire.kt` | The contract's shapes again, in Kotlin, only the fields used. Unknown fields are ignored |
| `net/Remote.kt` | `Server` (hello, pair), `Session` (requests, events, byte addresses), `Bearer` |
| `net/LocalAddress.kt` | Which addresses the app will talk to, and the interceptor that enforces it |
| `store/Vault.kt` | The device token sealed with an AES-GCM key in the Android Keystore |
| `store/Libraries.kt` | The paired libraries, in private preferences, keyed by library id |
| `ui/` | The four screens: libraries, pairing, a library, an asset |

Dependencies: OkHttp (every request, and the event stream), kotlinx.serialization, Coil 3 for
pictures, Media3 for video and audio. There is no navigation library: three places in a `when`.

## 2. One HTTP client

`RoostRemote.http` is the one `OkHttpClient`. The app's requests, Coil's pictures and Media3's video
all go through it. A picture or a video is built from a URL alone, so `Bearer` adds the open
library's token to any request for that library's host and port. A request elsewhere carries no
token.

## 3. Plain HTTP, local addresses only

The protocol is plain HTTP (RR-3). RR-3 asks for cleartext to be allowed for the server's hosts
only. A network security configuration can only name hosts known when the app is built, and these
addresses are typed by the owner. So the configuration allows cleartext, and `LocalOnly`, a network
interceptor, refuses any connection whose **reached** address is not one the server would answer:
loopback, the private IPv4 ranges, link-local, unique-local IPv6, and `100.64/10` for Tailscale. It
runs after the socket connects and before the request is written, so it holds for a name as much as
for a number, and nothing is sent to a public address, the password least of all.

## 4. The token

The password is typed once, sent once, and dropped when pairing answers (RR-6). The token is sealed
with a Keystore key that never leaves the phone, and the app has no backup (`allowBackup="false"`
and data extraction rules that exclude everything). A restored copy could not be opened anyway.

A library is remembered by its id, not its address. An address that answers with a library this
phone is paired with, and whose token still works, moves the library there without asking for the
password: the computer knows the phone, not the address.

`401 not_paired` anywhere forgets the token and offers to pair again (RR-7).

## 5. Staying current

`LibraryModel` reads the event stream for as long as the library is on screen, and reconnects with a
growing wait when it drops. Every connection, the first one included, re-reads the grid and the pane
(RR-12). `library:changed` re-reads after 250 ms, so a burst of edits is one re-read. A re-read asks
for as many rows as the grid holds, so it does not send the owner back to the top.
`assets:derived` patches the cards it names: their thumbnail address moves with `derivedAt` (RR-14).

Edits are re-read, not drawn from what was sent, as in the window: the engine decides what a tag
becomes.

## 6. Adding files

RR-15 to RR-17, from REMOTE.md revision 1.1 (ADR-0073 in Roost). The `+` button shows only when
`hello` says `upload: true`.

- **WorkManager, one batch per pick or share**, queued per library (`upload-<library id>`,
  `APPEND_OR_REPLACE`), so batches go one after another and survive the app being closed. A batch
  runs as a foreground service of type `dataSync`, with a notification that shows progress and
  **Cancel**. Cancelling the work cancels the OkHttp call, which closes the connection, which is
  how the server knows (RR-16).
- **One file per request**, its bytes streamed from the `ContentResolver` (`ContentBody`), never
  held. The length must be known first; a file whose provider says nothing of its size is not sent.
  A dropped connection is retried from the start, three times. A refusal is not retried, and a 401
  forgets the token and stops the batch (RR-7).
- **A batch's answer never reuses a key of its question.** WorkManager hands one batch's output to
  the next in the queue as input, and a shared key is overwritten: the first build put `folder` in
  both, and every batch after the first went to the first batch's folder.
- **A shared file is opened at once** (`Held`). A share's read grant belongs to the screen it was
  handed to, which closes before the work starts, so the screen opens each file and leaves the open
  descriptor in the process for the work to take. A process that dies first loses them, and the
  notification says so.
- **The system's file picker, not the Photo Picker**, for *Photos and videos* as for *Other files…*,
  filtered to `image/*` and `video/*`. The Photo Picker answers `<media id>.<ext>` for
  `DISPLAY_NAME`, so a photograph picked there arrived named `24`; a library is searched by name.
  The file picker keeps the name and needs no storage permission either (the owner's choice,
  2026-11-27).
- The date sent is `DATE_TAKEN` where the media store knows it, the document's last modification
  otherwise.

## 7. Settings

The gear on the list of libraries opens the app's own settings, kept in its private preferences
(`store/Settings.kt`) and nowhere in a library. For now there is one: the theme, **Follow the
system**, **Light** or **Dark**, the system's by default. Two things follow from it:

- The system bars' icons follow the app's theme, not the system's (`enableEdgeToEdge` with the
  style chosen), or a light app on a dark system draws white icons on white.
- The manifest lists `uiMode` in `configChanges`, so a theme changed in the system's quick settings
  recolours the screen in place rather than recreating the activity, which would close the library
  on screen.

The viewer is black in both themes.

## 8. Finding a place

The window's ways of reaching a folder or a tag, drawn for a phone:

- **Folders in the grid** (ADR-0043, ADR-0057): inside a folder its subfolders come first, as tiles
  of their four newest pictures (`folder:covers`); a typed search draws the folders whose names it
  finds (`folder:find`), each with the folder it sits in, because names repeat. A tile opens its
  folder. Its key in the grid is the folder's id, a number, because a lazy list's key has to fit in
  a `Bundle` and the thumbnails asked for on screen are the string keys alone.
- **The drawer's search**, behind a magnifier as the window's is (ADR-0033 §6): folders keep the
  tree's shape, a match with its ancestors, and smart folders and tags are filtered by the same
  rule, every word typed starting a word of the name. A field as the drawer's first control would
  take the focus each time the drawer opens, and raise the keyboard with it.
- **A tag or folder on a file** is a chip with a menu: go to it, in the grid's place, or take it
  off the file. *Add tag…* and *Add to folder…* open pickers searched the same way; the tag picker
  stays open for several, and makes a new tag from what is typed when none is called that.

Everything is matched in the app, over the pane's own rows (`ui/Find.kt`), except the grid's
folders: what a typed query finds is the engine's to say, because the app reads no RQL.

## 9. The filter bar

Tags, Rating and Type, as chips over the grid (ADR-0035), each opening a sheet of what to tick:
tags found by typing with **Any of them** or **All of them**, the five ratings and unrated, and the
extensions. The bar is a view of the query, as the window's is. A tick is sent as values, and
`query:join` writes the query; the chips are read back out of it with `query:split`, so the search
field shows what the bar did and a query typed there ticks the bar. The app writes no RQL of its
own. A sheet draws its ticks from a copy it holds, so a tick shows at once rather than after the
round trip, and its lists are what the place holds (`facets:vocabulary`), read as it opens.

Two of the window's rules come along. A place chosen from the pane, a tile or a chip **keeps the
filters standing**: its query is joined with them. And the tag rule is remembered by the app below
two tags, where the query cannot say it (ADR-0035 §7.2). A folder narrowed by the bar draws no
subfolder tiles (ADR-0043).

## 10. The video transport

`PlayerView`'s own controls sat inside the pager, which took every sideways drag, so the seek bar
could not be dragged. The transport is drawn in Compose instead, over a `PlayerView` with no
controller: play and pause, the time, and a `Slider`, which takes its drag before the pager sees
it. A drag anywhere else on the picture still swipes to the next file, and a tap shows and hides the
chrome as it does over a picture.

## 11. Not yet

- Deleting for good. When it comes, RR-10's confirmation and preview come with it.
- Comments, the library's own sort order, sharing a file out of the app.
