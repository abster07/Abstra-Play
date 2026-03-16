.
├── PROJECT_STRUCTURE.md
├── README.md
├── app
│   ├── build.gradle.kts
│   ├── libs
│   │   └── ffmpeg-decoder.aar
│   ├── proguard-rules.pro
│   └── src
│       └── main
│           ├── AndroidManifest.xml
│           ├── java
│           │   └── com
│           │       └── streamsphere
│           │           └── app
│           │               ├── AppModule.kt
│           │               ├── MainActivity.kt
│           │               ├── StreamSphereApp.kt
│           │               ├── data
│           │               │   ├── api
│           │               │   │   ├── Database.kt
│           │               │   │   └── IptvApi.kt
│           │               │   ├── dlna
│           │               │   │   ├── DlnaBrowseItem.kt
│           │               │   │   ├── DlnaDevice.kt
│           │               │   │   └── DlnaRepository.kt
│           │               │   ├── model
│           │               │   │   └── Models.kt
│           │               │   ├── preferences
│           │               │   │   └── SettingsDataStore.kt
│           │               │   └── repository
│           │               │       └── ChannelRepository.kt
│           │               ├── di
│           │               │   └── DlnaModule.kt
│           │               ├── ui
│           │               │   ├── components
│           │               │   │   ├── CategoryTabRow.kt
│           │               │   │   ├── ChannelCard.kt
│           │               │   │   ├── DlnaCastButton.kt
│           │               │   │   └── DlnaDevicePickerSheet.kt
│           │               │   ├── navigation
│           │               │   │   └── Navigation.kt
│           │               │   ├── screens
│           │               │   │   ├── DetailScreen.kt
│           │               │   │   ├── DlnaScreen.kt
│           │               │   │   ├── FavouritesScreen.kt
│           │               │   │   ├── HomeScreen.kt
│           │               │   │   ├── SearchScreen.kt
│           │               │   │   ├── SettingsScreen.kt
│           │               │   │   └── {DetailScreen.kt
│           │               │   └── theme
│           │               │       ├── Theme.kt
│           │               │       └── Typography.kt
│           │               ├── viewmodel
│           │               │   ├── ChannelViewModel.kt
│           │               │   ├── DlnaViewModel.kt
│           │               │   └── SettingsViewModel.kt
│           │               └── widget
│           │                   └── ChannelWidget.kt
│           └── res
│               ├── drawable
│               │   ├── ic_launcher_background.xml
│               │   ├── ic_launcher_foreground.xml
│               │   └── ic_splash_logo.xml
│               ├── ic_launcher-web.png
│               ├── layout
│               │   └── widget_initial_layout.xml
│               ├── mipmap-anydpi-v26
│               │   ├── ic_launcher.xml
│               │   └── ic_launcher_round.xml
│               ├── mipmap-hdpi
│               │   ├── ic_launcher.png
│               │   ├── ic_launcher_foreground.png
│               │   ├── ic_launcher_monochrome.png
│               │   └── ic_launcher_round.png
│               ├── mipmap-ldpi
│               │   ├── ic_launcher.png
│               │   └── ic_launcher_round.png
│               ├── mipmap-mdpi
│               │   ├── ic_launcher.png
│               │   ├── ic_launcher_foreground.png
│               │   ├── ic_launcher_monochrome.png
│               │   └── ic_launcher_round.png
│               ├── mipmap-xhdpi
│               │   ├── ic_launcher.png
│               │   ├── ic_launcher_foreground.png
│               │   ├── ic_launcher_monochrome.png
│               │   └── ic_launcher_round.png
│               ├── mipmap-xxhdpi
│               │   ├── ic_launcher.png
│               │   ├── ic_launcher_foreground.png
│               │   ├── ic_launcher_monochrome.png
│               │   └── ic_launcher_round.png
│               ├── mipmap-xxxhdpi
│               │   ├── ic_launcher.png
│               │   ├── ic_launcher_foreground.png
│               │   ├── ic_launcher_monochrome.png
│               │   └── ic_launcher_round.png
│               ├── playstore-icon.png
│               ├── values
│               │   ├── colors.xml
│               │   ├── ic_launcher_background.xml
│               │   ├── strings.xml
│               │   └── themes.xml
│               └── xml
│                   └── channel_widget_info.xml
├── app-metadata.json
├── build.gradle.kts
├── gradle
│   ├── libs.versions.toml
│   └── wrapper
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts

37 directories, 80 files
