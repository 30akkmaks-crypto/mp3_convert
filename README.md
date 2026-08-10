🎵 Конвертер аудио (MP3) — профессиональное преобразование звука
Версия: 1.0.0 | Лицензия: MIT | Статус: ✅ Активная разработка

https://img.shields.io/github/repo-size/yourusername/mp3-converter https://img.shields.io/github/last-commit/yourusername/mp3-converter https://img.shields.io/github/languages/count/yourusername/mp3-converter

🎧 Описание
Конвертер аудио (MP3) — это мощная консольная утилита для преобразования аудиофайлов в формат MP3 (и обратно, где это возможно). Программа поддерживает пакетную обработку, настройку битрейта, частоты дискретизации и другие параметры профессионального уровня.

Возможности:

✅ Конвертация в MP3 из: WAV, FLAC, OGG, M4A, AAC, WMA, AIFF, и др.

✅ Конвертация из MP3 в другие форматы (WAV, FLAC, OGG, M4A)

✅ Настройка битрейта (32–320 кбит/с, VBR/CBR)

✅ Настройка частоты дискретизации (8–192 кГц)

✅ Изменение количества каналов (моно/стерео)

✅ Нормализация громкости (опционально)

✅ Сохранение ID3-тегов (при конвертации MP3 → MP3)

✅ Пакетная обработка всей папки (рекурсивно)

✅ Прогресс-бар и подробная статистика

✅ Кроссплатформенность (Linux, macOS, Windows)

Проект содержит 8 полноценных реализаций на разных языках программирования. Все версии используют FFmpeg — самый мощный и универсальный инструмент для работы с аудио и видео.

✨ Возможности
Функция	Описание
Конвертация в MP3	Из WAV, FLAC, OGG, M4A, AAC, WMA, AIFF и других
Конвертация из MP3	В WAV, FLAC, OGG, M4A, AAC (опционально)
Битрейт	32, 64, 96, 128, 192, 256, 320 кбит/с (CBR/VBR)
Частота	8, 11.025, 16, 22.05, 24, 32, 44.1, 48, 96, 192 кГц
Каналы	Моно (1), Стерео (2), или автоматически
Нормализация	Выравнивание громкости (EBU R128)
ID3-теги	Сохранение метаданных (название, артист, альбом)
Пакетная обработка	Рекурсивная конвертация всех файлов в папке
Прогресс	Отображение хода выполнения
Кроссплатформенность	Работает на всех основных ОС
📦 Установка и запуск
Общие требования
Для работы всех реализаций необходим установленный FFmpeg:

bash
# Ubuntu/Debian
sudo apt install ffmpeg

# macOS
brew install ffmpeg

# Windows
# Скачайте с https://ffmpeg.org/download.html и добавьте в PATH
Запуск на разных языках
Язык	Файл	Зависимости	Команда запуска
Python	mp3_convert.py	нет (использует subprocess)	python3 mp3_convert.py input.wav --bitrate 192
Node.js	mp3_convert.js	yargs	npm install yargs && node mp3_convert.js input.wav --bitrate 192
Rust	mp3_convert.rs	clap, glob	cargo run -- input.wav --bitrate 192
Go	mp3_convert.go	нет	go run mp3_convert.go input.wav --bitrate 192
Java	Mp3Convert.java	нет (Java 8+)	javac Mp3Convert.java && java Mp3Convert input.wav --bitrate 192
C#	mp3_convert.cs	нет (.NET Core)	dotnet run input.wav --bitrate 192
Ruby	mp3_convert.rb	нет	ruby mp3_convert.rb input.wav --bitrate 192
C++	mp3_convert.cpp	нет (C++17)	g++ -std=c++17 -o mp3_convert mp3_convert.cpp && ./mp3_convert input.wav --bitrate 192
📂 Структура репозитория
text
.
├── README.md
├── python/
│   └── mp3_convert.py
├── go/
│   └── mp3_convert.go
├── rust/
│   ├── Cargo.toml
│   └── src/
│       └── main.rs
├── cpp/
│   └── mp3_convert.cpp
├── java/
│   └── Mp3Convert.java
├── csharp/
│   └── mp3_convert.cs
├── ruby/
│   └── mp3_convert.rb
└── javascript/
    ├── package.json
    └── mp3_convert.js
🎮 Использование
bash
# Базовая конвертация в MP3 (битрейт 192, стерео, 44.1 кГц)
mp3_convert input.wav

# Указать битрейт и выходной файл
mp3_convert input.wav --bitrate 320 --output song.mp3

# Конвертация с нормализацией громкости
mp3_convert input.wav --normalize

# Моно-режим (уменьшение размера)
mp3_convert input.wav --channels 1

# Пакетная конвертация всех файлов в папке
mp3_convert ./audio/ --recursive

# Конвертация в другой формат (из MP3 в WAV)
mp3_convert input.mp3 --format wav

# Вывод списка поддерживаемых форматов
mp3_convert --formats
🛠️ Особенности реализаций
Python – subprocess и argparse, простой и надёжный код.

Node.js – child_process и yargs, асинхронная обработка.

Rust – std::process::Command и clap, безопасность и скорость.

Go – os/exec и flag, быстрый запуск.

Java – ProcessBuilder и ручной парсинг аргументов.

C# – System.Diagnostics.Process, современный синтаксис.

Ruby – system и optparse, выразительный код.

C++ – system() и ручной парсинг, классика.

Все версии используют FFmpeg для конвертации, что обеспечивает единообразие и высокое качество звука.

🤝 Вклад
PR и issues приветствуются. Добавляйте поддержку новых форматов, улучшайте производительность, расширяйте функциональность.

📄 Лицензия
MIT License.
