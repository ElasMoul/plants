import { HealthStatus } from '../../features/plant/models/plant.model';

export function healthBadgeClass(status: HealthStatus | null | undefined): string {
  switch (status) {
    case 'HEALTHY':
      return 'badge-healthy';
    case 'ISSUES_DETECTED':
      return 'badge-issues';
    case 'UNKNOWN':
      return 'badge-unknown';
    default:
      return '';
  }
}
