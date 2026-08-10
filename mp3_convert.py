# mp3_convert.py
import subprocess
import argparse
import os
import sys
import shutil
from pathlib import Path

SUPPORTED_INPUT = ('.wav', '.flac', '.ogg', '.m4a', '.aac', '.wma', '.aiff', '.mp3', '.m4b')
SUPPORTED_OUTPUT = ('mp3', 'wav', 'flac', 'ogg', 'm4a', 'aac')

def check_ffmpeg():
    """Проверяет наличие ffmpeg в PATH."""
    if shutil.which("ffmpeg") is None:
        print("Ошибка: ffmpeg не найден. Установите FFmpeg и добавьте в PATH.", file=sys.stderr)
        sys.exit(1)

def build_ffmpeg_cmd(input_file, output_file, bitrate, channels, sample_rate, normalize, format_out):
    """Формирует команду ffmpeg с учётом всех параметров."""
    cmd = ["ffmpeg", "-i", input_file, "-y"]
    
    # Битрейт
    if bitrate:
        cmd.extend(["-b:a", f"{bitrate}k"])
    
    # Частота дискретизации
    if sample_rate:
        cmd.extend(["-ar", str(sample_rate)])
    
    # Количество каналов
    if channels:
        cmd.extend(["-ac", str(channels)])
    
    # Нормализация громкости (EBU R128)
    if normalize:
        cmd.extend(["-af", "loudnorm=I=-16:LRA=11:TP=-1.5"])
    
    # Кодек (MP3 по умолчанию)
    if format_out == 'mp3':
        cmd.extend(["-c:a", "libmp3lame"])
    elif format_out == 'wav':
        cmd.extend(["-c:a", "pcm_s16le"])
    elif format_out == 'flac':
        cmd.extend(["-c:a", "flac"])
    elif format_out == 'ogg':
        cmd.extend(["-c:a", "libvorbis"])
    elif format_out == 'm4a' or format_out == 'aac':
        cmd.extend(["-c:a", "aac"])
    
    cmd.append(output_file)
    return cmd

def convert_file(input_path, output_path, bitrate, channels, sample_rate, normalize, format_out):
    """Конвертирует один файл."""
    cmd = build_ffmpeg_cmd(input_path, output_path, bitrate, channels, sample_rate, normalize, format_out)
    print(f"  Выполняется: {' '.join(cmd)}")
    try:
        subprocess.run(cmd, check=True, capture_output=True)
        return True
    except subprocess.CalledProcessError as e:
        print(f"  Ошибка: {e.stderr.decode()}", file=sys.stderr)
        return False

def find_audio_files(root, recursive):
    """Находит все аудиофайлы в указанной директории."""
    files = []
    root_path = Path(root)
    if root_path.is_file() and root_path.suffix.lower() in SUPPORTED_INPUT:
        return [root_path]
    if not root_path.is_dir():
        return []
    pattern = "**/*" if recursive else "*"
    for p in root_path.glob(pattern):
        if p.is_file() and p.suffix.lower() in SUPPORTED_INPUT:
            files.append(p)
    return files

def main():
    parser = argparse.ArgumentParser(description='Конвертер аудио в MP3 (и обратно)')
    parser.add_argument('source', help='Входной файл или папка')
    parser.add_argument('--bitrate', type=int, default=192, help='Битрейт (32-320 кбит/с)')
    parser.add_argument('--channels', type=int, choices=[1, 2], help='Количество каналов (1=моно, 2=стерео)')
    parser.add_argument('--sample-rate', type=int, help='Частота дискретизации (кГц)')
    parser.add_argument('--normalize', action='store_true', help='Нормализация громкости')
    parser.add_argument('--format', choices=SUPPORTED_OUTPUT, default='mp3', help='Выходной формат')
    parser.add_argument('--output', '-o', help='Выходной файл или папка')
    parser.add_argument('--recursive', action='store_true', help='Рекурсивный обход папок')
    parser.add_argument('--formats', action='store_true', help='Показать поддерживаемые форматы')
    args = parser.parse_args()

    if args.formats:
        print("Поддерживаемые входные форматы:", ', '.join(SUPPORTED_INPUT))
        print("Поддерживаемые выходные форматы:", ', '.join(SUPPORTED_OUTPUT))
        return

    check_ffmpeg()

    # Если указан выходной файл, а вход — папка — ошибка
    if args.output and os.path.isdir(args.source):
        print("Ошибка: при указании папки источником нельзя указать один выходной файл.", file=sys.stderr)
        sys.exit(1)

    # Поиск файлов
    files = find_audio_files(args.source, args.recursive)
    if not files:
        print(f"Не найдено аудиофайлов в {args.source}")
        sys.exit(1)

    # Подготовка выходной директории
    output_path = Path(args.output) if args.output else None
    if output_path and output_path.suffix:
        # Это конкретный файл
        if len(files) > 1:
            print("Ошибка: указан один выходной файл, но найдено несколько входных.", file=sys.stderr)
            sys.exit(1)
        output_path.parent.mkdir(parents=True, exist_ok=True)
    else:
        # Это папка
        out_dir = output_path if output_path else Path("./converted")
        out_dir.mkdir(parents=True, exist_ok=True)

    total = len(files)
    print(f"Найдено {total} аудиофайлов.")
    success = 0

    for i, input_file in enumerate(files, 1):
        if output_path and output_path.suffix:
            # Одиночный файл
            out_file = output_path
        else:
            # Сохраняем в папку с сохранением структуры
            rel = input_file.relative_to(args.source) if Path(args.source).is_dir() else input_file.name
            ext = f".{args.format}"
            out_file = (out_dir if output_path else Path("./converted")) / rel.with_suffix(ext)
            out_file.parent.mkdir(parents=True, exist_ok=True)

        print(f"[{i}/{total}] Конвертация {input_file} -> {out_file}")
        if convert_file(str(input_file), str(out_file), args.bitrate, args.channels,
                        args.sample_rate, args.normalize, args.format):
            success += 1
        else:
            print(f"  Ошибка при конвертации {input_file}")

    print(f"Готово! Успешно: {success}, Всего: {total}")

if __name__ == '__main__':
    main()
