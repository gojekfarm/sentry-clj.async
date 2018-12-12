# Sentry Clojure (Asynchronous) - Changelog

All notable changes to [farm.gojek/sentry-clj.async][0] will be documented 
in this file.

The format is based on [Keep a Changelog][1], and this project adheres to 
[Semantic Versioning][2].

## [Unreleased]

## [0.0.5] - 2018-12-15
### Changed
- Client contract from reporter (with `report-error`) to captor (with `capture!`).
- Macros to functions with injected logger.

### Added
- Better unit testing specifications.
- Bug fix in `uncaught-exception-handler` with cyclic errors that might lead to dead lock.
- `Makefile` for old-timers.

### Removed
- Unused macros `report-error` & `report-warn`.

[Unreleased]: https://github.com/gojekfarm/sentry-clj.async/compare/v0.0.5...HEAD
[0.0.5]: https://github.com/gojekfarm/sentry-clj.async/compare/v0.0.1...v0.0.5

[0]: http://keepachangelog.com/en/1.0.0/
[1]: http://semver.org/spec/v2.0.0.html
[2]: https://github.com/gojekfarm/sentry-clj.async
