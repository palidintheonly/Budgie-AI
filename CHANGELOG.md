# Changelog

## 0.1.1-beta

### Release

- Updated Android version name to `0.1.1-beta`.
- Updated release label to `v1`.
- Kept Android version code unchanged.

### AI Routing

- Added live OpenRouter free-model discovery from the current `/models` catalog.
- Added hourly in-app caching for discovered free text and vision routers.
- Added free OpenRouter text model failover for normal chat.
- Added free OpenRouter vision model failover for attached-image analysis when Gemini is unavailable.
- Kept normal text chat on OpenRouter.
- Kept attached image analysis on Gemini first, then OpenRouter vision fallback.
- Kept user image requests routed through image search.

### Database

- Added the `provider_models` table to the Budgie AI database.
- Added the `20260623_provider_models` schema migration record.
- Added the `sync_provider_models` backend sync action.
- Seeded the live database with the current OpenRouter free router catalog.
- Verified the backend health endpoint reports `12` live tables.

### Fixes

- Fixed router lists becoming stale by using OpenRouter's live model catalog with a local fallback list.
- Fixed normal chat giving up after only one free OpenRouter route failed.
- Fixed old image attachments forcing later text messages through image-analysis routing.
- Fixed attached-image fallback so image-capable OpenRouter routes can be tried after Gemini.
