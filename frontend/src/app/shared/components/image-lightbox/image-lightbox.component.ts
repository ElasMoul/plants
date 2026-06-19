import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface ImageLightboxData {
  imageUrl: string;
  alt?: string;
}

@Component({
  selector: 'app-image-lightbox',
  templateUrl: './image-lightbox.component.html',
  styleUrls: ['./image-lightbox.component.scss'],
})
export class ImageLightboxComponent {
  constructor(
    private readonly dialogRef: MatDialogRef<ImageLightboxComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: ImageLightboxData,
  ) {}

  close(): void {
    this.dialogRef.close();
  }
}
