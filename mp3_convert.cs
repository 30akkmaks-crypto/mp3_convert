// mp3_convert.cs
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading.Tasks;

class Mp3Convert
{
    private static readonly HashSet<string> InputExts = new HashSet<string>
        { ".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".aiff", ".mp3", ".m4b" };
    private static readonly HashSet<string> OutputExts = new HashSet<string>
        { "mp3", "wav", "flac", "ogg", "m4a", "aac" };

    static void CheckFFmpeg()
    {
        try
        {
            var psi = new ProcessStartInfo("ffmpeg", "-version") { RedirectStandardOutput = true };
            using (var p = Process.Start(psi)) { p.WaitForExit(); }
        }
        catch
        {
            Console.Error.WriteLine("Ошибка: ffmpeg не найден. Установите FFmpeg и добавьте в PATH.");
            Environment.Exit(1);
        }
    }

    static string[] BuildFFmpegCmd(string input, string output, int bitrate, int channels, int sampleRate, bool normalize, string format)
    {
        var cmd = new List<string>();
        cmd.Add("ffmpeg"); cmd.Add("-i"); cmd.Add(input); cmd.Add("-y");
        cmd.Add("-b:a"); cmd.Add($"{bitrate}k");
        if (channels > 0) { cmd.Add("-ac"); cmd.Add(channels.ToString()); }
        if (sampleRate > 0) { cmd.Add("-ar"); cmd.Add(sampleRate.ToString()); }
        if (normalize) { cmd.Add("-af"); cmd.Add("loudnorm=I=-16:LRA=11:TP=-1.5"); }
        string codec = format switch
        {
            "wav" => "pcm_s16le",
            "flac" => "flac",
            "ogg" => "libvorbis",
            "m4a" or "aac" => "aac",
            _ => "libmp3lame"
        };
        cmd.Add("-c:a"); cmd.Add(codec); cmd.Add(output);
        return cmd.ToArray();
    }

    static bool ConvertFile(string input, string output, int bitrate, int channels, int sampleRate, bool normalize, string format)
    {
        var args = BuildFFmpegCmd(input, output, bitrate, channels, sampleRate, normalize, format);
        Console.WriteLine($"  Выполняется: {string.Join(" ", args)}");
        try
        {
            var psi = new ProcessStartInfo("ffmpeg", string.Join(" ", args.Skip(1)))
            {
                UseShellExecute = false,
                RedirectStandardError = true
            };
            using (var p = Process.Start(psi))
            {
                p.WaitForExit();
                return p.ExitCode == 0;
            }
        }
        catch (Exception e)
        {
            Console.WriteLine($"  Ошибка: {e.Message}");
            return false;
        }
    }

    static List<string> FindAudioFiles(string root, bool recursive)
    {
        var files = new List<string>();
        if (File.Exists(root))
        {
            var ext = Path.GetExtension(root).ToLower();
            if (InputExts.Contains(ext)) files.Add(root);
            return files;
        }
        if (!Directory.Exists(root)) return files;
        var option = recursive ? SearchOption.AllDirectories : SearchOption.TopDirectoryOnly;
        files.AddRange(Directory.GetFiles(root, "*.*", option)
            .Where(f => InputExts.Contains(Path.GetExtension(f).ToLower())));
        return files;
    }

    static async Task Main(string[] args)
    {
        if (args.Length == 0)
        {
            Console.Error.WriteLine("Использование: dotnet run <вход> [--bitrate N] [--channels 1|2] [--sample-rate N] [--normalize] [--format FMT] [--output FILE|DIR] [--recursive] [--formats]");
            return;
        }
        if (args[0] == "--formats")
        {
            Console.WriteLine("Поддерживаемые входные форматы: " + string.Join(", ", InputExts));
            Console.WriteLine("Поддерживаемые выходные форматы: " + string.Join(", ", OutputExts));
            return;
        }

        CheckFFmpeg();

        string source = args[0];
        int bitrate = 192;
        int channels = 0;
        int sampleRate = 0;
        bool normalize = false;
        string format = "mp3";
        string output = null;
        bool recursive = false;

        for (int i=1; i<args.Length; i++)
        {
            switch (args[i])
            {
                case "--bitrate": if (i+1 < args.Length) bitrate = int.Parse(args[++i]); break;
                case "--channels": if (i+1 < args.Length) channels = int.Parse(args[++i]); break;
                case "--sample-rate": if (i+1 < args.Length) sampleRate = int.Parse(args[++i]); break;
                case "--normalize": normalize = true; break;
                case "--format": if (i+1 < args.Length) format = args[++i]; break;
                case "--output": if (i+1 < args.Length) output = args[++i]; break;
                case "--recursive": recursive = true; break;
            }
        }

        if (!OutputExts.Contains(format))
        {
            Console.Error.WriteLine($"Неизвестный формат: {format}");
            Environment.Exit(1);
        }

        var files = FindAudioFiles(source, recursive);
        if (files.Count == 0)
        {
            Console.WriteLine($"Не найдено аудиофайлов в {source}");
            return;
        }

        string outDir = output != null && Directory.Exists(output) ? output : "./converted";
        Directory.CreateDirectory(outDir);
        int total = files.Count;
        Console.WriteLine($"Найдено {total} аудиофайлов.");
        var semaphore = new SemaphoreSlim(4);
        var tasks = new List<Task>();
        int success = 0;

        for (int i=0; i<total; i++)
        {
            await semaphore.WaitAsync();
            int idx = i;
            string inputFile = files[i];
            tasks.Add(Task.Run(() =>
            {
                try
                {
                    var rel = Path.GetRelativePath(source, inputFile);
                    if (rel == ".") rel = Path.GetFileName(inputFile);
                    string outPath = Path.Combine(outDir, Path.ChangeExtension(rel, "." + format));
                    if (output != null && !Directory.Exists(output)) outPath = output;
                    Directory.CreateDirectory(Path.GetDirectoryName(outPath));
                    Console.WriteLine($"[{idx+1}/{total}] Конвертация {inputFile} -> {outPath}");
                    if (ConvertFile(inputFile, outPath, bitrate, channels, sampleRate, normalize, format))
                        Interlocked.Increment(ref success);
                }
                catch (Exception e)
                {
                    Console.WriteLine($"  Ошибка при конвертации {inputFile}: {e.Message}");
                }
                finally { semaphore.Release(); }
            }));
        }
        await Task.WhenAll(tasks);
        Console.WriteLine($"Готово! Успешно: {success}, Всего: {total}");
    }
}
