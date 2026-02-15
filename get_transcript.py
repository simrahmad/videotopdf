import sys
import os

def get_transcript(video_id):
    try:
        from youtube_transcript_api import YouTubeTranscriptApi
        
        print(f"Fetching transcript: {video_id}", file=sys.stderr)
        api = YouTubeTranscriptApi()

        # PRIORITY 1: Try English first
        try:
            transcript = api.fetch(video_id, languages=['en'])
            text = " ".join([s.text for s in transcript])
            print(f"Got English transcript: {len(text)} chars", file=sys.stderr)
            print(text)
            return
        except Exception as e:
            print(f"English not available: {e}", file=sys.stderr)

        # PRIORITY 2: Get any available transcript
        try:
            transcript_list = api.list(video_id)
            
            for t in transcript_list:
                try:
                    fetched = t.fetch()
                    text = " ".join([s.text for s in fetched])
                    print(f"Got {t.language} transcript: {len(text)} chars", file=sys.stderr)
                    print(text)
                    return
                except:
                    continue
                    
        except Exception as e:
            print(f"No transcripts: {e}", file=sys.stderr)

        print("NO_TRANSCRIPT_AVAILABLE")

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        print("NO_TRANSCRIPT_AVAILABLE")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(1)
    get_transcript(sys.argv[1])
