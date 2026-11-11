# 📦 ZSticker — Share Custom Stickers to Zalo
*(Forked from Zaticker)*

[ Tiếng Việt ](#-tiếng-việt) | [ English ](#-english)

---

## 🇻🇳 Tiếng Việt

ℹ️ **Trạng thái dự án:** Các lỗi giao diện chính đã được khắc phục. Mặc dù tần suất cập nhật có thể chậm do lịch trình cá nhân, ứng dụng vẫn hoạt động ổn định và API lấy từ Zamoji (từ năm 2018) vẫn hoạt động bình thường tính đến tháng 3/2026.

---

### 💡 Ý tưởng & Nguồn gốc

ZSticker là một dự án lập trình ngẫu hứng (vibe-coding), ra đời nhằm nghiên cứu cách Zalo xử lý việc chia sẻ sticker qua Android `Intent`.

Dự án lấy cảm hứng từ **Zamoji** — ứng dụng chính thức của VNG cho phép gửi sticker trực tiếp vào Zalo.
* **Cách phát hiện:** Dùng ADB và `logcat` để theo dõi luồng dữ liệu khi Zamoji gửi sticker sang Zalo, kết hợp dịch ngược (reverse engineering) mã nguồn Zamoji để phân tích cơ chế truyền Intent.

---

### 🎯 Mục tiêu dự án

- Cho phép người dùng thử nghiệm các bộ sticker tự tạo trên Zalo.
- Ghi lại log chi tiết (thành công / thất bại) trong quá trình truyền gửi sticker.

---

### ⚙️ Tính năng nổi bật & Trạng thái

> **Trạng thái:** Dùng tốt cho nhu cầu cơ bản (vẫn còn một số lỗi nhỏ).

* **Chia sẻ trực tiếp:** Hỗ trợ Android Share Intent (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) thẳng sang Zalo.
* **Nhập / Xuất linh hoạt:** Import ảnh đơn lẻ hoặc hàng loạt (trực tiếp trong app hoặc qua menu chia sẻ hệ thống); xuất gói sticker khi cần.
* **Tách nền ảnh (Background Removal):** Cung cấp 2 cơ chế (tùy chỉnh trong Cài đặt):
  * **Offline:** Chạy hoàn toàn trên thiết bị thông qua *Google MediaPipe Tasks Vision* (Mã nguồn mở, Apache 2.0). Dữ liệu hoàn toàn không rời khỏi thiết bị.
  * **Online:** Sử dụng mô hình *BRIA-RMBG-1.4* hosted trên HuggingFace Spaces. Endpoint proxy (`bria14proxy.vercel.app`) chỉ đóng vai trò trung gian kết nối giữa Android và HuggingFace API, hoàn toàn không lưu trữ dữ liệu hay thu thập thông tin cá nhân ([Xem mã nguồn proxy](https://github.com/huanhoahongso3-collab/BRIA-RMBG-1.4-PROXY)).
* **Cắt GIF:** Giữ lâu một GIF rồi chọn *Cắt* như với ảnh; vùng cắt áp dụng cho mọi khung hình.
* **Sao lưu `.zsticker`:** Sao lưu là tệp zip có mật khẩu (đuôi `.zsticker`, mật khẩu được nhúng lúc biên dịch từ CI, không có trong mã nguồn). Bản sao lưu từ phiên bản cũ (`.zip`) khôi phục bằng nút *Nhập bản sao lưu cũ*.
* **Giao diện:** Thiết kế theo chuẩn Google Material Design 3 (Material You).

---

### 📥 Tải về

* **Bản phát hành chính thức:** [Releases](https://github.com/huanhoahongso3-collab/ZSticker/releases) — ổn định, đã ký.
* **Bản dev:** bản [pre-release `dev`](https://github.com/huanhoahongso3-collab/ZSticker/releases/tag/dev) luôn là bản dựng mới nhất từ nhánh `main` (đã ký, thay thế sau mỗi lần cập nhật). Có thể chưa ổn định hoặc còn tính năng dang dở.

---

### 🛠️ Yêu cầu hệ thống & Ngôn ngữ

* **Android OS:** Hỗ trợ tối thiểu **Android 10 (SDK 29)**, khuyến nghị **Android 16 (SDK 36)**.
* **Đa ngôn ngữ:** Tiếng Việt, Tiếng Anh, Tiếng Nga, Tiếng Trung giản thể.

---

### 🔧 Cấu hình khi tự biên dịch

Mật khẩu của tệp sao lưu `.zsticker` được nhúng lúc biên dịch từ biến môi trường (hoặc gradle property), **không được ghi cứng trong mã nguồn**:

| Biến | Ý nghĩa |
|---|---|
| `ZS_BACKUP_PASSWORD` | Mật khẩu của tệp sao lưu `.zsticker` (để trống = không mã hóa) |

Trên GitHub Actions, đặt nó trong *Settings > Secrets and variables > Actions* (cùng tên). Hãy giữ giá trị này cố định giữa các bản dựng, nếu không bản sao lưu cũ sẽ không mở được. ⚠️ Mọi thứ nằm trong APK đều có thể bị trích xuất, nên mật khẩu này chỉ mang tính che giấu.

---

### 📜 Giấy phép & Bản quyền (License)

Dự án được phát hành theo giấy phép **GNU General Public License v3.0 (GPL-3.0)**.

⚠️ **LƯU Ý QUAN TRỌNG VỀ BẢN QUYỀN ASSET:**
* Giấy phép GPL-3.0 **chỉ áp dụng cho mã nguồn**.
* Tất cả tài nguyên đồ họa (assets) thuộc dự án — bao gồm logo, tệp PNG bổ sung và tệp HTML — đều thuộc bản quyền tác giả. **Không được phép tái sử dụng dưới bất kỳ hình thức nào** khi chưa có sự đồng ý bằng văn bản từ tác giả (ngoại trừ các tệp vector XML).
* Quy định này áp dụng cho mọi phiên bản và mọi nhánh (branch) của dự án từ ngày đầu tiên khởi tạo.

---

## 🇬🇧 English

ℹ️ **Project Status:** Core UI bugs have been fixed. While active updates may be infrequent due to personal schedules, the app remains functional and the Zamoji API integration (from 2018) continues to work seamlessly as of March 2026.

---

### 💡 Background & Origin

ZSticker is an exploratory, vibe-driven coding project focused on analyzing how Zalo handles sticker sharing via Android `Intent`s.

It was inspired by **Zamoji** — an official VNG application that integrates sticker sharing directly into Zalo.
* **Discovery process:** Monitored sticker sharing events using ADB and `logcat`, followed by reverse-engineering Zamoji's codebase to understand its Intent structure.

---

### 🎯 Project Goals

- Allow users to test custom sticker packs directly with Zalo.
- Log operational successes and failures during sticker dispatch.

---

### ⚙️ Features & Current Status

> **Status:** Functional for general use (minor bugs may still exist).

* **Direct Share:** Native support for Android Share Intents (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) targeting Zalo.
* **Flexible Import/Export:** Import single or batch images via in-app picker or system share menu; export custom packs on demand.
* **Background Removal:** Offers two configurable engines in Settings:
  * **Offline:** Powered on-device by *Google MediaPipe Tasks Vision* (Apache 2.0 open-source). Zero data leaves your device.
  * **Online:** Powered by the *BRIA-RMBG-1.4* model hosted on HuggingFace Spaces. The endpoint proxy (`bria14proxy.vercel.app`) acts strictly as a bridge between Android and the HuggingFace API — no personal data or images are logged or stored ([View proxy source code](https://github.com/huanhoahongso3-collab/BRIA-RMBG-1.4-PROXY)).
* **GIF crop:** Long-press a GIF and choose *Crop* like for photos; the chosen region is applied to every frame.
* **`.zsticker` backups:** Backups are password-protected zip files (`.zsticker`; the password is injected at build time from CI and is not in the source). Backups from older versions (`.zip`) are restored with the *Import legacy backup* button.
* **UI/UX:** Updated to Google Material Design 3 (Material You).

---

### 📥 Downloads

* **Official releases:** [Releases](https://github.com/huanhoahongso3-collab/ZSticker/releases) — stable and signed.
* **Dev build:** the [`dev` pre-release](https://github.com/huanhoahongso3-collab/ZSticker/releases/tag/dev) always holds the latest build of `main` (signed, replaced on every push). It may be unstable or contain unfinished features.

---

### 🛠️ System Requirements & Languages

* **Android OS:** Minimum **Android 10 (SDK 29)**, recommended **Android 16 (SDK 36)**.
* **Localization:** English, Vietnamese, Russian, and Simplified Chinese.

---

### 🔧 Build configuration

The password of `.zsticker` backups is injected at build time from an environment variable (or gradle property); **it is not hardcoded in the repository**:

| Variable | Meaning |
|---|---|
| `ZS_BACKUP_PASSWORD` | Password of `.zsticker` backups (empty = unencrypted) |

On GitHub Actions set it under *Settings > Secrets and variables > Actions* (same name). Keep the value the same across builds, or older backups will not open. ⚠️ Anything inside an APK can be extracted, so this password is obscurity, not secrecy.

---

### 📜 License & Asset Copyright

ZSticker is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

⚠️ **IMPORTANT NOTICE ON GRAPHICAL ASSETS:**
* The GPL-3.0 license **applies strictly to the source code**.
* All visual assets (including logos, extra PNGs, and HTML files) are copyrighted property. They **may not be reused or redistributed** for any purpose without explicit written approval from the author (Vector XML files are exempt).
* This restriction applies retroactively to all past and future versions, tags, and branches of this project.

---

*ZSticker Copyright (c) 2026*
