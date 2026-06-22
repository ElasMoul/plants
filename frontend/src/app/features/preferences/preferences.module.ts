import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { PreferencesRoutingModule } from './preferences-routing.module';
import { PreferencesPageComponent } from './pages/preferences-page/preferences-page.component';

@NgModule({
  declarations: [PreferencesPageComponent],
  imports: [SharedModule, PreferencesRoutingModule],
})
export class PreferencesModule {}
