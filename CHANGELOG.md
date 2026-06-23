# Changelog

## 0.1.0-beta

### Release

- Moved Budgie AI from alpha builds into beta.
- Updated Android version name to `0.1.0-beta`.
- Updated release label to `v1.5`.

### Branding

- Renamed project branding fully to Budgie AI.
- Renamed the Android application ID to `com.budgieai.app`.
- Renamed the Kotlin package to `com.budgieai.app`.
- Renamed the internal Compose theme to Budgie.
- Updated Firebase configuration for the Budgie AI package.

### AI Routing

- Kept normal text chat on OpenRouter.
- Routed attached image analysis to Gemini.
- Routed user requests for images, photos, or pictures to image search.
- Made provider routing explicit in the agent instructions.

### Agent Behavior

- Made Budgie AI behave more like an agent instead of a passive chatbot.
- Updated the system prompt so the AI infers the user's goal and chooses the next useful step.
- Improved current-information behavior so the AI is instructed to use web search instead of guessing.
- Improved image-message behavior so attached images are inspected directly.
- Reduced unnecessary clarification questions by asking only when blocked.

### Fixes

- Fixed OpenRouter being treated as image-capable for attached-image analysis.
- Fixed image requests and attached-image analysis sharing the same route.
- Fixed raw search-tool directives being able to leak after a tool retry.
