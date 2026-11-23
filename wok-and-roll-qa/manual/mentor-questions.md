# Questions for the technical mentor - Team 12 "Wok & Roll"

From: Asif Najafov (QA Manual)
For: Q&A with the technical mentor, Monday 2 November, 18:00
Issue: #44 - Mentor Q&A
Status: blocking questions 1-3 are answered in the Decision Log. Question 5 (cart) and question 11 (browsers) are decided by the team: the cart keeps its items and recalculates in the new currency; browsers are Chrome and Firefox.

Hello! I wrote the test strategy and I found some things that the Project Plan does not answer. I cannot write the price test cases until we know the first three answers. Thank you!

## Blocking - we need these first

1. The discount levels (100 / 500 / 700 / 1000 / 5000) - are they in the **local currency** of the country, or in **one base currency** for all countries?
2. **Rounding** - do we round to 2 decimals after every step (VAT, discount, total), or only one time at the end?
3. Is the **delivery fee** in the local currency of the country?

## Menu and country

4. Can the Order Date be in the past? We need old orders to test the reports. Or only today and later?
5. The user puts items in the cart and then changes the country. What happens with the cart - we clear it, keep it with new prices, or keep it and show a warning?
6. A product exists in one country but not in another. Do we hide it, or show it as "not available"?
7. "In stock" - is it only a yes/no flag, or a number that goes down when somebody makes an order?

## Reports

8. Which date do we use to group the sales - the order date or the date when the order was created? And which time zone?
9. The CSV file - the same rows as the chart on the page, or all the order lines?

## Quality

10. "WCAG AA basics" - which exact things are checked (contrast, keyboard, labels, focus)? I want to make a simple pass/fail checklist.
11. Which browsers do we support, and is 320 px the smallest width?

If some of these are already in the task description and I missed them, please tell me where.
