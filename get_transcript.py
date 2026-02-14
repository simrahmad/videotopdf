import sys
import os


def get_transcript(video_id):
    print("Fetching transcript: " + video_id, file=sys.stderr)

    api_key = os.environ.get("SUPADATA_API_KEY", "")
    if not api_key:
        print("No SUPADATA_API_KEY set", file=sys.stderr)
        print("NO_TRANSCRIPT_AVAILABLE")
        return

    try:
        from supadata import Supadata
        client = Supadata(api_key=api_key)
        result = client.youtube.transcript(video_id, text=True)

        content = result.content if hasattr(result, "content") else str(result)
        transcript = content.strip() if isinstance(content, str) else " ".join([
            s.text if hasattr(s, "text") else str(s) for s in content
        ])

        print("Transcript: " + str(len(transcript)) + " chars", file=sys.stderr)

        if len(transcript) > 30:
            print(transcript)
        else:
            print("NO_TRANSCRIPT_AVAILABLE")

    except Exception as e:
        print("SDK error: " + str(e), file=sys.stderr)
        print("NO_TRANSCRIPT_AVAILABLE")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(1)
    get_transcript(sys.argv[1])
