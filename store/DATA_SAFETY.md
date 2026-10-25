# Play Console — Data safety answers

## Overview
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (only through the Google Mobile Ads SDK) |
| Is all of the user data collected by your app encrypted in transit? | **Yes** — the Google Mobile Ads and UMP SDKs use HTTPS |
| Do you provide a way for users to request that their data is deleted? | **No** — the app holds no user data on servers. Songs are deleted on the device by deleting the song or uninstalling. (Advertising data is controlled through Google's ad settings.) |

## Data types
| Data type | Collected | Shared | Purpose | Optional? | Notes |
|---|---|---|---|---|---|
| **Audio — Voice or sound recordings** | **No** | No | — | — | Recorded and processed only on the device ("processed ephemerally / on device" — not collected, because it never leaves the device). |
| **Device or other IDs** (advertising ID) | **Yes** (by AdMob) | **Yes** (with Google AdMob) | Advertising or marketing; Analytics; Fraud prevention, security and compliance | Required for ads (personalization subject to UMP consent) | Collected by the Google Mobile Ads SDK. |
| **App activity — App interactions** (ad views/taps) | **Yes** (by AdMob) | **Yes** (with Google AdMob) | Advertising or marketing; Analytics | Required for ads | Collected by the Google Mobile Ads SDK. |
| **App info and performance — Crash logs, Diagnostics** | **Yes** (by AdMob) | **Yes** (with Google AdMob) | Analytics; Fraud prevention | Required for ads | SDK diagnostics only; the app has no crash reporting of its own. |
| **Approximate location** | **Yes** (by AdMob, derived from IP) | **Yes** (with Google AdMob) | Advertising or marketing | Required for ads | No location permission is requested. |
| Personal info (name, email, user IDs…) | No | No | — | — | No accounts. |
| Files and docs, Photos and videos, Contacts, Messages, Calendar, Health, Financial | No | No | — | — | Exports go only where the user chooses via system dialogs. |

## Security practices
- Data is encrypted in transit: **Yes** (SDK traffic uses HTTPS).
- Independent security review: **No**.
- Committed to the Play Families Policy: **No** (target audience 13+, see CONTENT_RATING.md).
