// mp3_convert.cpp
#include <iostream>
#include <string>
#include <vector>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <regex>
#include <cstdlib>
#include <thread>
#include <mutex>

namespace fs = std::filesystem;
using namespace std;

const vector<string> INPUT_EXTS = {".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".aiff", ".mp3", ".m4b"};
const vector<string> OUTPUT_EXTS = {"mp3", "wav", "flac", "ogg", "m4a", "aac"};

bool checkFFmpeg() {
    return system("ffmpeg -version > /dev/null 2>&1") == 0;
}

string buildFFmpegCmd(const string& input, const string& output, int bitrate, int channels,
                      int sampleRate, bool normalize, const string& format) {
    stringstream cmd;
    cmd << "ffmpeg -i " << input << " -y";
    cmd << " -b:a " << bitrate << "k";
    if (channels > 0) cmd << " -ac " << channels;
    if (sampleRate > 0) cmd << " -ar " << sampleRate;
    if (normalize) cmd << " -af loudnorm=I=-16:LRA=11:TP=-1.5";
    string codec = "libmp3lame";
    if (format == "wav") codec = "pcm_s16le";
    else if (format == "flac") codec = "flac";
    else if (format == "ogg") codec = "libvorbis";
    else if (format == "m4a" || format == "aac") codec = "aac";
    cmd << " -c:a " << codec << " " << output;
    return cmd.str();
}

bool convertFile(const string& input, const string& output, int bitrate, int channels,
                 int sampleRate, bool normalize, const string& format) {
    string cmd = buildFFmpegCmd(input, output, bitrate, channels, sampleRate, normalize, format);
    cout << "  Выполняется: " << cmd << endl;
    return system(cmd.c_str()) == 0;
}

vector<string> findAudioFiles(const string& root, bool recursive) {
    vector<string> files;
    fs::path path(root);
    if (fs::is_regular_file(path)) {
        string ext = path.extension().string();
        for (const auto& e : INPUT_EXTS) {
            if (ext == e) { files.push_back(path.string()); break; }
        }
        return files;
    }
    if (!fs::is_directory(path)) return files;
    for (auto& entry : fs::directory_iterator(path)) {
        if (entry.is_regular_file()) {
            string ext = entry.path().extension().string();
            for (const auto& e : INPUT_EXTS) {
                if (ext == e) { files.push_back(entry.path().string()); break; }
            }
        }
        if (recursive && entry.is_directory()) {
            auto sub = findAudioFiles(entry.path().string(), true);
            files.insert(files.end(), sub.begin(), sub.end());
        }
    }
    return files;
}

int main(int argc, char* argv[]) {
    if (argc < 2) {
        cerr << "Использование: mp3_convert <вход> [--bitrate N] [--channels 1|2] [--sample-rate N] [--normalize] [--format FMT] [--output FILE|DIR] [--recursive] [--formats]" << endl;
        return 1;
    }

    if (string(argv[1]) == "--formats") {
        cout << "Поддерживаемые входные форматы: ";
        for (auto& e : INPUT_EXTS) cout << e << " ";
        cout << endl;
        cout << "Поддерживаемые выходные форматы: ";
        for (auto& e : OUTPUT_EXTS) cout << e << " ";
        cout << endl;
        return 0;
    }

    if (!checkFFmpeg()) {
        cerr << "Ошибка: ffmpeg не найден. Установите FFmpeg и добавьте в PATH." << endl;
        return 1;
    }

    string source = argv[1];
    int bitrate = 192;
    int channels = 0;
    int sampleRate = 0;
    bool normalize = false;
    string format = "mp3";
    string output = "";
    bool recursive = false;

    for (int i=2; i<argc; ++i) {
        string arg = argv[i];
        if (arg == "--bitrate" && i+1 < argc) bitrate = stoi(argv[++i]);
        else if (arg == "--channels" && i+1 < argc) channels = stoi(argv[++i]);
        else if (arg == "--sample-rate" && i+1 < argc) sampleRate = stoi(argv[++i]);
        else if (arg == "--normalize") normalize = true;
        else if (arg == "--format" && i+1 < argc) format = argv[++i];
        else if (arg == "--output" && i+1 < argc) output = argv[++i];
        else if (arg == "--recursive") recursive = true;
    }

    bool formatOk = false;
    for (auto& e : OUTPUT_EXTS) if (e == format) { formatOk = true; break; }
    if (!formatOk) {
        cerr << "Неизвестный формат: " << format << endl;
        return 1;
    }

    auto files = findAudioFiles(source, recursive);
    if (files.empty()) {
        cout << "Не найдено аудиофайлов в " << source << endl;
        return 1;
    }

    string outDir = output.empty() ? "./converted" : (fs::is_directory(output) ? output : fs::path(output).parent_path().string());
    fs::create_directories(outDir);
    int total = files.size();
    cout << "Найдено " << total << " аудиофайлов." << endl;
    int success = 0;
    mutex mtx;

    vector<thread> threads;
    for (int i=0; i<total; ++i) {
        threads.emplace_back([&, i]() {
            string inputFile = files[i];
            string outPath;
            if (!output.empty() && !fs::is_directory(output)) {
                outPath = output;
            } else {
                fs::path rel = fs::relative(inputFile, source);
                if (rel.empty()) rel = fs::path(inputFile).filename();
                outPath = (fs::path(outDir) / rel).replace_extension("." + format).string();
            }
            fs::create_directories(fs::path(outPath).parent_path());
            lock_guard<mutex> lock(mtx);
            cout << "[" << i+1 << "/" << total << "] Конвертация " << inputFile << " -> " << outPath << endl;
            if (convertFile(inputFile, outPath, bitrate, channels, sampleRate, normalize, format)) {
                success++;
            }
        });
    }
    for (auto& t : threads) t.join();
    cout << "Готово! Успешно: " << success << ", Всего: " << total << endl;
    return 0;
}
