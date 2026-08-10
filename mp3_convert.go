// mp3_convert.go
package main

import (
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
)

var inputExts = map[string]bool{
	".wav": true, ".flac": true, ".ogg": true, ".m4a": true,
	".aac": true, ".wma": true, ".aiff": true, ".mp3": true, ".m4b": true,
}
var outputExts = map[string]bool{
	"mp3": true, "wav": true, "flac": true, "ogg": true, "m4a": true, "aac": true,
}

func checkFFmpeg() {
	if _, err := exec.LookPath("ffmpeg"); err != nil {
		fmt.Fprintln(os.Stderr, "Ошибка: ffmpeg не найден. Установите FFmpeg и добавьте в PATH.")
		os.Exit(1)
	}
}

func buildFFmpegCmd(input, output string, bitrate int, channels int, sampleRate int, normalize bool, format string) []string {
	cmd := []string{"ffmpeg", "-i", input, "-y"}
	cmd = append(cmd, "-b:a", fmt.Sprintf("%dk", bitrate))
	if channels > 0 {
		cmd = append(cmd, "-ac", fmt.Sprintf("%d", channels))
	}
	if sampleRate > 0 {
		cmd = append(cmd, "-ar", fmt.Sprintf("%d", sampleRate))
	}
	if normalize {
		cmd = append(cmd, "-af", "loudnorm=I=-16:LRA=11:TP=-1.5")
	}
	codec := "libmp3lame"
	switch format {
	case "wav":
		codec = "pcm_s16le"
	case "flac":
		codec = "flac"
	case "ogg":
		codec = "libvorbis"
	case "m4a", "aac":
		codec = "aac"
	default:
		codec = "libmp3lame"
	}
	cmd = append(cmd, "-c:a", codec, output)
	return cmd
}

func convertFile(input, output string, bitrate int, channels int, sampleRate int, normalize bool, format string) bool {
	cmdArgs := buildFFmpegCmd(input, output, bitrate, channels, sampleRate, normalize, format)
	fmt.Printf("  Выполняется: %s\n", strings.Join(cmdArgs, " "))
	cmd := exec.Command(cmdArgs[0], cmdArgs[1:]...)
	if err := cmd.Run(); err != nil {
		fmt.Printf("  Ошибка: %v\n", err)
		return false
	}
	return true
}

func findAudioFiles(root string, recursive bool) []string {
	var files []string
	info, err := os.Stat(root)
	if err != nil {
		return files
	}
	if !info.IsDir() {
		ext := strings.ToLower(filepath.Ext(root))
		if inputExts[ext] {
			files = append(files, root)
		}
		return files
	}
	walkFn := func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if info.IsDir() && !recursive && path != root {
			return filepath.SkipDir
		}
		if !info.IsDir() {
			ext := strings.ToLower(filepath.Ext(path))
			if inputExts[ext] {
				files = append(files, path)
			}
		}
		return nil
	}
	filepath.Walk(root, walkFn)
	return files
}

func main() {
	var bitrate int
	var channels int
	var sampleRate int
	var normalize bool
	var format string
	var output string
	var recursive bool
	var formats bool

	flag.IntVar(&bitrate, "bitrate", 192, "Битрейт (32-320)")
	flag.IntVar(&channels, "channels", 0, "Количество каналов (1=моно, 2=стерео)")
	flag.IntVar(&sampleRate, "sample-rate", 0, "Частота дискретизации (кГц)")
	flag.BoolVar(&normalize, "normalize", false, "Нормализация громкости")
	flag.StringVar(&format, "format", "mp3", "Выходной формат")
	flag.StringVar(&output, "output", "", "Выходной файл или папка")
	flag.BoolVar(&recursive, "recursive", false, "Рекурсивный обход")
	flag.BoolVar(&formats, "formats", false, "Показать форматы")
	flag.Parse()

	if formats {
		fmt.Println("Поддерживаемые входные форматы:", keys(inputExts))
		fmt.Println("Поддерживаемые выходные форматы:", keys(outputExts))
		return
	}

	checkFFmpeg()

	args := flag.Args()
	if len(args) == 0 {
		fmt.Println("Укажите входной файл или папку")
		os.Exit(1)
	}
	source := args[0]

	if _, ok := outputExts[format]; !ok {
		fmt.Printf("Неизвестный формат: %s\n", format)
		os.Exit(1)
	}

	files := findAudioFiles(source, recursive)
	if len(files) == 0 {
		fmt.Printf("Не найдено аудиофайлов в %s\n", source)
		os.Exit(1)
	}

	outDir := output
	if outDir == "" {
		outDir = "./converted"
	}
	if err := os.MkdirAll(outDir, 0755); err != nil {
		fmt.Printf("Ошибка создания папки: %v\n", err)
		os.Exit(1)
	}

	total := len(files)
	fmt.Printf("Найдено %d аудиофайлов.\n", total)
	var wg sync.WaitGroup
	sem := make(chan struct{}, 4)
	success := 0
	var mu sync.Mutex

	for i, inputFile := range files {
		wg.Add(1)
		go func(idx int, inPath string) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			var outPath string
			if output != "" && !isDir(output) {
				outPath = output
			} else {
				rel, _ := filepath.Rel(source, inPath)
				if rel == "." {
					rel = filepath.Base(inPath)
				}
				ext := "." + format
				if format == "mp3" {
					ext = ".mp3"
				}
				outPath = filepath.Join(outDir, strings.TrimSuffix(rel, filepath.Ext(rel))+ext)
			}
			if err := os.MkdirAll(filepath.Dir(outPath), 0755); err != nil {
				fmt.Printf("[%d/%d] Ошибка создания папки: %v\n", idx+1, total, err)
				return
			}
			fmt.Printf("[%d/%d] Конвертация %s -> %s\n", idx+1, total, inPath, outPath)
			if convertFile(inPath, outPath, bitrate, channels, sampleRate, normalize, format) {
				mu.Lock()
				success++
				mu.Unlock()
			}
		}(i, inputFile)
	}
	wg.Wait()
	fmt.Printf("Готово! Успешно: %d, Всего: %d\n", success, total)
}

func keys(m map[string]bool) []string {
	var k []string
	for key := range m {
		k = append(k, key)
	}
	return k
}

func isDir(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.IsDir()
}
