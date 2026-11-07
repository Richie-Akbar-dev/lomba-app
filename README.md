# Warung Matriks — prototipe Android

Aplikasi Android native berbasis Kotlin, Jetpack Compose dan Material 3. Tampilan lain masih menggunakan data demo; tahap saat ini berfokus pada alur masuk Google dan pengisian profil warung. Logo pengguna dipertahankan. Warna utama: `#2563EB`, `#38BDF8`, `#F1F5F9`, dan `#FFFFFF`.

## Buka proyek

1. Buka folder yang berisi `settings.gradle.kts` di Android Studio.
2. Gunakan JDK 17 dan Android SDK Platform 34. Jalankan Gradle Sync dan instal komponen yang diminta.
3. Pilih konfigurasi `app` dan emulator/perangkat Android 7.0 (API 24) atau lebih baru. Untuk login Google, gunakan perangkat atau emulator yang memiliki Google Play services dan koneksi internet.
4. Terminal: `./gradlew assembleDebug` (Windows `gradlew.bat assembleDebug`); unit test: `./gradlew testDebugUnitTest`.

Tanpa konfigurasi Google/Firebase di bawah, proyek tetap dapat disinkronkan dan dibuka, tetapi layar masuk menampilkan pemberitahuan dan tombol login dinonaktifkan; aplikasi tidak membuat sesi palsu.

## Alur masuk dan daftar

1. Layar logo tampil sekitar dua detik. Jika Firebase memiliki sesi pengguna yang tersimpan di perangkat, aplikasi mencoba memulihkannya tanpa meminta login baru.
2. Jika belum ada sesi, pengguna masuk atau mendaftar dengan **akun Google** melalui Android Credential Manager dan Firebase Authentication. Pembuatan akun maupun masuk memerlukan internet. Tidak ada OTP aplikasi, login nomor HP, atau PIN tambahan.
3. Pada perangkat yang belum memiliki profil untuk akun tersebut, isi nama pemilik, nama warung dan jenis usaha. Profil disimpan di penyimpanan privat perangkat, terpisah menurut UID akun. Tidak diunggah ke server.
4. Setelah masuk dan profil tersimpan, aplikasi dapat dibuka kembali tanpa internet menggunakan sesi Firebase yang tersimpan dan profil lokal. Firebase tidak bisa memeriksa pencabutan akses atau penonaktifan akun secara online ketika perangkat offline; ini adalah kompromi keamanan yang perlu dipertimbangkan sebelum produksi.
5. **Akun → Keluar akun** menghapus sesi Firebase dari perangkat; untuk masuk kembali diperlukan internet. Profil tetap di perangkat, tidak dikirim ke cloud. Menghapus data aplikasi atau menginstal ulang akan menghapus profil; perangkat lain perlu mengisi profil lagi.

Sesi yang dipulihkan saat offline bukan bukti verifikasi ulang terhadap server. Hindari memakai aplikasi prototipe ini untuk data sensitif atau transaksi nyata. Firebase SDK dapat melakukan pemeliharaan sesi saat jaringan tersedia; tidak ada sinkronisasi data warung.

## Konfigurasi Google Sign-In (diperlukan untuk pengujian nyata)

Konfigurasi harus berasal dari proyek Firebase/Google Cloud milik Anda. Nilai ini adalah **identitas konfigurasi klien** yang akan tertanam dalam APK, bukan rahasia server. Jangan menaruh token, password, private key, service-account key, atau OAuth client secret di repository, file konfigurasi aplikasi, log maupun chat. Batasi API key dan OAuth client sesuai paket, fingerprint dan layanan yang diperlukan.

1. Di Firebase Console, buat/pilih proyek dan tambahkan aplikasi Android dengan package `id.warungmatriks.app`.
2. Tambahkan fingerprint SHA-1 sertifikat penandatanganan untuk debug dan, nanti, release/Play App Signing yang digunakan. Jalankan `./gradlew signingReport` pada komputer pengembang untuk melihat fingerprint debug/release yang tersedia.
3. Aktifkan **Google** pada Firebase Authentication → Sign-in method dan lengkapi konfigurasi OAuth yang diminta Google. Pastikan ada **Web client ID** untuk token Google yang diverifikasi Firebase. Gunakan Web client ID, bukan Android client ID, pada `WM_GOOGLE_WEB_CLIENT_ID`.
4. Dapatkan Android App ID, Project ID, dan API key dari pengaturan proyek Firebase atau file konfigurasi Android yang diunduh dari Firebase. Jika memakai `google-services.json`, nilai tersedia pada `client[].client_info.mobilesdk_app_id`, `project_info.project_id`, dan `client[].api_key[].current_key`; pilih client yang package-nya cocok. Web OAuth client ID biasanya ada pada `client[].oauth_client[]` dengan `client_type` 3 atau pada Google Cloud Console.
5. Tambahkan nilai berikut ke **`local.properties`** di folder proyek, tanpa menghapus `sdk.dir` yang dibuat Android Studio. Ganti teks contoh dengan nilai proyek Anda. File ini sudah diabaikan Git; jangan tempel nilainya di chat.

```properties
WM_FIREBASE_API_KEY=ISI_DARI_FIREBASE
WM_FIREBASE_APP_ID=ISI_DARI_FIREBASE
WM_FIREBASE_PROJECT_ID=ISI_DARI_FIREBASE
WM_GOOGLE_WEB_CLIENT_ID=ISI_WEB_CLIENT_ID
```

6. Gradle Sync dan rebuild. Login Google harus diuji pada perangkat/emulator dengan Google Play services. Jika gagal, periksa koneksi, aktivasi Google provider, package, SHA-1, OAuth consent/client, dan kecocokan nilai Firebase. Kesalahan hanya ditampilkan sebagai pesan umum; token tidak dicetak di log aplikasi.

Karena nilai `local.properties` tidak ikut Git, setiap lingkungan build harus mengonfigurasikannya sendiri. Konfigurasi klien bukan pengganti aturan keamanan server. Firebase Authentication memverifikasi token Google secara online; tidak ada server atau database untuk data warung pada tahap ini.

## Cara memeriksa alur

- Saat belum ada konfigurasi, lihat pemberitahuan pada layar masuk dan pastikan tidak bisa masuk palsu.
- Setelah konfigurasi: masuk dengan Google saat online, isi profil, lalu tutup dan buka ulang aplikasi dengan internet dimatikan. Periksa profil dan layar Kasir muncul kembali.
- Nyalakan internet, keluar melalui Akun, lalu buka aplikasi kembali. Pastikan layar masuk tampil dan login ulang membutuhkan internet.
- Uji pembatalan pemilihan akun, koneksi mati saat login, nilai konfigurasi salah, serta rotasi layar. Semua ini memerlukan pengujian langsung di Android Studio/perangkat.

## Halaman demo dan Preview

Navigasi bawah: **Kasir, Pesanan, Barang, Laporan, Akun**. Halaman Kas, Kasbon, Tambah Barang dan Notifikasi diakses melalui halaman terkait. Daftar/masuk sekarang satu tombol Google, diikuti formulir profil lokal.

Cari Preview di file `ui/SplashScreen.kt`, `ui/AuthScreens.kt`, `ui/WarungMatriksApp.kt`, `ui/OverviewScreens.kt`, `ui/InventoryScreens.kt`, dan `ui/AccountScreens.kt`. Setelah Gradle Sync, pilih **Split** atau **Design**, lalu **Build & Refresh** jika diminta.

## Batasan fitur lain

Data produk, keranjang, kas, kasbon, dan laporan tetap **demo**. Contoh keranjang menghasilkan total Rp29.500 dan laba kotor Rp6.000, sebelum biaya operasional. Belum ada database transaksi, pembaruan stok, kontrol akses, QRIS, notifikasi, sinkronisasi, backup, printer atau ekspor. Pembayaran tunai hanya simulasi dan data keranjang dapat hilang saat Activity dibuat ulang. Harga modal dan laba yang tampak pada demo tidak dilindungi hak akses. Jangan memasukkan data keuangan nyata.

## Struktur

- `auth/` — integrasi Credential Manager/Firebase dan profil warung lokal.
- `data/` — model serta data produk demo.
- `ui/` — alur autentikasi, layar, komponen dan Compose Preview.
- `ui/theme/` dan `res/drawable/` — tema, logo dan ilustrasi lokal.
- `app/src/test/` — contoh unit test perhitungan.
