# 📦 ZSticker — Share custom sticker to Zalo
(Currently is a fork of Zaticker)

Warning: I do not own or linked with the removebg branch. I use the BRIA-RMBG-2.0 model at https://huggingface.co/spaces/briaai/BRIA-RMBG-2.0. Also, the URL I use: https://briarmbg20.vercel.app/ is just a proxy to connect Android with HuggingFace API and I don't collect any data or care about who you are through it. If you're not sure, here's the endpoint source code: https://github.com/huanhoahongso3-collab/briarmbg2.0

Support min SDK 27 (Android 8.1). Recommend using SDK 34 (Android 14).

App support English, Vietnamese, Russian and Chinese Simplified.

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

Warning: GPL-3.0 only applies to the code of this project. All assets of this project (logo file, additional png and html) is copyright and must not be used for any reason without author's approval (vector xml is allowed).

This warning applys to all versions and all branches of this program from its very first day
