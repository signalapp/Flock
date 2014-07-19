package org.anhonesteffort.flock.util;

import java.util.ArrayList;

/**
 * Java Code to get a color name from rgb/hex value/awt color
 *
 * The part of looking up a color name from the rgb values is edited from
 * https://gist.github.com/nightlark/6482130#file-gistfile1-java (that has some errors) by Ryan Mast (nightlark)
 *
 * @author Xiaoxiao Li
 *
 */
public class ColorUtils {

  /**
   * Initialize the color list that we have.
   */
  private ArrayList<ColorName> initColorList() {
    ArrayList<ColorName> colorList = new ArrayList<ColorName>();
    colorList.add(new ColorName("alice blue", 0xF0, 0xF8, 0xFF));
    colorList.add(new ColorName("antique white", 0xFA, 0xEB, 0xD7));
    colorList.add(new ColorName("aqua", 0x00, 0xFF, 0xFF));
    colorList.add(new ColorName("aquamarine", 0x7F, 0xFF, 0xD4));
    colorList.add(new ColorName("azure", 0xF0, 0xFF, 0xFF));
    colorList.add(new ColorName("beige", 0xF5, 0xF5, 0xDC));
    colorList.add(new ColorName("bisque", 0xFF, 0xE4, 0xC4));
    colorList.add(new ColorName("black", 0x00, 0x00, 0x00));
    colorList.add(new ColorName("blanched almond", 0xFF, 0xEB, 0xCD));
    colorList.add(new ColorName("blue", 0x00, 0x00, 0xFF));
    colorList.add(new ColorName("blue violet", 0x8A, 0x2B, 0xE2));
    colorList.add(new ColorName("brown", 0xA5, 0x2A, 0x2A));
    colorList.add(new ColorName("burly wood", 0xDE, 0xB8, 0x87));
    colorList.add(new ColorName("cadet blue", 0x5F, 0x9E, 0xA0));
    colorList.add(new ColorName("chartreuse", 0x7F, 0xFF, 0x00));
    colorList.add(new ColorName("chocolate", 0xD2, 0x69, 0x1E));
    colorList.add(new ColorName("coral", 0xFF, 0x7F, 0x50));
    colorList.add(new ColorName("cornflower blue", 0x64, 0x95, 0xED));
    colorList.add(new ColorName("cornsilk", 0xFF, 0xF8, 0xDC));
    colorList.add(new ColorName("crimson", 0xDC, 0x14, 0x3C));
    colorList.add(new ColorName("cyan", 0x00, 0xFF, 0xFF));
    colorList.add(new ColorName("dark blue", 0x00, 0x00, 0x8B));
    colorList.add(new ColorName("dark cyan", 0x00, 0x8B, 0x8B));
    colorList.add(new ColorName("dark golden rod", 0xB8, 0x86, 0x0B));
    colorList.add(new ColorName("dark gray", 0xA9, 0xA9, 0xA9));
    colorList.add(new ColorName("dark green", 0x00, 0x64, 0x00));
    colorList.add(new ColorName("dark khaki", 0xBD, 0xB7, 0x6B));
    colorList.add(new ColorName("dark magenta", 0x8B, 0x00, 0x8B));
    colorList.add(new ColorName("dark olive green", 0x55, 0x6B, 0x2F));
    colorList.add(new ColorName("dark orange", 0xFF, 0x8C, 0x00));
    colorList.add(new ColorName("dark orchid", 0x99, 0x32, 0xCC));
    colorList.add(new ColorName("dark red", 0x8B, 0x00, 0x00));
    colorList.add(new ColorName("dark salmon", 0xE9, 0x96, 0x7A));
    colorList.add(new ColorName("dark sea green", 0x8F, 0xBC, 0x8F));
    colorList.add(new ColorName("dark slate blue", 0x48, 0x3D, 0x8B));
    colorList.add(new ColorName("dark slate gray", 0x2F, 0x4F, 0x4F));
    colorList.add(new ColorName("dark turquoise", 0x00, 0xCE, 0xD1));
    colorList.add(new ColorName("dark violet", 0x94, 0x00, 0xD3));
    colorList.add(new ColorName("deep pink", 0xFF, 0x14, 0x93));
    colorList.add(new ColorName("deep sky blue", 0x00, 0xBF, 0xFF));
    colorList.add(new ColorName("dim gray", 0x69, 0x69, 0x69));
    colorList.add(new ColorName("dodger blue", 0x1E, 0x90, 0xFF));
    colorList.add(new ColorName("fire brick", 0xB2, 0x22, 0x22));
    colorList.add(new ColorName("floral white", 0xFF, 0xFA, 0xF0));
    colorList.add(new ColorName("forest green", 0x22, 0x8B, 0x22));
    colorList.add(new ColorName("fuchsia", 0xFF, 0x00, 0xFF));
    colorList.add(new ColorName("gainsboro", 0xDC, 0xDC, 0xDC));
    colorList.add(new ColorName("ghost white", 0xF8, 0xF8, 0xFF));
    colorList.add(new ColorName("gold", 0xFF, 0xD7, 0x00));
    colorList.add(new ColorName("golden rod", 0xDA, 0xA5, 0x20));
    colorList.add(new ColorName("gray", 0x80, 0x80, 0x80));
    colorList.add(new ColorName("green", 0x00, 0x80, 0x00));
    colorList.add(new ColorName("green yellow", 0xAD, 0xFF, 0x2F));
    colorList.add(new ColorName("honey dew", 0xF0, 0xFF, 0xF0));
    colorList.add(new ColorName("hot pink", 0xFF, 0x69, 0xB4));
    colorList.add(new ColorName("indian red", 0xCD, 0x5C, 0x5C));
    colorList.add(new ColorName("indigo", 0x4B, 0x00, 0x82));
    colorList.add(new ColorName("ivory", 0xFF, 0xFF, 0xF0));
    colorList.add(new ColorName("khaki", 0xF0, 0xE6, 0x8C));
    colorList.add(new ColorName("lavender", 0xE6, 0xE6, 0xFA));
    colorList.add(new ColorName("lavender blush", 0xFF, 0xF0, 0xF5));
    colorList.add(new ColorName("lawn green", 0x7C, 0xFC, 0x00));
    colorList.add(new ColorName("lemon chiffon", 0xFF, 0xFA, 0xCD));
    colorList.add(new ColorName("light blue", 0xAD, 0xD8, 0xE6));
    colorList.add(new ColorName("light coral", 0xF0, 0x80, 0x80));
    colorList.add(new ColorName("light cyan", 0xE0, 0xFF, 0xFF));
    colorList.add(new ColorName("light golden rod yellow", 0xFA, 0xFA, 0xD2));
    colorList.add(new ColorName("light gray", 0xD3, 0xD3, 0xD3));
    colorList.add(new ColorName("light green", 0x90, 0xEE, 0x90));
    colorList.add(new ColorName("light pink", 0xFF, 0xB6, 0xC1));
    colorList.add(new ColorName("light salmon", 0xFF, 0xA0, 0x7A));
    colorList.add(new ColorName("light sea green", 0x20, 0xB2, 0xAA));
    colorList.add(new ColorName("light sky blue", 0x87, 0xCE, 0xFA));
    colorList.add(new ColorName("light slate gray", 0x77, 0x88, 0x99));
    colorList.add(new ColorName("light steel blue", 0xB0, 0xC4, 0xDE));
    colorList.add(new ColorName("light yellow", 0xFF, 0xFF, 0xE0));
    colorList.add(new ColorName("lime", 0x00, 0xFF, 0x00));
    colorList.add(new ColorName("lime green", 0x32, 0xCD, 0x32));
    colorList.add(new ColorName("linen", 0xFA, 0xF0, 0xE6));
    colorList.add(new ColorName("magenta", 0xFF, 0x00, 0xFF));
    colorList.add(new ColorName("maroon", 0x80, 0x00, 0x00));
    colorList.add(new ColorName("medium aqua marine", 0x66, 0xCD, 0xAA));
    colorList.add(new ColorName("medium blue", 0x00, 0x00, 0xCD));
    colorList.add(new ColorName("medium orchid", 0xBA, 0x55, 0xD3));
    colorList.add(new ColorName("medium purple", 0x93, 0x70, 0xDB));
    colorList.add(new ColorName("medium sea green", 0x3C, 0xB3, 0x71));
    colorList.add(new ColorName("medium slate blue", 0x7B, 0x68, 0xEE));
    colorList.add(new ColorName("medium spring green", 0x00, 0xFA, 0x9A));
    colorList.add(new ColorName("medium turquoise", 0x48, 0xD1, 0xCC));
    colorList.add(new ColorName("medium violet red", 0xC7, 0x15, 0x85));
    colorList.add(new ColorName("midnight blue", 0x19, 0x19, 0x70));
    colorList.add(new ColorName("mint cream", 0xF5, 0xFF, 0xFA));
    colorList.add(new ColorName("misty rose", 0xFF, 0xE4, 0xE1));
    colorList.add(new ColorName("moccasin", 0xFF, 0xE4, 0xB5));
    colorList.add(new ColorName("navajo white", 0xFF, 0xDE, 0xAD));
    colorList.add(new ColorName("navy", 0x00, 0x00, 0x80));
    colorList.add(new ColorName("old lace", 0xFD, 0xF5, 0xE6));
    colorList.add(new ColorName("olive", 0x80, 0x80, 0x00));
    colorList.add(new ColorName("olive drab", 0x6B, 0x8E, 0x23));
    colorList.add(new ColorName("orange", 0xFF, 0xA5, 0x00));
    colorList.add(new ColorName("orange red", 0xFF, 0x45, 0x00));
    colorList.add(new ColorName("orchid", 0xDA, 0x70, 0xD6));
    colorList.add(new ColorName("pale golden rod", 0xEE, 0xE8, 0xAA));
    colorList.add(new ColorName("pale green", 0x98, 0xFB, 0x98));
    colorList.add(new ColorName("pale turquoise", 0xAF, 0xEE, 0xEE));
    colorList.add(new ColorName("pale violet red", 0xDB, 0x70, 0x93));
    colorList.add(new ColorName("papaya whip", 0xFF, 0xEF, 0xD5));
    colorList.add(new ColorName("peach puff", 0xFF, 0xDA, 0xB9));
    colorList.add(new ColorName("peru", 0xCD, 0x85, 0x3F));
    colorList.add(new ColorName("pink", 0xFF, 0xC0, 0xCB));
    colorList.add(new ColorName("plum", 0xDD, 0xA0, 0xDD));
    colorList.add(new ColorName("powder blue", 0xB0, 0xE0, 0xE6));
    colorList.add(new ColorName("purple", 0x80, 0x00, 0x80));
    colorList.add(new ColorName("red", 0xFF, 0x00, 0x00));
    colorList.add(new ColorName("rosy brown", 0xBC, 0x8F, 0x8F));
    colorList.add(new ColorName("royal blue", 0x41, 0x69, 0xE1));
    colorList.add(new ColorName("saddle brown", 0x8B, 0x45, 0x13));
    colorList.add(new ColorName("salmon", 0xFA, 0x80, 0x72));
    colorList.add(new ColorName("sandy brown", 0xF4, 0xA4, 0x60));
    colorList.add(new ColorName("sea green", 0x2E, 0x8B, 0x57));
    colorList.add(new ColorName("sea shell", 0xFF, 0xF5, 0xEE));
    colorList.add(new ColorName("sienna", 0xA0, 0x52, 0x2D));
    colorList.add(new ColorName("silver", 0xC0, 0xC0, 0xC0));
    colorList.add(new ColorName("sky blue", 0x87, 0xCE, 0xEB));
    colorList.add(new ColorName("slate blue", 0x6A, 0x5A, 0xCD));
    colorList.add(new ColorName("slate gray", 0x70, 0x80, 0x90));
    colorList.add(new ColorName("snow", 0xFF, 0xFA, 0xFA));
    colorList.add(new ColorName("spring green", 0x00, 0xFF, 0x7F));
    colorList.add(new ColorName("steel blue", 0x46, 0x82, 0xB4));
    colorList.add(new ColorName("tan", 0xD2, 0xB4, 0x8C));
    colorList.add(new ColorName("teal", 0x00, 0x80, 0x80));
    colorList.add(new ColorName("thistle", 0xD8, 0xBF, 0xD8));
    colorList.add(new ColorName("tomato", 0xFF, 0x63, 0x47));
    colorList.add(new ColorName("turquoise", 0x40, 0xE0, 0xD0));
    colorList.add(new ColorName("violet", 0xEE, 0x82, 0xEE));
    colorList.add(new ColorName("wheat", 0xF5, 0xDE, 0xB3));
    colorList.add(new ColorName("white", 0xFF, 0xFF, 0xFF));
    colorList.add(new ColorName("white smoke", 0xF5, 0xF5, 0xF5));
    colorList.add(new ColorName("yellow", 0xFF, 0xFF, 0x00));
    colorList.add(new ColorName("yellow green", 0x9A, 0xCD, 0x32));
    return colorList;
  }

  /**
   * Get the closest color name from our list
   *
   * @param r
   * @param g
   * @param b
   * @return
   */
  public String getColorNameFromRgb(int r, int g, int b) {
    ArrayList<ColorName> colorList = initColorList();
    ColorName closestMatch = null;
    int minMSE = Integer.MAX_VALUE;
    int mse;
    for (ColorName c : colorList) {
      mse = c.computeMSE(r, g, b);
      if (mse < minMSE) {
        minMSE = mse;
        closestMatch = c;
      }
    }

    if (closestMatch != null) {
      return closestMatch.getName();
    } else {
      return "no matched color name.";
    }
  }

  /**
   * Convert hexColor to rgb, then call getColorNameFromRgb(r, g, b)
   *
   * @param hexColor
   * @return
   */
  public String getColorNameFromHex(int hexColor) {
    int r = (hexColor & 0xFF0000) >> 16;
    int g = (hexColor & 0xFF00) >> 8;
    int b = (hexColor & 0xFF);
    return getColorNameFromRgb(r, g, b);
  }

  /**
   * SubClass of ColorUtils. In order to lookup color name
   *
   * @author Xiaoxiao Li
   *
   */
  public class ColorName {
    public int r, g, b;
    public String name;

    public ColorName(String name, int r, int g, int b) {
      this.r = r;
      this.g = g;
      this.b = b;
      this.name = name;
    }

    public int computeMSE(int pixR, int pixG, int pixB) {
      return (int) (((pixR - r) * (pixR - r) + (pixG - g) * (pixG - g) + (pixB - b)
          * (pixB - b)) / 3);
    }

    public int getR() {
      return r;
    }

    public int getG() {
      return g;
    }

    public int getB() {
      return b;
    }

    public String getName() {
      return name;
    }
  }
}
