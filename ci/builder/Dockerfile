FROM jenkinsci/slave
LABEL MAINTAINER="ConnectBot Developers <developers@connectbot.org>"

ENV CI true
ENV ANDROID_HOME /opt/android
ENV ANDROID_SDK_HOME $ANDROID_HOME
ENV ANDROID_NDK_HOME $ANDROID_HOME/ndk-bundle
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools

USER root
RUN mkdir -p $ANDROID_HOME && chown -R jenkins $ANDROID_HOME
USER jenkins

# Accept Android SDK licenses
RUN mkdir -p $ANDROID_HOME/licenses && \
    echo -ne "\n8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e" >> $ANDROID_HOME/licenses/android-sdk-license && \
    echo -ne "\n84831b9409646a918e30573bab4c9c91346d8abd\n504667f4c0de7af1a06de9f4b1727b84351f2910" >> $ANDROID_HOME/licenses/android-sdk-preview-license

# Install the base part of Android's tools
RUN mkdir -p $ANDROID_HOME && \
    curl -L https://dl.google.com/android/repository/tools_r25.2.3-linux.zip \
        -o $ANDROID_HOME/tools.zip && \
    unzip -oq $ANDROID_HOME/tools.zip -d $ANDROID_HOME && \
    rm -f $ANDROID_HOME/tools.zip

# Install Android and the things ConnectBot needs
RUN mkdir -p $ANDROID_HOME/.android && \
    touch $ANDROID_HOME/.android/repositories.cfg && \
    $ANDROID_HOME/tools/bin/sdkmanager \
        tools \
        platform-tools \
        emulator \
        'system-images;android-23;default;x86' \
        'build-tools;27.0.3' \
        'platforms;android-27' \
        'extras;android;m2repository' \
        'extras;google;m2repository' \
        'extras;google;google_play_services' \
        ndk-bundle && \
    chown -R jenkins ${ANDROID_HOME}

