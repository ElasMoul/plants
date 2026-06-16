import { Component } from '@angular/core';

interface MockReminder {
  id: number;
  plantName: string;
  species: string;
  careType: 'WATERING' | 'FERTILIZING' | 'REPOTTING' | 'PRUNING';
  icon: string;
  group: 'today' | 'tomorrow' | 'nextWeek';
  healthStatus?: 'HEALTHY' | 'ISSUES_DETECTED';
  done: boolean;
}

@Component({
  selector: 'app-reminder-list',
  templateUrl: './reminder-list.component.html',
  styleUrls: ['./reminder-list.component.scss'],
})
export class ReminderListComponent {
  readonly reminders: MockReminder[] = [
    { id: 1, plantName: 'Monty', species: 'Monstera deliciosa', careType: 'WATERING', icon: 'water_drop', group: 'today', healthStatus: 'ISSUES_DETECTED', done: false },
    { id: 2, plantName: 'Fernando', species: 'Boston fern', careType: 'FERTILIZING', icon: 'grass', group: 'today', healthStatus: 'HEALTHY', done: false },
    { id: 3, plantName: 'Spike', species: 'Snake plant', careType: 'WATERING', icon: 'water_drop', group: 'tomorrow', healthStatus: 'HEALTHY', done: false },
    { id: 4, plantName: 'Petra', species: 'Fiddle leaf fig', careType: 'PRUNING', icon: 'content_cut', group: 'tomorrow', healthStatus: 'HEALTHY', done: false },
    { id: 5, plantName: 'Basil Buddy', species: 'Sweet basil', careType: 'WATERING', icon: 'water_drop', group: 'nextWeek', healthStatus: 'HEALTHY', done: false },
    { id: 6, plantName: 'Rosie', species: 'Rose bush', careType: 'REPOTTING', icon: 'eco', group: 'nextWeek', healthStatus: 'HEALTHY', done: false },
  ];

  get todayReminders(): MockReminder[] {
    return this.reminders.filter((r) => r.group === 'today');
  }

  get tomorrowReminders(): MockReminder[] {
    return this.reminders.filter((r) => r.group === 'tomorrow');
  }

  get nextWeekReminders(): MockReminder[] {
    return this.reminders.filter((r) => r.group === 'nextWeek');
  }

  toggleDone(reminder: MockReminder): void {
    reminder.done = !reminder.done;
  }
}
