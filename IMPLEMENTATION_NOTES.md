# Implementation Notes

## Suggested module roadmap

### Phase 1 - account and metadata
- done: Microsoft OAuth entry point
- done: encrypted account storage
- done: version manifest loading

### Phase 2 - install pipeline
- add version metadata resolver
- add library downloader with sha1 verification
- add asset index downloader and asset object fetcher
- add Java runtime manager

### Phase 3 - launch pipeline
- assemble classpath
- prepare natives extraction directory
- set renderer env vars
- write generated JVM args and game args
- launch through native bootstrap

### Phase 4 - renderer integration
- add renderer registry
- expose selectable backends
- detect device capability and safe fallback

### Phase 5 - polish
- instance management
- mod loader install flows
- controller / touch UI
- crash log viewer
- import/export
