package com.plantpal.identification.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Reads the capture time from an image's EXIF data; never throws. */
public final class ExifDateTakenReader {

  private static final DateTimeFormatter EXIF_FORMAT =
      DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss");

  private ExifDateTakenReader() {}

  public static Optional<java.time.Instant> read(byte[] imageBytes) {
    if (imageBytes == null || imageBytes.length == 0) {
      return Optional.empty();
    }
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
      ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
      ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
      Optional<java.time.Instant> result = Optional.empty();
      if (sub != null) {
        result =
            parse(
                sub.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL),
                sub.getString(ExifSubIFDDirectory.TAG_TIME_ZONE_ORIGINAL));
        if (result.isEmpty()) {
          result =
              parse(
                  sub.getString(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED),
                  sub.getString(ExifSubIFDDirectory.TAG_TIME_ZONE_DIGITIZED));
        }
      }
      if (result.isEmpty() && ifd0 != null) {
        result = parse(ifd0.getString(ExifIFD0Directory.TAG_DATETIME), null);
      }
      return result;
    } catch (Exception | LinkageError e) {
      return Optional.empty();
    }
  }

  private static Optional<java.time.Instant> parse(String value, String offset) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      LocalDateTime local = LocalDateTime.parse(value.trim(), EXIF_FORMAT);
      ZoneOffset zone = ZoneOffset.UTC;
      if (offset != null && !offset.isBlank()) {
        try {
          zone = ZoneOffset.of(offset.trim());
        } catch (Exception ignored) {
          // malformed offset: keep UTC
        }
      }
      return Optional.of(local.toInstant(zone));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
