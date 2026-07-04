import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { PreferencesRoutingModule } from './preferences-routing.module';
import { PreferencesPageComponent } from './pages/preferences-page/preferences-page.component';
import { PlantNetConfigService } from './services/plantnet-config.service';

@NgModule({
  declarations: [PreferencesPageComponent],
  imports: [SharedModule, PreferencesRoutingModule],
  providers: [PlantNetConfigService],
})
export class PreferencesModule {}
