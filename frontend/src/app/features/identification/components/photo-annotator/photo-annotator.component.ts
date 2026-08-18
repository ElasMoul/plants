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
import {
  AnnotationRegion,
  AnnotationRegionType,
  PolygonPoint,
} from '../../models/identification.model';

const REGION_COLORS: Record<AnnotationRegionType, { fill: string; stroke: string }> = {
  PLANT:        { fill: 'rgba(33, 150, 243, 0.2)',  stroke: '#1565C0' },
  DISEASE:      { fill: 'rgba(244, 67, 54, 0.2)',   stroke: '#c62828' },
  HEALTHY_AREA: { fill: 'rgba(76, 175, 80, 0.2)',   stroke: '#2e7d32' },
};

@Component({
    selector: 'app-photo-annotator',
    templateUrl: './photo-annotator.component.html',
    styleUrls: ['./photo-annotator.component.scss'],
    standalone: false
})
export class PhotoAnnotatorComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() imageUrl = '';
  @Input() regions: AnnotationRegion[] | null = null;
  @Input() selectedRegionIndex: number | null = null;

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
    const img = this.imgRef?.nativeElement;
    if (!img?.complete) return;
    if (changes['regions'] || changes['selectedRegionIndex']) {
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

    for (let idx = 0; idx < this.regions.length; idx++) {
      // Regions overlap heavily — once one is selected, only draw that one
      // instead of dimming the rest (dimmed shapes still visually competed).
      if (this.selectedRegionIndex !== null && this.selectedRegionIndex !== idx) {
        continue;
      }
      const region = this.regions[idx];
      const colors = REGION_COLORS[region.type];
      const lineWidth = this.selectedRegionIndex !== null ? 3 : 2;
      const label = region.label || region.type.replace(/_/g, ' ');

      if (region.polygon && region.polygon.length >= 3) {
        const pts = region.polygon.map(p => ({
          x: (p.xPct / 100) * w,
          y: (p.yPct / 100) * h,
        }));
        this.drawPolygon(ctx, region.polygon, w, h, colors, lineWidth);
        const cx = pts.reduce((s, p) => s + p.x, 0) / pts.length;
        const cy = pts.reduce((s, p) => s + p.y, 0) / pts.length;
        this.drawLabel(ctx, label, cx, cy - 10, colors.stroke);

      } else if (region.boundingBox) {
        const bb = region.boundingBox;
        const rx = (bb.xPct / 100) * w;
        const ry = (bb.yPct / 100) * h;
        const rw = (bb.widthPct / 100) * w;
        const rh = (bb.heightPct / 100) * h;

        ctx.fillStyle = colors.fill;
        ctx.fillRect(rx, ry, rw, rh);
        ctx.strokeStyle = colors.stroke;
        ctx.lineWidth = lineWidth;
        ctx.strokeRect(rx, ry, rw, rh);

        this.drawLabel(ctx, label, rx, Math.max(ry - 20, 0), colors.stroke);
      }
    }
  }

  private drawPolygon(
    ctx: CanvasRenderingContext2D,
    polygon: PolygonPoint[],
    w: number,
    h: number,
    colors: { fill: string; stroke: string },
    lineWidth: number,
  ): void {
    const points = polygon.map(p => ({ x: (p.xPct / 100) * w, y: (p.yPct / 100) * h }));
    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length; i++) {
      ctx.lineTo(points[i].x, points[i].y);
    }
    ctx.closePath();
    ctx.fillStyle = colors.fill;
    ctx.fill();
    ctx.strokeStyle = colors.stroke;
    ctx.lineWidth = lineWidth;
    ctx.stroke();
  }

  private drawLabel(
    ctx: CanvasRenderingContext2D,
    text: string,
    x: number,
    y: number,
    color: string,
  ): void {
    ctx.font = 'bold 12px system-ui, sans-serif';
    const textW = ctx.measureText(text).width;
    const pad = 6;
    const pillH = 20;
    ctx.fillStyle = color;
    ctx.fillRect(x, y, textW + pad * 2, pillH);
    ctx.fillStyle = '#ffffff';
    ctx.fillText(text, x + pad, y + pillH - 5);
  }
}
