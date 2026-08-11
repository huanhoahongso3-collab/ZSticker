# Chính Sách Quyền Riêng Tư

_Last Updated: August 11, 2026_

## 📑 Điều hướng / Navigation

- [🇻🇳 Tiếng Việt](#-tiếng-việt)
  - [Quyền riêng tư là ưu tiên hàng đầu](#quyền-riêng-tư-là-ưu-tiên-hàng-đầu)
  - [Thu thập dữ liệu](#thu-thập-dữ-liệu)
  - [Truy cập Internet](#truy-cập-internet)
  - [Tính năng Xóa nền](#tính-năng-xóa-nền)
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

## Dịch vụ bên thứ ba

Ứng dụng chỉ sử dụng dịch vụ bên thứ ba sau khi bạn sử dụng chế độ Trực tuyến của tính năng Xóa nền:

| Dịch vụ | Mục đích |
|---|---|
| Hugging Face | Lưu trữ và chạy mô hình BRIA-RMBG-1.4 để xóa nền hình ảnh |

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

## Third-Party Services

The application uses the following third-party service only when you use the Online mode of the Remove Background feature:

| Service | Purpose |
|---|---|
| Hugging Face | Hosts and runs the BRIA-RMBG-1.4 model for image background removal |

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
