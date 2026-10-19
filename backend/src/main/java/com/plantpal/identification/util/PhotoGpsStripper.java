package com.plantpal.identification.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Removes GPS from an image without re-encoding the pixels; every other EXIF field (date taken,
 * orientation, camera) is kept for JPEG. PNG and WebP carry their EXIF in one chunk that cannot be
 * edited in place, so a chunk that contains GPS is dropped whole. Images without GPS are returned
 * as the same array instance. Fails closed: input that cannot be parsed or cleaned throws, so
 * unverified bytes are never stored.
 */
public final class PhotoGpsStripper {

  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  private static final int WEBP_VP8X_EXIF_FLAG = 0x08;
  private static final int TAG_GPS_IFD = 0x8825;
  private static final int TAG_PADDING = 0xEA1C;
  private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
  private static final byte[] XMP_LATITUDE = "GPSLatitude".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] XMP_LONGITUDE = "GPSLongitude".getBytes(StandardCharsets.US_ASCII);
  private static final int[] TYPE_SIZES = {0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8};

  private PhotoGpsStripper() {}

  /** Thrown when an image cannot be inspected or cleaned. */
  public static final class PhotoGpsStripException extends RuntimeException {
    public PhotoGpsStripException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static boolean hasGps(byte[] imageBytes) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
      return metadata.containsDirectoryOfType(GpsDirectory.class)
          || containsXmpGps(imageBytes, 0, imageBytes.length);
    } catch (Exception e) {
      throw new PhotoGpsStripException("Image metadata could not be read", e);
    }
  }

  public static byte[] strip(byte[] imageBytes) {
    if (imageBytes == null || imageBytes.length == 0) {
      throw new PhotoGpsStripException("Empty image", null);
    }
    if (!hasGps(imageBytes)) {
      return imageBytes;
    }
    byte[] cleaned;
    try {
      if (startsWithAt(imageBytes, 0, PNG_SIGNATURE)) {
        cleaned = stripPng(imageBytes);
      } else if (isWebp(imageBytes)) {
        cleaned = stripWebp(imageBytes);
      } else {
        cleaned = stripJpeg(imageBytes);
      }
    } catch (RuntimeException e) {
      throw new PhotoGpsStripException("GPS metadata could not be removed", e);
    }
    if (hasGps(cleaned)) {
      throw new PhotoGpsStripException("GPS metadata still present after stripping", null);
    }
    return cleaned;
  }

  /**
   * Scrubs the GPS IFD in place (zeroed, so no coordinate bytes remain), neutralises the pointer to
   * it, and drops any XMP segment that repeats the coordinates. Pixels and every other EXIF field
   * are untouched, and no offsets move.
   */
  private static byte[] stripJpeg(byte[] bytes) {
    byte[] copy = bytes.clone();
    ByteArrayOutputStream out = new ByteArrayOutputStream(copy.length);
    out.write(copy, 0, 2);
    int pos = 2;
    while (pos + 4 <= copy.length && (copy[pos] & 0xFF) == 0xFF) {
      int marker = copy[pos + 1] & 0xFF;
      if (marker == 0xDA || marker == 0xD9) {
        break;
      }
      if (isStandaloneMarker(marker)) {
        out.write(copy, pos, 2);
        pos += 2;
      } else {
        int end = segmentEnd(copy, pos);
        copySegment(copy, pos, end, marker, out);
        pos = end;
      }
    }
    out.write(copy, pos, copy.length - pos);
    return out.toByteArray();
  }

  private static boolean isStandaloneMarker(int marker) {
    return marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8) || marker == 0xFF;
  }

  private static int segmentEnd(byte[] copy, int pos) {
    int length = ((copy[pos + 2] & 0xFF) << 8) | (copy[pos + 3] & 0xFF);
    int end = pos + 2 + length;
    if (length < 2 || end > copy.length) {
      throw new IllegalArgumentException("Truncated JPEG segment");
    }
    return end;
  }

  private static void copySegment(
      byte[] copy, int pos, int end, int marker, ByteArrayOutputStream out) {
    boolean app1 = marker == 0xE1;
    if (app1 && startsWithAt(copy, pos + 4, EXIF_HEADER)) {
      scrubTiffGps(copy, pos + 10, end);
    }
    if (!(app1 && containsXmpGps(copy, pos + 4, end))) {
      out.write(copy, pos, end - pos);
    }
  }

  private static void scrubTiffGps(byte[] b, int tiff, int limit) {
    ByteOrder order = b[tiff] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    ByteBuffer buf = ByteBuffer.wrap(b).order(order);
    int ifd0 = tiff + buf.getInt(tiff + 4);
    int count = buf.getShort(ifd0) & 0xFFFF;
    for (int i = 0; i < count; i++) {
      int entry = ifd0 + 2 + i * 12;
      if (entry + 12 > limit) {
        throw new IllegalArgumentException("Truncated EXIF directory");
      }
      if ((buf.getShort(entry) & 0xFFFF) == TAG_GPS_IFD) {
        int gps = tiff + buf.getInt(entry + 8);
        zeroDirectory(b, buf, tiff, gps, limit);
        // Repurpose the pointer as an empty padding tag so readers see no GPS directory.
        buf.putShort(entry, (short) TAG_PADDING);
        buf.putShort(entry + 2, (short) 7);
        buf.putInt(entry + 4, 4);
        buf.putInt(entry + 8, 0);
      }
    }
  }

  private static void zeroDirectory(byte[] b, ByteBuffer buf, int tiff, int ifd, int limit) {
    int count = buf.getShort(ifd) & 0xFFFF;
    if (ifd + 2 + count * 12 + 4 > limit) {
      throw new IllegalArgumentException("Truncated GPS directory");
    }
    for (int i = 0; i < count; i++) {
      int entry = ifd + 2 + i * 12;
      int type = buf.getShort(entry + 2) & 0xFFFF;
      long size = (long) TYPE_SIZES[type > 12 ? 0 : type] * (buf.getInt(entry + 4) & 0xFFFFFFFFL);
      if (size > 4) {
        long valueAt = tiff + (buf.getInt(entry + 8) & 0xFFFFFFFFL);
        if (valueAt + size > limit) {
          throw new IllegalArgumentException("GPS value out of range");
        }
        Arrays.fill(b, (int) valueAt, (int) (valueAt + size), (byte) 0);
      }
    }
    Arrays.fill(b, ifd, ifd + 2 + count * 12 + 4, (byte) 0);
  }

  private static boolean containsXmpGps(byte[] b, int from, int to) {
    return indexOf(b, XMP_LATITUDE, from, to) >= 0 || indexOf(b, XMP_LONGITUDE, from, to) >= 0;
  }

  private static int indexOf(byte[] b, byte[] needle, int from, int to) {
    for (int i = from; i + needle.length <= to; i++) {
      if (startsWithAt(b, i, needle)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean startsWithAt(byte[] b, int at, byte[] prefix) {
    if (at < 0 || at + prefix.length > b.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (b[at + i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static byte[] stripPng(byte[] bytes) {
    ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
    out.write(bytes, 0, PNG_SIGNATURE.length);
    int pos = PNG_SIGNATURE.length;
    while (pos + 12 <= bytes.length) {
      int length = in.getInt(pos);
      String type = new String(bytes, pos + 4, 4, StandardCharsets.US_ASCII);
      int total = 12 + length;
      if (length < 0 || pos + total > bytes.length) {
        throw new IllegalArgumentException("Truncated PNG chunk");
      }
      boolean xmpGps = "iTXt".equals(type) && containsXmpGps(bytes, pos + 8, pos + 8 + length);
      if (!"eXIf".equals(type) && !xmpGps) {
        out.write(bytes, pos, total);
      }
      pos += total;
    }
    return out.toByteArray();
  }

  private static byte[] stripWebp(byte[] bytes) {
    ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
    out.write(bytes, 0, 12);
    int pos = 12;
    while (pos + 8 <= bytes.length) {
      int size = in.getInt(pos + 4);
      int total = 8 + size + (size & 1);
      if (size < 0 || pos + total > bytes.length + (size & 1)) {
        throw new IllegalArgumentException("Truncated WebP chunk");
      }
      int end = Math.min(pos + total, bytes.length);
      copyWebpChunk(bytes, pos, end, out);
      pos = end;
    }
    byte[] result = out.toByteArray();
    ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(4, result.length - 8);
    return result;
  }

  private static void copyWebpChunk(byte[] bytes, int pos, int end, ByteArrayOutputStream out) {
    String type = new String(bytes, pos, 4, StandardCharsets.US_ASCII);
    if ("EXIF".equals(type) || ("XMP ".equals(type) && containsXmpGps(bytes, pos + 8, end))) {
      return;
    }
    byte[] chunk = Arrays.copyOfRange(bytes, pos, end);
    if ("VP8X".equals(type) && chunk.length > 8) {
      chunk[8] &= (byte) ~WEBP_VP8X_EXIF_FLAG;
    }
    out.write(chunk, 0, chunk.length);
  }

  private static boolean isWebp(byte[] b) {
    return b.length > 12
        && b[0] == 'R'
        && b[1] == 'I'
        && b[2] == 'F'
        && b[3] == 'F'
        && b[8] == 'W'
        && b[9] == 'E'
        && b[10] == 'B'
        && b[11] == 'P';
  }
}
