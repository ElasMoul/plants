import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { World } from './world/world';

@Component({
  selector: 'rz-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [World],
  template: '<rz-world />',
})
export class App {
  readonly title = signal('atlas');
}
