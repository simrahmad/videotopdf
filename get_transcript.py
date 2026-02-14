import sys
import os

def get_transcript(video_id):
    try:
        from youtube_transcript_api import YouTubeTranscriptApi
        from youtube_transcript_api._errors import (
            TranscriptsDisabled,
            NoTranscriptFound,
            VideoUnavailable
        )

        print(f"Fetching transcript for: {video_id}", file=sys.stderr)
        api = YouTubeTranscriptApi()

        # Try English first
        try:
            transcript = api.fetch(video_id, languages=["en"])
            text = " ".join([s.text for s in transcript])
            print(f"Got English transcript: {len(text)} chars", file=sys.stderr)
            print(text)
            return
        except Exception as e:
            print(f"English failed: {e}", file=sys.stderr)

        # Try any available language
        try:
            transcript_list = api.list(video_id)

            # Try manually created first
            try:
                transcript = transcript_list.find_manually_created_transcript(
                    ["en","ar","fr","de","es","hi","ur",
                     "zh","ru","pt","ja","ko","tr","it"])
                fetched = transcript.fetch()
                text = " ".join([s.text for s in fetched])
                print(f"Got manual transcript: {len(text)} chars", file=sys.stderr)
                print(text)
                return
            except Exception as e:
                print(f"Manual failed: {e}", file=sys.stderr)

            # Try auto-generated
            try:
                transcript = transcript_list.find_generated_transcript(
                    ["en","ar","fr","de","es","hi","ur",
                     "zh","ru","pt","ja","ko","tr","it"])
                fetched = transcript.fetch()
                text = " ".join([s.text for s in fetched])
                print(f"Got auto transcript: {len(text)} chars", file=sys.stderr)
                print(text)
                return
            except Exception as e:
                print(f"Auto failed: {e}", file=sys.stderr)

        except TranscriptsDisabled:
            print("Transcripts disabled", file=sys.stderr)
        except VideoUnavailable:
            print("Video unavailable", file=sys.stderr)
        except Exception as e:
            print(f"List error: {e}", file=sys.stderr)

        print("NO_TRANSCRIPT_AVAILABLE")

    except ImportError as e:
        print(f"Import error: {e}", file=sys.stderr)
        print("NO_TRANSCRIPT_AVAILABLE")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: get_transcript.py <video_id>", file=sys.stderr)
        sys.exit(1)
    get_transcript(sys.argv[1])
