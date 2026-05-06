# 🃏 Qoneqo Solitaire — Dokumentasi Teknis & Fitur Lengkap

Game Klondike Solitaire profesional untuk Android yang dibangun dengan **Native Canvas & Kotlin**. Arsitektur berbasis MVVM dengan game engine custom yang dioptimasi untuk performa **60 FPS** yang stabil dan pengalaman bermain yang menenangkan.

---

## 🏗️ Arsitektur Sistem

### 1. Game Engine (Custom Canvas)
Berbeda dengan aplikasi Android standar yang menggunakan banyak `View`, game ini menggunakan sistem **Single Surface View** untuk rendering:
- **WorldRenderer**: Menangani penggambaran semua elemen game (kartu, partikel, efek visual) langsung ke `Canvas`.
- **PhysicsEngine**: Menghitung animasi fisik seperti kartu yang memantul (snap-back) dan animasi kemenangan (cascade).
- **InputHandler**: Menerjemahkan koordinat sentuhan ke dalam aksi game, menangani *Dual Coordinate Space* (Fixed HUD vs Scrollable Tableau).

### 2. Layout Architecture (Sticky HUD)
Game menggunakan desain **Sticky HUD** untuk kenyamanan maksimal:
- **HUD (Top Area)**: Stock, Waste, dan Foundations tetap berada di posisi atas layar (sticky).
- **Tableau (Scrollable Area)**: Area kartu utama dapat di-scroll secara vertikal jika jumlah kartu terlalu banyak, sehingga tetap rapi dan tidak saling menumpuk secara berlebihan.
- **Clipped Rendering**: Engine secara cerdas memotong area rendering agar kartu tableau tidak menutupi area HUD saat di-scroll ke atas.

---

## 🎮 Gameplay & Fitur Utama

### 1. Logbook System (100% Solvable)
Kami menjamin pengalaman bermain yang adil dan menantang:
- **Guaranteed Winnable**: Semua deck diambil dari `Logbook` yang telah diverifikasi secara profesional. Tidak ada lagi deck acak yang mustahil dimenangkan.
- **Persistent Game ID**: Melacak ID game tertentu untuk pemecahan masalah atau tantangan ulang.

### 2. Intelligent Solver & Hint
- **Smart Solver**: Algoritma `InternalSolver` menggunakan logika prioritas (greedy) untuk memberikan langkah terbaik.
- **Dual Visual Hint**: Menyoroti kartu sumber dan target, serta menarik garis penghubung visual untuk panduan yang jelas.
- **Auto-Finish**: Saat game sudah dipastikan menang (semua kartu terbuka), tombol "Auto-Finish" akan muncul untuk menyelesaikan sisa gerakan secara otomatis.

### 3. Visual Effects & "Cozy" Vibe
- **Win Cascade**: Animasi kartu melompat keluar dari foundation dengan fisika gravitasi dan pantulan (bounce).
- **Particle System**: Efek sparkle saat kartu masuk ke foundation.
- **Glassmorphism UI**: Modal dialog menggunakan efek transparansi modern (75% opacity) yang memberikan kesan premium dan bersih.
- **Adaptive Spacing**: Jarak antar kartu di tableau (offset) menyesuaikan secara dinamis untuk menjaga kerapian visual.

---

## 📱 Antarmuka Pengguna (UI)

### Stats & Controls
| Elemen | Fungsi |
| --- | --- |
| **Score** | Poin real-time (+10 foundation, +5 tableau). |
| **Moves** | Penghitung jumlah gerakan. |
| **Timer** | Berhenti otomatis saat modal dibuka, lanjut saat ditutup. |
| **Undo** | Pembatalan langkah tak terbatas. |
| **Settings** | Konfigurasi musik, suara FX, dan kustomisasi warna meja. |

### Kustomisasi
Pemain dapat mengubah warna meja (Table Color) melalui Color Picker. UI secara otomatis menyesuaikan warna aksen dan transparansi dialog agar serasi dengan warna meja yang dipilih.

---

## 🛠️ Tech Stack
- **Language**: Kotlin 1.8+
- **Rendering**: Android Canvas API
- **Threading**: Background Game Thread (SurfaceHolder)
- **Database**: Room Persistence (SQLite) for High Scores
- **Architecture**: MVVM (ViewModel + Flow)
- **Serialisasi**: Kotlinx Serialization (for Logbook JSON)

---
_Dokumentasi Terupdate - Mei 2026_
_Dibuat oleh Qoneqo Team — Nikmati pengalaman Solitaire terbaik Anda!_
