// Mp3Convert.java
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class Mp3Convert {
    private static final Set<String> INPUT_EXTS = new HashSet<>(Arrays.asList(".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".aiff", ".mp3", ".m4b"));
    private static final Set<String> OUTPUT_EXTS = new HashSet<>(Arrays.asList("mp3", "wav", "flac", "ogg", "m4a", "aac"));

    public static void checkFFmpeg() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"ffmpeg", "-version"});
            if (p.waitFor() != 0) {
                throw new Exception();
            }
        } catch (Exception e) {
            System.err.println("Ошибка: ffmpeg не найден. Установите FFmpeg и добавьте в PATH.");
            System.exit(1);
        }
    }

    public static String[] buildFFmpegCmd(String input, String output, int bitrate, int channels, int sampleRate, boolean normalize, String format) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-i");
        cmd.add(input);
        cmd.add("-y");
        cmd.add("-b:a");
        cmd.add(bitrate + "k");
        if (channels > 0) {
            cmd.add("-ac");
            cmd.add(String.valueOf(channels));
        }
        if (sampleRate > 0) {
            cmd.add("-ar");
            cmd.add(String.valueOf(sampleRate));
        }
        if (normalize) {
            cmd.add("-af");
            cmd.add("loudnorm=I=-16:LRA=11:TP=-1.5");
        }
        String codec;
        switch (format) {
            case "wav": codec = "pcm_s16le"; break;
            case "flac": codec = "flac"; break;
            case "ogg": codec = "libvorbis"; break;
            case "m4a": case "aac": codec = "aac"; break;
            default: codec = "libmp3lame";
        }
        cmd.add("-c:a");
        cmd.add(codec);
        cmd.add(output);
        return cmd.toArray(new String[0]);
    }

    public static boolean convertFile(String input, String output, int bitrate, int channels, int sampleRate, boolean normalize, String format) {
        String[] cmd = buildFFmpegCmd(input, output, bitrate, channels, sampleRate, normalize, format);
        System.out.println("  Выполняется: " + String.join(" ", cmd));
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            return p.waitFor() == 0;
        } catch (Exception e) {
            System.err.println("  Ошибка: " + e.getMessage());
            return false;
        }
    }

    public static List<Path> findAudioFiles(String root, boolean recursive) throws IOException {
        List<Path> files = new ArrayList<>();
        Path path = Paths.get(root);
        if (Files.isRegularFile(path)) {
            String ext = getExtension(path.toString()).toLowerCase();
            if (INPUT_EXTS.contains(ext)) {
                files.add(path);
            }
            return files;
        }
        if (!Files.isDirectory(path)) return files;
        if (recursive) {
            Files.walk(path)
                .filter(p -> Files.isRegularFile(p))
                .forEach(p -> {
                    String ext = getExtension(p.toString()).toLowerCase();
                    if (INPUT_EXTS.contains(ext)) files.add(p);
                });
        } else {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p)) {
                        String ext = getExtension(p.toString()).toLowerCase();
                        if (INPUT_EXTS.contains(ext)) files.add(p);
                    }
                }
            }
        }
        return files;
    }

    private static String getExtension(String path) {
        int i = path.lastIndexOf('.');
        return i > 0 ? path.substring(i) : "";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Использование: java Mp3Convert <вход> [--bitrate N] [--channels 1|2] [--sample-rate N] [--normalize] [--format FMT] [--output FILE|DIR] [--recursive] [--formats]");
            System.exit(1);
        }
        if (args[0].equals("--formats")) {
            System.out.println("Поддерживаемые входные форматы: " + INPUT_EXTS);
            System.out.println("Поддерживаемые выходные форматы: " + OUTPUT_EXTS);
            return;
        }

        checkFFmpeg();

        String source = args[0];
        int bitrate = 192;
        int channels = 0;
        int sampleRate = 0;
        boolean normalize = false;
        String format = "mp3";
        String output = null;
        boolean recursive = false;

        for (int i=1; i<args.length; i++) {
            if (args[i].equals("--bitrate") && i+1 < args.length) {
                bitrate = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--channels") && i+1 < args.length) {
                channels = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--sample-rate") && i+1 < args.length) {
                sampleRate = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--normalize")) {
                normalize = true;
            } else if (args[i].equals("--format") && i+1 < args.length) {
                format = args[++i];
            } else if (args[i].equals("--output") && i+1 < args.length) {
                output = args[++i];
            } else if (args[i].equals("--recursive")) {
                recursive = true;
            }
        }

        if (!OUTPUT_EXTS.contains(format)) {
            System.err.println("Неизвестный формат: " + format);
            System.exit(1);
        }

        List<Path> files = findAudioFiles(source, recursive);
        if (files.isEmpty()) {
            System.out.println("Не найдено аудиофайлов в " + source);
            System.exit(1);
        }

        String outDir = output != null ? (Files.isDirectory(Paths.get(output)) ? output : Paths.get(output).getParent().toString()) : "./converted";
        Files.createDirectories(Paths.get(outDir));
        int total = files.size();
        System.out.println("Найдено " + total + " аудиофайлов.");

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<Boolean>> futures = new ArrayList<>();
        int[] success = {0};

        for (int i=0; i<total; i++) {
            Path inputFile = files.get(i);
            int idx = i;
            futures.add(executor.submit(() -> {
                Path rel = Paths.get(source).relativize(inputFile);
                if (rel.toString().isEmpty()) rel = inputFile.getFileName();
                String outPath = Paths.get(outDir, rel.toString().replaceFirst("\\.[^.]+$", "") + "." + format).toString();
                if (output != null && !Files.isDirectory(Paths.get(output))) {
                    outPath = output;
                }
                Files.createDirectories(Paths.get(outPath).getParent());
                System.out.printf("[%d/%d] Конвертация %s -> %s\n", idx+1, total, inputFile, outPath);
                boolean ok = convertFile(inputFile.toString(), outPath, bitrate, channels, sampleRate, normalize, format);
                if (ok) success[0]++;
                return ok;
            }));
        }
        for (Future<Boolean> f : futures) f.get();
        executor.shutdown();
        System.out.printf("Готово! Успешно: %d, Всего: %d\n", success[0], total);
    }
}
