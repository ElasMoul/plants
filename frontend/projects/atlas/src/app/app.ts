import { Component, signal } from '@angular/core';

@Component({
  selector: 'rz-root',
  imports: [],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  readonly title = signal('atlas');
}
