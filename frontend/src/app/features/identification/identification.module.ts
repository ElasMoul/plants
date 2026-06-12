import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { IdentificationRoutingModule } from './identification-routing.module';
import { IdentificationHomeComponent } from './identification-home/identification-home.component';

@NgModule({
  declarations: [IdentificationHomeComponent],
  imports: [SharedModule, IdentificationRoutingModule],
})
export class IdentificationModule {}
