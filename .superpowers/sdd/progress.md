# Live TV UX v2 — SDD progress ledger
Branch: feat/live-tv-ux-v2
Plan: docs/superpowers/plans/2026-06-21-live-tv-ux-v2.md
Base before work: 5beb52d

- [x] Phase 1: Room cache-first persistence
- [x] Phase 2: RegionClassifier + region filter (default UK+US+EN)
- [x] Phase 3: Favourites (channels+categories) + recently-watched
- [ ] Phase 4: For You landing + region/category pickers
- [ ] Phase 5: review + docs + finalize

Phase 1: complete (cache-first validated, 0 re-fetch on cold start). head a83294c
Phase 2: complete (region filter default UK+US+EN ~7.2k; classifier 24/7 fix; tests 10/10). head 6ca5f08
Phase 3: complete (★ channels persist; recents on play validated). head 91d10e3
