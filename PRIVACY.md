# Roost Remote — Privacy policy

*Effective 27 November 2026. Published by Digitalku.*

Roost Remote opens a Roost library kept on a computer you own, over your own network or your
Tailscale. It has no server of its own. Digitalku receives nothing from it.

## What the app sends, and where

The app talks only to the address you type when you add a library. That address is the Roost
application on your computer. Nothing goes anywhere else: no analytics, no crash reporting, no
advertising, no account, and no update check.

What it sends to that address:

- **The library's password, once, when you pair.** The computer answers with a device token. The
  password is not kept on the phone.
- **This phone's name**, as you typed it when pairing, so that the computer can list the phone and
  let you unpair it.
- **The requests you make**: browsing, searching, and the edits you make to tags, ratings, folders,
  notes and the trash.
- **The files you choose to add**, from the `+` button or from *Share* in another app, with each
  file's name and modification time. The app never sends a file you did not pick.

The connection is plain HTTP. On your own Wi-Fi, what crosses it is not encrypted. Through
Tailscale it is. The app says so on the pairing screen.

## What the app keeps on the phone

- For each library you add: its name, its address, and its device token. The token is encrypted
  with a key held in the Android Keystore, which cannot be read out of the phone.
- Your theme choice.
- Pictures it has already shown, in a cache that Android may clear at any time.

None of this is backed up to Google or copied to another device. Removing a library from the app
deletes its address and token. Uninstalling the app deletes everything it kept, the cache
included.

## Permissions

- **Network**, to reach the computer.
- **Notifications and a foreground service**, so that files you add keep sending with the app
  closed, with progress and a Cancel button.

The app asks for no storage, photo, camera, contacts or location permission. A file you add is
picked through Android's own file picker, which gives the app that one file only.

## Children

Roost Remote is not directed at children and collects nothing from anybody.

## Changes

A change to this policy is published at the same address, with a new effective date.

## Contact

roostsupport@digitalku.com
