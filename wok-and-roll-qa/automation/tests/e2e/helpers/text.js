// Interface texts the E2E tests look for (wok-and-roll-front/src/i18n/messages). The first visit is
// in Uzbek, the language of the default city, Tashkent; the language is chosen apart from the city.
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
  openMenu: 'Menyuni ochish',
  closeMenu: 'Menyuni yopish',
  cafesTitle: 'Kafelarimiz',
  chilonzor: 'Wok & Roll Chilonzor',
  yunusobod: 'Wok & Roll Yunusobod',
  bookAt: (name) => 'Stol band qilish: ' + name,
  cafeHours: 'Har kuni, 10:00–22:00',
  // "Open now · until 22:00" or "Closed · opens at 10:00": it depends on the time in Tashkent.
  cafeStatus: /Hozir ochiq · 22:00 gacha|Yopiq · 10:00 da ochiladi/,
  tashkent: 'Toshkent',
  almaty: 'Olmaota',
  london: 'London',
  boston: 'Boston',
};

const kk = {
  udon: 'Ащы удон',
  udonAction: /^(Толығырақ|Таңдау): Ащы удон$/,
  add: /^Қосу ·/,
  cartLink: /^Себет, тағамдар:/,
  cartTitle: 'Себет',
  vat: (rate) => 'ҚҚС (' + rate + '%)',
  priceSummary: 'Бағалар қорытындысы',
  almaty: 'Алматы',
};

// The two switchers of the header, in any language: "Shahar, hozir: Toshkent, Oʻzbekiston",
// "Тіл, қазір: Қазақша", "Language, now: English (US)".
const citySelect = /^(Shahar|Қала|ქალაქი|Місто|城市|City),/;
const languageSelect = /^(Til|Тіл|ენა|Мова|语言|Language),/;

module.exports = { uz, kk, citySelect, languageSelect };
