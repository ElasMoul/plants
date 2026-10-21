import { ChangeDetectorRef } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { PlantGroupService } from '../../services/plant-group.service';
import { GroupManagerDialogComponent } from './group-manager-dialog.component';

describe('GroupManagerDialogComponent (PLA-110)', () => {
  const group = { id: 3, name: 'Greenhouse', plantCount: 2, createdAt: '' };

  function build() {
    const service = {
      getGroups: jest.fn(() => of({ data: { content: [group] } })),
      createGroup: jest.fn(() => of({ data: group })),
      renameGroup: jest.fn(() => of({ data: group })),
      deleteGroup: jest.fn(() => of(undefined)),
      logCare: jest.fn(() => of({ data: [{ id: 1 }, { id: 2 }] })),
    };
    const snack = { open: jest.fn() };
    const ref = { close: jest.fn() };
    const component = new GroupManagerDialogComponent(
      service as unknown as PlantGroupService,
      snack as unknown as MatSnackBar,
      ref as unknown as MatDialogRef<GroupManagerDialogComponent, boolean>,
      { markForCheck: jest.fn() } as unknown as ChangeDetectorRef,
    );
    component.ngOnInit();
    return { component, service, snack, ref };
  }

  it('loads the groups', () => {
    expect(build().component.groups).toEqual([group]);
  });

  it('creates a trimmed group and ignores a blank name', () => {
    const { component, service } = build();
    component.newName = '   ';
    component.create();
    expect(service.createGroup).not.toHaveBeenCalled();
    component.newName = ' Bed 1 ';
    component.create();
    expect(service.createGroup).toHaveBeenCalledWith('Bed 1');
    expect(component.newName).toBe('');
    expect(component.changed).toBe(true);
  });

  it('renames a group', () => {
    const { component, service } = build();
    component.startEdit(group);
    component.editName = 'Kitchen';
    component.saveEdit(group);
    expect(service.renameGroup).toHaveBeenCalledWith(3, 'Kitchen');
    expect(component.editingId).toBeNull();
  });

  it('deletes only after confirmation', () => {
    const { component, service } = build();
    const confirm = jest.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    component.remove(group);
    expect(service.deleteGroup).not.toHaveBeenCalled();
    component.remove(group);
    expect(service.deleteGroup).toHaveBeenCalledWith(3);
    confirm.mockRestore();
  });

  it('calls the bulk care action and reports the number of plants', () => {
    const { component, service, snack } = build();
    component.care(group, 'WATERED');
    component.care(group, 'FED');
    expect(service.logCare).toHaveBeenCalledWith(3, 'WATERED');
    expect(service.logCare).toHaveBeenCalledWith(3, 'FED');
    expect(snack.open).toHaveBeenCalledWith('Care logged for 2 plant(s).', undefined, { duration: 3000 });
  });

  it('tells the opener whether anything changed', () => {
    const { component, ref } = build();
    component.close();
    expect(ref.close).toHaveBeenCalledWith(false);
  });
});