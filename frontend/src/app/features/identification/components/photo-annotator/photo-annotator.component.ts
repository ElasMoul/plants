import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { AnnotationRegion, AnnotationRegionType } from '../../models/identification.model';

const REGION_COLORS: Record<AnnotationRegionType, { fill: string; stroke: string }> = {
  PLANT:        { fill: 'rgba(33, 150, 243, 0.2)',  stroke: '#1565C0' },
  DISEASE:      { fill: 'rgba(244, 67, 54, 0.2)',   stroke: '#c62828' },
  HEALTHY_AREA: { fill: 'rgba(76, 175, 80, 0.2)',   stroke: '#2e7d32' },
};

@Component({
  selector: 'app-photo-annotator',
  templateUrl: './photo-annotator.component.html',
  styleUrls: ['./photo-annotator.component.scss'],
})
export class PhotoAnnotatorComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() imageUrl: string = '';
  @Input() regions: AnnotationRegion[] | null = null;

  @ViewChild('photoImg', { static: false })
  private readonly imgRef!: ElementRef<HTMLImageElement>;

  @ViewChild('annotationCanvas', { static: false })
  private readonly canvasRef?: ElementRef<HTMLCanvasElement>;

  showAnnotations = true;

  private resizeObserver?: ResizeObserver;

  get hasRegions(): boolean {
    return !!this.regions && this.regions.length > 0;
  }

  ngAfterViewInit(): void {
    if (this.imgRef.nativeElement.complete && this.hasRegions) {
      this.drawAnnotations();
    }
    if (this.hasRegions && typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.drawAnnotations());
      this.resizeObserver.observe(this.imgRef.nativeElement);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['regions'] && this.imgRef?.nativeElement?.complete) {
      setTimeout(() => this.drawAnnotations(), 0);
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  onImageLoad(): void {
    if (this.hasRegions) {
      this.drawAnnotations();
    }
  }

  toggleAnnotations(): void {
    this.showAnnotations = !this.showAnnotations;
    if (this.showAnnotations) {
      setTimeout(() => this.drawAnnotations(), 0);
    }
  }

  private drawAnnotations(): void {
    const canvas = this.canvasRef?.nativeElement;
    const img = this.imgRef?.nativeElement;
    if (!canvas || !img || !this.regions?.length) return;

    const w = img.clientWidth;
    const h = img.clientHeight;
    if (!w || !h) return;

    canvas.width = w;
    canvas.height = h;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, w, h);

    for (const region of this.regions) {
      const bb = region.boundingBox;
      const rx = (bb.xPct / 100) * w;
      const ry = (bb.yPct / 100) * h;
      const rw = (bb.widthPct / 100) * w;
      const rh = (bb.heightPct / 100) * h;
      const colors = REGION_COLORS[region.type];

      // Semi-transparent fill
      ctx.fillStyle = colors.fill;
      ctx.fillRect(rx, ry, rw, rh);

      // Solid stroke
      ctx.strokeStyle = colors.stroke;
      ctx.lineWidth = 2;
      ctx.strokeRect(rx, ry, rw, rh);

      // Label pill above the box
      const label = region.label || region.type.replace(/_/g, ' ');
      ctx.font = 'bold 12px system-ui, sans-serif';
      const textW = ctx.measureText(label).width;
      const pillH = 20;
      const pad = 6;
      const pillY = Math.max(ry - pillH, 0);

      ctx.fillStyle = colors.stroke;
      ctx.fillRect(rx, pillY, textW + pad * 2, pillH);

      ctx.fillStyle = '#ffffff';
      ctx.fillText(label, rx + pad, pillY + pillH - 5);
    }
  }
}
