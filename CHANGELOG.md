# Changelog

## 0.0.12-alpha

### Added

- Added an Extra tokens page for future ad watching and token rewards.
- Added a top-right ads/tokens button beside the new-chat button.
- Added a back button on the Extra tokens page.
- Added image-capable OpenRouter message sending.
- Added compressed image payload generation before sending images to OpenRouter.
- Added a default image-analysis prompt when a user sends an image without text.
- Added direct handling for online-search prompts such as `look online`, `search online`, and `online about`.
- Added device-based date and time answers for time/date questions.
- Added broader app permissions for camera, microphone, images, video, audio, selected photo access, and legacy storage support.
- Added optional camera and microphone feature declarations so devices without that hardware are not blocked.

### Changed

- Updated app version to `0.0.12-alpha`.
- Hid the chat composer while the Extra tokens page is open.
- Marked the OpenRouter Free Router path as image-capable in app logic.
- Resized attached images to a maximum side of 1280px before encoding.
- Converted attached images to JPEG data URLs before sending.
- Made `look online` reuse the previous user question when no search query is provided.
- Kept debug and release builds on the same version name by removing the debug version suffix.

### Fixed

- Fixed raw `[[web_search: ...]]` tool requests appearing as chat replies.
- Fixed `null` provider output being displayed to users.
- Fixed raw provider safety metadata being displayed as normal AI replies.
- Fixed router failures after web search by falling back to usable search-result output.
- Fixed time/date questions relying on the AI provider instead of the device clock.
- Fixed image uploads being shown locally but blocked from being sent to the AI provider.

### Verification

- Gradle sync-only command completed successfully with `gradlew --refresh-dependencies help`.
- No app build or APK assemble task was run for this version update.
