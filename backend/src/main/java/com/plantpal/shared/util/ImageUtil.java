package com.plantpal.shared.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImageUtil {

  private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

  private ImageUtil() {}

  public static byte[] resizeAndConvertToJpeg(byte[] original, int maxSide) {
    try {
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
      if (img == null) {
        log.debug("ImageIO could not decode image — returning original bytes");
        return original;
      }
      int w = img.getWidth(), h = img.getHeight();
      double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
      int newW = (int) (w * scale), newH = (int) (h * scale);
      BufferedImage output = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = output.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(img, 0, 0, newW, newH, null);
      g.dispose();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(output, "JPEG", out);
      byte[] result = out.toByteArray();
      log.debug(
          "Resized image: {}x{} → {}x{} ({} KB → {} KB)",
          w,
          h,
          newW,
          newH,
          original.length / 1024,
          result.length / 1024);
      return result;
    } catch (Exception e) {
      log.debug("Image resize failed ({}), using original bytes", e.getMessage());
      return original;
    }
  }

  public static int[] readDimensions(byte[] imageBytes) {
    try {
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
      return img != null ? new int[] {img.getWidth(), img.getHeight()} : new int[] {0, 0};
    } catch (Exception e) {
      return new int[] {0, 0};
    }
  }
}
