import { translate } from '../../../../shared/i18n/language.service';
import { INSPECT_BYTES, PhotoInfo, inspectPhoto } from './photo-inspect';

export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
export const MAX_RAW_BYTES = 30 * 1024 * 1024;
export const MAX_PIXELS = 12_000_000;
export const LONG_EDGE_CAP = 2048;
export const JPEG_QUALITY = 0.85;

export type PhotoRejectionReason = 'unsupported' | 'heic-undecodable' | 'too-large' | 'unreadable';

export class PhotoRejection extends Error {
  constructor(readonly reason: PhotoRejectionReason) {
    super(reason);
  }
}

export interface DecodedPhoto { width: number; height: number; source: unknown; }

/** Browser-dependent steps, injectable so jest (no canvas) can exercise the rest. */
export interface PhotoCodec {
  decode(file: File): Promise<DecodedPhoto>;
  encode(photo: DecodedPhoto, width: number, height: number, quality: number): Promise<Blob>;
}

export const browserCodec: PhotoCodec = {
  async decode(file) {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    return { width: bitmap.width, height: bitmap.height, source: bitmap };
  },
  async encode(photo, width, height, quality) {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('no 2d context');
    ctx.drawImage(photo.source as CanvasImageSource, 0, 0, width, height);
    (photo.source as ImageBitmap).close?.();
    return new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(b => (b ? resolve(b) : reject(new Error('encode failed'))), 'image/jpeg', quality));
  },
};

export function readLeadingBytes(file: Blob): Promise<Uint8Array> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(file.slice(0, INSPECT_BYTES));
  });
}

/**
 * Returns the file untouched when it is a normal JPEG/PNG/WebP within the limits (keeps EXIF);
 * otherwise (HEIC, above 12 MP, above 10 MB) re-encodes it as an upright, downscaled JPEG.
 */
export async function normalizePhoto(file: File, codec: PhotoCodec = browserCodec): Promise<File> {
  let info: PhotoInfo | null;
  try {
    info = inspectPhoto(await readLeadingBytes(file));
  } catch {
    throw new PhotoRejection('unreadable');
  }
  if (!info) throw new PhotoRejection('unsupported');
  if (file.size > MAX_RAW_BYTES) throw new PhotoRejection('too-large');

  const pixels = (info.width ?? 0) * (info.height ?? 0);
  const isHeic = info.kind === 'heic';
  if (!isHeic && pixels <= MAX_PIXELS && file.size <= MAX_UPLOAD_BYTES) return file;

  let blob: Blob;
  try {
    const decoded = await codec.decode(file);
    const scale = Math.min(1, LONG_EDGE_CAP / Math.max(decoded.width, decoded.height));
    blob = await codec.encode(
      decoded,
      Math.max(1, Math.round(decoded.width * scale)),
      Math.max(1, Math.round(decoded.height * scale)),
      JPEG_QUALITY,
    );
  } catch {
    throw new PhotoRejection(isHeic ? 'heic-undecodable' : 'unreadable');
  }
  if (blob.size > MAX_UPLOAD_BYTES) throw new PhotoRejection('too-large');
  return new File([blob], file.name.replace(/\.[^./\\]+$/, '') + '.jpg', { type: 'image/jpeg', lastModified: file.lastModified });
}

export function rejectionMessage(reason: PhotoRejectionReason, name: string): string {
  switch (reason) {
    case 'unsupported':
      return translate('"{0}": unsupported format — use a JPEG, PNG, WebP or iPhone (HEIC) photo', [name]);
    case 'heic-undecodable':
      return translate('"{0}": this browser cannot open HEIC photos — pick it from the iPhone photo library or camera, or export it as JPEG', [name]);
    case 'too-large':
      return translate('"{0}": file too large — the photo must be under 10 MB (30 MB before resizing)', [name]);
    default:
      return translate('"{0}": this image could not be read', [name]);
  }
}
