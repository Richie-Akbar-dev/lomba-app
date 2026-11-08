# Warung Matriks — prototipe Android

Aplikasi Android native berbasis Kotlin, Jetpack Compose dan Material 3. Prototipe mencakup alur masuk Google, profil lokal, katalog dan kasir lokal; sebagian halaman lain masih menggunakan data demo. Logo pengguna dipertahankan. Warna utama: `#2563EB`, `#38BDF8`, `#F1F5F9`, dan `#FFFFFF`.

## Buka proyek

1. Buka folder yang berisi `settings.gradle.kts` di Android Studio.
2. Gunakan JDK 17 dan Android SDK Platform 34. Jalankan Gradle Sync dan instal komponen yang diminta.
3. Pilih konfigurasi `app` dan emulator/perangkat Android 7.0 (API 24) atau lebih baru. Untuk login Google, gunakan perangkat atau emulator yang memiliki Google Play services dan koneksi internet.
4. Terminal: `./gradlew assembleDebug` (Windows `gradlew.bat assembleDebug`); unit test: `./gradlew testDebugUnitTest`. Pada emulator/perangkat: `./gradlew connectedDebugAndroidTest` untuk pengujian database lokal.

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

Karena nilai `local.properties` tidak ikut Git, setiap lingkungan build harus mengonfigurasikannya sendiri. Konfigurasi klien bukan pengganti aturan keamanan server. Firebase Authentication memverifikasi token Google secara online; belum ada server atau database cloud untuk data warung pada tahap ini.

## Cara memeriksa alur

- Saat belum ada konfigurasi, lihat pemberitahuan pada layar masuk dan pastikan tidak bisa masuk palsu.
- Setelah konfigurasi: masuk dengan Google saat online, isi profil, lalu tutup dan buka ulang aplikasi dengan internet dimatikan. Periksa profil dan layar Kasir muncul kembali.
- Nyalakan internet, keluar melalui Akun, lalu buka aplikasi kembali. Pastikan layar masuk tampil dan login ulang membutuhkan internet.
- Uji pembatalan pemilihan akun, koneksi mati saat login, nilai konfigurasi salah, serta rotasi layar. Semua ini memerlukan pengujian langsung di Android Studio/perangkat.

## Halaman demo dan Preview

Navigasi bawah: **Kasir, Pesanan, Barang, Laporan, Akun**. Halaman Kas, Kasbon, Tambah/Edit Barang dan Notifikasi diakses melalui halaman terkait. Daftar/masuk sekarang satu tombol Google, diikuti formulir profil lokal.

Cari Preview di file `ui/SplashScreen.kt`, `ui/AuthScreens.kt`, `ui/WarungMatriksApp.kt`, `ui/OverviewScreens.kt`, `ui/OrdersScreen.kt`, `ui/InventoryScreens.kt`, dan `ui/AccountScreens.kt`. Setelah Gradle Sync, pilih **Split** atau **Design**, lalu **Build & Refresh** jika diminta.

## Katalog dan kasir lokal (prototipe)

- Barang disimpan per UID akun di SQLite pada perangkat, bersama stok, transaksi tunai dan kasbon. Katalog inventaris dan kasir membaca sumber yang sama. Saat pertama digunakan, **empat barang contoh otomatis ditambahkan** untuk mempertahankan data stok prototipe sebelumnya; bukan data toko sebenarnya. Barang contoh dapat diubah atau dinonaktifkan.
- Tambah barang: nama dan harga jual wajib, satuan pcs; kategori dapat ditulis sendiri, barcode opsional harus unik per akun, stok awal dan batas minimum tidak negatif. Restok ditandai saat stok ≤ batas. Stok awal tidak otomatis menjadi catatan kulakan atau kas keluar.
- Modal boleh kosong dan berarti tidak diketahui, bukan Rp0. Jika modal diketahui dan harga jual lebih rendah, form memperingatkan pengguna. Laba kotor tidak dihitung untuk keranjang dengan modal yang tidak diketahui.
- Foto opsional dapat dipilih dari galeri atau diambil lewat aplikasi kamera perangkat. Foto diubah ukurannya dan disimpan di penyimpanan privat aplikasi; tanpa foto dipakai ikon barang (barang contoh memiliki ilustrasi bawaan). Edit foto mengganti gambar di katalog. Izin kamera diberikan kepada aplikasi kamera melalui FileProvider; kompatibilitas perlu diuji di perangkat.
- Edit nama, kategori, barcode, harga, modal, foto dan batas restok tersedia. Edit tidak mengubah stok; penyesuaian stok/kulakan belum tersedia. Menonaktifkan barang menyimpan datanya dan riwayatnya, tetapi mencegah penjualan baru; dapat diaktifkan kembali. Penjualan menyimpan snapshot nama/harga/modal saat transaksi, sehingga edit berikutnya tidak mengubah riwayat sebelumnya.
- Tombol simpan menampilkan popup selama **50 ms (0,05 detik)** sesuai permintaan; durasi ini mungkin sulit terlihat. Keranjang belum disimpan saat Activity dibuat ulang.
- Migrasi skema SQLite v1 ke v2 dirancang mempertahankan stok, transaksi, penerimaan tunai dan kasbon lama, sambil menambahkan katalog dan modal opsional pada rincian penjualan. Migrasi harus diuji pada perangkat dengan data lama sebelum penggunaan nyata.

Pada Dashboard Kasir, pembayaran tunai (termasuk Rp0) menyimpan transaksi, stok, penerimaan tunai dan sisa kasbon secara atomik. Nama pelanggan wajib untuk kasbon. Ringkasan kasbon lokal ditampilkan di Dashboard. **Pesanan → Penjualan** membaca transaksi per akun dari SQLite, termasuk transaksi tunai sebagian atau Rp0, dan menampilkan rincian snapshot barang, total, uang diserahkan, kembalian, tunai masuk dan kasbon saat transaksi. Filter hari ini menggunakan tanggal/jam perangkat; filter lunas/kasbon dan angka kasbon menunjukkan kondisi saat transaksi, bukan saldo setelah pelunasan. Ringkasan omzet dan tunai penjualan bukan saldo laci. Laba kotor ditampilkan hanya jika modal seluruh barang diketahui. Data lama yang tersimpan dapat terlihat setelah migrasi; QRIS tidak dicatat. Daftar dimuat bertahap dan dapat dimuat ulang. Bagian **Kulakan** belum tersedia; halaman Kas, Kasbon terperinci, Laporan dan Notifikasi masih demo. Pelunasan kasbon, saldo laci nyata, kulakan dan laporan nyata belum tersedia. Angka contoh pada halaman-halaman tersebut bukan pembukuan toko.

## QRIS dan batasan

Tombol QRIS menampilkan QR nominal penuh secara offline dari teks payload QRIS statis toko; input hanya tersimpan di memori selama sesi. **QRIS tidak membuat kasbon, tetapi saat ini juga tidak mencatat transaksi maupun membuktikan pembayaran berhasil.** QR tampil/nominal pas bukan konfirmasi lunas. Belum ada konfirmasi otomatis atau manual; cara pencatatan pembayaran QRIS perlu diputuskan. Implementasi hanya menerima payload ASCII tertentu dan memerlukan validasi terhadap penyedia QRIS; belum diuji dengan merchant sesungguhnya. Jangan gunakan QR ini atau aplikasi ini untuk pembayaran dan pembukuan nyata sebelum build, pengujian dan integrasi tuntas.

Belum ada sinkronisasi, backup, printer, ekspor atau pelacakan perubahan stok. SQLite, foto dan profil hanya ada di perangkat; penghapusan data aplikasi, instal ulang atau kerusakan perangkat dapat menghilangkannya. Login offline tidak memvalidasi pencabutan sesi terhadap server. Pengujian unit dan instrumentasi tersedia di proyek, tetapi belum dijalankan dalam lingkungan kerja ini karena JDK dan Android SDK tidak tersedia; build, foto, migrasi dan fitur lainnya perlu diuji di Android Studio/emulator dan perangkat.

## Struktur

- `auth/` — integrasi Credential Manager/Firebase dan profil warung lokal.
- `data/` — katalog dan foto lokal, penyimpanan kasir SQLite, riwayat penjualan, perhitungan tunai dan pembentukan payload QRIS.
- `ui/` — alur autentikasi, layar, komponen dan Compose Preview.
- `ui/theme/` dan `res/drawable/` — tema, logo dan ilustrasi lokal.
- `app/src/test/` dan `app/src/androidTest/` — tes unit dan instrumentasi database.
