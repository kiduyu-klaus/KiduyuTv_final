# Changelog

**Comparison range:** `1b2d5bc755048e47534c131f767f4e3eb827e5fa` (`v1.1.760`) → `2297368e`  
**Current branch:** `main`  
**Commits included:** 41

## What’s new

- Added country-based Live TV with country flags, country search, expanded US state names, regional/state playlist selection, playlist-name resolution from IPTV group titles, and channel loading from country playlists.
- Added reliable flag image loading with remote-image fixes and fallback flag URLs.
- Added IPTV country-playlist refresh progress reporting so users can track playlist updates.
- Restored the last selected country focus when returning to the Live TV country directory.
- Added Daddylive Live TV support with a dedicated tab, cached channel loading, scraping, and parsing of all available Daddylive server options.
- Redesigned the Schedule Player experience with improved Material layouts, a compact playlist/server picker, and clearer channel/server navigation.
- Fixed Schedule Player server selection so it no longer shows duplicate or fabricated servers and instead displays the actual available server list.
- Switched scheduled stream sourcing to `dlive.sx` and disabled unnecessary stream sniffing for scheduled playback.
- Improved Schedule Player reliability with D-pad back handling, cursor restoration, tab loading states, and cleaner ad/popup blocking.
- Modernized the IPTV player channel picker and improved playlist refresh progress, autoplay audio handling, and WebView audio-loop behavior.
- Improved stream validation and playback compatibility by filtering invalid streams, detecting incorrectly labelled HLS playlists, normalizing Googleusercontent MIME hints, and recovering from incompatible audio tracks.
- Improved video volume handling to prevent repeated volume-controller injection after audio is enabled.
- Improved DirectStream provider loading with stream-fetch progress reporting, clearer loading feedback, cyan focus states, and refreshed CineSrc Mega tokens before validation.
- Added stream-language marker handling for `hi` and `en` sources and improved provider stream filtering and error handling.
- Added modern TV and mobile episode overview screens with episode metadata, credits, still-image galleries, improved navigation, and more compact layouts.
- Improved episode image loading and routing for mobile and TV episode profiles, including season/episode-specific image requests.
- Improved TV D-pad usability with scrollable update and biography dialogs, focus restoration, and close-button focus behavior.
- Added LiveTv settings with country-playlist refresh controls and a persistent option to hide channels containing `18+` in their titles.
- Improved app diagnostics and reliability by filtering app logcat output, resolving logcat process-type collisions, and refining ad eligibility handling.

> This changelog covers committed changes in the range above. Existing uncommitted working-tree changes were not included in the comparison.
