import { MatPaginatorIntl } from '@angular/material/paginator';
import { translate } from './language.service';

export function localizedPaginator(): MatPaginatorIntl {
  const labels = new MatPaginatorIntl();
  labels.itemsPerPageLabel = translate('Items per page');
  labels.nextPageLabel = translate('Next page');
  labels.previousPageLabel = translate('Previous page');
  labels.firstPageLabel = translate('First page');
  labels.lastPageLabel = translate('Last page');
  labels.getRangeLabel = (page, size, length) => {
    const start = length === 0 || size === 0 ? 0 : page * size + 1;
    const end = Math.min((page + 1) * size, length);
    return translate('{0}–{1} of {2}', [start, end, length]);
  };
  return labels;
}
