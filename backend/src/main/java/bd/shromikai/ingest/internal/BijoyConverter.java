package bd.shromikai.ingest.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts text extracted from PDFs that use a legacy ANSI Bangla font (Bijoy / SutonnyMJ) to
 * Unicode (FR-26). The mapping covers the letters, vowel signs, digits, reph, ya-phala, ra-phala,
 * hasanta, the common {@code ক্ষ} glyph and the pre-base vowel reordering. Rarer precomposed
 * conjunct glyphs are not mapped, so the caller checks {@link #bengaliShare(String)} of the result
 * and falls back to OCR for the page below 0.90 (FR-26). Known gap, recorded in the phase report.
 */
public final class BijoyConverter {

  /** Minimum share of Bengali-block letters in the converted text to trust the conversion. */
  public static final double MIN_BENGALI_SHARE = 0.90;

  private static final String MARKERS = "‡‰Š¨©ª¶­";
  private static final Map<Character, String> LETTER = new HashMap<>();
  private static final Map<Character, String> SIGN = new HashMap<>();

  static {
    String[][] letters = {
      {"A", "অ"}, {"B", "ই"}, {"C", "ঈ"}, {"D", "উ"}, {"E", "ঊ"}, {"F", "ঋ"}, {"G", "এ"},
      {"H", "ঐ"}, {"I", "ও"}, {"J", "ঔ"}, {"K", "ক"}, {"L", "খ"}, {"M", "গ"}, {"N", "ঘ"},
      {"O", "ঙ"}, {"P", "চ"}, {"Q", "ছ"}, {"R", "জ"}, {"S", "ঝ"}, {"T", "ঞ"}, {"U", "ট"},
      {"V", "ঠ"}, {"W", "ড"}, {"X", "ঢ"}, {"Y", "ণ"}, {"Z", "ত"}, {"_", "থ"}, {"`", "দ"},
      {"a", "ধ"}, {"b", "ন"}, {"c", "প"}, {"d", "ফ"}, {"e", "ব"}, {"f", "ভ"}, {"g", "ম"},
      {"h", "য"}, {"i", "র"}, {"j", "ল"}, {"k", "শ"}, {"l", "ষ"}, {"m", "স"}, {"n", "হ"},
      {"o", "ড়"}, {"p", "ঢ়"}, {"q", "য়"}, {"r", "ৎ"}, {"s", "ং"}, {"t", "ঃ"}, {"u", "ঁ"},
      {"¶", "ক্ষ"}
    };
    for (String[] pair : letters) {
      LETTER.put(pair[0].charAt(0), pair[1]);
    }
    String[][] signs = {{"v", "া"}, {"x", "ী"}, {"y", "ু"}, {"z", "ূ"}, {"~", "ূ"}, {"„", "ৃ"}};
    for (String[] pair : signs) {
      SIGN.put(pair[0].charAt(0), pair[1]);
    }
  }

  private BijoyConverter() {}

  /** {@code true} if the text looks like legacy-font Bangla: marker glyphs and no Bengali text. */
  public static boolean looksLegacy(String text) {
    int markers = 0;
    int letters = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= 'ঀ' && c <= '৿') {
        return false;
      }
      if (MARKERS.indexOf(c) >= 0) {
        markers++;
      }
      if (Character.isLetter(c)) {
        letters++;
      }
    }
    return markers >= 3 && letters > 0 && markers * 100 >= letters;
  }

  /** Share of letters in the Bengali block among all letters; 0 when there are no letters. */
  public static double bengaliShare(String text) {
    int bengali = 0;
    int letters = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isLetter(c) || (c >= 'ঀ' && c <= '৿' && !Character.isDigit(c))) {
        letters++;
        if (c >= 'ঀ' && c <= '৿') {
          bengali++;
        }
      }
    }
    return letters == 0 ? 0 : (double) bengali / letters;
  }

  /** Converts legacy-encoded text line by line; characters without a mapping pass through. */
  public static String toUnicode(String legacy) {
    StringBuilder out = new StringBuilder(legacy.length());
    for (String line : legacy.split("\n", -1)) {
      out.append(convertLine(line)).append('\n');
    }
    out.setLength(out.length() - 1);
    return out.toString();
  }

  private static String convertLine(String line) {
    Converter conv = new Converter();
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == 'A' && i + 1 < line.length() && line.charAt(i + 1) == 'v') {
        conv.endCluster();
        conv.out.append('আ');
        i++;
        continue;
      }
      conv.accept(c);
    }
    conv.endCluster();
    conv.flushPending();
    return conv.out.toString();
  }

  /** One pass over a line: tracks the pending pre-base vowel and the open consonant cluster. */
  private static final class Converter {
    final StringBuilder out = new StringBuilder();
    private char pending; // pre-base vowel typed before its consonant cluster
    private char clusterPre; // pre-base vowel that belongs to the open cluster
    private int clusterStart = -1;
    private boolean open;

    void accept(char c) {
      switch (c) {
        case 'w', '‡', '‰' -> {
          endCluster();
          flushPending();
          pending = c;
        }
        case '&' -> {
          if (open) {
            out.append('্');
          } else {
            out.append('&');
          }
        }
        case 'ª' -> appendInCluster("্র");
        case '¨' -> appendInCluster("্য");
        case '©' -> {
          if (open && clusterStart >= 0) {
            out.insert(clusterStart, "র্");
          } else {
            out.append("র্");
          }
        }
        case 'v' -> {
          if (clusterPre == '‡') {
            out.append("ো");
            clusterPre = 0;
          } else {
            endCluster();
            out.append('া');
          }
          close();
        }
        case 'Š' -> {
          if (clusterPre == '‡') {
            out.append("ৌ");
            clusterPre = 0;
          } else {
            endCluster();
            out.append("ৗ");
          }
          close();
        }
        case 's', 't', 'u' -> {
          endCluster();
          out.append(LETTER.get(c));
        }
        default -> other(c);
      }
    }

    private void other(char c) {
      String sign = SIGN.get(c);
      String letter = LETTER.get(c);
      if (sign != null) {
        endCluster();
        out.append(sign);
      } else if (letter != null && c >= 'A' && c <= 'J') {
        endCluster();
        flushPending();
        out.append(letter);
      } else if (letter != null) {
        startConsonant(letter);
      } else {
        endCluster();
        flushPending();
        out.append(c == '|' ? '।' : c);
      }
    }

    private void startConsonant(String letter) {
      boolean afterHasanta = open && out.length() > 0 && out.charAt(out.length() - 1) == '্';
      if (!afterHasanta) {
        endCluster();
        clusterStart = out.length();
        clusterPre = pending;
        pending = 0;
        open = true;
      }
      out.append(letter);
    }

    private void appendInCluster(String text) {
      if (open) {
        out.append(text);
      } else {
        out.append(text);
      }
    }

    /** The open cluster is complete: its pre-base vowel goes after it. */
    void endCluster() {
      if (open && clusterPre != 0) {
        out.append(vowelSign(clusterPre));
        clusterPre = 0;
      }
      close();
    }

    private void close() {
      open = false;
    }

    void flushPending() {
      if (pending != 0) {
        out.append(vowelSign(pending));
        pending = 0;
      }
    }
  }

  private static String vowelSign(char prebase) {
    return switch (prebase) {
      case 'w' -> "ি";
      case '‡' -> "ে";
      default -> "ৈ";
    };
  }
}
