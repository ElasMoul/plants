package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.identification.util.ExifDateTakenReader;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExifDateTakenReader - Unit Tests")
class ExifDateTakenReaderTest {

  @Test
  @DisplayName("reads DateTimeOriginal as UTC when no offset is present")
  void shouldReadDateTimeOriginal() {
    byte[] jpeg = jpegWithExif(0x9003, "2021:03:04 05:06:07");

    assertThat(ExifDateTakenReader.read(jpeg)).contains(Instant.parse("2021-03-04T05:06:07Z"));
  }

  @Test
  @DisplayName("falls back to DateTimeDigitized when DateTimeOriginal is absent")
  void shouldFallBackToDigitized() {
    byte[] jpeg = jpegWithExif(0x9004, "2020:01:02 03:04:05");

    assertThat(ExifDateTakenReader.read(jpeg)).contains(Instant.parse("2020-01-02T03:04:05Z"));
  }

  @Test
  @DisplayName("returns empty for a JPEG without EXIF")
  void shouldReturnEmptyWithoutExif() {
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};

    assertThat(ExifDateTakenReader.read(jpeg)).isEmpty();
  }

  @Test
  @DisplayName("returns empty for a PNG without EXIF")
  void shouldReturnEmptyForPng() {
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    assertThat(ExifDateTakenReader.read(png)).isEmpty();
  }

  @Test
  @DisplayName("returns empty for garbage, empty and null input")
  void shouldReturnEmptyForGarbage() {
    assertThat(ExifDateTakenReader.read("not an image".getBytes(StandardCharsets.UTF_8))).isEmpty();
    assertThat(ExifDateTakenReader.read(new byte[0])).isEmpty();
    assertThat(ExifDateTakenReader.read(null)).isEmpty();
  }

  @Test
  @DisplayName("returns empty for a zero or invalid date string")
  void shouldReturnEmptyForInvalidDate() {
    assertThat(ExifDateTakenReader.read(jpegWithExif(0x9003, "0000:00:00 00:00:00"))).isEmpty();
    assertThat(ExifDateTakenReader.read(jpegWithExif(0x9003, "2021:13:45 99:99:99"))).isEmpty();
  }

  private static byte[] jpegWithExif(int tag, String date) {
    ByteBuffer tiff = ByteBuffer.allocate(64);
    tiff.put(new byte[] {'M', 'M', 0x00, 0x2A}).putInt(8);
    tiff.putShort((short) 1).putShort((short) 0x8769).putShort((short) 4).putInt(1).putInt(26);
    tiff.putInt(0);
    tiff.putShort((short) 1).putShort((short) tag).putShort((short) 2).putInt(20).putInt(44);
    tiff.putInt(0);
    tiff.put((date + "\0").getBytes(StandardCharsets.US_ASCII));
    byte[] tiffBytes = new byte[tiff.position()];
    tiff.flip().get(tiffBytes);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(0xFF);
    out.write(0xD8);
    out.write(0xFF);
    out.write(0xE1);
    int length = 2 + 6 + tiffBytes.length;
    out.write(length >> 8);
    out.write(length & 0xFF);
    out.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(tiffBytes);
    out.write(0xFF);
    out.write(0xD9);
    return out.toByteArray();
  }
}
