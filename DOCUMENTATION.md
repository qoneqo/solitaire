# 🃏 Qoneqo Solitaire — Dokumentasi Fitur Lengkap

Game Klondike Solitaire profesional untuk Android yang dibangun dengan **Native Canvas & Kotlin**. Arsitektur berbasis MVVM dengan game engine custom yang dioptimasi untuk performa **60 FPS** tanpa lag.

---

## 🎮 Gameplay Dasar

### Aturan Klondike Solitaire

- **Tujuan**: Memindahkan semua 52 kartu ke 4 **Foundation Pile** (♣♦♥♠), dimulai dari Ace hingga King.
- **Tableau**: Kartu disusun berurutan menurun dengan warna bergantian (merah-hitam).
- **Stock & Waste**: Kartu diambil dari Stock ke Waste. Jika Stock habis, Waste dapat didaur ulang.
- **King**: Satu-satunya kartu yang bisa menempati slot Tableau kosong.

### Kontrol Sentuhan

| Aksi               | Gesture                                                               |
| ------------------ | --------------------------------------------------------------------- |
| Ambil kartu        | Tap & drag pada kartu terbuka                                         |
| Pindahkan tumpukan | Drag kartu di tengah tumpukan untuk memindah seluruh grup             |
| Auto-Foundation    | Double tap pada kartu untuk mengirimnya ke foundation secara otomatis |
| Kontrol Stock      | Tap pada Stock untuk deal, atau saat kosong untuk daur ulang          |

---

## 🖥️ Antarmuka (HUD)

### Stats Bar (Atas)

- **Score**: Poin real-time (+10 per foundation, +5 per tableau move).
- **Moves**: Penghitung jumlah gerakan yang dilakukan.
- **Time**: Timer otomatis yang mencatat durasi permainan.

### Tombol Aksi (Bawah)

- **Undo**: Riwayat pembatalan tidak terbatas (Command Pattern).
- **Hint**: Menampilkan saran langkah terbaik dengan visualisasi canggih.
- **Settings**: Akses ke konfigurasi game, kustomisasi visual, dan fitur tambahan.

---

## 💡 Sistem Hint (Smart Guide)

Sistem Hint di game ini dirancang untuk sangat informatif dan visual:

- **Dual Highlighting**: Menerangi kartu yang harus dipindah (Source) dan lokasi tujuannya (Target) secara bersamaan dengan glow kuning.
- **Connecting Line**: Menampilkan garis penghubung antar kartu untuk menunjukkan arah pergerakan yang disarankan.
- **Auto-Dismiss**: Efek glow akan **langsung hilang** begitu Anda mulai menggerakkan kartu, menjaga layar tetap bersih.
- **Logika Algoritma**: Menggunakan `InternalSolver` yang diprioritaskan untuk memaksimalkan peluang kemenangan.

---

## 🤖 Auto Solve (Bot Intelligence)

Game ini dilengkapi dengan bot pintar yang dapat membantu atau menyelesaikan permainan:

- **Prioritas Cerdas**: Bot mendahulukan mengungkap kartu tertutup dan mengisi foundation.
- **Kecepatan**: Berjalan otomatis dengan interval 0.3 detik per langkah.
- **Sinkronisasi Logbook**: Bot mengikuti urutan logbook yang sudah diverifikasi di awal permainan.
- **Deteksi Loop**: Memiliki pengaman untuk berhenti jika terjebak dalam perulangan gerakan yang sia-sia.

---

## 🗂️ Logbook System (100% Solvable)

Kami menjamin pengalaman bermain yang adil dan menantang:

- **Guaranteed Winnable**: Tidak ada lagi deck acak yang mustahil dimenangkan. Semua 100 game awal di Logbook telah diverifikasi oleh bot solver.
- **Reverse-Engineered Decks**: Deck disusun secara khusus untuk memastikan aliran permainan yang logis dan menyenangkan.
- **Persistent ID**: Setiap game memiliki ID unik yang tercatat saat Anda menang.

---

## ✨ Visual Effects & "Juice"

- **Cascading Win**: Saat menang, kartu akan melompat keluar dari foundation dengan fisika gravitasi dan pantulan (bounce) yang ikonik.
- **Particle System**: Ledakan kembang api kecil (sparkles) saat kartu masuk ke foundation atau tableau.
- **Smooth Physics**: Animasi "Snap-Back" yang halus jika kartu dilepas di posisi yang tidak valid.

1.  **Optimized Audio**: Efek suara seperti `paper_slide` dan `tapping_glass` telah dipangkas (0.3s) untuk respon yang lebih instan dan snappy.

---

## ⚙️ Fitur Pengaturan & Eksternal

Menu Settings menyediakan akses ke:

- **New Game**: Memulai sesi baru dari koleksi game winnable.
- **High Scores**: Papan peringkat lokal (Top 10) berbasis database Room dengan UI responsif.
- **Customize Table Color**: Fitur kustomisasi warna meja melalui modal pemilihan warna berbasis grid yang responsif.
- **Auto Solve Toggle**: Mengaktifkan/matikan bantuan bot.
- **Sound Toggle**: Mengontrol semua efek suara game (Deal, Place, Win).
- **Ambient Music Toggle**: Mengontrol musik latar yang menenangkan.
- **Support Developer**: Tombol dukungan.
- **Exit Game**: Tombol keluar.

### 🕒 Timer Intelligence

- **Auto-Pause**: Timer permainan akan otomatis berhenti (_paused_) saat modal Settings atau High Score dibuka, memberikan pemain waktu untuk bernapas tanpa merusak skor waktu.
- **Auto-Resume**: Timer akan otomatis berlanjut begitu modal ditutup.

---

## 🛠️ Tools Pengembang (Internal)

Tersedia di folder `/tools`:

- `logbook_generator.py`: Script Python untuk membuat koleksi game baru yang dijamin 100% winnable.
- `logbook_verifier.py`: Script untuk memverifikasi integritas file logbook terhadap logika bot saat ini.

---

## 🏗️ Arsitektur Teknis

- **Core**: Kotlin Native Canvas API (Zero-Allocation Loop).
- **Database**: Android Room (SQLite) untuk Skor & State.
- **Pattern**: MVVM (Model-View-ViewModel) + Command Pattern (Undo).
- **Modern UI Components**: Menggunakan `ConstraintLayout` dan `RecyclerView` untuk dialog responsif dengan efek **Glassmorphism (75% opacity)**.
- **Adaptive Tableau Layout**: Sistem cerdas yang secara otomatis mengatur jarak antar kartu (*vertical offset*) saat tumpukan kartu semakin tinggi, memastikan semua kartu tetap terlihat dan dapat diklik bahkan di layar horizontal yang sempit.
- **Thread Management**: Dedicated Background Game Thread untuk memastikan UI tetap responsif.

---

_Dibuat oleh Qoneqo Team — 2026. Nikmati pengalaman Solitaire terbaik Anda!_
