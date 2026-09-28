# Changelog

## [0.5.0](https://github.com/deranjer/bookorbit-android/compare/v0.4.2...v0.5.0) (2026-09-28)


### Features

* **auto:** browse and jump to chapters of the playing book ([e03232c](https://github.com/deranjer/bookorbit-android/commit/e03232caee7705c9a1e1c6ca0654a1b25f3d2cb7))
* combine dashboard, player, and download fixes ([07b34e6](https://github.com/deranjer/bookorbit-android/commit/07b34e635fdf3c3b24560a05568ce623b8de09c5))
* **player:** add ChapterClock for book/chapter time math ([8aba55f](https://github.com/deranjer/bookorbit-android/commit/8aba55f9f6cec2cdacde67be5eaa8b3a46e7ce71))
* **player:** add progress bar mode setting ([0777d76](https://github.com/deranjer/bookorbit-android/commit/0777d76b27116cef609adcafeaeacb08fee22a41))
* **player:** chapter progress bar and chapter list sheet ([30fa586](https://github.com/deranjer/bookorbit-android/commit/30fa5861173f66a5e2fc85b3850cbfbac5f64b9b))
* **player:** normalize chapters into book-time ranges ([efb9244](https://github.com/deranjer/bookorbit-android/commit/efb92442e67f2773884ed66fcaf62ca8b79b96f2))
* **player:** reopen the last audiobook, paused, when the app starts ([861d05f](https://github.com/deranjer/bookorbit-android/commit/861d05f47e1197ce9a05e72dd2a862cfd1809304))
* **player:** report chapter-relative progress on the media session ([1ab945f](https://github.com/deranjer/bookorbit-android/commit/1ab945f476a1059502f1c4a387caef14e4a00bfe))
* **settings:** progress bar mode picker ([574ecb7](https://github.com/deranjer/bookorbit-android/commit/574ecb7de2f2c2e744fd7cf414d13fe8801aa56e))


### Bug Fixes

* address final review (lint opt-in, spec accuracy, Auto chapter start) ([52ca023](https://github.com/deranjer/bookorbit-android/commit/52ca0238f40697ea6237d716774fd261ed9966d3))
* **dashboard:** decode the 3.x { books, total } scroller response ([f9e857e](https://github.com/deranjer/bookorbit-android/commit/f9e857efd179775761283be3a8c8a0f7c6b292b3))
* **downloads:** keep downloads running in the background and resume them ([f8c9585](https://github.com/deranjer/bookorbit-android/commit/f8c958567f5807fa377b4cf1fe47a2ddc76e3131))
* **downloads:** validate resumed range and file length ([8710e47](https://github.com/deranjer/bookorbit-android/commit/8710e47f645c64fc7661597d0bf3429945fd6480))
* **nav:** ignore back taps from screens that are already leaving ([6720f84](https://github.com/deranjer/bookorbit-android/commit/6720f849a852ebaab15e212747482d1700a7ac71))
* **player:** harden the launch restore ([7263fc7](https://github.com/deranjer/bookorbit-android/commit/7263fc71a3158ee82a60462a5d87b7775e54913a))
* **player:** keep skip/cast book-time behavior neutral ([cb41080](https://github.com/deranjer/bookorbit-android/commit/cb41080472b12b0e8abdbdfc8618ecf330564b8a))
* **settings:** keep whole-book progress as the default ([33b9ddb](https://github.com/deranjer/bookorbit-android/commit/33b9ddb15c60387852dfcfe56fda1df874e96daf))

## [0.4.2](https://github.com/deranjer/bookorbit-android/compare/v0.4.1...v0.4.2) (2026-09-28)


### Bug Fixes

* **player,downloads:** fetch audio via the audiobook asset API on server 3.0+ ([214b647](https://github.com/deranjer/bookorbit-android/commit/214b647118217734655bc748738846b8ca5be92e)), closes [#41](https://github.com/deranjer/bookorbit-android/issues/41)
* **player:** sync audiobook position via playback-state on server 3.0+ ([c0e48e8](https://github.com/deranjer/bookorbit-android/commit/c0e48e861205a1298fec8c9ccf96d0bc3d8976de))
* support BookOrbit 3.x audiobook API (downloads, streaming, progress sync) ([fa644e5](https://github.com/deranjer/bookorbit-android/commit/fa644e5f8f2818570e1b5b2df60a5ae27383dbf2))

## [0.4.1](https://github.com/deranjer/bookorbit-android/compare/v0.4.0...v0.4.1) (2026-09-27)


### Bug Fixes

* **downloads,player:** surface real errors instead of silently failing ([f5aca78](https://github.com/deranjer/bookorbit-android/commit/f5aca7856f801b1b47ade3acff7fab070dc38866))
* **downloads,player:** surface real errors instead of silently failing ([#38](https://github.com/deranjer/bookorbit-android/issues/38)) ([38d4c97](https://github.com/deranjer/bookorbit-android/commit/38d4c9744a4e9cbdb294909438f844f24d222068))

## [0.4.0](https://github.com/deranjer/bookorbit-android/compare/v0.3.0...v0.4.0) (2026-08-02)


### Features

* **fdroid:** add fastlane metadata for store listings ([d8915d6](https://github.com/deranjer/bookorbit-android/commit/d8915d6061fd35f8726c2bf336173cf4d44a8ccc))
* **fdroid:** add fastlane metadata for store listings ([6b9daab](https://github.com/deranjer/bookorbit-android/commit/6b9daab7f4909336a235af0a3af942d334d2ef3e))
* **fdroid:** also build and attach the fdroid-flavor APK to releases ([a462822](https://github.com/deranjer/bookorbit-android/commit/a4628224146a6b22aad39fc38ea2384e594a2262))
* **fdroid:** also build and attach the fdroid-flavor APK to releases ([0c6fa26](https://github.com/deranjer/bookorbit-android/commit/0c6fa262fe56f09436350cf46913ea0a59222f36))


### Bug Fixes

* **fdroid:** match changelog filename to the real current versionCode ([07b78aa](https://github.com/deranjer/bookorbit-android/commit/07b78aa594db488e769305a35887b2d15affd619))

## [0.3.0](https://github.com/deranjer/bookorbit-android/compare/v0.2.1...v0.3.0) (2026-08-02)


### Features

* **fdroid:** add a Cast-free build flavor for official F-Droid ([fb6f252](https://github.com/deranjer/bookorbit-android/commit/fb6f252efdb9c014bcad0050c9e1f12910346558))
* **fdroid:** add a Cast-free build flavor for official F-Droid ([592b016](https://github.com/deranjer/bookorbit-android/commit/592b0161f7d656704266252c8fa51632f7a64264))

## [0.2.1](https://github.com/deranjer/bookorbit-android/compare/v0.2.0...v0.2.1) (2026-08-01)


### Bug Fixes

* **series:** numeric series ID for book lookups + list/grid toggle ([0008707](https://github.com/deranjer/bookorbit-android/commit/0008707134370bf0d0b57548701a1294b9129666))

## [0.2.0](https://github.com/deranjer/bookorbit-android/compare/v0.1.0...v0.2.0) (2026-08-01)


### Features

* **release:** fully automate versioning via release-please ([d5318b4](https://github.com/deranjer/bookorbit-android/commit/d5318b4f475d7bbf70961a5516266b0b22491c32))
* **release:** fully automate versioning via release-please ([758a488](https://github.com/deranjer/bookorbit-android/commit/758a4886cdd676361c8c1f46789c9a736b62a247))


### Bug Fixes

* **release:** stop relying on release-please's flaky generic updater ([b8556d4](https://github.com/deranjer/bookorbit-android/commit/b8556d4185880b562856603becf3391abe404fe2))
* **release:** stop relying on release-please's flaky generic updater ([44194a0](https://github.com/deranjer/bookorbit-android/commit/44194a01c6ae516c2b31914b9f5ff9621c121871))

## [0.1.0](https://github.com/deranjer/bookorbit-android/releases/tag/v0.1.0) (2026-07-30)

Initial release. See the [GitHub Release notes](https://github.com/deranjer/bookorbit-android/releases/tag/v0.1.0) for what shipped.
