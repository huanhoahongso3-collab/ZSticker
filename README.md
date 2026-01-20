# 📦 ZSticker — Share custom sticker to Zalo
(Currently is a fork of Zaticker)

Warning: removebg branch. Need to use third-party API to remove background. Still in beta because API longevity is not sure. 

Support min SDK 26 (Android 8.0). Recommend using SDK 34 (Android 14)

ZSticker is a vibe-driven coding project focused on how Zalo handles sticker share by Intent in Android.

It is inspired by how Zamoji - a VNG app can send sticker to Zalo

Released under **GPLv3** to keep it open.

---

## Project Goals

- Allow users to test custom sticker packs with Zalo
- Log successes and failures during sticker sending

---
## How I found it:
- I started using ADB and logcat to observe how Zamoji sends stickers to Zalo
- Then, I also deep in Zamoji source code using reverse engineering to see how the intent is sent to Zalo to send stickers

## Status Overview

> **STATUS:** Functional. Usable for normal uses, but bugs are still existed

Working features include:

- Share intents (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) directly to Zalo
- Export stickers when needed
- Import single and multiple images both in the app and share intent
- New Material Design 3 from Google

## License
ZSticker Copyright (c) 2026

Licensed under the GNU General Public License v3.0 (GPL-3.0)
