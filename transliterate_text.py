#!/usr/bin/env python3.10
import sys
import os

def transliterate_text(text, lang_code):
    lang = lang_code.lower()[:2] if lang_code else 'en'

    if lang == 'en':
        print(text)
        return

    # Method 1: deep-translator (most reliable, free)
    try:
        from deep_translator import GoogleTranslator
        lines = text.strip().split('\n')
        result_lines = []
        chunk = []
        chunk_len = 0

        for line in lines:
            line = line.strip()
            if not line:
                continue
            if chunk_len + len(line) > 4500:
                joined = ' '.join(chunk)
                translated = GoogleTranslator(
                    source='auto', target='en').translate(joined)
                result_lines.append(translated)
                chunk = []
                chunk_len = 0
            chunk.append(line)
            chunk_len += len(line) + 1

        if chunk:
            joined = ' '.join(chunk)
            translated = GoogleTranslator(
                source='auto', target='en').translate(joined)
            result_lines.append(translated)

        result = '\n'.join(result_lines)
        if result.strip():
            print(result)
            return

    except Exception as e:
        print(f"deep-translator error: {e}", file=sys.stderr)

    # Method 2: transliterate library (Cyrillic languages)
    try:
        if lang in ['ru', 'uk', 'bg', 'sr', 'mk']:
            from transliterate import translit
            result = translit(text, lang, reversed=True)
            if result.strip():
                print(result)
                return
    except Exception as e:
        print(f"transliterate error: {e}", file=sys.stderr)

    # Method 3: indic-transliteration (South Asian scripts)
    try:
        if lang in ['hi', 'bn', 'ta', 'te', 'ml', 'kn', 'gu', 'pa']:
            from indic_transliteration import sanscript
            from indic_transliteration.sanscript import transliterate as it
            script_map = {
                'hi': sanscript.DEVANAGARI,
                'bn': sanscript.BENGALI,
                'ta': sanscript.TAMIL,
                'te': sanscript.TELUGU,
                'ml': sanscript.MALAYALAM,
                'kn': sanscript.KANNADA,
                'gu': sanscript.GUJARATI,
                'pa': sanscript.GURMUKHI,
            }
            src = script_map.get(lang)
            if src:
                result = it(text, src, sanscript.IAST)
                if result.strip():
                    print(result)
                    return
    except Exception as e:
        print(f"indic error: {e}", file=sys.stderr)

    # Method 4: MyMemory API fallback (no install needed)
    try:
        import urllib.request
        import urllib.parse
        import json
        encoded = urllib.parse.quote(text[:500])
        url = f"https://api.mymemory.translated.net/get?q={encoded}&langpair={lang}|en"
        req = urllib.request.Request(url,
            headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read())
            if data.get('responseStatus') == 200:
                translated = data['responseData']['translatedText']
                if translated.strip():
                    print(translated)
                    return
    except Exception as e:
        print(f"MyMemory error: {e}", file=sys.stderr)

    # Final fallback: return original text
    print(text)


if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: python3.10 transliterate_text.py <text_file> <lang_code>",
              file=sys.stderr)
        sys.exit(1)

    text_file = sys.argv[1]
    lang_code = sys.argv[2]

    if not os.path.exists(text_file):
        print(f"File not found: {text_file}", file=sys.stderr)
        sys.exit(1)

    with open(text_file, 'r', encoding='utf-8') as f:
        text = f.read()

    transliterate_text(text, lang_code)
