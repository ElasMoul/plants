import {
  CareLogDto,
  DashboardDto,
  DEFAULT_ASSEMBLY_SETTINGS,
  IdentificationDto,
  PlantDto,
  ReminderDto,
  ReminderSummaryDto,
  SpeciesDto,
  TreatmentDto,
  TreatmentPlanDto,
  UserPreferencesDto,
  WorldSources,
  WorldUser,
} from '../world/world.dto';

/**
 * The mock garden's seed data. One dataset per scenario, every date an offset
 * from `now`, so the garden is always "today" and a fixed `now` makes the whole
 * thing deterministic (spec-asserted).
 *
 * Shapes mirror the backend wire format exactly: enums are strings, instants are
 * ISO-8601, and absent fields are ABSENT (Jackson non_null) — never null.
 */

export type MockScenario = 'garden' | 'day-zero' | 'outage';

export interface MockUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  status: 'ACTIVE' | 'INACTIVE';
}

/**
 * The mock garden's photographs, inline as base64 JPEGs (~1.4 kB each). A data
 * URI keeps the promise the mock makes: it renders with no backend and no
 * network at all, so a plate looks the way it will look against a real garden.
 * Drawn, not photographed — they are obviously specimens of nothing.
 */
const MOCK_PHOTOS = {
  fig: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDABMNDhEODBMRDxEVFBMXHTAfHRoaHToqLCMwRT1JR0Q9Q0FMVm1dTFFoUkFDX4JgaHF1e3x7SlyGkIV3j214e3b/2wBDARQVFR0ZHTgfHzh2T0NPdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnb/wAARCACAAGADASIAAhEBAxEB/8QAGgABAAMBAQEAAAAAAAAAAAAAAAECAwUEBv/EACwQAAIBAwMCBQQCAwAAAAAAAAABAgMRMQQSIVFhIjJBcbETM1KBNMFykdH/xAAYAQEBAQEBAAAAAAAAAAAAAAAAAQMCBP/EABwRAQEBAQEBAAMAAAAAAAAAAAABAhExAyFBUf/aAAwDAQACEQMRAD8A+XAAQAAAAAAAAAAAAAAAAAOhptKqa3TSc/g5t451qZYUtFOavN7F/tnojo6KXKcu7ZuDO6tYXdrB6Oi1ZRa7pmFTQyXNOW7s+Ge7OAJqwm9Rx2mnZqzQOlqNPGtG6sp+j/6c2ScZNPKdmaS9bZ1NAAOnYAAPVoaO6X1JYi+Pc90pKKu3ZFKNP6VKMOiLRhFc2u+ryY29ry6vajdJ+VftjZfzNy7ehYiUlHLIiNjivBL9PlDfbzq3fKJjJSV0SATTV07o8muo3X1VlcM9Lgr3V0+qJcd0HGTyrNiXlXN5euQCZRcZOLynYg3eoL0I760Fa/PKKG2j/kw/fwS+JfHSIc0nbL6IkGDyK2lLPhXbJKjGOFz1JK778QW74KqXBN3w+q4IvKOfEu2Qp4UvDLuWAhSTdk+ehIcU7XWARHN1kduolxZPlGJ6df8Aej/j/bPMbTx6s+QNdLJR1EG+tjIRbjJNZTui1bOx2CJNqySuyYtSimsNXQMXkV2N+d37LBbGBKSirt2RW8pPjwrq1yFWaTVmrortcfI+OjIu4Lxcrqi6aaundARGV3Zpp9CQCI5+uknXsvRJHnL1p76s5Xum+PYobzx6szkAAV09+hqKVNweY/B6Xe3Fr9zk05unNTjlHUp1I1YKUXx8GWpy9ef6Z5ekYJO75l1ZYrOahnPRZI2yn5+I/ijlwvnBVw5vHwv5I2ShzT5X4smM1LjlPowJje3iST7GWrqKnRa9ZcI1lJQi5SdkvU5deq61RyeML2Osztd4z2qAA1egAAAvSqzpSvB26rqUBE9dCjXoyu77Zeu5/wBm9T7cvZnILU5yTUVJpN8pM4uGd+f8dOh9qJnXq0YrxSvJfjk8VWctzjue3pfgzEyk+f7rSrXnV8z4WEZgHbWTgACqAAAAABMPPH3IBBar9xlQ22+QEn4gACqAAD//2Q==',
  monstera: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDABMNDhEODBMRDxEVFBMXHTAfHRoaHToqLCMwRT1JR0Q9Q0FMVm1dTFFoUkFDX4JgaHF1e3x7SlyGkIV3j214e3b/2wBDARQVFR0ZHTgfHzh2T0NPdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnb/wAARCACAAGADASIAAhEBAxEB/8QAGgABAAMBAQEAAAAAAAAAAAAAAAEDBAIFBv/EACwQAAIBAgUDAwMFAQAAAAAAAAABAgMRBBIhMTJBUWETcbEiUqEUM0JygZH/xAAYAQEAAwEAAAAAAAAAAAAAAAAAAQIDBP/EAB0RAQEAAgIDAQAAAAAAAAAAAAABAhEDMRIhQTL/2gAMAwEAAhEDEQA/APlgASgAAAAAAAAAAAAAAAAANOFw6napPj0XchFsk3VdHDzq6rSPdmmGDpx5Nyf/AA0JJKy0QK7c95LVX6Wj9n5ZTPBL+Ev8kawNomeU+vLnCVOWWaszk9SpTjUjaSv57HnVqTpTyvXqn3LS7bYZ+TgAEtAAAWUKXq1FHpuz0tEuiSM+ChlpOX3F7gm7tX9ylc/Jd1Ge/FN/Ayt8pf4tDoNpK7diGbnIlxbiLyXJXXdExmpbMkCFJS2aZXiKXq07LktUWOKbu1r3EU0rNt+4TLr3HlAtxUMlZ9palRo6pdzYAAl6lKOSlGNrNLUlyUd2SDNxubylssvlkqCTu9X3ZJDkk7bvsgDipboi0o8XddmM7XNW8nW4EKavZ3T8khpNWauErKyAyY+OkJW8NmQ3Y79lf2MJedOjj/IACWj1YyUoqS2auS3ZbN+xVhZZqEdbtaFpm5LNXTm0pcnZdkdJKKslYbHOZy4K/noEOjnJbWLy/A+qOvJfklSUtmBCk1pJW8rY6AAzY6S9OMerdzEaMbK9VRvpFfkzl506cJrEABK7RgqihUcXtL5Nx5J6GGrKrCzf1rcrYx5MfqzJfWbv46HQbUVduxxeU7W+mPfqyrF2RKKlvv3Rz6eW2R2fyTGeqU1ll8gTHMnaVn5E5KEHKWyJMOLrKpLJF3ivyyZNrY4+VUSk5Scnu3cgAu6gAACYycJKUXZogAbKOIhJr1dJLr0NSakrxaa7o8kmMpRd4tp+GV0yy45eno0udT3JqTpxVqjXsYZznGMWpSTa1s9yoaVnHv2uq4iUk4wbUPO5SAWbSSdAACQAAAAAAAFlThD2Kw23uwQiTUAASkAAH//Z',
  lemon: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDABMNDhEODBMRDxEVFBMXHTAfHRoaHToqLCMwRT1JR0Q9Q0FMVm1dTFFoUkFDX4JgaHF1e3x7SlyGkIV3j214e3b/2wBDARQVFR0ZHTgfHzh2T0NPdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnb/wAARCACAAGADASIAAhEBAxEB/8QAGgABAAMBAQEAAAAAAAAAAAAAAAECBQQDBv/EADUQAAIBAgQDBgQDCQAAAAAAAAABAgMRBBIhMSJRYQUTMkFxgTORsdFywfAUNEJDUmKh4fH/xAAYAQEBAQEBAAAAAAAAAAAAAAAAAQIEA//EAB0RAQEBAQEAAgMAAAAAAAAAAAABAhEDITESMkH/2gAMAwEAAhEDEQA/APlwAQAAAAAAAAAAAAAAAACYQlUmowV5PZE0qcq1SMIbvmbWHw0MPBKKvLzlbVnnvcyrjo9ltpOtO39sfudK7PwySTp36uTOkbHPd6v9VyVOzaElwJwfR3+pxYjAVaKclxwW7W69jYBc+moPnQaWOwUcrq0Uo2XFFaK3MzTpzqanYgADSAB6Yel31eFO9k3r6Et4NLs7D91S7x+Ka+SOuU4xdm9X5Bq6tdr0EYxiuFJHHb29rSt5y2WVddWO7i97t82y5WVSMXZvX6EEWnHaWZcpfcKotp8L6l1qroPVWYAxsfh+4rXj4J6rp0NfIk+FuPTyPDH0u9wstdY8S9jfnrlGMADrZDt7KhevKVrqMd+Tf6ZxGj2R/N9vzMen61WiU7xPwJy9NvmXC0VkciqZHLxvTkiySirJJehLaSu3ZFM7esItrm9ADppeBuL6bfIZpRdpRv1iTGalot+T3LARGSkrxd0S0mmmrp7pkZVmzW15kgfPSi4ScZKzTsyD0xP7zV/G/qeZ2z6ZDv7JklOpDzaT+X/TgOjs+p3eLhd2UuF/r1sZ3O5qtoq3Ju0V7ssDkVVU1e8nmfNlispqOj35Lci05a3y8l9wLSipboracduNddwp20qcL5+TLgRGSkrq/o0SDzxFTusPUnezS0fXyH2MOrJTrTmtpSbVyoB3MgAA3cNWVehGa381yZ6SUnazsvMxsFif2erxPglpLQ2k00mndPZo5N5/GtIjFRWnu+ZOxSVRKWWKzS5Ijus2tV5nyWyMD0eqsymRxd4Oy/pe3+iOOnznH/KLxkpq8XdASrtK6s+Rm9q1k8tFeXE/yOzFYiOHpOTfE/CubMSUnOTlJ3bd2evlnt6lQADpQAAA6MNi50bQbbp+aW69DnBLJfijbw9ehUilSkk3/C9ycV8NephnThak5TyynJxUdE3ojw15c+YvWy2km27JbtnDicZRptujK9TnHb3M2dSdS2ecpW2u7lTWfKT7OrVas6081SWZlQD2QAAAAAAAAPfCfFf4TwCk4u8W16Es7OAACgAAAAA//9k=',
  pothos: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDABMNDhEODBMRDxEVFBMXHTAfHRoaHToqLCMwRT1JR0Q9Q0FMVm1dTFFoUkFDX4JgaHF1e3x7SlyGkIV3j214e3b/2wBDARQVFR0ZHTgfHzh2T0NPdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnb/wAARCACAAGADASIAAhEBAxEB/8QAGgABAAIDAQAAAAAAAAAAAAAAAAIFAQMEBv/EADIQAAIBAgQEBAUCBwAAAAAAAAABAgMRBBIhMSIyQVEFE2FxM1JigZGx8DRDcqHB0fH/xAAYAQEBAQEBAAAAAAAAAAAAAAAAAwECBP/EAB0RAQEBAQACAwEAAAAAAAAAAAABAhExQQMSIVH/2gAMAwEAAhEDEQA/APLAAxyAAAAAAAAAAAAAAAAAFjgsEsqq1UpXXDF6qxlvGW8aMPgalZKT4IPZvd/Y7Kfh9GK4rzfq7fodYI3dqd1a5ngMO01kt6qTOar4a0m6U7/TL/ZY77GTJqw+1UE4SpzcZq0luiJd4jDwxEGpK0ukuqKerTlSqShPddi2ddUmuoAA6aAADpwOH86teXLDV+voW7aSu3ZHPgaXlYaOusuL8m5U43u1d93qQ3e1LV7WM7lyRv6vRGcjlzyb9FoiZiUlFXk7I5Yj5duSTj6boxnlHnjp3WqJRmp7dOhIDCakrpp+xyeIYfzKXmLmgvyjplCMnfZ907Mkk0tXf1EvL0l48+DZiKXk15073Sensaz0rBmMXKSjFXbdkYNmH/iKX9a/UUXiSSSSslskYlNR0e/ZbkjHW55UUeOT+RfliNOMdbXfd6smQdRXtFZpdkaMyhGTu1quvUjacNuNeu4VS2lRZX36fk2AQjOMnbZ9mrMmYlFSVpK6MmCr8UhavGVrKUd+7/djiLDxX+V9/wDBXnox4Vz4CdKShVhJ7Rkm7EAdNehIybS0i2+yI4ep5tCE73bWr9epsPMi15JS53p8qJpJKyVkG0lduyIZpT5NF8zX6ATeqsyGRx+G7ej2HFDfijffqicZKSvF3QEVPW0otP8AsyYBgrfFZJzpx6pN/n/hwHRj6mfFT1uo8K/fvc5z0ZnIrPAADpqw8LrJZqT68SLB3s7b9LlBGTjJSi7NO6LrDV44ikpJ8S5l2ZHefaep7TUNc0nmfTsiZGc4wV5Mg1OrvwQ7dWcOWzfYjKCbuuGXdEXTcNaTt9L2ZKNRSeVpxl2YGY5tVJL3XUhiKyoUZT69F3ZsbSTbdkt2ynxuI8+rw8keXQ6zntbmdc4ALqgAAE6dSdKeanLKyAAtMPi6NSV6jyVPqen2OxNNJp3T2aPPkoVJwvknKN97OxO/H/HFwuML8N+4xFahCLVWSuui3K3EVJxlljOSi1qk9Gc5kx39JnrfiMVUrXjmfl30T3+5oAKycdycAAAAAAAAAABuxXxF7Gky25PVt+5gQk4AAAAAP//Z',
  snake: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDABMNDhEODBMRDxEVFBMXHTAfHRoaHToqLCMwRT1JR0Q9Q0FMVm1dTFFoUkFDX4JgaHF1e3x7SlyGkIV3j214e3b/2wBDARQVFR0ZHTgfHzh2T0NPdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnb/wAARCACAAGADASIAAhEBAxEB/8QAGgABAAMBAQEAAAAAAAAAAAAAAAECAwQFBv/EACwQAAIBAwMDAgYCAwAAAAAAAAABAgMRIQQSMTJRYXGxEyIzQVKBNHKhwdH/xAAXAQEBAQEAAAAAAAAAAAAAAAAAAwEC/8QAGBEBAQEBAQAAAAAAAAAAAAAAAAERAjH/2gAMAwEAAhEDEQA/APlQAGAAAAAAAAAAAAAAAAAB36fTKmt00nP2MtxluMKejnNXm9i/ydEdJSSynL1ZuDjalerWL0lFqyi15TMKmiks03u8PDO3kDaTqx5DTTs8MHpV9PGrG6sp9zzpJxk0+VhncuqS6gAGugAAdOipbpfEfEePU7m1FXbsVow+HSjDsSoJZtd93yTt1Hq7Ubm+lftjZfqbfsWIclHlmOUbWul/p5Q326lbz9iYyUldEgE01dO5ya2ldfFXKwzqcFe6un4Djug4t8qzZsuNlyvJBMouMnF8p2IKLhehHdWgrXzwUNtJ/Jh+/YVl8eiQ5JO3L7IkEkFbSlz8q8ckqKjwv2SV33xFbvYCXBN34fdEXlHn5l45CnwpYfksBCkm7J57EhpO11wAPO1cduoliyeUYnTrvrL+v/TmKTxfnwNdNJR1EG+9jIlNxkmuVlGlesQ21wrkpqUU1w8oEkFdrfW7+FwW4Dairt2K3lLj5V3fIFmk1Zq5Xa49Lx2Yu4dWV3RZNNXTuBEZXdmmn2JAA4NbJOvbskjnL1p76spXum8ehQpF54AA1ru0VRSp7PvH2Ol3ti1/J5VObpzUo8o9OnUjUgpReDjqJdTLqVGzu8vuySJTUeeeyK7ZT68R/FHLhfkq4ZvHD9yNsoZhlfiy0ZqWMp9mAje3zJX8GWrqKFFr7ywjWUlCLlJ2SPNrVXVqOT44XodSa65m1mADtYAAAvSqzpSvB+q7lAB6FGvSld32y++5/wCzafRL0PJL05yTSUmk3lJnN5TvD0aP0kUrVaUeqV5L8eTiqzlucdz29r4MxOScNKtadXqeFwjMA6dgADQAAAAAJh1x9SABer9RlA228gMgAA0AAH//2Q==',
  scan: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDABMNDhEODBMRDxEVFBMXHTAfHRoaHToqLCMwRT1JR0Q9Q0FMVm1dTFFoUkFDX4JgaHF1e3x7SlyGkIV3j214e3b/2wBDARQVFR0ZHTgfHzh2T0NPdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnb/wAARCACAAGADASIAAhEBAxEB/8QAGgABAAMBAQEAAAAAAAAAAAAAAAECBQMEBv/EAC0QAAIBAwMCBAYCAwAAAAAAAAABAgMRMQQSIVFhIjJBsRMzUnGBwQWRNEJy/8QAGAEBAQEBAQAAAAAAAAAAAAAAAAMBAgT/xAAbEQEBAQEBAQEBAAAAAAAAAAAAAQIRMSFBUf/aAAwDAQACEQMRAD8A+WAAAAAAAAAAAAAAAAAAAA0dNpVTW6ok5+xlvGW8eeloak1eb2L+2emOiopcpy7tnoBK6tRurXCWjotWUWu6Z56ugkuact3Z8M9+cATVhNWMVpp2as0DU1GmjWi2rKfo+v3MyScZOLynZlJrquddQADp0AAD16Gjvl8SWIvj7nulJRV27IrRp/CpRh0XJMYRjza76vJHV7UNXtN0n5V+WNl/O3Lt6FiJSUcs5co2OK8Evw+UN9vOrd8omMlJXRIBNNXTujx6+jdfFWVwz1OCvdXT6omUd1Nxk8qzZsvK2XlYwJnFwm4vKdiC70BehHfXgrX55XYodtH/AJUPz7GXxl8ahVzSdsvoiwIPOracs+FdskqMY4XPUkrvvxBbvYA4Ju+H1XAvOOfEu2RGeFLwy7lgIUlJ2T56EkOKdrrBIGZrY7dTLiyfKOB6v5D58f8An9s8pfPj0Z8DrpZKOpg31t/ZyJi3GSksp3RtK2SJNqyUbsRalFSWGrok87zq7HLzu/ZYLYwRKSirt2RF5SfHhXVrkCzSas1dFdrj5Hx0ZF5QXi5X1L9l001dO6ArGV3Zpp9CwAGbr5J6iy/1SR5y9afxK05Xum+H2KF58j0ScgADWtDQVVKk6bzH2PU724tfuY9ObpzU45RrU6kasFKDuvYlucvUdzl6Rgk7vmXVlis5qGc9FkrtlU8/EfpRw4dM4KuHN4+F+5XZKHNPlfSy0ZqXHKa9GBMb28SSfY46yqqdBr1lwjtKShFyk7JeplV6rrVXJ4wvsdZna7zO1zABZYAAAvSrToyvB26rqUAGjQ1FGV3fbL13P9nep8uX2Zjl6c5RaipSSb5SZO4TuP41KHyonOvWoxXileS+nJ4K05bnHdLb0vwcxMfrJj9dKtepW874WEcwCivOAAAAAAAABMPPH7kDAF63zGUDbbu3cBk+QAAaAAD/2Q==',
} as const;

/** Stored plant row — healthStatus/nextWaterDays/activeTreatmentId are DERIVED on read. */
export interface MockPlant {
  id: number;
  nickname: string;
  /** Inline so the mock garden shows photographs with no network at all. */
  photoUrl?: string;
  species?: string;
  commonName?: string;
  speciesId?: number;
  location?: string;
  notes?: string;
  lastScanId?: number;
  /** Fallback health when no completed scan names this plant (a real column server-side). */
  healthStatus?: string;
  status: 'ACTIVE' | 'ARCHIVED';
}

export interface MockSpecies {
  id: number;
  scientificName: string;
  commonName?: string;
}

export interface MockIdentification {
  id: number;
  species?: string;
  commonName?: string;
  healthStatus?: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  plantId?: number;
  /** The photograph that was scanned — inline, so the mock needs no network. */
  photoUrl?: string;
}

export interface MockReminder {
  id: number;
  plantId: number;
  plantNickname?: string;
  careType: string;
  frequencyDays: number;
  nextDueAt: string;
  enabled: boolean;
  recurring: boolean;
  updatedAt: string;
  treatmentPlanId?: number;
  treatmentPlanTitle?: string;
  stepOrder?: number;
  instruction?: string;
  stepDetail?: string;
  stepDiagramFormat?: string;
  stepDiagramContent?: string;
}

export interface MockCareLog {
  id: number;
  plantId: number;
  plantNickname?: string;
  careType: string;
  notes?: string;
  performedAt: string;
}

export interface MockTreatmentPlan {
  id: number;
  plantId: number;
  title: string;
  diagramFormat?: string;
  diagramContent?: string;
  status: 'ACTIVE' | 'COMPLETED' | 'ABANDONED';
  createdAt: string;
}

export interface MockTreatment {
  id: number;
  plantId: number;
  diseaseName: string;
  status: 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'DISMISSED';
  descriptionStatus?: 'PENDING' | 'READY' | 'FAILED';
  diseaseDescription?: string;
  diseaseDescriptionModel?: string;
  treatmentPlanModel?: string;
  identificationId?: number;
  treatmentPlanId?: number;
  needsReview?: boolean;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
}

export interface MockPreferences {
  aiModelPreference: string;
  visionModelPreference: string;
  reasoningModelPreference: string;
  visionModelAvailability: Record<string, boolean>;
  reasoningModelAvailability: Record<string, boolean>;
  plantnetProject: string;
  plantnetLang: string;
  businessTier: boolean;
}

export interface MockPushSubscription {
  endpoint: string;
  keyP256dh: string;
  keyAuth: string;
}

export interface MockSeed {
  user: MockUser;
  plants: MockPlant[];
  species: MockSpecies[];
  identifications: MockIdentification[];
  reminders: MockReminder[];
  careLogs: MockCareLog[];
  treatmentPlans: MockTreatmentPlan[];
  treatments: MockTreatment[];
  preferences: MockPreferences;
  pushSubscriptions: MockPushSubscription[];
  pausedPlanIds: number[];
  failing: { method: string; re: RegExp }[];
  flags: { rateLimitOnceTreatmentIds: number[] };
  nextId: Record<'plant' | 'identification' | 'reminder' | 'plan' | 'treatment' | 'careLog', number>;
}

/** A step template used when the mock crafts a plan for a disease. */
export interface PlanTemplateStep {
  instruction: string;
  dueOffsetDays: number;
  detail?: string;
}

export const PLAN_TEMPLATES: Record<string, PlanTemplateStep[]> = {
  default: [
    { instruction: 'Isolate the plant from its neighbours', dueOffsetDays: 0 },
    { instruction: 'Remove the affected leaves', dueOffsetDays: 1, detail: '1. Sterilise the blade. 2. Cut back to healthy tissue.' },
    { instruction: 'Treat the remaining foliage', dueOffsetDays: 4 },
    { instruction: 'Evaluate recovery', dueOffsetDays: 8 },
  ],
  'Leaf miner': [
    { instruction: 'Pick off the mined leaves', dueOffsetDays: 0 },
    { instruction: 'Check the undersides for fresh trails', dueOffsetDays: 1, detail: '1. Hold the leaf to the light. 2. Look for pale winding lines.' },
    { instruction: 'Apply a horticultural oil', dueOffsetDays: 4 },
    { instruction: 'Evaluate recovery', dueOffsetDays: 8 },
  ],
};

const DAY = 86400000;

/** Local wall-clock hour on the day `now` falls in — "today at 18:00" in any zone. */
export function todayAt(now: number, hour: number): string {
  const d = new Date(now);
  d.setHours(hour, 0, 0, 0);
  return d.toISOString();
}

/** Offset helper — every seeded instant is relative to `now`. */
export function atFrom(now: number) {
  return (days: number, hours = 0): string => new Date(now + days * DAY + hours * 3600000).toISOString();
}

function garden(now: number): MockSeed {
  const at = atFrom(now);
  const species: MockSpecies[] = [
    { id: 1, scientificName: 'Ficus lyrata', commonName: 'Fiddle-leaf fig' },
    { id: 2, scientificName: 'Monstera deliciosa', commonName: 'Swiss cheese plant' },
    { id: 3, scientificName: 'Epipremnum aureum', commonName: 'Pothos' },
    { id: 4, scientificName: 'Dracaena trifasciata', commonName: 'Snake plant' },
    { id: 5, scientificName: 'Citrus × limon', commonName: 'Lemon tree' },
  ];

  const plants: MockPlant[] = [
    { id: 1, nickname: 'Office Fig', photoUrl: MOCK_PHOTOS.fig, species: 'Ficus lyrata', commonName: 'Fiddle-leaf fig', speciesId: 1, location: 'Office · south window', lastScanId: 501, healthStatus: 'ISSUES_DETECTED', status: 'ACTIVE' },
    { id: 2, nickname: 'Studio Fig', photoUrl: MOCK_PHOTOS.fig, species: 'Ficus lyrata', commonName: 'Fiddle-leaf fig', speciesId: 1, location: 'Studio', lastScanId: 504, healthStatus: 'HEALTHY', status: 'ACTIVE' },
    { id: 3, nickname: 'Monstera', photoUrl: MOCK_PHOTOS.monstera, species: 'Monstera deliciosa', commonName: 'Swiss cheese plant', speciesId: 2, location: 'Living room', lastScanId: 502, healthStatus: 'HEALTHY', status: 'ACTIVE' },
    { id: 4, nickname: 'Hallway Pothos', photoUrl: MOCK_PHOTOS.pothos, species: 'Epipremnum aureum', commonName: 'Pothos', speciesId: 3, location: 'Hallway', status: 'ACTIVE' },
    { id: 5, nickname: 'Terrace Lemon', photoUrl: MOCK_PHOTOS.lemon, species: 'Citrus × limon', commonName: 'Lemon tree', speciesId: 5, location: 'Terrace', lastScanId: 503, healthStatus: 'ISSUES_DETECTED', status: 'ACTIVE' },
    { id: 6, nickname: 'Bedroom Snake Plant', photoUrl: MOCK_PHOTOS.snake, species: 'Dracaena trifasciata', commonName: 'Snake plant', speciesId: 4, location: 'Bedroom', healthStatus: 'HEALTHY', status: 'ACTIVE' },
  ];

  const identifications: MockIdentification[] = [
    { id: 505, status: 'PENDING', createdAt: at(0, -0.02) , photoUrl: MOCK_PHOTOS.scan },
    { id: 503, status: 'FAILED', createdAt: at(-0.04) , photoUrl: MOCK_PHOTOS.scan },
    { id: 504, species: 'Ficus lyrata', commonName: 'Fiddle-leaf fig', healthStatus: 'HEALTHY', status: 'COMPLETED', createdAt: at(-3), plantId: 2 , photoUrl: MOCK_PHOTOS.scan },
    { id: 501, species: 'Ficus lyrata', commonName: 'Fiddle-leaf fig', healthStatus: 'ISSUES_DETECTED', status: 'COMPLETED', createdAt: at(-5), plantId: 1 , photoUrl: MOCK_PHOTOS.scan },
    { id: 502, species: 'Monstera deliciosa', commonName: 'Swiss cheese plant', healthStatus: 'HEALTHY', status: 'COMPLETED', createdAt: at(-12), plantId: 3 , photoUrl: MOCK_PHOTOS.scan },
  ];

  const routine: MockReminder[] = [
    { id: 601, plantId: 1, plantNickname: 'Office Fig', careType: 'WATERING', frequencyDays: 7, nextDueAt: at(-2), enabled: true, recurring: true, updatedAt: at(-9) },
    { id: 602, plantId: 2, plantNickname: 'Studio Fig', careType: 'WATERING', frequencyDays: 5, nextDueAt: todayAt(now, 18), enabled: true, recurring: true, updatedAt: at(-5) },
    { id: 603, plantId: 3, plantNickname: 'Monstera', careType: 'WATERING', frequencyDays: 7, nextDueAt: at(3), enabled: true, recurring: true, updatedAt: at(-4) },
    { id: 604, plantId: 3, plantNickname: 'Monstera', careType: 'FERTILIZING', frequencyDays: 30, nextDueAt: at(12), enabled: true, recurring: true, updatedAt: at(-18) },
    { id: 605, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'WATERING', frequencyDays: 14, nextDueAt: at(14), enabled: true, recurring: true, updatedAt: at(-2) },
    { id: 606, plantId: 5, plantNickname: 'Terrace Lemon', careType: 'PRUNING', frequencyDays: 60, nextDueAt: at(-5), enabled: true, recurring: true, updatedAt: at(-65) },
    { id: 607, plantId: 2, plantNickname: 'Studio Fig', careType: 'REPOTTING', frequencyDays: 365, nextDueAt: at(40), enabled: true, recurring: true, updatedAt: at(-325) },
  ];

  const step = (
    id: number,
    plantId: number,
    plantNickname: string,
    planId: number,
    planTitle: string,
    order: number,
    instruction: string,
    nextDueAt: string,
    enabled: boolean,
    updatedAt: string,
    extra: Partial<MockReminder> = {},
  ): MockReminder => ({
    id,
    plantId,
    plantNickname,
    careType: 'PEST',
    frequencyDays: 0,
    nextDueAt,
    enabled,
    recurring: false,
    updatedAt,
    treatmentPlanId: planId,
    treatmentPlanTitle: planTitle,
    stepOrder: order,
    instruction,
    ...extra,
  });

  const steps: MockReminder[] = [
    step(701, 1, 'Office Fig', 201, 'Root rot', 1, 'Deep water and let it drain', at(-3), false, at(-1)),
    step(702, 1, 'Office Fig', 201, 'Root rot', 2, 'Check the crown for soft tissue', at(0), true, at(-6), {
      stepDetail: '1. Tip the pot on its side. 2. Ease the root ball out. 3. Press the crown — firm is well, soft is rot.',
    }),
    step(703, 1, 'Office Fig', 201, 'Root rot', 3, 'Repot into a gritty mix', at(4), true, at(-6), {
      stepDiagramFormat: 'MERMAID',
      stepDiagramContent: 'graph TD; A[Old pot] --> B[Trim rot]; B --> C[Gritty mix];',
    }),
    step(704, 1, 'Office Fig', 201, 'Root rot', 4, 'Evaluate recovery', at(8), true, at(-6)),
    step(711, 6, 'Bedroom Snake Plant', 202, 'Mealybugs', 1, 'Wipe the leaves with alcohol', at(-24), false, at(-24)),
    step(712, 6, 'Bedroom Snake Plant', 202, 'Mealybugs', 2, 'Repeat the wipe', at(-22), false, at(-22)),
    step(713, 6, 'Bedroom Snake Plant', 202, 'Mealybugs', 3, 'Evaluate recovery', at(-20), false, at(-20)),
    step(801, 3, 'Monstera', 203, 'Spider mites', 1, 'Shower the foliage', at(-6), false, at(-6)),
    step(802, 3, 'Monstera', 203, 'Spider mites', 2, 'Apply a horticultural oil', at(2), true, at(-8)),
    step(803, 3, 'Monstera', 203, 'Spider mites', 3, 'Evaluate recovery', at(6), true, at(-8)),
  ];

  const treatmentPlans: MockTreatmentPlan[] = [
    { id: 201, plantId: 1, title: 'Root rot', status: 'ACTIVE', createdAt: at(-6), diagramFormat: 'MERMAID', diagramContent: 'graph TD; A[Root rot] --> B[Dry out]; B --> C[Repot];' },
    { id: 202, plantId: 6, title: 'Mealybugs', status: 'COMPLETED', createdAt: at(-25) },
    { id: 203, plantId: 3, title: 'Spider mites', status: 'ACTIVE', createdAt: at(-8) },
  ];

  const treatments: MockTreatment[] = [
    {
      id: 301, plantId: 1, diseaseName: 'Root rot', status: 'IN_PROGRESS', descriptionStatus: 'READY',
      diseaseDescription: 'Root rot sets in when the mix stays wet longer than the roots can breathe. The crown softens first; the leaves follow.',
      diseaseDescriptionModel: 'ANTHROPIC_CLAUDE', treatmentPlanModel: 'ANTHROPIC_CLAUDE',
      identificationId: 501, treatmentPlanId: 201, needsReview: false, createdAt: at(-6), startedAt: at(-6),
    },
    { id: 302, plantId: 2, diseaseName: 'Underwatering', status: 'DRAFT', descriptionStatus: 'PENDING', identificationId: 504, needsReview: false, createdAt: at(-3) },
    { id: 303, plantId: 5, diseaseName: 'Leaf miner', status: 'DRAFT', descriptionStatus: 'FAILED', identificationId: 503, needsReview: false, createdAt: at(-1) },
    { id: 304, plantId: 6, diseaseName: 'Mealybugs', status: 'COMPLETED', descriptionStatus: 'READY', diseaseDescription: 'Mealybugs cluster in leaf axils and drink sap.', treatmentPlanId: 202, needsReview: false, createdAt: at(-25), startedAt: at(-25), completedAt: at(-20) },
    { id: 305, plantId: 3, diseaseName: 'Spider mites', status: 'IN_PROGRESS', descriptionStatus: 'READY', diseaseDescription: 'Spider mites thrive in dry air and stipple the leaf surface.', treatmentPlanId: 203, needsReview: false, createdAt: at(-8), startedAt: at(-8) },
  ];

  const careLogs: MockCareLog[] = [
    { id: 901, plantId: 1, plantNickname: 'Office Fig', careType: 'WATERING', notes: 'Full soak, drained', performedAt: at(-9) },
    { id: 902, plantId: 1, plantNickname: 'Office Fig', careType: 'PEST', notes: 'Deep water · drained fully', performedAt: at(-1) },
    { id: 903, plantId: 2, plantNickname: 'Studio Fig', careType: 'WATERING', performedAt: at(-5) },
    { id: 904, plantId: 3, plantNickname: 'Monstera', careType: 'WATERING', performedAt: at(-4) },
    { id: 905, plantId: 3, plantNickname: 'Monstera', careType: 'FERTILIZING', notes: 'half strength', performedAt: at(-18) },
    { id: 906, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'WATERING', performedAt: at(-2) },
    { id: 907, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'PEST', performedAt: at(-20) },
    { id: 908, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'PEST', performedAt: at(-22) },
    { id: 909, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'PEST', performedAt: at(-24) },
    { id: 910, plantId: 5, plantNickname: 'Terrace Lemon', careType: 'PRUNING', performedAt: at(-65) },
  ];

  return {
    user: { id: 1, email: 'sam@example.org', firstName: 'Sam', lastName: 'Okafor', status: 'ACTIVE' },
    plants,
    species,
    identifications,
    reminders: [...routine, ...steps],
    careLogs,
    treatmentPlans,
    treatments,
    preferences: defaultPreferences(),
    pushSubscriptions: [],
    pausedPlanIds: [203],
    failing: [],
    flags: { rateLimitOnceTreatmentIds: [303] },
    nextId: { plant: 7, identification: 506, reminder: 900, plan: 204, treatment: 306, careLog: 911 },
  };
}

/**
 * What a fresh account carries since 2026-08-24, when GitHub Models was retired
 * upstream: both menus default to Claude, which production keys, and the older
 * options stay in the availability maps — the server does not gate them.
 */
function defaultPreferences(): MockPreferences {
  return {
    aiModelPreference: 'GITHUB_GPT4O',
    visionModelPreference: 'ANTHROPIC_CLAUDE',
    reasoningModelPreference: 'ANTHROPIC_CLAUDE',
    visionModelAvailability: {
      GITHUB_GPT4O: true, GITHUB_GPT41: true, OLLAMA_GEMMA3: true, PLANTNET: true, ANTHROPIC_CLAUDE: true,
    },
    reasoningModelAvailability: {
      DEEPSEEK_R1: true, GITHUB_O4_MINI: true, GITHUB_GPT41_MINI: true, OLLAMA_GEMMA3: true, ANTHROPIC_CLAUDE: true,
    },
    plantnetProject: 'all',
    plantnetLang: 'en',
    businessTier: false,
  };
}

function dayZero(now: number): MockSeed {
  const g = garden(now);
  return {
    ...g,
    plants: [],
    species: [],
    identifications: [],
    reminders: [],
    careLogs: [],
    treatmentPlans: [],
    treatments: [],
    pausedPlanIds: [],
    flags: { rateLimitOnceTreatmentIds: [] },
  };
}

/** The seed for a scenario, at a given instant. Pure — same `now`, same seed. */
export function buildMockSeed(scenario: MockScenario, now: number): MockSeed {
  if (scenario === 'day-zero') return dayZero(now);
  if (scenario === 'outage') {
    return {
      ...garden(now),
      failing: [
        { method: 'GET', re: /^\/reminders$/ },
        { method: 'GET', re: /^\/dashboard$/ },
        { method: 'GET', re: /^\/treatment-plans\/201$/ },
        { method: 'POST', re: /^\/chat(\/stream)?$/ },
      ],
    };
  }
  return garden(now);
}

/** Days until the earliest enabled recurring WATERING reminder is due. */
function nextWaterDays(seed: MockSeed, plantId: number, now: number): number | undefined {
  const due = seed.reminders
    .filter(r => r.plantId === plantId && r.enabled && r.recurring && r.careType === 'WATERING')
    .map(r => Date.parse(r.nextDueAt))
    .sort((a, b) => a - b)[0];
  if (due === undefined) return undefined;
  return Math.floor((due - now) / DAY);
}

/** The plant as the server would answer it: derived health, water and treatment. */
export function derivePlant(seed: MockSeed, p: MockPlant, now: number): PlantDto {
  const latestScan = seed.identifications
    .filter(i => i.plantId === p.id && i.status === 'COMPLETED' && i.healthStatus)
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))[0];
  const active = seed.treatments
    .filter(t => t.plantId === p.id && (t.status === 'DRAFT' || t.status === 'IN_PROGRESS'))
    .sort((a, b) => b.id - a.id)[0];
  return {
    id: p.id,
    nickname: p.nickname,
    species: p.species ?? null,
    commonName: p.commonName ?? null,
    healthStatus: latestScan?.healthStatus ?? p.healthStatus,
    nextWaterDays: nextWaterDays(seed, p.id, now),
    activeTreatmentId: active?.id,
    location: p.location,
    photoUrl: p.photoUrl,
  };
}

/**
 * The seed as WorldSources — the assembly's own input, so specs (and S8's
 * constitution suite) can assemble a mock board without any HTTP at all.
 * Rounds 1-3: every family the assembly reads.
 */
export function seedToSources(seed: MockSeed, now: string): WorldSources {
  const t = Date.parse(now);
  const user: WorldUser | null = seed.user
    ? { firstName: seed.user.firstName, lastName: seed.user.lastName, email: seed.user.email }
    : null;
  const species: SpeciesDto[] = seed.species.map(s => ({
    id: s.id,
    scientificName: s.scientificName,
    commonName: s.commonName ?? null,
  }));
  const identifications: IdentificationDto[] = seed.identifications.map(i => ({
    id: i.id,
    species: i.species ?? null,
    commonName: i.commonName ?? null,
    healthStatus: i.healthStatus ?? null,
    status: i.status,
    createdAt: i.createdAt,
    plantId: i.plantId,
    photoUrl: i.photoUrl,
  }));

  const careLogsByPlant: Record<number, CareLogDto[]> = {};
  for (const log of [...seed.careLogs].sort((a, b) => Date.parse(b.performedAt) - Date.parse(a.performedAt))) {
    (careLogsByPlant[log.plantId] ??= []).push(reminderlessLog(log));
  }

  const plansById: Record<number, TreatmentPlanDto> = {};
  for (const plan of seed.treatmentPlans) plansById[plan.id] = planOut(seed, plan);

  return {
    now,
    plants: seed.plants.filter(p => p.status === 'ACTIVE').map(p => derivePlant(seed, p, t)),
    species,
    identifications,
    user,
    reminders: seed.reminders.filter(r => r.enabled).map(reminderOut).sort((a, b) => Date.parse(a.nextDueAt) - Date.parse(b.nextDueAt)),
    careLogsByPlant,
    treatments: seed.treatments.map(treatmentOut),
    plansById,
    dashboard: seedDashboard(seed, t),
    preferences: seed.preferences as UserPreferencesDto,
    failures: [],
    settings: { ...DEFAULT_ASSEMBLY_SETTINGS },
    paused: [...seed.pausedPlanIds],
    snoozed: {},
    stoppedReminders: [],
    rateLimited: {},
    push: 'off',
  };
}

function reminderlessLog(l: MockCareLog): CareLogDto {
  return { id: l.id, plantId: l.plantId, plantNickname: l.plantNickname, careType: l.careType, notes: l.notes, performedAt: l.performedAt };
}

/** completedAt is derived exactly as ReminderMapper does: updatedAt once disabled. */
export function reminderOut(r: MockReminder): ReminderDto {
  return {
    id: r.id,
    plantId: r.plantId,
    plantNickname: r.plantNickname,
    careType: r.careType,
    frequencyDays: r.frequencyDays,
    nextDueAt: r.nextDueAt,
    enabled: r.enabled,
    recurring: r.recurring,
    treatmentPlanId: r.treatmentPlanId,
    treatmentPlanTitle: r.treatmentPlanTitle,
    stepOrder: r.stepOrder,
    instruction: r.instruction,
    completedAt: r.enabled ? undefined : r.updatedAt,
    stepDetail: r.stepDetail,
    stepDiagramFormat: r.stepDiagramFormat,
    stepDiagramContent: r.stepDiagramContent,
  };
}

function treatmentOut(t: MockTreatment): TreatmentDto {
  return { ...t };
}

function planOut(seed: MockSeed, plan: MockTreatmentPlan): TreatmentPlanDto {
  return {
    id: plan.id,
    plantId: plan.plantId,
    title: plan.title,
    diagramFormat: plan.diagramFormat,
    diagramContent: plan.diagramContent,
    status: plan.status,
    createdAt: plan.createdAt,
    steps: seed.reminders
      .filter(r => r.treatmentPlanId === plan.id)
      .sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0))
      .map(reminderOut),
  };
}

/**
 * The dashboard the server would compute: buckets by LOCAL start of day with
 * daysOverdue precomputed, health from each plant's latest completed scan, and
 * trends from the last two scans of any plant that has two.
 */
export function seedDashboard(seed: MockSeed, now: number): DashboardDto {
  const startOfDay = new Date(now);
  startOfDay.setHours(0, 0, 0, 0);
  const dayStart = startOfDay.getTime();
  const dayEnd = dayStart + DAY;

  const enabled = seed.reminders.filter(r => r.enabled);
  const overdueReminders: ReminderSummaryDto[] = enabled
    .filter(r => Date.parse(r.nextDueAt) < dayStart)
    .map(r => ({ ...reminderOut(r), daysOverdue: Math.floor((dayStart - Date.parse(r.nextDueAt)) / DAY) }));
  const todayReminders: ReminderSummaryDto[] = enabled
    .filter(r => {
      const t = Date.parse(r.nextDueAt);
      return t >= dayStart && t < dayEnd;
    })
    .map(r => reminderOut(r));

  const active = seed.plants.filter(p => p.status === 'ACTIVE');
  const healthSummary = { healthy: 0, issues: 0, unknown: 0 };
  for (const p of active) {
    const h = derivePlant(seed, p, now).healthStatus;
    if (h === 'HEALTHY') healthSummary.healthy++;
    else if (h === 'ISSUES_DETECTED') healthSummary.issues++;
    else healthSummary.unknown++;
  }

  const recentScans = [...seed.identifications]
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
    .slice(0, 3)
    .map(i => ({ id: i.id, species: i.species, commonName: i.commonName, healthStatus: i.healthStatus, status: i.status, createdAt: i.createdAt, plantId: i.plantId }));

  const healthTrends = active
    .map(p => {
      const scans = seed.identifications
        .filter(i => i.plantId === p.id && i.status === 'COMPLETED')
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
      if (scans.length < 2) return undefined;
      const [latest, previous] = scans;
      const trend =
        latest.healthStatus === previous.healthStatus
          ? 'STABLE'
          : latest.healthStatus === 'HEALTHY'
            ? 'IMPROVING'
            : 'WORSENING';
      return { plantId: p.id, plantNickname: p.nickname, trend };
    })
    .filter((x): x is { plantId: number; plantNickname: string; trend: string } => x !== undefined);

  return {
    healthSummary,
    overdueReminders,
    todayReminders,
    healthTrends,
    recentScans,
    plantCount: active.length,
    speciesCount: new Set(active.map(p => p.speciesId).filter(x => x !== undefined)).size,
  };
}

// ---------------------------------------------------------------------------
// The companion's answers.
//
// A pure function of (seed, question, plantId). No Math.random, no Date.now, no
// wall-clock words — the same seed and the same question give a byte-identical
// reply for ever, which is what the determinism walk rests on. Every answer ends
// by pointing at the plant: this companion reads the garden, it never writes to
// it, so the press that changes anything lives on the plant's own node.
// ---------------------------------------------------------------------------

const PRESS_NOTE = 'If you want that done, the plant itself carries the press.';

function plantOf(seed: MockSeed, plantId?: number): MockPlant | undefined {
  if (plantId === undefined) return undefined;
  return seed.plants.find(p => p.id === plantId && p.status === 'ACTIVE');
}

function latestHealth(seed: MockSeed, plantId: number): string | undefined {
  return seed.identifications
    .filter(i => i.plantId === plantId && i.status === 'COMPLETED' && i.healthStatus)
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))[0]?.healthStatus;
}

function activeDisease(seed: MockSeed, plantId: number): string | undefined {
  return seed.treatments
    .filter(t => t.plantId === plantId && (t.status === 'DRAFT' || t.status === 'IN_PROGRESS'))
    .sort((a, b) => b.id - a.id)[0]?.diseaseName;
}

/** "the fig, the Monstera and the Pothos" — deterministic, seed order. */
function nameList(seed: MockSeed, limit = 3): string {
  const names = seed.plants.filter(p => p.status === 'ACTIVE').map(p => p.nickname).slice(0, limit);
  if (names.length === 0) return '';
  if (names.length === 1) return names[0];
  return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}

function subject(seed: MockSeed, plant: MockPlant | undefined): string {
  if (plant) return plant.nickname;
  const first = seed.plants.find(p => p.status === 'ACTIVE');
  return first ? first.nickname : 'a plant';
}

type Bucket = 'water' | 'light' | 'yellow' | 'pests' | 'treatment' | 'due' | 'who' | 'unknown';

/** Which bucket a question falls in. First match wins, so the order is the rule. */
export function chatBucket(question: string): Bucket {
  const q = question.toLowerCase();
  if (/\bwho\b|what are you|your name/.test(q)) return 'who';
  if (/water|thirsty|dry soil|how often/.test(q)) return 'water';
  if (/light|sun|shade|window/.test(q)) return 'light';
  if (/yellow|dropping|browning|leaves? (are|going|falling)/.test(q)) return 'yellow';
  if (/pest|mite|gnat|bug|insect|miner/.test(q)) return 'pests';
  if (/treatment|course|disease|cure|sick|ill/.test(q)) return 'treatment';
  if (/due|today|overdue|schedule|remind/.test(q)) return 'due';
  return 'unknown';
}

/**
 * The mock companion's whole answer, in the voice: warm, concrete about what it
 * can actually see, and honest the moment it cannot.
 */
export function chatReply(
  seed: MockSeed,
  question: string,
  now: number,
  plantId?: number,
): string {
  const plant = plantOf(seed, plantId);
  const active = seed.plants.filter(p => p.status === 'ACTIVE');
  const steps = seed.reminders.filter(r => r.enabled).length;
  // The honest due picture, computed exactly as the dashboard computes it.
  const board = seedDashboard(seed, now);
  const dueToday = board.todayReminders.length;
  const overdue = board.overdueReminders.length;
  const bucket = chatBucket(question);

  if (active.length === 0) {
    const opening = 'Your garden is empty here: no plants, and no care steps on the list.';
    if (bucket === 'who') {
      return `${opening}

I am PlantPal — the knowledgeable friend rather than the botanist. Once a plant is in the garden I can answer about that one in particular.`;
    }
    return `${opening}

So I can only answer generally, and I would rather say that than invent a plant for you. Add one and ask me again, and the answer will be about yours.`;
  }

  const it = subject(seed, plant);
  switch (bucket) {
    case 'who':
      return `I am PlantPal. I read your garden — right now ${nameList(seed)} and ${steps} care steps on the list — and I answer questions about it. I never change anything myself. ${PRESS_NOTE}`;
    case 'water':
      return `${it} would rather be a little dry than a little wet: water when the top few centimetres are dry to the finger, not on a fixed day.

Your watering schedule already sits with the plant, and ${steps} care steps stand on the list overall. ${PRESS_NOTE}`;
    case 'light':
      return `Bright light, no direct midday sun — a metre back from a window facing the sun is usually right for ${it}.

If it has been leaning, turn it a quarter each week. ${PRESS_NOTE}`;
    case 'yellow': {
      const health = plant ? latestHealth(seed, plant.id) : undefined;
      const seen =
        health === 'ISSUES_DETECTED'
          ? `The last scan of ${it} did flag something, so this may not be watering alone.`
          : health === 'HEALTHY'
            ? `The last scan of ${it} came back healthy, so I would look at watering before anything else.`
            : `I have no recent scan of ${it} to lean on, so take this as the general answer.`;
      return `Lower leaves going yellow is usually drought or the opposite of it — roots standing in water.

${seen} ${PRESS_NOTE}`;
    }
    case 'pests':
      return `Look at the undersides and where leaf meets stem: mites leave fine webbing, gnats mean the top of the soil is staying wet.

For ${it} I would check both before treating anything. ${PRESS_NOTE}`;
    case 'treatment': {
      const disease = plant ? activeDisease(seed, plant.id) : undefined;
      if (disease) {
        return `${it} has a course running for ${disease}. Its steps are on the plant, in order, with the next one due first.

I can talk you through any step, but I do not tick them off. ${PRESS_NOTE}`;
      }
      return `Nothing is under treatment on ${it} at the moment.

If a scan turns something up, a course is offered there rather than here. ${PRESS_NOTE}`;
    }
    case 'due':
      return `${dueCount(dueToday)} today${overdue > 0 ? `, and ${overdueCount(overdue)} still standing from before` : ', and nothing standing from before'} — out of ${steps} care steps across ${active.length} plants.

Today's are gathered on the reminders board. ${PRESS_NOTE}`;
    default:
      return `I am not sure about that one, and I would rather say so than guess.

What I can see is ${active.length} plants — ${nameList(seed)} — and ${steps} care steps on the list. Ask me about watering, light, leaves, pests or a course and I will be on firmer ground.`;
  }
}

/** "Nothing is due" / "One step is due" / "Three steps are due", never a bare number. */
function dueCount(n: number): string {
  if (n === 0) return 'Nothing is due';
  return n === 1 ? 'One step is due' : `${n} steps are due`;
}

function overdueCount(n: number): string {
  return n === 1 ? 'one step is' : `${n} steps are`;
}

/** The reply cut into tokens, whitespace kept, so the stream reads as writing. */
export function chatTokens(reply: string): string[] {
  return reply.match(/\S+\s*/g) ?? [];
}
