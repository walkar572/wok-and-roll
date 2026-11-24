// Interface texts the E2E tests look for. The language follows the country: UZB (the default) is
// Uzbek, KAZ is Kazakh (wok-and-roll-front/src/i18n/messages).
const uz = {
  udon: 'Achchiq udon',
  udonAction: /^(Batafsil|Tanlash): Achchiq udon$/,
  add: /^Qoʻshish ·/,
  cart: (count) => 'Savat, taomlar: ' + count,
  cartLink: /^Savat, taomlar:/,
  cartTitle: 'Savat',
  menuTitle: 'Menyu',
  quantityOf: (name) => name + ': soni',
  quantityRule: /^1 dan 99 gacha butun son kiriting\./,
  quantityStays: (quantity) => 'Soni ' + quantity + ' boʻlib qoladi.',
  removeOf: (name) => name + ': oʻchirish',
  emptyCart: 'Savatingiz sizni kutmoqda',
  menuError: 'Menyuni yuklab boʻlmadi. Qayta urinib koʻring.',
  tryAgain: 'Qayta urinib koʻring',
  pricesError: 'Joriy narxlarni olib boʻlmadi. Qayta urinib koʻring.',
  priceSummary: 'Narxlar xulosasi',
  viewMenu: 'Menyuni koʻrish',
  cafesTitle: 'Kafelarimiz',
  chilonzor: 'Wok & Roll Chilonzor',
  yunusobod: 'Wok & Roll Yunusobod',
  bookAt: (name) => 'Stol band qilish: ' + name,
  cafeHours: 'Har kuni, 10:00–22:00',
  // "Open now · until 22:00" or "Closed · opens at 10:00": it depends on the time in Tashkent.
  cafeStatus: /Hozir ochiq · 22:00 gacha|Yopiq · 10:00 da ochiladi/,
};

const kk = {
  udon: 'Ащы удон',
  udonAction: /^(Толығырақ|Таңдау): Ащы удон$/,
  add: /^Қосу ·/,
  cartLink: /^Себет, тағамдар:/,
  cartTitle: 'Себет',
  vat: (rate) => 'ҚҚС (' + rate + '%)',
  priceSummary: 'Бағалар қорытындысы',
};

// The country switcher, in any language: "Mamlakat va til, hozir: …" / "Ел мен тіл, қазір: …".
const countrySelect = /Mamlakat|Ел мен тіл|Country/;

module.exports = { uz, kk, countrySelect };
