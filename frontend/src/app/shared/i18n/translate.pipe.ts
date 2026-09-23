import { Pipe, PipeTransform } from '@angular/core';
import { pluralSuffix, translate } from './language.service';

@Pipe({ name: 't', standalone: true })
export class TranslatePipe implements PipeTransform {
  transform(message: string | null | undefined, values: readonly unknown[] = []): string {
    return translate(message ?? '', values);
  }
}

@Pipe({ name: 'pluralSuffix', standalone: true })
export class PluralSuffixPipe implements PipeTransform {
  transform(count: number): string { return pluralSuffix(count); }
}
