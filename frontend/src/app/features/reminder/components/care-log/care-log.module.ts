import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { CareLogComponent } from './care-log.component';

@NgModule({
  declarations: [CareLogComponent],
  imports: [CommonModule, MatIconModule],
  exports: [CareLogComponent],
})
export class CareLogModule {}
