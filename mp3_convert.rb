# mp3_convert.rb
require 'find'
require 'optparse'
require 'shellwords'

INPUT_EXTS = ['.wav', '.flac', '.ogg', '.m4a', '.aac', '.wma', '.aiff', '.mp3', '.m4b']
OUTPUT_EXTS = ['mp3', 'wav', 'flac', 'ogg', 'm4a', 'aac']

def check_ffmpeg
  system('ffmpeg -version > /dev/null 2>&1') or begin
    $stderr.puts "Ошибка: ffmpeg не найден. Установите FFmpeg и добавьте в PATH."
    exit 1
  end
end

def build_ffmpeg_cmd(input, output, bitrate, channels, sample_rate, normalize, format)
  cmd = ['ffmpeg', '-i', input, '-y']
  cmd += ['-b:a', "#{bitrate}k"]
  cmd += ['-ac', channels.to_s] if channels && channels > 0
  cmd += ['-ar', sample_rate.to_s] if sample_rate && sample_rate > 0
  cmd += ['-af', 'loudnorm=I=-16:LRA=11:TP=-1.5'] if normalize
  codec = case format
  when 'wav' then 'pcm_s16le'
  when 'flac' then 'flac'
  when 'ogg' then 'libvorbis'
  when 'm4a', 'aac' then 'aac'
  else 'libmp3lame'
  end
  cmd += ['-c:a', codec, output]
  cmd
end

def convert_file(input, output, bitrate, channels, sample_rate, normalize, format)
  cmd = build_ffmpeg_cmd(input, output, bitrate, channels, sample_rate, normalize, format)
  puts "  Выполняется: #{cmd.join(' ')}"
  system(*cmd)
end

def find_audio_files(root, recursive)
  files = []
  if File.file?(root) && INPUT_EXTS.include?(File.extname(root).downcase)
    return [root]
  end
  return files unless File.directory?(root)
  if recursive
    Find.find(root) do |path|
      files << path if File.file?(path) && INPUT_EXTS.include?(File.extname(path).downcase)
    end
  else
    Dir.glob(File.join(root, '*')).each do |path|
      files << path if File.file?(path) && INPUT_EXTS.include?(File.extname(path).downcase)
    end
  end
  files
end

options = {}
OptionParser.new do |opts|
  opts.banner = "Использование: ruby mp3_convert.rb <вход> [опции]"
  opts.on("--bitrate N", Integer, "Битрейт (32-320)") { |v| options[:bitrate] = v }
  opts.on("--channels N", Integer, "Каналы (1=моно, 2=стерео)") { |v| options[:channels] = v }
  opts.on("--sample-rate N", Integer, "Частота (кГц)") { |v| options[:sample_rate] = v }
  opts.on("--normalize", "Нормализация") { options[:normalize] = true }
  opts.on("--format F", "Выходной формат") { |v| options[:format] = v }
  opts.on("--output FILE|DIR", "Выходной файл или папка") { |v| options[:output] = v }
  opts.on("--recursive", "Рекурсивный обход") { options[:recursive] = true }
  opts.on("--formats", "Показать форматы") { options[:formats] = true }
end.parse!

if options[:formats]
  puts "Поддерживаемые входные форматы: #{INPUT_EXTS.join(', ')}"
  puts "Поддерживаемые выходные форматы: #{OUTPUT_EXTS.join(', ')}"
  exit
end

check_ffmpeg

source = ARGV[0]
unless source
  puts "Укажите входной файл или папку."
  exit 1
end

bitrate = options[:bitrate] || 192
channels = options[:channels]
sample_rate = options[:sample_rate]
normalize = options[:normalize] || false
format = options[:format] || 'mp3'
output = options[:output]
recursive = options[:recursive] || false

unless OUTPUT_EXTS.include?(format)
  puts "Неизвестный формат: #{format}"
  exit 1
end

files = find_audio_files(source, recursive)
if files.empty?
  puts "Не найдено аудиофайлов в #{source}"
  exit 1
end

out_dir = output && File.directory?(output) ? output : './converted'
Dir.mkdir(out_dir) unless Dir.exist?(out_dir)
total = files.size
puts "Найдено #{total} аудиофайлов."
success = 0

files.each_with_index do |input_file, idx|
  rel = Pathname.new(input_file).relative_path_from(Pathname.new(source)).to_s rescue File.basename(input_file)
  out_path = if output && !File.directory?(output)
    output
  else
    File.join(out_dir, File.basename(rel, '.*') + ".#{format}")
  end
  FileUtils.mkdir_p(File.dirname(out_path))
  puts "[#{idx+1}/#{total}] Конвертация #{input_file} -> #{out_path}"
  if convert_file(input_file, out_path, bitrate, channels, sample_rate, normalize, format)
    success += 1
  else
    puts "  Ошибка при конвертации #{input_file}"
  end
end
puts "Готово! Успешно: #{success}, Всего: #{total}"
