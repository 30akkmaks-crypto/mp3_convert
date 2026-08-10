// mp3_convert.rs
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::env;
use std::sync::Arc;
use std::sync::mpsc;
use std::thread;
use clap::{Parser, Arg};

#[derive(Parser)]
#[command(name = "mp3_convert")]
struct Args {
    #[arg(help = "Входной файл или папка")]
    source: String,
    #[arg(short, long, default_value_t = 192)]
    bitrate: u32,
    #[arg(short = 'c', long)]
    channels: Option<u8>,
    #[arg(short = 'r', long)]
    sample_rate: Option<u32>,
    #[arg(long)]
    normalize: bool,
    #[arg(short, long, default_value = "mp3")]
    format: String,
    #[arg(short = 'o', long)]
    output: Option<String>,
    #[arg(long)]
    recursive: bool,
    #[arg(long)]
    formats: bool,
}

const SUPPORTED_INPUT: &[&str] = &[".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".aiff", ".mp3", ".m4b"];
const SUPPORTED_OUTPUT: &[&str] = &["mp3", "wav", "flac", "ogg", "m4a", "aac"];

fn check_ffmpeg() {
    if Command::new("ffmpeg").arg("-version").output().is_err() {
        eprintln!("Ошибка: ffmpeg не найден. Установите FFmpeg и добавьте в PATH.");
        std::process::exit(1);
    }
}

fn build_ffmpeg_cmd(input: &str, output: &str, bitrate: u32, channels: Option<u8>,
                    sample_rate: Option<u32>, normalize: bool, format: &str) -> Vec<String> {
    let mut cmd = vec!["ffmpeg".to_string(), "-i".to_string(), input.to_string(), "-y".to_string()];
    cmd.push("-b:a".to_string());
    cmd.push(format!("{}k", bitrate));
    if let Some(ch) = channels {
        cmd.push("-ac".to_string());
        cmd.push(ch.to_string());
    }
    if let Some(sr) = sample_rate {
        cmd.push("-ar".to_string());
        cmd.push(sr.to_string());
    }
    if normalize {
        cmd.push("-af".to_string());
        cmd.push("loudnorm=I=-16:LRA=11:TP=-1.5".to_string());
    }
    let codec = match format {
        "mp3" => "libmp3lame",
        "wav" => "pcm_s16le",
        "flac" => "flac",
        "ogg" => "libvorbis",
        "m4a" | "aac" => "aac",
        _ => "libmp3lame",
    };
    cmd.push("-c:a".to_string());
    cmd.push(codec.to_string());
    cmd.push(output.to_string());
    cmd
}

fn convert_file(input: &str, output: &str, bitrate: u32, channels: Option<u8>,
                sample_rate: Option<u32>, normalize: bool, format: &str) -> bool {
    let cmd = build_ffmpeg_cmd(input, output, bitrate, channels, sample_rate, normalize, format);
    println!("  Выполняется: {}", cmd.join(" "));
    match Command::new(&cmd[0]).args(&cmd[1..]).status() {
        Ok(status) => status.success(),
        Err(_) => false,
    }
}

fn find_audio_files(root: &str, recursive: bool) -> Vec<PathBuf> {
    let mut files = Vec::new();
    let path = Path::new(root);
    if path.is_file() {
        if let Some(ext) = path.extension() {
            if SUPPORTED_INPUT.contains(&ext.to_str().unwrap_or("").to_lowercase().as_str()) {
                files.push(path.to_path_buf());
            }
        }
        return files;
    }
    if !path.is_dir() {
        return files;
    }
    if recursive {
        for entry in walkdir::WalkDir::new(path) {
            if let Ok(entry) = entry {
                if entry.file_type().is_file() {
                    if let Some(ext) = entry.path().extension() {
                        let ext_lower = ext.to_str().unwrap_or("").to_lowercase();
                        if SUPPORTED_INPUT.contains(&ext_lower.as_str()) {
                            files.push(entry.path().to_path_buf());
                        }
                    }
                }
            }
        }
    } else {
        if let Ok(entries) = fs::read_dir(path) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_file() {
                    if let Some(ext) = p.extension() {
                        let ext_lower = ext.to_str().unwrap_or("").to_lowercase();
                        if SUPPORTED_INPUT.contains(&ext_lower.as_str()) {
                            files.push(p);
                        }
                    }
                }
            }
        }
    }
    files
}

fn main() {
    let args = Args::parse();

    if args.formats {
        println!("Поддерживаемые входные форматы: {}", SUPPORTED_INPUT.join(", "));
        println!("Поддерживаемые выходные форматы: {}", SUPPORTED_OUTPUT.join(", "));
        return;
    }

    check_ffmpeg();

    if !SUPPORTED_OUTPUT.contains(&args.format.as_str()) {
        eprintln!("Неизвестный формат: {}. Доступны: {}", args.format, SUPPORTED_OUTPUT.join(", "));
        std::process::exit(1);
    }

    let files = find_audio_files(&args.source, args.recursive);
    if files.is_empty() {
        println!("Не найдено аудиофайлов в {}", args.source);
        std::process::exit(1);
    }

    let out_dir = match args.output {
        Some(ref o) => {
            if Path::new(o).is_dir() {
                o.clone()
            } else {
                Path::new(o).parent().unwrap_or(Path::new(".")).to_str().unwrap().to_string()
            }
        }
        None => "./converted".to_string(),
    };
    fs::create_dir_all(&out_dir).unwrap();

    let total = files.len();
    println!("Найдено {} аудиофайлов.", total);
    let (tx, rx) = mpsc::channel();
    let threads = 4;
    let mut handles = vec![];

    let args = Arc::new(args);

    for chunk in files.chunks((total + threads - 1) / threads) {
        let chunk = chunk.to_vec();
        let tx = tx.clone();
        let out_dir = out_dir.clone();
        let args = args.clone();
        handles.push(thread::spawn(move || {
            for (i, input_file) in chunk.iter().enumerate() {
                let rel = input_file.strip_prefix(&args.source).unwrap_or(input_file);
                let out_path = Path::new(&out_dir).join(rel.with_extension(&args.format));
                if let Some(parent) = out_path.parent() {
                    fs::create_dir_all(parent).unwrap();
                }
                tx.send((i+1, format!("Конвертация {} -> {}", input_file.display(), out_path.display()))).unwrap();
                let success = convert_file(
                    input_file.to_str().unwrap(),
                    out_path.to_str().unwrap(),
                    args.bitrate,
                    args.channels,
                    args.sample_rate,
                    args.normalize,
                    &args.format,
                );
                if !success {
                    tx.send((i+1, format!("  Ошибка при конвертации {}", input_file.display()))).unwrap();
                }
            }
        }));
    }
    drop(tx);
    let mut count = 0;
    for (idx, msg) in rx {
        count += 1;
        println!("[{}/{}] {}", count, total, msg);
    }
    for h in handles {
        h.join().unwrap();
    }
    println!("Готово!");
}
