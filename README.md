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
- Edit nama, kategori, barcode, harga, modal, foto dan batas restok tersedia. Edit tidak mengubah stok; penyesuaian stok/kulakan belum tersedia. Menonaktifkan barang menyimpan datanya dan riwayatnya, tetapi mencegah penjualan baru; dapat diaktifkan kembali. Penjualan menyimpan snapshot nama/harga/modal saat transaksi, sehingga edit berikutnya tidak mengubah riwayat sebelumnya selama riwayat tersebut masih tersimpan.
- Tombol simpan menampilkan popup selama **50 ms (0,05 detik)** sesuai permintaan; durasi ini mungkin sulit terlihat. Keranjang belum disimpan saat Activity dibuat ulang.
- Migrasi SQLite v1/v2 ke v3 dirancang mempertahankan data dan menambahkan metode pembayaran; transaksi lama dianggap tunai. Setelah migrasi, kebijakan penghapusan tujuh hari tetap berlaku untuk penjualan lama yang memenuhi syarat. Uji pada salinan data sebelum penggunaan nyata.

Pada Dashboard Kasir, pembayaran tunai (termasuk Rp0) menyimpan transaksi, stok, penerimaan tunai dan sisa kasbon secara atomik. Nama pelanggan wajib untuk kasbon. Ringkasan kasbon lokal ditampilkan di Dashboard. **Pesanan → Penjualan** membaca riwayat per akun, dan menampilkan rincian dalam popup tanpa halaman baru. Filter hari ini mengikuti jam perangkat; filter tunai lunas/kasbon menunjukkan kondisi saat transaksi, bukan saldo setelah pelunasan. Filter QRIS terpisah. Ringkasan nilai penjualan termasuk QRIS yang dicatat tetapi belum diverifikasi; tunai masuk bukan saldo laci dan nominal QRIS bukan bukti saldo bank. Laba kotor hanya ditampilkan jika modal seluruh barang diketahui.

### Retensi riwayat tujuh hari

- Riwayat penjualan dan rincian barang yang **tidak memiliki kasbon belum lunas** dihapus permanen setelah 7 × 24 jam sejak waktu transaksi. Pembersihan berjalan saat riwayat dibaca, saat aplikasi dibuka, dan dijadwalkan berkala tanpa internet oleh WorkManager. Android dapat menunda pekerjaan latar belakang (misalnya karena pembatasan baterai atau aplikasi dihentikan paksa), sehingga penghapusan tidak dijamin tepat pada detik kedaluwarsa. Jam perangkat yang keliru atau berubah memengaruhi waktu penghapusan.
- Transaksi kasbon dengan saldo belum lunas, beserta rincian barangnya, tidak dihapus oleh batas tujuh hari. Pembersihan menghapus catatan kasbon dengan saldo nol; jika transaksi terkait sudah berusia tujuh hari, riwayatnya ikut dihapus. **Pelunasan kasbon belum diimplementasikan**, sehingga pengguna belum dapat melunasinya di aplikasi; mekanisme ini perlu diintegrasikan dan diuji saat fitur pelunasan dibuat.
- Catatan penerimaan tunai tersimpan terpisah dan tidak dihapus oleh pembersihan riwayat, agar tidak menghilangkan data kas. Halaman Kas masih demo. Setelah riwayat dihapus, rincian barang dan nilai penjualan lama tidak dapat dipulihkan; tanpa backup, laporan historis yang mengandalkan rincian tersebut tidak bisa dibuat dengan lengkap. Penghapusan juga berlaku pada data lama yang memenuhi kriteria setelah pembaruan.

Bagian **Kulakan** belum tersedia; halaman Kas, Kasbon terperinci, Laporan dan Notifikasi masih demo. Angka contoh pada halaman-halaman tersebut bukan pembukuan toko.

## QRIS dan batasan

Tombol QRIS menampilkan QR nominal penuh secara offline dari teks payload QRIS statis toko; input hanya tersimpan di memori selama sesi. **Ketika tombol untuk membuat QR berhasil dijalankan, penjualan langsung dicatat dan stok dikurangi, tanpa membuat kasbon.** Tindakan itu mengasumsikan barang terjual walaupun pelanggan belum membayar atau tidak menyelesaikan pembayaran. Penjualan dicatat terpisah dari tunai dan diberi keterangan pembayaran belum diverifikasi; tidak dicatat sebagai penerimaan kas atau saldo bank. QR tampil/nominal pas bukan bukti pembayaran, dan aplikasi tidak melakukan konfirmasi otomatis atau manual.

Membuka lalu menutup dialog sebelum membuat QR, atau memasukkan payload tidak valid, tidak mencatat penjualan. Penyimpanan dan pengurangan stok dilakukan dalam transaksi database dan menggunakan ID transaksi untuk mencegah pengulangan pada percobaan yang sama. Jika aplikasi terhenti setelah penyimpanan tetapi sebelum QR tampil, transaksi mungkin sudah tercatat meskipun QR tidak lagi terlihat; periksa Pesanan dan stok sebelum mencoba kembali. Implementasi hanya menerima payload ASCII tertentu dan memerlukan validasi terhadap penyedia QRIS; belum diuji dengan merchant sesungguhnya. Jangan gunakan QR ini atau aplikasi ini untuk pembayaran dan pembukuan nyata sebelum build, pengujian dan integrasi tuntas.

## Pengujian manual di Android Studio

Gunakan emulator/perangkat dan **data uji yang boleh dihapus**. Login Google harus dikonfigurasi sesuai petunjuk di atas; tanpa konfigurasi, aplikasi tidak menyediakan login palsu.

1. Jalankan Gradle Sync, `assembleDebug`, `testDebugUnitTest`, dan `connectedDebugAndroidTest`. Periksa juga rotasi dan tampilan layar.
2. Simpan penjualan tunai lunas, sebagian, dan Rp0 dengan pelanggan. Buka Pesanan: periksa filter, popup rincian, snapshot harga dan pengurangan stok.
3. Untuk QRIS, coba payload invalid dan tutup dialog sebelum membuat QR: pastikan tidak ada penjualan baru atau perubahan stok. Dengan payload statis **uji**, buat QR, lalu periksa satu transaksi QRIS pada Pesanan, stok berkurang sekali, dan tidak ada kasbon/kas masuk tunai. Coba tekan tombol ulang, rotasi atau menutup aplikasi selama proses; periksa Pesanan sebelum mengulangi jika hasil tidak jelas. Payload sintetis dari `QrisPayloadTest` **bukan QR merchant dan tidak dapat menerima pembayaran**.
4. Pada data uji, gunakan Android Studio Database Inspector untuk mengubah `created_at` suatu transaksi lunas menjadi setidaknya 7 × 24 jam yang lalu, lalu buka/muat ulang Pesanan. Pastikan transaksi dan item terhapus, tetapi kasbon belum lunas tetap ada. Verifikasi transaksi berumur kurang dari tujuh hari tetap ada. Waktu pembersihan latar belakang tidak persis; membuka riwayat memicu pembersihan tanpa menunggu jadwal.
5. Uji pembaruan dari salinan database v1/v2, termasuk transaksi lama dan kasbon belum lunas. **Jangan gunakan database penting tanpa cadangan**: pembersihan riwayat adalah permanen dan dimulai setelah aplikasi dibuka.

Belum ada sinkronisasi, backup, printer, ekspor atau pelacakan perubahan stok. SQLite, foto dan profil hanya ada di perangkat; penghapusan data aplikasi, instal ulang atau kerusakan perangkat dapat menghilangkannya. Login offline tidak memvalidasi pencabutan sesi terhadap server. Pengujian unit dan instrumentasi tersedia di proyek, tetapi belum dijalankan dalam lingkungan kerja ini karena JDK dan Android SDK tidak tersedia; build, foto, migrasi dan fitur lainnya perlu diuji di Android Studio/emulator dan perangkat.

## Struktur

- `auth/` — integrasi Credential Manager/Firebase dan profil warung lokal.
- `data/` — katalog dan foto lokal, penyimpanan kasir SQLite, riwayat penjualan, perhitungan tunai dan pembentukan payload QRIS.
- `ui/` — alur autentikasi, layar, komponen dan Compose Preview.
- `ui/theme/` dan `res/drawable/` — tema, logo dan ilustrasi lokal.
- `app/src/test/` dan `app/src/androidTest/` — tes unit dan instrumentasi database.
