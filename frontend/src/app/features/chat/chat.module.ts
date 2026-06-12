import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { ChatRoutingModule } from './chat-routing.module';
import { ChatHomeComponent } from './chat-home/chat-home.component';

@NgModule({
  declarations: [ChatHomeComponent],
  imports: [SharedModule, ChatRoutingModule],
})
export class ChatModule {}
