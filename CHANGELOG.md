# Changelog

## 0.0.13-alpha

### Added

- Added an Extra tokens page for future ad watching and token rewards.
- Added a top-right ads/tokens button beside the new-chat button.
- Added a back button on the Extra tokens page.
- Added Gemini image-analysis routing for image messages.
- Added local Gemini API key support through ignored local Gradle properties.
- Added compressed image payload generation before sending images to AI providers.
- Added a default image-analysis promptfailed

- Added direct handling for online-search prompts such as `look online`, `search online`, and `online about`.
- Added device-based date and time answers for time/date questions.
- Added broader app permissions for camera, microphone, images, video, audio, selected photo access, and legacy storage support.
- Added optional camera and microphone feature declarations so devices without that hardware are not blocked.

### Changed

- Updated app version to `0.0.13-alpha`.
- Made the Budgie AI system prompt more agentic.
- Improved goal handling so the AI is instructed to infer the user's next useful step.
- Improved tool-use behavior for current facts, websites, products, and changing information.
- Improved image-message behavior so the AI is instructed to inspect images directly and answer the user's request.
- Reduced passive chatbot behavior by instructing the AI to ask clarifying questions only when blocked.
- Hid the chat composer while the Extra tokens page is open.
- Kept OpenRouter Free Router for text messages.
- Routed image messages to Gemini instead of the OpenRouter text route.
- Resized attached images to a maximum side of 1280px before encoding.
- Converted attached images to JPEG data before sending.
- Made `look online` reuse the previous user question when no search query is provided.
- Kept debug and release builds on the same version name by removing the debug version suffix.

### Fixed

- Fixed raw `[[web_search: ...]]` tool requests appearing as chat replies.
- Fixed `null` provider output being displayed to users.
- Fixed raw provider safety metadata being displayed as normal AI replies.
- Fixed router failures after web search by falling back to usable search-result output.
- Fixed time/date questions relying on the AI provider instead of the device clock.
- Fixed image uploads being shown locally but blocked from being sent to an image-capable AI provider.
- Fixed deprecated back-arrow icon usage by switching to the auto-mirrored icon.
