package com.wokandroll.back.report.pdf;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.math.BigDecimal;
import java.util.List;

/**
 * A simple vertical bar chart drawn with PDF vector graphics (#173). No chart library: the bars are
 * rectangles, so the chart stays sharp at any zoom and the values come straight from the report
 * DTOs (PDF = web = CSV). Every bar has its value printed above it, so the chart can be read
 * without colour.
 */
final class PdfBarChart {

  static final Color BAR = new Color(180, 59, 44); // --color-bg-brand #b43b2c
  private static final Color AXIS = new Color(102, 100, 94); // --ink-500
  private static final Color GRID = new Color(217, 214, 206); // --sand-200
  private static final Color TEXT = new Color(32, 31, 27); // --ink-900

  private static final float LEFT = 44;
  private static final float BOTTOM = 22;
  private static final float TOP = 16;
  private static final int GRID_LINES = 4;

  private PdfBarChart() {}

  /**
   * Draws the chart into a template and returns it as an image element for the document.
   *
   * @param labels one label per bar, short (a country code or a month)
   * @param values one value per bar, 0 or more
   * @param valueLabels the text above each bar (the formatted value)
   */
  static Image draw(
      PdfWriter writer,
      float width,
      float height,
      List<String> labels,
      List<BigDecimal> values,
      List<String> valueLabels) {
    if (labels.size() != values.size() || labels.size() != valueLabels.size()) {
      throw new IllegalArgumentException("labels, values and valueLabels must have one entry each");
    }
    PdfContentByte canvas = writer.getDirectContent();
    PdfTemplate t = canvas.createTemplate(width, height);
    BaseFont font = helvetica();

    double max = values.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
    double scaleMax = niceMax(max);
    float plotWidth = width - LEFT - 4;
    float plotHeight = height - BOTTOM - TOP;

    // Grid lines and the axis values.
    t.setLineWidth(0.4f);
    for (int i = 0; i <= GRID_LINES; i++) {
      float y = BOTTOM + plotHeight * i / GRID_LINES;
      t.setColorStroke(i == 0 ? AXIS : GRID);
      t.moveTo(LEFT, y);
      t.lineTo(LEFT + plotWidth, y);
      t.stroke();
      text(t, font, 6.5f, axisLabel(scaleMax * i / GRID_LINES), LEFT - 4, y - 2, Align.RIGHT);
    }

    // Bars with their value above and the label below.
    int n = values.size();
    float slot = plotWidth / Math.max(n, 1);
    float barWidth = Math.min(slot * 0.62f, 48);
    for (int i = 0; i < n; i++) {
      double v = values.get(i).doubleValue();
      float barHeight = scaleMax == 0 ? 0 : (float) (plotHeight * v / scaleMax);
      float x = LEFT + slot * i + (slot - barWidth) / 2;
      if (barHeight > 0) {
        t.setColorFill(BAR);
        t.rectangle(x, BOTTOM, barWidth, barHeight);
        t.fill();
      }
      float center = x + barWidth / 2;
      text(t, font, 6.5f, valueLabels.get(i), center, BOTTOM + barHeight + 3, Align.CENTER);
      text(t, font, 7.5f, labels.get(i), center, BOTTOM - 11, Align.CENTER);
    }

    try {
      Image image = Image.getInstance(t);
      image.setAlignment(Image.MIDDLE);
      return image;
    } catch (BadElementException e) {
      throw new IllegalStateException("Could not place the chart", e);
    }
  }

  /** A round upper limit for the axis: 1, 2 or 5 times a power of ten, at least the maximum. */
  static double niceMax(double max) {
    if (max <= 0) {
      return 0;
    }
    double magnitude = Math.pow(10, Math.floor(Math.log10(max)));
    for (double step : new double[] {1, 2, 2.5, 5, 10}) {
      if (step * magnitude >= max) {
        return step * magnitude;
      }
    }
    return 10 * magnitude;
  }

  /** Axis numbers: whole numbers, with k or M for big values (the exact values are on the bars). */
  static String axisLabel(double value) {
    if (value >= 1_000_000) {
      return trim(value / 1_000_000) + "M";
    }
    if (value >= 10_000) {
      return trim(value / 1_000) + "k";
    }
    return trim(value);
  }

  private static String trim(double value) {
    return value == Math.rint(value)
        ? String.valueOf((long) value)
        : String.valueOf(Math.round(value * 10) / 10.0);
  }

  private enum Align {
    CENTER,
    RIGHT
  }

  private static void text(
      PdfTemplate t, BaseFont font, float size, String text, float x, float y, Align align) {
    t.beginText();
    t.setColorFill(TEXT);
    t.setFontAndSize(font, size);
    float w = font.getWidthPoint(text, size);
    t.setTextMatrix(align == Align.CENTER ? x - w / 2 : x - w, y);
    t.showText(text);
    t.endText();
  }

  private static BaseFont helvetica() {
    try {
      return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    } catch (Exception e) {
      throw new IllegalStateException("Helvetica is missing", e);
    }
  }
}
