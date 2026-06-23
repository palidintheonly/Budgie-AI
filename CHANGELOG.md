# Changelog

## 0.0.13-alpha

### Changed

- Made Budgie AI behave more like an agent instead of a passive chatbot.
- Updated the system prompt so the AI infers the user's goal and chooses the next useful step.
- Made provider routing explicit in the agent instructions.
- Kept normal text chat on OpenRouter.
- Routed attached image analysis to Gemini.
- Routed user requests for images, photos, or pictures to image search.
- Improved current-information behavior so the AI is instructed to use web search instead of guessing.
- Improved image-message behavior so attached images are inspected directly.
- Reduced unnecessary clarification questions by asking only when blocked.

### Added

- Added direct image-search intent handling for prompts such as `show images of`, `find pictures of`, and `photos of`.
- Added Gemini local key support for image analysis through ignored local properties.

### Fixed

- Fixed OpenRouter being treated as image-capable for attached-image analysis.
- Fixed image requests and attached-image analysis sharing the same route.
- Fixed raw search-tool directives being able to leak after a tool retry.
