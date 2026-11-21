# Third-party components

This standalone host is new application code. It executes the workshop rules in `app/src/main/assets/delta`. It does not incorporate PalmClaw or AutoJs6 application source code.

- Mozilla Rhino 1.7.15: Mozilla Public License 2.0. Bundled license in licenses/Rhino-LICENSE.txt. Source: https://github.com/mozilla/rhino/tree/Rhino1_7_15_Release . No Rhino source modifications.
- AndroidX and Material Components for Android: Apache License 2.0. Source and notices: https://github.com/androidx/androidx and https://github.com/material-components/material-components-android .
- Google ML Kit Chinese Text Recognition 16.0.1: bundled on-device recognition and its transitive Google components. Terms: https://developers.google.com/ml-kit/terms ; Android implementation guide: https://developers.google.com/ml-kit/vision/text-recognition/v2/android . ML Kit is a separately licensed dependency and is not represented as entirely open source.
- Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`): Apache License 2.0, see `licenses/Gradle-LICENSE.txt` and `licenses/Gradle-NOTICE.txt`. Android SDK is a separately installed build tool.

Dependency license and notice files in the resolved artifacts remain included according to the Android packager defaults. Do not remove attribution when distributing this app.
