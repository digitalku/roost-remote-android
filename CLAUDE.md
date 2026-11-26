# CLAUDE.md — Roost Remote

**Roost Remote** is an Android client for a Roost library that a Roost window shares on the local
network (ADR-0072 in the Roost repository, `../roost`). Read this file, then `docs/DESIGN.md` and
`docs/TODO.md`, before doing anything else.

## The protocol is Roost's

`../roost/docs/REMOTE.md` is **normative** for this app, and its requirements `RR-n` are cited in
code comments by number. The request and answer shapes are `@roost/contract`'s
(`../roost/packages/contract/src/`). This app never invents a request. If it needs one the
allowlist does not have, that is a change to Roost and its ADR, not a workaround here.

## Language

Everything written to the repository is in **English**: code, comments, strings, commits, docs.
Conversation with the owner is in **Indonesian**.

## Rules

- The token is sealed by `Vault` and nothing else. The password is never stored, logged or kept
  in state after pairing answers (RR-6).
- Every connection goes through `RoostRemote.http`, so `LocalOnly` sees it. No second client.
- No analytics, no crash reporting, no network beyond the paired server.
- Edits are sent and then re-read, never applied locally from what was sent.
- Deleting for good needs RR-10's confirmation before it is added.

## Conventions

Kotlin, Compose, Material 3. Comments say why, not what. Tests are JVM unit tests in
`app/src/test`. Commit messages are imperative English, and **end at the body**: no
`Co-Authored-By`, no trailer naming an assistant.

## Commands

See `docs/SETUP.md`. `./gradlew assembleDebug testDebugUnitTest` before finishing.
