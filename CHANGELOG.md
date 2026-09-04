# Changelog

## [0.2.1](https://github.com/regulskimichal/testcontainers-gradle-plugin/compare/v0.2.0...v0.2.1) (2026-09-04)


### Bug Fixes

* TestcontainersBuildService should not unnecessarily call constructor of provided JdbcDatabaseContainer subclass ([09d2b88](https://github.com/regulskimichal/testcontainers-gradle-plugin/commit/09d2b8851463b3b1df6647b46a0618d015b08b55))
* unlock Gradle daemon failed state when started without Docker by decorating container.start() with TestcontainersCircuitBreaker ([52ca47c](https://github.com/regulskimichal/testcontainers-gradle-plugin/commit/52ca47cb4abbcb1deb3afbd1ac93bff2216db080))

## [0.2.0](https://github.com/regulskimichal/testcontainers-gradle-plugin/compare/v0.1.1...v0.2.0) (2026-08-26)


### Features

* add trackedFiles support for incremental container start tasks ([3af5227](https://github.com/regulskimichal/testcontainers-gradle-plugin/commit/3af5227d2fd1605292723825af5b50d34ef9b1c1))


### Bug Fixes

* **deps:** update dependency org.junit.jupiter:junit-jupiter to v6.1.3 ([d75dd91](https://github.com/regulskimichal/testcontainers-gradle-plugin/commit/d75dd91c28d46389825b9aa86e87aba27d8f6ee8))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v6.1.3 ([06a0909](https://github.com/regulskimichal/testcontainers-gradle-plugin/commit/06a0909fe6b5e0b7028b47c0df424319dee62817))

## [0.1.1](https://github.com/regulskimichal/testcontainers-gradle-plugin/compare/v0.1.0...v0.1.1) (2026-07-24)


### Features

* add DSL marker annotation to prevent scope leakage in Testcontainers DSL ([02d2725](https://github.com/regulskimichal/testcontainers-gradle-plugin/commit/02d2725e41fb48f43a76e34bb88a0a19375dd8c7))


### Bug Fixes

* **deps:** update dependency org.jetbrains.kotlin:kotlin-test-junit5 to v2.4.10 ([f01c000](https://github.com/regulskimichal/testcontainers-gradle-plugin/commit/f01c000350c0d97799f5db18ffe45a6dedc691ac))
