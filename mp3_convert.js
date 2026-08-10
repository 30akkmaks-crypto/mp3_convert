// mp3_convert.js
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const { promisify } = require('util');
const yargs = require('yargs');
const { hideBin } = require('yargs/helpers');

const execPromise = promisify(exec);

const SUPPORTED_INPUT = ['.wav', '.flac', '.ogg', '.m4a', '.aac', '.wma', '.aiff', '.mp3', '.m4b'];
const SUPPORTED_OUTPUT = ['mp3', 'wav', 'flac', 'ogg', 'm4a', 'aac'];

function checkFFmpeg() {
    try {
        exec('ffmpeg -version', (err) => {
            if (err) {
                console.error('Ошибка: ffmpeg не найден. Установите FFmpeg и добавьте в PATH.');
                process.exit(1);
            }
        });
    } catch {
        console.error('Ошибка: ffmpeg не найден.');
        process.exit(1);
    }
}

function buildFFmpegCmd(inputFile, outputFile, bitrate, channels, sampleRate, normalize, format) {
    const cmd = ['ffmpeg', '-i', inputFile, '-y'];
    if (bitrate) {
        cmd.push('-b:a', `${bitrate}k`);
    }
    if (sampleRate) {
        cmd.push('-ar', String(sampleRate));
    }
    if (channels) {
        cmd.push('-ac', String(channels));
    }
    if (normalize) {
        cmd.push('-af', 'loudnorm=I=-16:LRA=11:TP=-1.5');
    }
    if (format === 'mp3') {
        cmd.push('-c:a', 'libmp3lame');
    } else if (format === 'wav') {
        cmd.push('-c:a', 'pcm_s16le');
    } else if (format === 'flac') {
        cmd.push('-c:a', 'flac');
    } else if (format === 'ogg') {
        cmd.push('-c:a', 'libvorbis');
    } else if (format === 'm4a' || format === 'aac') {
        cmd.push('-c:a', 'aac');
    }
    cmd.push(outputFile);
    return cmd;
}

async function convertFile(inputPath, outputPath, bitrate, channels, sampleRate, normalize, format) {
    const cmd = buildFFmpegCmd(inputPath, outputPath, bitrate, channels, sampleRate, normalize, format);
    console.log(`  Выполняется: ${cmd.join(' ')}`);
    try {
        await execPromise(cmd.join(' '));
        return true;
    } catch (err) {
        console.error(`  Ошибка: ${err.stderr}`);
        return false;
    }
}

function findAudioFiles(root, recursive) {
    const files = [];
    if (fs.existsSync(root) && fs.statSync(root).isFile()) {
        if (SUPPORTED_INPUT.includes(path.extname(root).toLowerCase())) {
            files.push(root);
        }
        return files;
    }
    if (!fs.existsSync(root) || !fs.statSync(root).isDirectory()) {
        return files;
    }
    const walk = (dir) => {
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
            const fullPath = path.join(dir, entry.name);
            if (entry.isDirectory() && recursive) {
                walk(fullPath);
            } else if (entry.isFile()) {
                if (SUPPORTED_INPUT.includes(path.extname(entry.name).toLowerCase())) {
                    files.push(fullPath);
                }
            }
        }
    };
    walk(root);
    return files;
}

async function main() {
    const argv = yargs(hideBin(process.argv))
        .usage('Использование: $0 <вход> [--bitrate N] [--channels 1|2] [--sample-rate N] [--normalize] [--format FMT] [--output FILE|DIR] [--recursive] [--formats]')
        .demandCommand(1)
        .argv;

    if (argv.formats) {
        console.log('Поддерживаемые входные форматы:', SUPPORTED_INPUT.join(', '));
        console.log('Поддерживаемые выходные форматы:', SUPPORTED_OUTPUT.join(', '));
        return;
    }

    checkFFmpeg();

    const source = argv._[0];
    const bitrate = argv.bitrate || 192;
    const channels = argv.channels || null;
    const sampleRate = argv.sampleRate || null;
    const normalize = argv.normalize || false;
    const format = argv.format || 'mp3';
    const output = argv.output || null;
    const recursive = argv.recursive || false;

    if (!SUPPORTED_OUTPUT.includes(format)) {
        console.error(`Неизвестный формат: ${format}. Доступны: ${SUPPORTED_OUTPUT.join(', ')}`);
        process.exit(1);
    }

    const files = findAudioFiles(source, recursive);
    if (files.length === 0) {
        console.log(`Не найдено аудиофайлов в ${source}`);
        process.exit(1);
    }

    if (output && !fs.existsSync(path.dirname(output))) {
        fs.mkdirSync(path.dirname(output), { recursive: true });
    }

    const outDir = output ? (fs.existsSync(output) && fs.statSync(output).isDirectory() ? output : path.dirname(output)) : './converted';
    if (!fs.existsSync(outDir)) {
        fs.mkdirSync(outDir, { recursive: true });
    }

    const total = files.length;
    console.log(`Найдено ${total} аудиофайлов.`);
    let success = 0;

    for (let i=0; i<total; i++) {
        const inputFile = files[i];
        let outFile;
        if (output && !fs.existsSync(output)) {
            outFile = output;
        } else {
            const rel = path.relative(source, inputFile) || path.basename(inputFile);
            outFile = path.join(outDir, path.dirname(rel), path.basename(rel, path.extname(rel)) + `.${format}`);
        }
        if (!fs.existsSync(path.dirname(outFile))) {
            fs.mkdirSync(path.dirname(outFile), { recursive: true });
        }
        console.log(`[${i+1}/${total}] Конвертация ${inputFile} -> ${outFile}`);
        if (await convertFile(inputFile, outFile, bitrate, channels, sampleRate, normalize, format)) {
            success++;
        }
    }
    console.log(`Готово! Успешно: ${success}, Всего: ${total}`);
}

main().catch(console.error);
