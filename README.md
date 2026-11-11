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
* **Cắt GIF:** Giữ lâu một GIF rồi chọn *Cắt*: GIF vẫn chuyển động ngay dưới khung cắt, có nút phát/tạm dừng và thanh thời gian để tua từng khung hình; vùng cắt áp dụng cho mọi khung hình.
* **Yêu thích & thư mục:** Gắn sao cho nhãn dán, tạo thư mục và lọc nhanh bằng các nút *Tất cả / Yêu thích / thư mục* phía trên lưới (giữ lâu một thư mục để đổi tên hoặc xóa).
* **Chọn nhiều:** Giữ lâu một nhãn dán rồi chọn *Chọn* để thao tác hàng loạt: yêu thích, chuyển thư mục, thêm viền, xuất ra thư viện hoặc xóa.
* **Trình chỉnh sửa:** Giữ lâu một nhãn dán ảnh tĩnh rồi chọn *Chỉnh sửa*: tẩy, cọ **khôi phục** (lấy lại phần ảnh gốc đã bị xóa nền; ảnh gốc được giữ trên thiết bị sau khi xóa nền), vẽ, thêm chữ, hoàn tác/làm lại, thu phóng bằng hai ngón. Kết quả lưu thành nhãn dán mới.
* **Sao lưu `.zsticker`:** Sao lưu là một tệp zip thông thường (đuôi `.zsticker`, không có mật khẩu). Bản sao lưu từ phiên bản cũ (`.zip`, có mật khẩu) khôi phục bằng nút *Nhập bản sao lưu cũ*.
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
* **GIF crop:** Long-press a GIF and choose *Crop*: the GIF keeps playing under the crop frame, with a play / pause button and a timeline to scrub through frames; the chosen region is applied to every frame.
* **Favorites & folders:** Star stickers, create folders, and filter with the *All / Favorites / folders* chips above the grid (long-press a folder chip to rename or delete it).
* **Multi-select:** Long-press a sticker and choose *Select* to act on many at once: favorite, move to a folder, add a border, export to the gallery, or delete.
* **Editor:** Long-press a still sticker and choose *Edit*: eraser, a **restore** brush (brings back parts of the original photo that background removal cut away; the original is kept on the device after background removal), drawing, text, undo / redo, and two-finger zoom. The result is saved as a new sticker.
* **`.zsticker` backups:** Backups are plain zip files (`.zsticker`, no password). Backups from older versions (`.zip`, password-protected) are restored with the *Import legacy backup* button.
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

### 📜 License & Asset Copyright

ZSticker is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

⚠️ **IMPORTANT NOTICE ON GRAPHICAL ASSETS:**
* The GPL-3.0 license **applies strictly to the source code**.
* All visual assets (including logos, extra PNGs, and HTML files) are copyrighted property. They **may not be reused or redistributed** for any purpose without explicit written approval from the author (Vector XML files are exempt).
* This restriction applies retroactively to all past and future versions, tags, and branches of this project.

---

*ZSticker Copyright (c) 2026*
