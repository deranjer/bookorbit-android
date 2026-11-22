FROM bookorbit-client-android:local

RUN sdkmanager "emulator" "system-images;android-36;google_apis;x86_64"
ENV ANDROID_USER_HOME=/emulator-home/.android
ENV ANDROID_AVD_HOME=/emulator-home/.android/avd
ENV HOME=/emulator-home
ENTRYPOINT ["bash", "/workspace/tools/android/run-emulator.sh"]
