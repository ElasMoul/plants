import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PlantService } from '../../services/plant.service';
import { CreatePlantRequest, UpdatePlantRequest } from '../../models/plant.model';

@Component({
    selector: 'app-plant-form',
    templateUrl: './plant-form.component.html',
    styleUrls: ['./plant-form.component.scss'],
    standalone: false
})
export class PlantFormComponent implements OnInit {
  form!: FormGroup;
  editId: number | null = null;
  loading = false;
  submitting = false;

  get isEdit(): boolean {
    return this.editId !== null;
  }

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly plantService: PlantService,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.buildForm();

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId = Number(id);
      this.loadForEdit(this.editId);
    } else {
      this.prefillFromQueryParams();
    }
  }

  private buildForm(): void {
    this.form = this.fb.group({
      nickname: ['', [Validators.required, Validators.maxLength(255)]],
      species: [''],
      commonName: [''],
      location: [''],
      notes: [''],
      acquiredAt: [null],
    });
  }

  private prefillFromQueryParams(): void {
    const p = this.route.snapshot.queryParams;
    if (p['nickname'] || p['species'] || p['commonName']) {
      this.form.patchValue({
        nickname:   p['nickname']   || p['commonName'] || '',
        species:    p['species']    || '',
        commonName: p['commonName'] || '',
        location:   p['location']   || '',
      });
    }
  }

  private loadForEdit(id: number): void {
    this.loading = true;
    this.plantService.getPlant(id).subscribe({
      next: (res) => {
        const p = res.data;
        this.form.patchValue({
          nickname: p.nickname,
          species: p.species ?? '',
          commonName: p.commonName ?? '',
          location: p.location ?? '',
          notes: p.notes ?? '',
          acquiredAt: p.acquiredAt ? new Date(p.acquiredAt + 'T00:00:00') : null,
        });
        this.loading = false;
      },
      error: () => {
        this.snackBar.open('Could not load plant data.', 'Dismiss', { duration: 4000 });
        this.router.navigate(['/garden']);
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.submitting) return;

    this.submitting = true;
    const raw = this.form.value;
    const acquiredAt = raw.acquiredAt ? this.toDateString(raw.acquiredAt) : undefined;

    if (this.isEdit) {
      const request: UpdatePlantRequest = {
        nickname: raw.nickname,
        species: raw.species || undefined,
        commonName: raw.commonName || undefined,
        location: raw.location || undefined,
        notes: raw.notes || undefined,
        acquiredAt,
      };
      this.plantService.updatePlant(this.editId ?? 0, request).subscribe({
        next: () => {
          this.snackBar.open('Plant updated.', undefined, { duration: 3000 });
          this.router.navigate(['/plants', this.editId], { replaceUrl: true });
        },
        error: () => {
          this.snackBar.open('Could not update plant.', 'Dismiss', { duration: 4000 });
          this.submitting = false;
        },
      });
    } else {
      const request: CreatePlantRequest = {
        nickname: raw.nickname,
        species: raw.species || undefined,
        commonName: raw.commonName || undefined,
        location: raw.location || undefined,
        notes: raw.notes || undefined,
        acquiredAt,
      };
      this.plantService.createPlant(request).subscribe({
        next: (res) => {
          this.snackBar.open('Plant added!', undefined, { duration: 3000 });
          this.router.navigate(['/plants', res.data.id], { replaceUrl: true });
        },
        error: () => {
          this.snackBar.open('Could not save plant.', 'Dismiss', { duration: 4000 });
          this.submitting = false;
        },
      });
    }
  }

  onCancel(): void {
    this.router.navigate(this.isEdit ? ['/plants', this.editId] : ['/garden']);
  }

  private toDateString(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
