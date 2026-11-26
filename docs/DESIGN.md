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

## 6. Not yet

- Deleting for good. When it comes, RR-10's confirmation and preview come with it.
- Comments, the library's own sort order, sharing a file out of the app, uploading.
- A release build signed for Google Play's internal testing.
