# D-Pad Reader — proyek Android (WebView, offline)

Package ID: `com.dpadreader.app` · Nama aplikasi: **D-Pad Reader** · Hasil: `D-Pad-Reader.apk`

## Cara 1 — Build otomatis di GitHub (tanpa Android Studio)
1. Buat repositori baru di github.com (boleh private).
2. Unggah **seluruh isi folder ini** (termasuk folder tersembunyi `.github`) ke cabang `main`.
   Cara mudah: di halaman repositori pilih *Add file → Upload files*, seret semua isi folder.
3. Buka tab **Actions** → workflow **Build D-Pad Reader APK** (otomatis jalan setelah upload; atau klik *Run workflow*).
4. Tunggu ±5–8 menit sampai hijau. Buka run tersebut → bagian **Artifacts** → unduh **D-Pad-Reader-APK** (ZIP berisi `D-Pad-Reader.apk`).
5. Ekstrak ZIP, kirim APK ke HP, buka, izinkan "instal dari sumber ini", lalu pasang.

## Cara 2 — Android Studio
Buka folder ini → Gradle Sync (JDK 17) → *Build > Build APK(s)* → `app/build/outputs/apk/debug/app-debug.apk` (ganti nama menjadi `D-Pad-Reader.apk`).

## Perubahan dari paket sebelumnya
- Package ID diganti ke `com.dpadreader.app`.
- Ikon launcher baru (adaptive icon + PNG untuk Android lama + ikon bulat).
- `MainActivity` memakai `android.app.Activity` (menghilangkan dependensi AndroidX yang belum dideklarasikan di Gradle).
- targetSdk 34 agar layout + keyboard virtual (`adjustResize`) tidak rusak oleh edge-to-edge paksa Android 15.
- Ekspor CSV/XLSX: blob disimpan di memori sehingga tidak gagal saat `revokeObjectURL` dipanggil; dialog simpan Android dipakai.
- Impor XLSX: bila WebView tidak punya `DecompressionStream`, dipakai dekompresor JavaScript cadangan (sudah diuji terhadap zlib).
- Teks status ekspor tidak lagi menyebut "folder Download".

## Status pengujian (jujur)
Sudah: sintaks JS valid; dekompresor cadangan cocok dengan zlib pada 21 kasus uji.
Belum (butuh HP/emulator): kompilasi Gradle, instal, ikon di launcher, file picker, dialog simpan, mode pesawat, keyboard virtual.
