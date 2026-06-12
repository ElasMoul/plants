import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ChatHomeComponent } from './chat-home/chat-home.component';

const routes: Routes = [
  { path: '', component: ChatHomeComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ChatRoutingModule {}
