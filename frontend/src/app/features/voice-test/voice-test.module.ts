import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { VoiceTestRoutingModule } from './voice-test-routing.module';
import { VoiceTestComponent } from './voice-test.component';

@NgModule({
  declarations: [VoiceTestComponent],
  imports: [SharedModule, VoiceTestRoutingModule],
})
export class VoiceTestModule {}
