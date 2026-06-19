import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatBadgeModule } from '@angular/material/badge';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';

import { ModelSelectorComponent } from './components/model-selector/model-selector.component';
import { MermaidDiagramComponent } from './components/mermaid-diagram/mermaid-diagram.component';

const MATERIAL_MODULES = [
  MatButtonModule,
  MatCardModule,
  MatCheckboxModule,
  MatFormFieldModule,
  MatInputModule,
  MatSelectModule,
  MatIconModule,
  MatProgressSpinnerModule,
  MatSnackBarModule,
  MatChipsModule,
  MatBadgeModule,
  MatTooltipModule,
  MatDividerModule,
];

@NgModule({
  declarations: [ModelSelectorComponent, MermaidDiagramComponent],
  imports: [CommonModule, RouterModule, FormsModule, ReactiveFormsModule, ...MATERIAL_MODULES],
  exports: [
    CommonModule,
    RouterModule,
    FormsModule,
    ReactiveFormsModule,
    ...MATERIAL_MODULES,
    ModelSelectorComponent,
    MermaidDiagramComponent,
  ],
})
export class SharedModule {}
