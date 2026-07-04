import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { VoiceTestComponent } from './voice-test.component';

const routes: Routes = [{ path: '', component: VoiceTestComponent }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class VoiceTestRoutingModule {}
