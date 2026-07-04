import { Page } from '@playwright/test';

/** Stub all external AI and PlantNet API calls. Never let E2E hit live AI. */
export async function stubAiCalls(page: Page) {
  // Stub identification polling — returns COMPLETED immediately
  await page.route('**/api/v1/identifications/**', async (route) => {
    const method = route.request().method();
    if (method === 'POST') {
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ data: { id: 1, status: 'PENDING' } }),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            id: 1,
            status: 'COMPLETED',
            identificationStatus: 'COMPLETED',
            commonName: 'Monstera',
            species: 'Monstera deliciosa',
            confidence: 'HIGH',
            healthStatus: 'HEALTHY',
            healthNotes: null,
            carePlan: {
              wateringFrequencyDays: 7,
              careCards: [
                { type: 'WATERING', title: 'Watering', summary: 'Water every 7 days' }
              ]
            }
          }
        }),
      });
    }
  });

  // Stub PlantNet species match
  await page.route('**/api/v1/identifications/*/species-match', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          candidates: [
            { scientificName: 'Monstera deliciosa', score: 0.95, commonName: 'Swiss Cheese Plant' }
          ]
        }
      }),
    });
  });

  // Stub chat streaming
  await page.route('**/api/v1/chat/stream', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: 'data: {"token":"Your plant looks healthy!"}\n\ndata: [DONE]\n\n',
    });
  });
}

/** Stub auth endpoints for test users. */
export async function stubAuth(page: Page) {
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          token: 'test-jwt-token-for-e2e',
          userId: 1,
          email: 'test@plantpal.test',
        }
      }),
    });
  });

  await page.route('**/api/v1/auth/register', async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          token: 'test-jwt-token-for-e2e',
          userId: 1,
          email: 'test@plantpal.test',
        }
      }),
    });
  });
}

/** Stub plant CRUD endpoints with a seed garden. */
export async function stubPlants(page: Page) {
  const plants = [
    { id: 1, nickname: 'My Monstera', commonName: 'Monstera', status: 'ACTIVE', healthStatus: 'HEALTHY' },
  ];

  await page.route('**/api/v1/plants**', async (route) => {
    const method = route.request().method();
    if (method === 'POST') {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ data: { id: 2, nickname: 'New Plant', status: 'ACTIVE' } }),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: { content: plants, totalElements: plants.length, totalPages: 1, number: 0 } }),
      });
    }
  });
}
