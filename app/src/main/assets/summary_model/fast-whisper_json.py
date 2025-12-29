import os
import json
from faster_whisper import WhisperModel

# ffmpeg 경로
os.environ["PATH"] += os.pathsep + r"C:\ffmpeg\bin"

mp3_dir = r"C:\Users\ITSC\Desktop\파이널\head_tail_15s_cut_data"
output_dir = r"C:\Users\ITSC\Desktop\파이널\original_15cut_json"
os.makedirs(output_dir, exist_ok=True)

model = WhisperModel(
    "large-v2",
    device="cpu",
    compute_type="int8"
)

success = 0
failed = []

for file in os.listdir(mp3_dir):
    if not file.lower().endswith(".mp3"):
        continue

    mp3_path = os.path.join(mp3_dir, file)
    json_path = os.path.join(output_dir, file.replace(".mp3", ".json"))

    print(f"▶ 처리중: {file}")

    try:
        segments, info = model.transcribe(mp3_path, language="ko")

        result_json = {
            "file_name": file,
            "language": info.language,
            "duration": round(info.duration, 2),
            "segments": []
        }

        for seg in segments:
            result_json["segments"].append({
                "start": round(seg.start, 2),
                "end": round(seg.end, 2),
                "text": seg.text.strip()
            })

        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(result_json, f, ensure_ascii=False, indent=2)

        success += 1

    except Exception as e:
        print(f"❌ 실패: {file}")
        print(e)
        failed.append(file)

print("\n==============================")
print("✅ JSON 변환 완료")
print(f"성공: {success}개")
print(f"실패: {len(failed)}개")

if failed:
    print("실패 파일:")
    for f in failed:
        print(" -", f)
