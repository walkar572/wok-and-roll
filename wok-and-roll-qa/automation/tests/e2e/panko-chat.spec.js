const { test, expect } = require('@playwright/test');

// The chat with Panko (contract v1.14, section 3.21). The AI is replaced by fixed answers in the
// format of the server (server-sent events), so the test needs no API key and is the same every
// time. The texts are Uzbek, the language of the first visit.

const apiBaseUrl = process.env.API_BASE_URL || 'http://127.0.0.1:8080';
const panko = 'Wok & Roll oshpazi Panko bilan chat';

function sse(events) {
  return events.map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`).join('');
}

async function aiIs(page, enabled) {
  await page.route('**/api/v1/assistant', (route) =>
    route.fulfill({ json: { enabled, model: enabled ? 'claude-opus-5-5' : null } }),
  );
}

async function openChat(page) {
  await page.goto('/menu');
  await page.getByRole('button', { name: panko }).click();
  const chat = page.getByRole('dialog', { name: 'Panko' });
  await expect(chat).toBeVisible();
  return chat;
}

test('Panko answers as he writes, with dish cards that open the dish', async ({ page, request }) => {
  const products = await (
    await request.get(apiBaseUrl + '/api/v1/products', { params: { country: 'UZB' } })
  ).json();
  const dish = products.find((product) => product.inStock);

  await aiIs(page, true);
  const asked = [];
  await page.route('**/api/v1/assistant/chat', (route) => {
    asked.push(route.request().postDataJSON());
    const first = asked.length === 1;
    return route.fulfill({
      contentType: 'text/event-stream',
      body: first
        ? sse([
            ['delta', { text: 'Achchiq taom uchun ' }],
            ['delta', { text: 'shuni tavsiya qilaman.' }],
            ['done', { dishes: [dish], refused: false }],
          ])
        : sse([
            ['delta', { text: 'Albatta!' }],
            ['done', { dishes: [], refused: false }],
          ]),
    });
  });

  const chat = await openChat(page);
  const log = chat.getByRole('log');
  await expect(log.getByText('Salom! Men Panko')).toBeVisible();

  // a ready question: the whole conversation goes with the city and the language
  await chat.getByRole('button', { name: 'Achchiqroq narsa xohlayman' }).click();
  await expect(log.getByText('Achchiq taom uchun shuni tavsiya qilaman.')).toBeVisible();
  expect(asked[0]).toMatchObject({
    country: 'UZB',
    language: 'uz',
    messages: [{ role: 'user', text: 'Achchiqroq narsa xohlayman' }],
  });

  // the card of the dish opens the dish, as on the menu
  const cards = chat.getByRole('list', { name: 'Panko tavsiya qilgan taomlar' });
  await expect(cards.getByRole('listitem')).toHaveCount(1);
  await cards.getByRole('button', { name: /^Qoʻshish: / }).click();
  // the dish dialog of the menu (a native dialog, above the chat)
  const modal = page.locator('dialog.modal[open]');
  await expect(modal).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(modal).toHaveCount(0);
  await expect(chat).toBeVisible();

  // a question of the guest's own, sent with Enter, with the conversation before it
  const field = chat.getByRole('textbox', { name: 'Pankodan soʻrang…' });
  await field.fill('Yana bitta savol');
  await field.press('Enter');
  await expect(log.getByText('Albatta!')).toBeVisible();
  expect(asked[1].messages).toEqual([
    { role: 'user', text: 'Achchiqroq narsa xohlayman' },
    { role: 'assistant', text: 'Achchiq taom uchun shuni tavsiya qilaman.' },
    { role: 'user', text: 'Yana bitta savol' },
  ]);

  // the chat stays for the visit, and "New chat" clears it
  await page.reload();
  await page.getByRole('button', { name: panko }).click();
  await expect(log.getByText('Albatta!')).toBeVisible();
  await chat.getByRole('button', { name: 'Yangi chat' }).click();
  await expect(log.getByText('Albatta!')).toHaveCount(0);
  await expect(chat.getByRole('button', { name: 'Achchiqroq narsa xohlayman' })).toBeVisible();
});

test('too many questions: Panko asks for a break and can try again', async ({ page }) => {
  await aiIs(page, true);
  await page.route('**/api/v1/assistant/chat', (route) =>
    route.fulfill({
      status: 429,
      headers: { 'Retry-After': '120' },
      json: { code: 'TOO_MANY_MESSAGES', message: 'Too many questions', fields: {} },
    }),
  );

  const chat = await openChat(page);
  await chat.getByRole('button', { name: 'Kafelar qachon ochiq?' }).click();
  await expect(chat.getByRole('log').getByText(/2 daqiqadan keyin/)).toBeVisible();
  await expect(chat.getByRole('button', { name: 'Qayta urinish' })).toBeVisible();
});

test('a question the AI declines without a word gets the own answer of Panko', async ({ page }) => {
  await aiIs(page, true);
  await page.route('**/api/v1/assistant/chat', (route) =>
    route.fulfill({
      contentType: 'text/event-stream',
      body: sse([['done', { dishes: [], refused: true }]]),
    }),
  );

  const chat = await openChat(page);
  await chat.getByRole('button', { name: 'Mehmonlarga eng koʻp nima yoqadi?' }).click();
  await expect(
    chat.getByRole('log').getByText('faqat Wok & Roll boʻyicha yordam beraman'),
  ).toBeVisible();
});

test('Panko comes back without a reload when the server answers again', async ({ page }) => {
  // After a restart the site is up before the server: the first status request fails.
  let calls = 0;
  await page.route('**/api/v1/assistant', (route) => {
    calls += 1;
    return calls === 1
      ? route.abort('connectionrefused')
      : route.fulfill({ json: { enabled: true, model: 'gemini-3.1-flash-lite' } });
  });

  const chat = await openChat(page);
  await expect(chat.getByText('AI qismim hozir dam olmoqda')).toBeVisible();
  // the chat asks again by itself, and the field to ask comes back
  await expect(chat.getByRole('textbox', { name: 'Pankodan soʻrang…' })).toBeVisible({
    timeout: 10000,
  });
  expect(calls).toBeGreaterThanOrEqual(2);
});

test('without the AI Panko shows the way to the menu and the booking', async ({ page }) => {
  await aiIs(page, false);

  const chat = await openChat(page);
  await expect(chat.getByText('AI qismim hozir dam olmoqda')).toBeVisible();
  await expect(chat.getByRole('textbox')).toHaveCount(0);
  await chat.getByRole('link', { name: 'Stol band qilish' }).click();
  await expect(page).toHaveURL(/\/booking$/);
  await expect(chat).toHaveCount(0);
});
