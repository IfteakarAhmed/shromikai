package bd.shromikai.shared.text;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The one text normaliser (FR-14, architecture §7.2). {@link #matchingForm} is used to compare
 * quotes with statute text, to match cross-references and to search; display text is never changed
 * (see {@link NormalisedText}).
 */
public final class BanglaNormalizer {

  private static final char BENGALI_ZERO = '০';
  private static final char TA = 'ত';
  private static final char HASANTA = '্';
  private static final char ZWJ = '‍';
  private static final char KHANDA_TA = 'ৎ';

  private BanglaNormalizer() {}

  /** Steps 1 to 9 of architecture §7.2, in order. */
  public static String matchingForm(String text) {
    String s = Normalizer.normalize(text, Normalizer.Form.NFC);
    s = s.replace("" + TA + HASANTA + ZWJ, String.valueOf(KHANDA_TA));
    s = removeInvisible(s);
    // Removing a joiner can bring a base letter next to a combining mark: compose again so the
    // result is stable (idempotent) under a second pass.
    s = Normalizer.normalize(s, Normalizer.Form.NFC);
    s = toLatinDigits(s);
    s = mapPunctuation(s);
    s = s.toLowerCase(Locale.ROOT);
    s = Normalizer.normalize(s, Normalizer.Form.NFC);
    return collapse(s);
  }

  /** Bengali digits U+09E6 to U+09EF become {@code 0} to {@code 9}; other characters stay. */
  public static String toLatinDigits(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      out.append(isBengaliDigit(c) ? (char) ('0' + (c - BENGALI_ZERO)) : c);
    }
    return out.toString();
  }

  /** Latin digits become Bengali digits (inverse of {@link #toLatinDigits} on digit characters). */
  public static String toBanglaDigits(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      out.append(c >= '0' && c <= '9' ? (char) (BENGALI_ZERO + (c - '0')) : c);
    }
    return out.toString();
  }

  private static boolean isBengaliDigit(char c) {
    return c >= BENGALI_ZERO && c <= '৯';
  }

  private static String removeInvisible(String s) {
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean invisible = c == '​' || c == '‌' || c == '‍' || c == '﻿' || c == '­';
      if (!invisible) {
        out.append(c);
      }
    }
    return out.toString();
  }

  /** Steps 5, 6 and 8: danda, typographic quotes and dashes, then everything else to space. */
  private static String mapPunctuation(String s) {
    StringBuilder out = new StringBuilder(s.length());
    s.codePoints()
        .forEach(
            cp -> {
              int mapped = mapPunctuation(cp);
              if (keep(mapped)) {
                out.appendCodePoint(mapped);
              } else {
                out.append(' ');
              }
            });
    return out.toString();
  }

  private static int mapPunctuation(int cp) {
    return switch (cp) {
      case 0x0964, 0x0965 -> '.';
      case 0x2018, 0x2019 -> '\'';
      case 0x201C, 0x201D -> '"';
      case 0x2012, 0x2013, 0x2014, 0x2015 -> '-';
      default -> cp;
    };
  }

  private static boolean keep(int cp) {
    if (cp == '.' || cp == '(' || cp == ')') {
      return true;
    }
    if (Character.isLetterOrDigit(cp)) {
      return true;
    }
    int type = Character.getType(cp);
    return type == Character.NON_SPACING_MARK
        || type == Character.COMBINING_SPACING_MARK
        || type == Character.ENCLOSING_MARK;
  }

  private static String collapse(String s) {
    StringBuilder out = new StringBuilder(s.length());
    boolean space = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
        space = out.length() > 0;
      } else {
        if (space) {
          out.append(' ');
          space = false;
        }
        out.append(c);
      }
    }
    return out.toString();
  }
}
