# SK Digital TV Display · Android TV APK

A no-subscription local signage player for Android / Google TV devices. Plays JPG/JPEG, PNG, WEBP, MP4, M4V and WEBM from a TV USB folder or from media uploaded over local Wi-Fi using a mobile browser.

## Install APK
1. Open GitHub **Actions → SK Digital TV Display APK**.
2. Select the latest successful workflow run on branch `feature/sk-tv-signage-mobile`.
3. Download **SK-Digital-TV-Display-APK** from the run's **Artifacts** section.
4. Unzip it to get `app-debug.apk`. Copy to a USB drive or send to your Android / Google TV and sideload it, allowing installation from the chosen file manager when prompted.
5. Launch **SK Digital TV Display**.

**Compatibility:** Requires a Smart TV running Android TV / Google TV (Android 6+, API 23+). Samsung Tizen, LG webOS and other non-Android Smart TVs cannot install Android APKs. Some Android TVs prevent sideloading, omit the Android folder picker, or do not decode a particular video format.

## Operate from mobile (same local Wi-Fi)
1. Connect TV and phone to the same trusted Wi-Fi network; network isolation must be off.
2. On the TV press **MENU** or **OK** to show the setup controls.
3. Note the displayed address `http://TV-IP:8765` and six-digit PIN.
4. Open the displayed address in your **phone browser** and enter the PIN.
5. Upload images or videos, change the orientation, play/pause, adjust slideshow timing, reorder the playlist or remove uploaded content. The TV plays the local copies even if the phone disconnects.
6. The phone controller is a web page hosted **inside the TV app**. There is **no separate phone APK**. Multiple paired phones can connect on the same Wi-Fi.

### USB
Select **USB Folder** on the TV to choose an accessible directory through Android's system folder picker. The chosen folder is retained after restart. USB files are read-only in the app. Media inside subfolders is not scanned.

If your TV lacks a system folder picker, use the phone upload option. USB storage must be accessible to Android.

### Portrait
Mount the television vertically **only if its manufacturer permits portrait operation** (ventilation and mounting limitations vary). The app rotates its **own content**; it does not rotate the TV launcher, system menus or HDMI input. Try **90°** and **270°** until the display is upright on the mounted TV. The app screen remains locked to landscape at the OS level.

### TV remote
- Menu / Info / OK (while full-screen): show TV setup controls
- Left / Right (while full-screen): previous / next
- Back: hide or show TV controls
- The control bar automatically hides after 30 seconds

## Security and limitations
- PIN required to pair; the pairing endpoint has a basic failed-attempt rate limit.
- Only open on **trusted Wi-Fi**. The phone remote is plain HTTP on the local network, without TLS.
- Upload size limit: 500 MiB per file. Large files consume TV internal storage.
- Video scaling follows Android's built-in VideoView; Fit / Fill control applies to images.
- Playback starts when the app is launched; **auto-launch on TV power-on is not guaranteed** and depends on device policies and possible OEM kiosk configuration.
- No cloud control, multi-TV syncing, protected public internet access, scheduled playlists, or remote access from another network in this first version.
- The APK is a debug build for private testing; production/public distribution requires signing, deployment and device testing.

## Build locally
Use JDK 17, Android SDK API 35, Android Gradle Plugin 8.7.3 and Gradle 8.9:

```sh
gradle -p tv-display :app:assembleDebug
```

Expected APK: `tv-display/app/build/outputs/apk/debug/app-debug.apk`

## Troubleshooting
- **Phone cannot connect:** verify the TV and phone use the same Wi-Fi (not guest Wi-Fi); check the TV's IPv4 address; ensure TCP port 8765 is allowed; on some TVs Wi-Fi IP appears after connectivity starts, reopen MENU to update.
- **TV APK won't install:** it may be non-Android (Tizen/webOS) or blocked by the manufacturer.
- **USB folder chooser absent:** use phone uploads instead.
- **Video unsupported:** encode MP4 with H.264 video and AAC audio.
- **Orientation appears sideways:** switch 90° ↔ 270°.
- **GitHub APK missing:** inspect the Actions build log; the artifact appears only after a successful build.
