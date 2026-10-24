import { inspectPhoto } from './photo-inspect';
import {
  LONG_EDGE_CAP,
  MAX_RAW_BYTES,
  MAX_UPLOAD_BYTES,
  PhotoCodec,
  PhotoRejection,
  normalizePhoto,
  rejectionMessage,
} from './photo-normalizer';
import { PhotoUploadComponent } from './photo-upload.component';

/** Minimal JPEG: SOI, optional EXIF orientation, SOF0 with the declared size, padding. */
function jpegBytes(width: number, height: number, orientation?: number, padTo = 0): Uint8Array {
  const parts: number[] = [0xff, 0xd8];
  if (orientation !== undefined) {
    // APP1 "Exif\0\0" + little-endian TIFF with a single IFD0 entry (0x0112)
    const tiff = [0x49, 0x49, 0x2a, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, orientation, 0, 0, 0, 0, 0, 0, 0];
    const body = [0x45, 0x78, 0x69, 0x66, 0, 0, ...tiff];
    parts.push(0xff, 0xe1, (body.length + 2) >> 8, (body.length + 2) & 0xff, ...body);
  }
  parts.push(0xff, 0xc0, 0, 11, 8, height >> 8, height & 0xff, width >> 8, width & 0xff, 1, 1, 0x11, 0);
  parts.push(0xff, 0xda, 0, 2, 0xff, 0xd9);
  const out = new Uint8Array(Math.max(parts.length, padTo));
  out.set(parts);
  return out;
}

const heicBytes = (): Uint8Array =>
  Uint8Array.from([0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63, 0, 0, 0, 0, 0x6d, 0x69, 0x66, 0x31, 0x68, 0x65, 0x69, 0x63]);

const asFile = (bytes: Uint8Array, name: string, type = ''): File => new File([bytes as BlobPart], name, { type });

function fakeCodec(width: number, height: number, encodedSize = 1000): PhotoCodec & { encoded: { w: number; h: number }[] } {
  const encoded: { w: number; h: number }[] = [];
  return {
    encoded,
    decode: async () => ({ width, height, source: {} }),
    encode: async (_p, w, h) => {
      encoded.push({ w, h });
      return new Blob([new Uint8Array(encodedSize)], { type: 'image/jpeg' });
    },
  };
}

const failingCodec: PhotoCodec = {
  decode: () => Promise.reject(new Error('cannot decode')),
  encode: () => Promise.reject(new Error('unused')),
};

async function reasonOf(promise: Promise<File>): Promise<string> {
  try {
    await promise;
  } catch (e) {
    return (e as PhotoRejection).reason;
  }
  return 'none';
}

describe('inspectPhoto', () => {
  it('detects HEIC by its ftyp brand', () => {
    expect(inspectPhoto(heicBytes())?.kind).toBe('heic');
  });

  it('reads JPEG dimensions and EXIF orientation 6', () => {
    expect(inspectPhoto(jpegBytes(3024, 4032, 6))).toEqual({ kind: 'jpeg', width: 3024, height: 4032, orientation: 6 });
  });

  it('returns null for GIF and PDF', () => {
    expect(inspectPhoto(Uint8Array.from([0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]))).toBeNull();
    expect(inspectPhoto(new TextEncoder().encode('%PDF-1.7 .........'))).toBeNull();
  });
});

describe('normalizePhoto', () => {
  it('passes a normal JPEG through byte-identical', async () => {
    const file = asFile(jpegBytes(1600, 1200), 'a.jpg', 'image/jpeg');
    expect(await normalizePhoto(file, failingCodec)).toBe(file);
  });

  it('leaves an EXIF-rotated JPEG unmodified so orientation and date survive', async () => {
    const file = asFile(jpegBytes(4000, 3000, 6), 'rot.jpg', 'image/jpeg');
    expect(await normalizePhoto(file, failingCodec)).toBe(file);
  });

  it('converts HEIC with an empty file.type to a JPEG named .jpg', async () => {
    const out = await normalizePhoto(asFile(heicBytes(), 'IMG_0001.HEIC'), fakeCodec(4032, 3024));
    expect(out.type).toBe('image/jpeg');
    expect(out.name).toBe('IMG_0001.jpg');
  });

  it('rejects HEIC the browser cannot decode with the HEIC reason', async () => {
    expect(await reasonOf(normalizePhoto(asFile(heicBytes(), 'x.heic'), failingCodec))).toBe('heic-undecodable');
  });

  it('downscales a >12 MP JPEG to the long-edge cap as JPEG under 10 MB', async () => {
    const codec = fakeCodec(8000, 6000);
    const out = await normalizePhoto(asFile(jpegBytes(8000, 6000), 'big.jpg', 'image/jpeg'), codec);
    expect(out.type).toBe('image/jpeg');
    expect(out.size).toBeLessThanOrEqual(MAX_UPLOAD_BYTES);
    expect(codec.encoded[0]).toEqual({ w: LONG_EDGE_CAP, h: 1536 });
  });

  it('re-encodes a JPEG over 10 MB even when its pixel count is small', async () => {
    const big = asFile(jpegBytes(3000, 2000, undefined, MAX_UPLOAD_BYTES + 1), 'heavy.jpg', 'image/jpeg');
    const out = await normalizePhoto(big, fakeCodec(3000, 2000));
    expect(out.size).toBeLessThanOrEqual(MAX_UPLOAD_BYTES);
  });

  it('rejects unsupported types and files above the raw ceiling', async () => {
    const gif = asFile(Uint8Array.from([0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]), 'a.gif', 'image/gif');
    expect(await reasonOf(normalizePhoto(gif, failingCodec))).toBe('unsupported');
    const huge = asFile(jpegBytes(100, 100, undefined, MAX_RAW_BYTES + 1), 'h.jpg', 'image/jpeg');
    expect(await reasonOf(normalizePhoto(huge, failingCodec))).toBe('too-large');
  });

  it('rejects when the re-encoded output is still over 10 MB', async () => {
    const file = asFile(jpegBytes(8000, 6000), 'big.jpg', 'image/jpeg');
    expect(await reasonOf(normalizePhoto(file, fakeCodec(8000, 6000, MAX_UPLOAD_BYTES + 1)))).toBe('too-large');
  });
});

describe('rejection messages', () => {
  afterEach(() => localStorage.removeItem('plantpal.language'));

  for (const reason of ['unsupported', 'heic-undecodable', 'too-large', 'unreadable'] as const) {
    it(`has distinct EN and FR text for ${reason}`, () => {
      localStorage.setItem('plantpal.language', 'en');
      const en = rejectionMessage(reason, 'p.jpg');
      localStorage.setItem('plantpal.language', 'fr');
      const fr = rejectionMessage(reason, 'p.jpg');
      expect(en).toContain('p.jpg');
      expect(fr).toContain('p.jpg');
      expect(fr).not.toBe(en);
      expect(fr).not.toContain('{0}');
    });
  }
});

describe('PhotoUploadComponent file handling', () => {
  class Testable extends PhotoUploadComponent {
    codec: PhotoCodec = failingCodec;
    protected override normalize(file: File): Promise<File> {
      return normalizePhoto(file, this.codec);
    }
  }
  const make = (): Testable =>
    new Testable({} as never, {} as never, {} as never, { isSeen: () => true, track: () => undefined, markSeen: () => undefined } as never);
  const list = (...files: File[]): FileList => files as unknown as FileList;

  it('adds a converted HEIC and reports an unsupported file in the same drop', async () => {
    localStorage.setItem('plantpal.language', 'en');
    const c = make();
    c.codec = fakeCodec(4032, 3024);
    await (c as unknown as { processFiles(l: FileList): Promise<void> }).processFiles(
      list(asFile(heicBytes(), 'a.heic'), asFile(new TextEncoder().encode('%PDF-1.7 .........'), 'b.pdf', 'application/pdf')),
    );
    expect(c.entries).toHaveLength(1);
    expect(c.entries[0].file.type).toBe('image/jpeg');
    expect(c.validationErrors).toEqual([rejectionMessage('unsupported', 'b.pdf')]);
    localStorage.removeItem('plantpal.language');
  });
});
