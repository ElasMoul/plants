import { WorldData } from './world.model';

/**
 * The fixture world — the theme-a prototype's own board, verbatim (cells, kinds,
 * names, recaps, edges). It renders the Rhizome world with no backend, so the
 * shell can be built and verified before live data (Phase D) is wired in.
 */
export const FIXTURE_WORLD: WorldData = {
  initialFocus: 'n-fig',
  nodes: [
    { id: 'n-species', cell: { col: 0, row: 5 }, kind: 'collection', kindLabel: 'Collection', name: 'Species', recap: '28 species' },
    { id: 'n-monstera', cell: { col: 2, row: 2 }, kind: 'species', kindLabel: 'Species', name: 'Monstera', recap: '3 plants · wants repotting', recapNote: 'Monstera deliciosa · ranked 2nd by what is owed' },
    { id: 'n-species-more', cell: { col: 2, row: 8 }, kind: 'collection', kindLabel: 'Collection', name: '26 more species', recap: '+26 more' },
    { id: 'n-fig', cell: { col: 2, row: 5 }, kind: 'species', kindLabel: 'Species', name: 'Fiddle-leaf Fig', recap: '12 plants · 1 overdue', recapNote: 'Ficus lyrata' },
    { id: 'n-platform', cell: { col: 4, row: 0 }, kind: 'platform', kindLabel: 'Platform', name: 'Platform link', recap: 'Healthy · 2 feeds out' },
    { id: 'n-garden', cell: { col: 4, row: 2 }, kind: 'collection', kindLabel: 'Collection', name: 'My garden', recap: '12 plants · 2 need water' },
    { id: 'n-care', cell: { col: 4, row: 4 }, kind: 'guide', kindLabel: 'Guide', name: 'Care Guide', recap: 'Water · light · soil' },
    { id: 'n-problems', cell: { col: 4, row: 6 }, kind: 'problem', kindLabel: 'Problems', name: 'Problems', recap: '3 active' },
    { id: 'n-journal', cell: { col: 4, row: 8 }, kind: 'journal', kindLabel: 'Journal', name: 'Journal', recap: '14 entries' },
    { id: 'n-ident', cell: { col: 4, row: 10 }, kind: 'platform', kindLabel: 'Identification', name: 'Identification', recap: 'Last scan failed' },
    { id: 'n-office', cell: { col: 6, row: 1 }, kind: 'plant', kindLabel: 'Plant', name: 'Office Fig', recap: 'Needs water · 65', recapNote: 'Overdue 2 days · PL-002 · ranked 1st' },
    { id: 'n-studio', cell: { col: 6, row: 2 }, kind: 'plant', kindLabel: 'Plant', name: 'Studio Fig', recap: 'Watch · 58', recapNote: 'Under treatment · PL-005 · ranked 2nd' },
    { id: 'n-garden-more', cell: { col: 6, row: 3 }, kind: 'collection', kindLabel: 'Collection', name: '10 more plants', recap: '+10 more' },
    { id: 'n-reminders', cell: { col: 6, row: 4 }, kind: 'journal', kindLabel: 'Reminders', name: 'Reminders', recap: 'Nothing due' },
    { id: 'n-underwater', cell: { col: 6, row: 6 }, kind: 'problem', kindLabel: 'Problem', name: 'Underwatering', recap: 'Moderate · Jul 18' },
    { id: 'n-overwater', cell: { col: 6, row: 7 }, kind: 'problem', kindLabel: 'Problem', name: 'Overwatering', recap: 'Resolving · Jul 02' },
    { id: 'n-rootrot', cell: { col: 6, row: 8 }, kind: 'problem', kindLabel: 'Problem', name: 'Root rot', recap: 'Serious · Jun 28' },
    { id: 'n-j1', cell: { col: 6, row: 9 }, kind: 'journal', kindLabel: 'Entry', name: 'Jul 18 · photo', recap: 'Photo · Office Fig' },
    { id: 'n-j2', cell: { col: 6, row: 10 }, kind: 'journal', kindLabel: 'Entry', name: 'Jul 15 · watering', recap: 'Watered · 4 plants' },
    { id: 'n-journal-more', cell: { col: 8, row: 9 }, kind: 'collection', kindLabel: 'Collection', name: '12 more entries', recap: '+12 more' },
    { id: 'n-treatment', cell: { col: 8, row: 7 }, kind: 'problem', kindLabel: 'Treatment', name: 'Treatment plan', recap: '4 steps · active' },
    { id: 'n-account', cell: { col: 2, row: 0 }, kind: 'platform', kindLabel: 'Account', name: 'Your account', recap: 'Signed in · 12 plants' },
    { id: 'n-today', cell: { col: 2, row: 3 }, kind: 'guide', kindLabel: 'Dashboard', name: 'Today', recap: '2 due · 1 overdue' },
    { id: 'n-ask', cell: { col: 8, row: 5 }, kind: 'guide', kindLabel: 'Companion', name: 'Ask PlantPal', recap: 'Ask about this fig' },
    { id: 'n-unknown', cell: { col: 8, row: 3 }, kind: 'region', kindLabel: 'Region', name: 'Unloaded region', recap: 'Not fetched yet', unknown: true },
  ],
  edges: [
    ['n-species', 'n-fig'], ['n-species', 'n-monstera'], ['n-species', 'n-species-more'],
    ['n-fig', 'n-garden'], ['n-fig', 'n-care'], ['n-fig', 'n-problems'],
    ['n-fig', 'n-journal'], ['n-fig', 'n-ident'], ['n-fig', 'n-platform'],
    ['n-garden', 'n-office'], ['n-garden', 'n-studio'], ['n-garden', 'n-garden-more'],
    ['n-garden', 'n-reminders'],
    ['n-problems', 'n-underwater'], ['n-problems', 'n-overwater'], ['n-problems', 'n-rootrot'],
    ['n-underwater', 'n-treatment'], ['n-rootrot', 'n-treatment'], ['n-treatment', 'n-care'],
    ['n-journal', 'n-j1'], ['n-journal', 'n-j2'], ['n-journal', 'n-journal-more'],
    ['n-ident', 'n-care'], ['n-garden-more', 'n-unknown'], ['n-j1', 'n-office'],
    ['n-account', 'n-platform'], ['n-account', 'n-garden'],
    ['n-today', 'n-garden'], ['n-today', 'n-reminders'],
    ['n-ask', 'n-care'], ['n-ask', 'n-ident'],
  ],
};
