FROM reactnativecommunity/react-native-android@sha256:e817ea1959719c97c05adbc55348ebe21d286cec2caed1f18ad3c4b88c08cbf7

RUN sdkmanager "platforms;android-36" "platforms;android-37.2" "build-tools;35.0.0" "build-tools;36.0.0"
ENV GRADLE_USER_HOME=/gradle-cache
WORKDIR /workspace
