import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { CareLogComponent } from './care-log.component';
import { CareLogDetailDialogComponent } from './care-log-detail-dialog.component';

@NgModule({
  declarations: [CareLogComponent, CareLogDetailDialogComponent],
  imports: [CommonModule, MatIconModule, MatButtonModule, MatDialogModule],
  exports: [CareLogComponent],
})
export class CareLogModule {}
