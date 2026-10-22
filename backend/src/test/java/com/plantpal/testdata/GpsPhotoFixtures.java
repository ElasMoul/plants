package com.plantpal.testdata;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.GpsDirectory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/** GPS-tagged photo fixtures (PLA-118). The committed JPEG is produced by {@link #main}. */
public final class GpsPhotoFixtures {

  public static final String DATE_TAKEN_EXIF = "2023:05:17 14:30:00";
  public static final String DATE_TAKEN_ISO = "2023-05-17T14:30:00Z";
  public static final int WIDTH = 64;
  public static final int HEIGHT = 48;

  private static final String RESOURCE = "/photos/gps-tagged.jpg";

  private GpsPhotoFixtures() {}

  /** The committed GPS-tagged JPEG (DateTimeOriginal {@link #DATE_TAKEN_EXIF}). */
  public static byte[] gpsJpeg() {
    try (InputStream in = GpsPhotoFixtures.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture " + RESOURCE);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A JPEG with pixels but no EXIF at all. */
  public static byte[] plainJpeg() {
    return encode("jpg");
  }

  /** A JPEG carrying EXIF orientation 6 (rotated) together with GPS and the fixture date. */
  public static byte[] rotatedGpsJpeg() {
    try {
      TiffOutputSet set = new TiffOutputSet();
      set.getOrCreateRootDirectory().add(TiffTagConstants.TIFF_TAG_ORIENTATION, (short) 6);
      set.getOrCreateExifDirectory()
          .add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, DATE_TAKEN_EXIF);
      set.setGpsInDegrees(2.3522, 48.8566);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      new ExifRewriter().updateExifMetadataLossless(plainJpeg(), out, set);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** The EXIF orientation of the image, or -1 when absent. */
  public static int orientation(byte[] bytes) {
    try {
      var dir =
          ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes))
              .getFirstDirectoryOfType(com.drew.metadata.exif.ExifIFD0Directory.class);
      return dir != null
              && dir.containsTag(com.drew.metadata.exif.ExifIFD0Directory.TAG_ORIENTATION)
          ? dir.getInteger(com.drew.metadata.exif.ExifIFD0Directory.TAG_ORIENTATION)
          : -1;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** A PNG whose eXIf chunk carries the fixture's GPS and date. */
  public static byte[] gpsPng() {
    byte[] png = encode("png");
    byte[] chunk = pngChunk("eXIf", tiffOf(gpsJpeg()));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int afterIhdr = 8 + 12 + 13;
    out.write(png, 0, afterIhdr);
    out.write(chunk, 0, chunk.length);
    out.write(png, afterIhdr, png.length - afterIhdr);
    return out.toByteArray();
  }

  /** A minimal extended WebP whose EXIF chunk carries the fixture's GPS and date. */
  public static byte[] gpsWebp() {
    byte[] tiff = tiffOf(gpsJpeg());
    byte[] vp8x = new byte[10];
    vp8x[0] = 0x08;
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
    body.writeBytes(webpChunk("VP8X", vp8x));
    body.writeBytes(webpChunk("VP8 ", new byte[] {1, 2, 3, 4}));
    body.writeBytes(webpChunk("EXIF", tiff));
    ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(body.size());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(header.array());
    out.writeBytes(body.toByteArray());
    return out.toByteArray();
  }

  public static boolean hasGps(byte[] bytes) {
    try {
      return ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes))
          .containsDirectoryOfType(GpsDirectory.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] tiffOf(byte[] jpeg) {
    // APP1 segment: FF E1 len len "Exif\0\0" <tiff...>
    for (int i = 2; i < jpeg.length - 10; i++) {
      if ((jpeg[i] & 0xFF) == 0xFF
          && (jpeg[i + 1] & 0xFF) == 0xE1
          && jpeg[i + 4] == 'E'
          && jpeg[i + 5] == 'x') {
        int len = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
        return Arrays.copyOfRange(jpeg, i + 10, i + 2 + len);
      }
    }
    throw new IllegalStateException("No EXIF segment in fixture");
  }

  private static byte[] pngChunk(String type, byte[] data) {
    byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    ByteBuffer buf = ByteBuffer.allocate(12 + data.length);
    buf.putInt(data.length).put(typeBytes).put(data).putInt((int) crc.getValue());
    return buf.array();
  }

  private static byte[] webpChunk(String type, byte[] data) {
    int padded = data.length + (data.length & 1);
    ByteBuffer buf = ByteBuffer.allocate(8 + padded).order(ByteOrder.LITTLE_ENDIAN);
    buf.put(type.getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data);
    return buf.array();
  }

  private static byte[] encode(String format) {
    BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < WIDTH; x++) {
      for (int y = 0; y < HEIGHT; y++) {
        image.setRGB(x, y, (x * 4) << 16 | (y * 5) << 8 | 0x40);
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, format, out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /** Regenerates the committed fixture: {@code main(<output path>)}. */
  public static void main(String[] args) throws Exception {
    byte[] plain = plainJpeg();
    TiffOutputSet set = new TiffOutputSet();
    set.getOrCreateExifDirectory()
        .add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, DATE_TAKEN_EXIF);
    set.setGpsInDegrees(2.3522, 48.8566);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new ExifRewriter().updateExifMetadataLossless(plain, out, set);
    Files.createDirectories(Path.of(args[0]).getParent());
    Files.write(Path.of(args[0]), out.toByteArray());
  }
}
