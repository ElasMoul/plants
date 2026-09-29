package com.plantpal.identification.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads the capture time from an image's EXIF data; never throws. */
public final class ExifDateTakenReader {

  private static final Logger log = LoggerFactory.getLogger(ExifDateTakenReader.class);

  private static final DateTimeFormatter EXIF_FORMAT =
      DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss");

  private ExifDateTakenReader() {}

  public static Optional<Instant> read(byte[] imageBytes) {
    if (imageBytes == null || imageBytes.length == 0) {
      return Optional.empty();
    }
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
      ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
      ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
      Optional<Instant> result = Optional.empty();
      if (sub != null) {
        result =
            parse(
                sub.getString(ExifDirectoryBase.TAG_DATETIME_ORIGINAL),
                sub.getString(ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL));
        if (result.isEmpty()) {
          result =
              parse(
                  sub.getString(ExifDirectoryBase.TAG_DATETIME_DIGITIZED),
                  sub.getString(ExifDirectoryBase.TAG_TIME_ZONE_DIGITIZED));
        }
      }
      if (result.isEmpty() && ifd0 != null) {
        result = parse(ifd0.getString(ExifDirectoryBase.TAG_DATETIME), null);
      }
      return result;
    } catch (ImageProcessingException | IOException | RuntimeException e) {
      log.debug("Unable to read EXIF date taken", e);
      return Optional.empty();
    }
  }

  private static Optional<Instant> parse(String value, String offset) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      LocalDateTime local = LocalDateTime.parse(value.trim(), EXIF_FORMAT);
      ZoneOffset zone = ZoneOffset.UTC;
      if (offset != null && !offset.isBlank()) {
        zone = parseOffset(offset.trim());
      }
      return Optional.of(local.toInstant(zone));
    } catch (DateTimeParseException e) {
      log.debug("Unparseable EXIF date value: {}", value, e);
      return Optional.empty();
    }
  }

  private static ZoneOffset parseOffset(String offset) {
    try {
      return ZoneOffset.of(offset);
    } catch (DateTimeException e) {
      log.debug("Unparseable EXIF offset value: {}", offset, e);
      return ZoneOffset.UTC;
    }
  }
}
