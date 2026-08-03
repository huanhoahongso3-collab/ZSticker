# Privacy Policy

_Last Updated: August 3, 2026_

## Privacy First

This application is fully open source. You can inspect the complete source code to verify how the app works and how your data is handled.

We respect your privacy. This application does **not** collect, store, sell, or share your personal data.

## Data Collection

This application:

- Does **not** require an account.
- Does **not** include analytics or tracking services.
- Does **not** include advertising SDKs.
- Does **not** collect telemetry or usage statistics.
- Does **not** collect or store personal information on our servers.

## Internet Access

Most features of the application work completely offline and **do not require an internet connection**.

The only feature that requires internet access is **Remove Background**.

## Remove Background

The Remove Background feature uses the **BRIA-RMBG-1.4** AI model hosted on Hugging Face.

### Disclaimer

- I am **not affiliated with, endorsed by, or associated with RemoveBG** or any "removebg" branch or company.
- This application uses the **BRIA-RMBG-1.4** model because I do not have the infrastructure to host the model myself.
- Images are processed by the public Hugging Face Space hosting the BRIA-RMBG-1.4 model.

### Proxy Server

To allow the Android application to communicate with the Hugging Face Space, requests are routed through:

**https://bria14proxy.vercel.app**

This proxy exists **only** to forward requests between the Android application and the Hugging Face API.

The proxy:

- Does **not** collect personal information.
- Does **not** store uploaded images.
- Does **not** identify users.
- Does **not** analyze or track your activity.
- Does **not** use your data for any purpose other than forwarding requests.

If you would like to verify this yourself, the complete proxy source code is available here:

https://github.com/huanhoahongso3-collab/BRIA-RMBG-1.4-PROXY

If you do **not** use the Remove Background feature, the application does not send your images or data through this proxy.

## Third-Party Services

The application uses the following third-party service **only** when you use the Remove Background feature:

| Service | Purpose |
|---------|---------|
| Hugging Face | Hosts and runs the BRIA-RMBG-1.4 model for image background removal |

Any data processed by Hugging Face is subject to Hugging Face's own Privacy Policy and Terms of Service.

## Open Source Transparency

This project is completely open source.

Anyone can inspect the source code to verify:

- What data is (or is not) collected.
- Which network requests are made.
- Which third-party libraries are used.
- How your data is processed.

If you have concerns about privacy, you are encouraged to review the source code yourself.

## Open Source Licenses

This application includes third-party open-source software.

You can view the licenses for all included libraries inside the app:

**Settings → License Activity → Open Source Licenses**

## Changes to This Privacy Policy

If this application's privacy practices change in the future, this Privacy Policy will be updated. Any changes will be reflected by updating the **Last Updated** date at the top of this document.

## Contact

If you have any questions, suggestions, or concerns regarding this Privacy Policy, please open an issue on this project's GitHub repository.
