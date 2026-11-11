# Chính Sách Quyền Riêng Tư

_Last Updated: November 11, 2026_

## 📑 Điều hướng / Navigation

- [🇻🇳 Tiếng Việt](#-tiếng-việt)
  - [Quyền riêng tư là ưu tiên hàng đầu](#quyền-riêng-tư-là-ưu-tiên-hàng-đầu)
  - [Thu thập dữ liệu](#thu-thập-dữ-liệu)
  - [Truy cập Internet](#truy-cập-internet)
  - [Tính năng Xóa nền](#tính-năng-xóa-nền)
  - [Chế độ GIF động](#chế-độ-gif-động)
  - [Dịch vụ bên thứ ba](#dịch-vụ-bên-thứ-ba)
  - [Minh bạch mã nguồn mở](#minh-bạch-mã-nguồn-mở)
  - [Giấy phép mã nguồn mở](#giấy-phép-mã-nguồn-mở)
  - [Thay đổi chính sách](#thay-đổi-chính-sách)
  - [Liên hệ](#liên-hệ)

- [🇬🇧 English](#-english)
  - [Privacy First](#privacy-first)
  - [Data Collection](#data-collection)
  - [Internet Access](#internet-access)
  - [Remove Background](#remove-background)
  - [Animated GIF Mode](#animated-gif-mode)
  - [Third-Party Services](#third-party-services)
  - [Open Source Transparency](#open-source-transparency)
  - [Open Source Licenses](#open-source-licenses)
  - [Changes to This Privacy Policy](#changes-to-this-privacy-policy)
  - [Contact](#contact)


---

# 🇻🇳 Tiếng Việt

## Quyền riêng tư là ưu tiên hàng đầu

Ứng dụng này hoàn toàn là mã nguồn mở. Bạn có thể kiểm tra toàn bộ mã nguồn để xác minh cách ứng dụng hoạt động và cách dữ liệu của bạn được xử lý.

Chúng tôi tôn trọng quyền riêng tư của bạn. Ứng dụng này **không thu thập, lưu trữ, bán hoặc chia sẻ dữ liệu cá nhân của bạn**.

## Thu thập dữ liệu

Ứng dụng này:

- **Không** yêu cầu tài khoản.
- **Không** bao gồm dịch vụ phân tích hoặc theo dõi.
- **Không** bao gồm SDK quảng cáo.
- **Không** thu thập telemetry hoặc thống kê sử dụng.
- **Không** thu thập hoặc lưu trữ thông tin cá nhân trên máy chủ.

## Truy cập Internet

Hầu hết các tính năng của ứng dụng hoạt động hoàn toàn ngoại tuyến và **không yêu cầu kết nối Internet**.

Tính năng **Xóa nền (Remove Background)** có hai chế độ, có thể chuyển đổi trong Cài đặt:

- **Ngoại tuyến (Offline)**: xử lý hoàn toàn trên thiết bị bằng Google MediaPipe Tasks Vision, **không yêu cầu Internet** và hình ảnh không rời khỏi máy của bạn.
- **Trực tuyến (Online)**: yêu cầu kết nối Internet và gửi hình ảnh đến dịch vụ bên thứ ba như mô tả bên dưới.

Chế độ **GIF động** (không bật mặc định) cũng cần Internet để tải GIF đã nén lên máy chủ lưu trữ, xem [Chế độ GIF động](#chế-độ-gif-động).

## Tính năng Xóa nền

### Chế độ Ngoại tuyến (Offline)

Chế độ này sử dụng **Google MediaPipe Tasks Vision** (mã nguồn mở, giấy phép Apache 2.0) để xử lý hình ảnh trực tiếp trên thiết bị của bạn. Không có hình ảnh hoặc dữ liệu nào được gửi ra ngoài máy.

### Chế độ Trực tuyến (Online)

Chế độ này sử dụng mô hình AI **BRIA-RMBG-1.4** được lưu trữ trên Hugging Face.

### Tuyên bố

- Ứng dụng này **không liên kết, không được chứng nhận hoặc hợp tác** với RemoveBG hoặc bất kỳ công ty/nhánh nào có tên "removebg".
- Ứng dụng sử dụng mô hình **BRIA-RMBG-1.4** vì không có hạ tầng để tự lưu trữ mô hình.
- Hình ảnh được xử lý bởi Hugging Face Space công khai đang chạy mô hình BRIA-RMBG-1.4.

### Máy chủ Proxy

Để ứng dụng Android có thể giao tiếp với Hugging Face Space, các yêu cầu được chuyển tiếp qua:

**https://bria14proxy.vercel.app**

Proxy này chỉ tồn tại để **chuyển tiếp yêu cầu giữa ứng dụng Android và Hugging Face API**.

Proxy:

- **Không** thu thập thông tin cá nhân.
- **Không** lưu trữ hình ảnh tải lên.
- **Không** xác định danh tính người dùng.
- **Không** phân tích hoặc theo dõi hoạt động.
- **Không** sử dụng dữ liệu cho bất kỳ mục đích nào ngoài việc chuyển tiếp yêu cầu.

Mã nguồn proxy có thể được kiểm tra tại:

https://github.com/huanhoahongso3-collab/BRIA-RMBG-1.4-PROXY

Nếu bạn **không sử dụng chế độ Trực tuyến của tính năng Xóa nền**, ứng dụng không gửi hình ảnh hoặc dữ liệu qua proxy này.

## Chế độ GIF động

Tùy chọn **Chế độ gửi GIF** trong Cài đặt có hai giá trị:

- **GIF thường** (mặc định): GIF được gửi trực tiếp sang Zalo trên thiết bị. **Không có gì được tải lên.**
- **GIF động**: ứng dụng nén một bản sao của GIF và tải lên máy chủ lưu trữ để Zalo và người nhận có thể tải ảnh động qua URL. Bản gốc vẫn ở trên thiết bị của bạn.

> **Trạng thái:** máy chủ mặc định chưa được triển khai. Cho đến khi có, chế độ GIF động tự động quay về GIF thường và **không tải bất cứ gì lên**.

Khi chế độ GIF động hoạt động:

- Lần tải lên đầu tiên, ứng dụng sẽ hiển thị thông báo cho bạn biết GIF sẽ được tải lên.
- Máy chủ lưu **tệp nhãn dán đã chuyển đổi** (WebP động), đặt tên theo mã băm của tệp. Không có tên, tài khoản hay mã thiết bị nào được lưu cùng tệp.
- **Bất kỳ ai có đường dẫn đều xem được tệp.** Đừng tải lên nội dung riêng tư.
- Máy chủ **không lưu địa chỉ IP**. Bộ đếm giới hạn tốc độ chỉ dùng mã băm có muối của IP và mã cài đặt, và hết hạn trong vòng một ngày.
- Tệp tự động bị xóa sau thời gian lưu giữ (mặc định 90 ngày), hoặc sớm hơn khi có yêu cầu gỡ nội dung vi phạm. Máy chủ có đường báo cáo lạm dụng.
- Tệp trùng lặp chỉ được lưu một lần; ứng dụng ghi nhớ liên kết đã tải lên để tránh tải lại.
- Bạn có thể đặt **máy chủ của riêng mình** trong *Tùy chọn > Máy chủ GIF động (API)*. Khi đó GIF được gửi tới máy chủ đó và tuân theo chính sách của người vận hành; ứng dụng không gửi khóa API chính thức tới máy chủ tùy chỉnh.

Máy chủ chạy trên Vercel (lưu trữ: Vercel Blob; đếm giới hạn tốc độ: Upstash Redis) và là mã nguồn mở:

https://github.com/huanhoahongso3-collab/ZSticker-gif-proxy

Nếu bạn **không dùng chế độ GIF động**, ứng dụng không gửi GIF hay dữ liệu nào qua máy chủ này.

## Dịch vụ bên thứ ba

Ứng dụng chỉ sử dụng dịch vụ bên thứ ba sau khi bạn sử dụng chế độ Trực tuyến của tính năng Xóa nền, hoặc chế độ GIF động (khi máy chủ khả dụng):

| Dịch vụ | Mục đích |
|---|---|
| Hugging Face | Lưu trữ và chạy mô hình BRIA-RMBG-1.4 để xóa nền hình ảnh |
| Vercel (Vercel Blob) | Lưu trữ tệp nhãn dán động đã chuyển đổi của chế độ GIF động |
| Upstash | Bộ đếm giới hạn tốc độ (chỉ chứa mã băm có muối, hết hạn trong một ngày) |

Dữ liệu được xử lý bởi Hugging Face tuân theo Chính sách quyền riêng tư và Điều khoản dịch vụ của Hugging Face.

## Minh bạch mã nguồn mở

Dự án này hoàn toàn là mã nguồn mở.

Bất kỳ ai cũng có thể kiểm tra mã nguồn để xác minh:

- Dữ liệu nào được thu thập hoặc không được thu thập.
- Các yêu cầu mạng được thực hiện.
- Các thư viện bên thứ ba được sử dụng.
- Cách dữ liệu được xử lý.

## Giấy phép mã nguồn mở

Ứng dụng sử dụng một số phần mềm mã nguồn mở của bên thứ ba.

Bạn có thể xem giấy phép của các thư viện trong ứng dụng:

**Settings → License Activity → Open Source Licenses**

## Thay đổi chính sách

Nếu phương thức xử lý dữ liệu của ứng dụng thay đổi trong tương lai, chính sách này sẽ được cập nhật.

Ngày **Last Updated** ở đầu tài liệu sẽ được thay đổi để phản ánh các cập nhật.

## Liên hệ

Nếu bạn có câu hỏi, đề xuất hoặc quan ngại về Chính sách quyền riêng tư này, vui lòng tạo issue trên GitHub repository của dự án.


---

# 🇬🇧 English

## Privacy First

This application is fully open source. You can inspect the complete source code to verify how the app works and how your data is handled.

We respect your privacy. This application does **not collect, store, sell, or share your personal data**.

## Data Collection

This application:

- Does **not** require an account.
- Does **not** include analytics or tracking services.
- Does **not** include advertising SDKs.
- Does **not** collect telemetry or usage statistics.
- Does **not** collect or store personal information on our servers.

## Internet Access

Most features of the application work completely offline and **do not require an internet connection**.

The **Remove Background** feature has two modes, switchable in Settings:

- **Offline**: processed fully on-device using Google MediaPipe Tasks Vision, **does not require internet access**, and your images never leave your device.
- **Online**: requires an internet connection and sends your image to a third-party service as described below.

The **Animated GIF** mode (off by default) also needs internet access to upload the compressed GIF to a host, see [Animated GIF Mode](#animated-gif-mode).

## Remove Background

### Offline Mode

This mode uses **Google MediaPipe Tasks Vision** (open source, Apache 2.0 license) to process images directly on your device. No image or data is sent off the device.

### Online Mode

This mode uses the **BRIA-RMBG-1.4** AI model hosted on Hugging Face.

### Disclaimer

- This application is **not affiliated with, endorsed by, or associated with RemoveBG or any "removebg" branch or company**.
- This application uses the **BRIA-RMBG-1.4** model because we do not have the infrastructure to host the model ourselves.
- Images are processed by the public Hugging Face Space hosting the BRIA-RMBG-1.4 model.

### Proxy Server

To allow the Android application to communicate with the Hugging Face Space, requests are routed through:

**https://bria14proxy.vercel.app**

This proxy exists **only to forward requests between the Android application and the Hugging Face API**.

The proxy:

- Does **not** collect personal information.
- Does **not** store uploaded images.
- Does **not** identify users.
- Does **not** analyze or track activity.
- Does **not** use data for any purpose other than forwarding requests.

The complete proxy source code is available here:

https://github.com/huanhoahongso3-collab/BRIA-RMBG-1.4-PROXY

If you do **not** use the Online mode of the Remove Background feature, the application does not send your images or data through this proxy.

## Animated GIF Mode

The **GIF sending mode** option in Settings has two values:

- **Vanilla GIF** (default): the GIF is handed to Zalo directly on your device. **Nothing is uploaded.**
- **Animated GIF**: the app compresses a copy of the GIF and uploads it to a host so Zalo and the recipient can load the animation by URL. The original stays on your device.

> **Status:** the default server is not deployed yet. Until it is, Animated GIF mode automatically falls back to a vanilla GIF and **uploads nothing**.

When Animated GIF mode is active:

- On your first upload the app shows a notice that the GIF will be uploaded.
- The server stores the **converted sticker file** (animated WebP), named after the hash of the file. No name, account or device id is stored with it.
- **Anyone with the link can view the file.** Do not upload anything private.
- The server **does not store IP addresses**. Rate-limit counters use only a salted hash of the IP and install id, and expire within a day.
- Files are deleted automatically after the retention period (90 days by default), or sooner on a takedown request. The server has an abuse-report route.
- Duplicate files are stored once, and the app remembers links it already uploaded to avoid uploading again.
- You can set **your own server** in *Options > Animated GIF host (API)*. The GIF then goes to that server and is subject to its operator's policies; the app does not send the official API key to a custom server.

The server runs on Vercel (storage: Vercel Blob; rate-limit counters: Upstash Redis) and is open source:

https://github.com/huanhoahongso3-collab/ZSticker-gif-proxy

If you do **not** use Animated GIF mode, the application does not send any GIF or data to this server.

## Third-Party Services

The application uses the following third-party services only when you use the Online mode of the Remove Background feature, or the Animated GIF mode (once the server is available):

| Service | Purpose |
|---|---|
| Hugging Face | Hosts and runs the BRIA-RMBG-1.4 model for image background removal |
| Vercel (Vercel Blob) | Stores the converted animated sticker files of the Animated GIF mode |
| Upstash | Rate-limit counters (salted hashes only, expire within a day) |

Any data processed by Hugging Face is subject to Hugging Face's own Privacy Policy and Terms of Service.

## Open Source Transparency

This project is completely open source.

Anyone can inspect the source code to verify:

- What data is collected or not collected.
- Which network requests are made.
- Which third-party libraries are used.
- How your data is processed.

## Open Source Licenses

This application includes third-party open-source software.

You can view the licenses for all included libraries inside the app:

**Settings → License Activity → Open Source Licenses**

## Changes to This Privacy Policy

If this application's privacy practices change in the future, this Privacy Policy will be updated.

Any changes will be reflected by updating the **Last Updated** date at the top of this document.

## Contact

If you have any questions, suggestions, or concerns regarding this Privacy Policy, please open an issue on this project's GitHub repository.
