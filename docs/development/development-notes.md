# Development Notes (Billate)

## 0) Can Icon.png be removed?
Yes. The launcher icon is now generated in the mipmap folders, so Icon.png in the repo root is no longer used and can be deleted.

## 1) Saved bill data (tax, discounts, totals)
**Current gap**: The current `BillTransaction` structure assumes a single total and line items without explicit tax/discount breakdown. This causes ambiguity when receipts include subtotal, discount, service charge, VAT, or multiple totals.

**Recommendation**: Evolve the bill schema to separate *display totals* from *calculated totals* and capture explicit receipt components.

Suggested fields (conceptual):
- `subtotalAmountVnd`
- `taxAmountVnd`
- `discountAmountVnd`
- `serviceChargeVnd`
- `totalAmountVnd` (the final payable amount, authoritative)
- `totalAmountRaw` (raw string captured from receipt)
- `roundingAmountVnd` (optional)
- `paymentMethod` (optional: cash/card/e-wallet)
- `currency`
- `lineItems` keep as-is but not authoritative for total

**Prompt strategy**:
- Ask the model to always return `final_total` as the top priority.
- Request explicit fields for `subtotal`, `discounts`, `tax`, and `service_charge` if present.
- If multiple totals exist, ask the model to choose the final payable (often labeled “Total”, “Amount Due”, “Grand Total”).
- Ask for raw text values as a fallback for reconciliation.

**Parsing strategy**:
- Prefer `final_total` over computed sum of line items.
- Use line items to enrich the UX (detail view), not as the source of truth for total.
- Keep a `confidence` or `source` marker for each field if you want to show uncertainty or allow user edits.

## 2) UI/UX too simplistic (filters + breakdown)
**Current gap**: Only a list of bills with no aggregation, filtering, or insights.

**Recommendation**: Add a “Dashboard” tab and filtering controls.

Suggested features:
- **Time filters**: This week, month, year, custom range.
- **Category breakdown**: Pie or bar chart by category.
- **Merchant breakdown**: Top merchants list.
- **Trend chart**: Daily/weekly spend line chart.
- **Search & filter**: Merchant name, category, amount range.

**Layout suggestion**:
- Bottom navigation: Home, Dashboard, Settings.
- Dashboard cards: Total spend, Avg spend/day, Top category, Chart panel.

## 3) Debugging & developer features
**Current gap**: Minimal UI feedback and no traceability for API payloads/response.

**Recommendation**: Add a “Developer Mode” toggle in Settings (hidden behind long-press or build config) that enables:
- Request/response logging (redacted API key)
- Last raw Gemini response saved to disk
- Local debug screen to view logs
- Model name, token usage, latency, and status code

**Implementation approach**:
- Store logs in local database or file cache with size limits.
- Add a “Debug” screen to render recent entries.
- Add a global logger interface so it can be disabled in production builds.

## 4) Architecture & modularity
**Recommendation**:
- Keep data/domain/ui layers clean; add use cases for analysis queries.
- Consider `BillAnalyticsRepository` to avoid mixing analytics in the core repository.
- Keep UI state in viewmodels only; avoid direct DB queries from composables.

## 5) Additional suggestions
- Add “Edit Bill” entry from list with edit icon (already enabled by tap).
- Add “Delete Bill” with confirmation.
- Add receipt image preview stored for each bill (optional; storage impact).
- Add export to CSV for tax/spend reporting.
- Consider multi-currency if you plan to expand beyond VND.

## Questions / decisions needed
1. Do you want `totalAmountVnd` to always be authoritative, even if line items don’t sum to it?
2. Do you want to store a normalized numeric value AND raw strings for all money fields?
3. Should the dashboard prioritize categories or merchants by default?
4. How visible should “Developer Mode” be (hidden gesture vs. visible toggle)?
