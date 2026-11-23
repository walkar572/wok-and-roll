## Use Case: Select Country and Display Regional Pricing
Use Case ID: UC-01\
Use Case Name: Select Country\
Primary Actor: Guest (unauthenticated user)\
Goal: Allow the guest to select their country and view menu and cart prices in the corresponding local currency, including regional prices and VAT.

### Preconditions
- The website is accessible.
- The guest can access the country switcher in the header.
- The system supports five countries: Uzbekistan (UZB), Kazakhstan (KAZ), Georgia (GEO), Ukraine (UKR), and China (CHN).

### Main Success Scenario
1. The guest opens the website.
2. The system displays a country switcher in the header with five available countries.
3. The system checks whether the guest has previously selected a country.
4. If a previous selection exists, the system restores it and displays the selected country in the header.
5. Otherwise, the system applies the default country (UZB).
6. The guest selects a country from the switcher.
7. The system updates the selected country in the header.
8. The system retrieves the corresponding currency, regional prices, VAT rate, and pricing thresholds.
9. The system recalculates and updates prices and VAT in the Menu and Cart without reloading the page.
10. The system formats all prices using `Intl.NumberFormat` with two decimal places and the appropriate local currency.
11. The system saves the guest's country selection.
12. When the guest returns to the website or reloads the page, the system restores the previously selected country and its pricing settings.

### Alternative Flows
A1. No previous country selection
- At step 3, if no saved country is found, the system applies the default country (UZB).
- The corresponding currency, prices, and VAT are displayed.

A2. Guest changes country
- At step 6, the guest selects a different country.
- The system immediately updates the currency, regional prices, VAT, and thresholds in both Menu and Cart without a page reload.

### Postconditions
- The selected country is displayed in the header.
- The selected country's currency is used throughout the Menu and Cart.
- Prices, VAT, and pricing thresholds correspond to the selected country.
- The country preference is persisted for future visits.

## Use Case: Calculate and Display Order Price Breakdown
Use Case ID: UC-02\
Use Case Name: View Order Price Breakdown\
Primary Actor: Guest (unauthenticated user)\
Goal: Allow the guest to see a transparent breakdown of the order cost, including subtotal, VAT, amount including VAT, discount, delivery fee, and final total.

### Preconditions
- The guest has added at least one item to the cart.
- The guest has selected a country.
- The system has the corresponding country-specific VAT rate, currency, discount thresholds in local currency, and delivery fee.
- The pricing calculation API is available.

### Main Success Scenario
1. The guest opens the Cart.
2. The system retrieves the order price breakdown from the API.
3. The system performs all monetary calculations using BigDecimal with half-up rounding to two decimal places after each calculation step.
4. The system calculates the Subtotal based on the selected menu items and quantities.
5. The system calculates VAT using the selected country's VAT rate.
6. The system calculates Amount incl. VAT by adding VAT to the Subtotal.
7. The system determines the applicable discount based on Amount incl. VAT:
   - 100 → 3%
   - 500 → 5%
   - 700 → 7%
   - 1000 → 10%
   - 5000 → 15%
   - Below 100 → No discount.
8. The system calculates the Discount amount.
9. The system adds the Delivery fee after applying the discount.
10. The system calculates the final Total.
11. The system returns all calculated values through the API.
12. The UI displays the values returned by the API in the following order:
    - Subtotal
    - VAT
    - Amount incl. VAT
    - Discount
    - Delivery fee
    - Total
13. The guest reviews the complete order price breakdown.

### Alternative Flows
A1. Guest selects pickup
- At step 8, if the guest selects pickup instead of delivery, the delivery fee is 0.00.
- The system calculates the Total without a delivery fee.

A2. Guest changes country
- The system retrieves the VAT rate and regional pricing rules for the newly selected country.
- The API recalculates the entire price breakdown.
- The UI displays the updated values without client-side recalculation.

A3. Guest changes cart contents
- If the guest changes product quantities or removes items, the system requests an updated price breakdown.
- The API recalculates the subtotal, VAT, discount, delivery fee, and total.
- The UI displays the updated values.

A4. Order does not qualify for a discount
- If Amount incl. VAT is below 100.00, the discount is 0.00.
- The system calculates the Total without a discount.

### Postconditions
- The guest sees all six price components.
- All displayed values correspond to the selected country and currency.
- The final Total reflects VAT, discount, and delivery fee.
- The UI displays the API response without independently recalculating prices.
- All displayed monetary values are rounded to two decimal places using the half-up rounding mode.

## Use Case: Place an Order
Use Case ID: UC-03\
Use Case Name: Submit Order\
Primary Actor: Guest (unauthenticated user)\
Goal: Allow the guest to place an order by selecting an order date and pickup or delivery option so that the café can prepare the order.

### Preconditions
- The guest has access to the website.
- The guest has selected a country.
- The guest has added at least one item to the cart.
- The cart contains valid pricing information.

### Main Success Scenario
1. The guest opens the Checkout page.
2. The system displays the checkout form with the following fields:
   - Order date
   - Order summary
   - Pickup point / delivery address
3. The guest selects an order date.
4. The guest selects either Pickup or Delivery.
5. The system validates the entered data.
6. The guest submits the order.
7. The system sends the order information to the API.
8. The API validates the request and saves the order, including:
   - Selected country.
   - Order date.
   - Order scenario (Pickup / Delivery).
   - Ordered items and quantities.
   - Subtotal, VAT, discount, delivery fee, and total.
9. The API returns a successful response containing the order number.
10. The system displays a success page.
11. The success page shows the order number and confirms that the order has been placed.

### Alternative Flows
A1. Guest Selects a Past Order Date
- At step 5, the system detects that the selected date is earlier than today.
- The system displays a validation error.
- The guest is asked to select today or a future date.
- The order cannot be submitted until the date is corrected.

A2. Guest Selects Pickup
- At step 4, the guest selects Pickup.
- The system sets the order scenario to Pickup.
- The delivery fee is set to 0.00.

A3. Guest Selects Delivery
- At step 4, the guest selects Delivery.
- The system sets the order scenario to Delivery.
- The applicable delivery fee is included in the order total.

A4. API Returns an Error
- At step 9, the API returns an error response.
- The system processes the error using the standard error format:\
  `{code, message, fields}`.
- The system displays a clear and understandable error message.
- Field-specific errors are displayed next to the corresponding fields.
- The guest's previously entered information is preserved.
- The guest can correct the information and submit the order again.

A5. Guest Submits Invalid Data
- The system validates the checkout form.
- If required information is missing or invalid, the system displays validation messages.
- The guest remains on the Checkout page.
- No order is created until the data is valid.

### Postconditions
On success:
- The order is successfully saved in the system.
- The order contains the selected country, date, scenario, items, and totals.
- A unique order number is generated.
- The guest sees the order confirmation page.

On failure:
- The order is not created.
- The guest remains on the Checkout page.
- Previously entered information is preserved.
- Appropriate error messages are displayed.
