# Installing 2048 on an Android device

## What you need

- An Android phone or tablet running **Android 7.0 (API 24) or newer**.
- The APK file:
  `android\app\build\outputs\apk\release\app-release.apk` (about 1.1 MB).
  - Rebuild it if needed, from the `android\` folder in PowerShell:
    ```powershell
    $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --no-daemon :app:assembleRelease
    ```
- Package name: `com.apocalypse03.game2048`.

> The APK is signed with the Android **debug key**. Sideloading works, but the
> Play Store will not accept it, and Google Play Protect may show a warning
> ("unknown developer"). This is expected for this build.

Choose **one** of the methods below.

---

## Method A — Copy the file to the phone (no PC tools)

1. **Copy the APK to the phone**, using any of these:
   - USB cable: connect the phone, choose **File transfer** in the phone's USB
     notification, then copy `app-release.apk` into the phone's `Download` folder.
   - Google Drive, OneDrive or e-mail: upload the APK, then download it on the phone.
   - Bluetooth / Nearby Share / Quick Share.
2. **On the phone, open the file**: open the **Files** (or **My Files**) app,
   go to **Download**, and tap `app-release.apk`.
3. **Allow installs from this source** (first time only):
   - Android shows *"For your security, your phone is not allowed to install
     unknown apps from this source."* Tap **Settings**.
   - Turn on **Allow from this source** for the app you used (Files, Chrome, Drive…).
   - Press back to return to the installer.
   - On Android 7 (API 24–25) the switch is global instead:
     **Settings → Security → Unknown sources**.
4. Tap **Install**. If Play Protect shows a warning, tap **More details →
   Install anyway** (wording varies by device).
5. Tap **Open**, or find **2048** in the app drawer.
6. Optional, for safety: turn **Allow from this source** back off in
   **Settings → Apps → Special app access → Install unknown apps**.

---

## Method B — Install from the PC with ADB

ADB ships with the Android SDK on this PC:
`C:\Users\cx09d\AppData\Local\Android\Sdk\platform-tools\adb.exe`

1. **Enable Developer options on the phone**: **Settings → About phone**, then tap
   **Build number** 7 times (on some phones it is under **Software information**).
2. **Enable USB debugging**: **Settings → System → Developer options → USB debugging** → on.
3. **Connect the phone with a USB cable.** Accept the prompt
   *"Allow USB debugging?"* on the phone (tick **Always allow from this computer**).
4. **Check the phone is seen**, in PowerShell:
   ```powershell
   & "C:\Users\cx09d\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
   ```
   The phone must be listed as `device`. If it says `unauthorized`, accept the
   prompt on the phone and run the command again.
5. **Install** (from the `2048_Project` folder):
   ```powershell
   & "C:\Users\cx09d\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "android\app\build\outputs\apk\release\app-release.apk"
   ```
   `-r` replaces an existing install and keeps its data. The output should end with `Success`.
6. **Launch it** from the app drawer, or:
   ```powershell
   & "C:\Users\cx09d\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell monkey -p com.apocalypse03.game2048 1
   ```

Wireless alternative (Android 11+): **Developer options → Wireless debugging → Pair
device with pairing code**, then on the PC run `adb pair <ip>:<pair-port>`
(enter the code) and `adb connect <ip>:<port>`, and continue from step 5.

---

## Updating to a newer build

Install the new APK the same way. It updates in place and keeps the best score,
settings, saved game and results, **as long as both builds are signed with the
same key**. The debug key is specific to this PC: an APK built on another
computer has a different signature and will fail with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. In that case, uninstall first (this deletes
the app's data).

## Uninstalling

- On the phone: long-press the **2048** icon → **Uninstall** (or
  **Settings → Apps → 2048 → Uninstall**).
- With ADB:
  ```powershell
  & "C:\Users\cx09d\AppData\Local\Android\Sdk\platform-tools\adb.exe" uninstall com.apocalypse03.game2048
  ```

## Troubleshooting

| Problem | Fix |
|---|---|
| "App not installed" / "There was a problem parsing the package" | The file is incomplete or damaged: copy it again. Check that the phone runs Android 7.0 or newer. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A copy signed with a different key is installed. Uninstall it, then install again. |
| `INSTALL_FAILED_OLDER_SDK` | The phone runs Android older than 7.0; not supported. |
| No **Install** button / install blocked | Allow **Install unknown apps** for the app that opens the APK (Method A, step 3). On work-managed phones, the admin may block sideloading. |
| Play Protect blocks the install | Tap **More details → Install anyway**. |
| `adb devices` shows nothing | Use a data-capable USB cable, set the USB mode to **File transfer**, re-enable USB debugging, or install the phone maker's USB driver (Windows). |
