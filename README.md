# Android NS200 CDI R7

Native Android 10+ tanpa cloud. MainActivity memiliki menu Dashboard, Quick
Setup, Tuning dan Guide. Dashboard menampilkan RPM/TPS/advance/aki/dua HV/map/
limiter/fault seperti aplikasi Torque. Quick Setup menangani BARU sampai READY
tanpa flash ulang. Guide memuat wiring J1, WeAct, charger, koil, fan dan audio.

Tema `Technical Dashboard / Data Grid` memakai canvas `#0A0B0D`, surface
`#111214/#151619/#1A1B1E`, border `#222222/#333333`, kontrol utama `#F27D26`,
status siap `#00FF00`, telemetry `#00E5FF`, dan hazard `#FF4444`. Seluruh angka
memakai monospace sistem agar stabil tanpa dependensi font eksternal.

Virtual sound menyediakan preset 1/2/3/4 silinder dan pemilih file MP3/WAV/OGG.

Build: buka folder `android-app` di Android Studio, pasang SDK 35 dan Gradle JDK
17, lalu sync dan `Build > Build APK(s)`. Gradle wrapper 8.9 disertakan, sehingga
terminal juga dapat menjalankan `./gradlew clean assembleDebug lintDebug`.
Beri izin Nearby Devices; Android 10-11 memerlukan Location untuk scan. Aplikasi
mencari `NS200-CDI-R7`.

## Cara koneksi BLE

Jangan melakukan Pair manual lebih dahulu dari Pengaturan Bluetooth. Tekan
`Hubungkan` dari aplikasi; aplikasi memulai bonding dan Android akan meminta
PIN enam digit. Masukkan `123456`. Firmware mendeklarasikan board sebagai
perangkat headless `DISPLAY_ONLY`, memakai fixed passkey dan mewajibkan link
terautentikasi sebelum telemetry/perintah dapat diakses.

Jika ponsel pernah mencoba firmware lama atau PIN tetap ditolak, pilih
`Lupakan NS200-CDI-R7` sekali di Pengaturan Bluetooth, matikan/nyalakan
Bluetooth, lalu tekan `Hubungkan` dan masukkan `123456` kembali.

Aplikasi membuka service terlebih dahulu, meminta MTU 64, baru mengaktifkan dua
notification secara berurutan. Status 8 pada callback koneksi berarti timeout;
aplikasi mencoba ulang otomatis paling banyak dua kali.

Timing tetap lokal di STM32 dan terus berjalan memakai map tersimpan jika BLE
atau ponsel terputus.
