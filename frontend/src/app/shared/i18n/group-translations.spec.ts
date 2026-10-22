import { FR } from './fr';

describe('plant group translations (PLA-110)', () => {
  const keys = [
    'Manage groups', 'New group name', 'Add group',
    'No groups yet. Create one for a room, bed or greenhouse.', 'Watered', 'Fed',
    'Rename group', 'Delete group', 'Save', 'Delete this group? Its plants are kept.',
    'Care logged for {0} plant(s).', 'Could not load groups.', 'Could not save the group.',
    'Could not delete the group.', 'Could not log care for the group.', 'Group', 'All plants',
    'No group', 'Move to group', 'Plant moved.', 'Could not move the plant.', 'Cancel', 'Close',
  ];

  it.each(keys)('has non-empty French text for "%s"', (key) => {
    expect(FR[key]?.trim()).toBeTruthy();
  });
});