export type PhotoKind = 'jpeg' | 'png' | 'webp' | 'heic';

export interface PhotoInfo {
  kind: PhotoKind;
  width?: number;
  height?: number;
  /** EXIF orientation (1-8) for JPEG, when present. */
  orientation?: number;
}

/** Bytes read from the start of a file; enough for the JPEG EXIF block and frame header. */
export const INSPECT_BYTES = 262144;

const HEIC_BRANDS = ['heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'mif1', 'msf1', 'heif'];

const ascii = (b: Uint8Array, at: number, len: number): string =>
  String.fromCharCode(...Array.from(b.subarray(at, at + len)));

/** Detects the real format from magic bytes (file.type is empty for HEIC on many platforms). */
export function inspectPhoto(b: Uint8Array): PhotoInfo | null {
  if (b.length >= 3 && b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return inspectJpeg(b);
  if (b.length >= 24 && ascii(b, 1, 3) === 'PNG' && b[0] === 0x89) {
    const v = new DataView(b.buffer, b.byteOffset, b.byteLength);
    return { kind: 'png', width: v.getUint32(16), height: v.getUint32(20) };
  }
  if (b.length >= 16 && ascii(b, 0, 4) === 'RIFF' && ascii(b, 8, 4) === 'WEBP') return inspectWebp(b);
  if (b.length >= 12 && ascii(b, 4, 4) === 'ftyp' && HEIC_BRANDS.includes(ascii(b, 8, 4))) return { kind: 'heic' };
  return null;
}

function inspectJpeg(b: Uint8Array): PhotoInfo {
  const info: PhotoInfo = { kind: 'jpeg' };
  const v = new DataView(b.buffer, b.byteOffset, b.byteLength);
  let pos = 2;
  while (pos + 4 <= b.length && b[pos] === 0xff) {
    const marker = b[pos + 1];
    if (marker === 0xff) { pos++; continue; }
    if (marker === 0xd8 || marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) { pos += 2; continue; }
    if (marker === 0xda || marker === 0xd9) break;
    const len = v.getUint16(pos + 2);
    if (marker === 0xe1 && ascii(b, pos + 4, 4) === 'Exif') info.orientation = exifOrientation(b, pos + 10, pos + 2 + len);
    const isSof = marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
    if (isSof && pos + 9 <= b.length) {
      info.height = v.getUint16(pos + 5);
      info.width = v.getUint16(pos + 7);
      break;
    }
    pos += 2 + len;
  }
  return info;
}

function exifOrientation(b: Uint8Array, tiff: number, end: number): number | undefined {
  if (tiff + 8 > b.length) return undefined;
  const v = new DataView(b.buffer, b.byteOffset, b.byteLength);
  const little = b[tiff] === 0x49;
  const ifd = tiff + v.getUint32(tiff + 4, little);
  if (ifd + 2 > Math.min(end, b.length)) return undefined;
  const count = v.getUint16(ifd, little);
  for (let i = 0; i < count; i++) {
    const entry = ifd + 2 + i * 12;
    if (entry + 12 > b.length) return undefined;
    if (v.getUint16(entry, little) === 0x0112) return v.getUint16(entry + 8, little);
  }
  return undefined;
}

function inspectWebp(b: Uint8Array): PhotoInfo {
  const info: PhotoInfo = { kind: 'webp' };
  const v = new DataView(b.buffer, b.byteOffset, b.byteLength);
  const chunk = ascii(b, 12, 4);
  if (chunk === 'VP8X' && b.length >= 30) {
    info.width = 1 + (b[24] | (b[25] << 8) | (b[26] << 16));
    info.height = 1 + (b[27] | (b[28] << 8) | (b[29] << 16));
  } else if (chunk === 'VP8 ' && b.length >= 30) {
    info.width = v.getUint16(26, true) & 0x3fff;
    info.height = v.getUint16(28, true) & 0x3fff;
  } else if (chunk === 'VP8L' && b.length >= 25) {
    const bits = v.getUint32(21, true);
    info.width = (bits & 0x3fff) + 1;
    info.height = ((bits >> 14) & 0x3fff) + 1;
  }
  return info;
}
