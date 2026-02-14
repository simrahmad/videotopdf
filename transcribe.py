import sys
import os
import subprocess

os.environ["PATH"] = "/usr/bin:/usr/local/bin:/bin:" + os.environ.get("PATH", "")

def find_ffmpeg():
    for loc in ["/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg"]:
        if os.path.exists(loc):
            return loc
    return None

def convert_to_wav(input_path, ffmpeg_path):
    wav_path = input_path + "_audio.wav"
    cmd = [ffmpeg_path, "-i", input_path, "-ac", "1", "-ar", "16000", "-vn", "-y", wav_path]
    result = subprocess.run(cmd, capture_output=True, text=True)
    print("ffmpeg done, returncode:", result.returncode, file=sys.stderr)
    if not os.path.exists(wav_path):
        print("WAV not created:", result.stderr[-200:], file=sys.stderr)
        return None
    print("WAV size:", os.path.getsize(wav_path), file=sys.stderr)
    return wav_path

def transcribe_wav(wav_path):
    import speech_recognition as sr
    from pydub import AudioSegment
    from pydub.silence import split_on_silence

    recognizer = sr.Recognizer()
    audio = AudioSegment.from_wav(wav_path)
    duration = len(audio) / 1000
    print(f"Duration: {duration:.1f} sec", file=sys.stderr)

    chunks = split_on_silence(audio, min_silence_len=600, silence_thresh=audio.dBFS - 14, keep_silence=300)
    if not chunks:
        chunk_ms = 20 * 1000
        chunks = [audio[i:i+chunk_ms] for i in range(0, len(audio), chunk_ms)]

    print(f"Chunks: {len(chunks)}", file=sys.stderr)
    results = []

    for i, chunk in enumerate(chunks):
        chunk_path = f"/tmp/chunk_{i}_{os.getpid()}.wav"
        try:
            padded = AudioSegment.silent(200) + chunk + AudioSegment.silent(200)
            padded.export(chunk_path, format="wav")
            with sr.AudioFile(chunk_path) as source:
                audio_data = recognizer.record(source)
            text = recognizer.recognize_google(audio_data, language="en-US")
            print(f"[{i+1}/{len(chunks)}] {text}", file=sys.stderr)
            if text.strip():
                results.append(text.strip())
        except sr.UnknownValueError:
            print(f"[{i+1}/{len(chunks)}] unclear", file=sys.stderr)
        except sr.RequestError as e:
            print(f"[{i+1}/{len(chunks)}] API error: {e}", file=sys.stderr)
        except Exception as e:
            print(f"[{i+1}/{len(chunks)}] error: {e}", file=sys.stderr)
        finally:
            if os.path.exists(chunk_path):
                os.remove(chunk_path)

    return " ".join(results)

def main():
    if len(sys.argv) < 2:
        sys.exit(1)
    input_file = sys.argv[1]
    print("Input:", input_file, file=sys.stderr)
    if not os.path.exists(input_file):
        print("FILE NOT FOUND", file=sys.stderr)
        sys.exit(1)
    ffmpeg = find_ffmpeg()
    if not ffmpeg:
        sys.exit(1)
    wav_path = convert_to_wav(input_file, ffmpeg)
    if not wav_path:
        sys.exit(1)
    try:
        transcript = transcribe_wav(wav_path)
        if transcript.strip():
            print(transcript)
        else:
            print("Empty transcript", file=sys.stderr)
            sys.exit(1)
    finally:
        if os.path.exists(wav_path):
            os.remove(wav_path)

if __name__ == "__main__":
    main()
