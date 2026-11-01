# Rule sources

Every figure in `rules/` must be traceable to a primary source. Secondary
articles are frequently years out of date — during research for this project,
several well-known Indonesian payroll blogs still quoted the 2023 JP wage cap as
the current one, and at least one quoted a 2026 figure that was actually 2023's.

**Rule: if a number is not from a primary source, it does not go in this repo.**

## JP wage cap by period

Effective from **1 March** each year (not January). Source: annual circular from
BPJS Ketenagakerjaan, issued under PP 45/2015 Art. 29(3).

| Period | Wage cap (IDR) | Employee 1% | Employer 2% |
|---|---:|---:|---:|
| Mar 2022 – Feb 2023 | 9.077.600 | 90.776 | 181.552 |
| Mar 2023 – Feb 2024 | 9.559.600 | 95.596 | 191.192 |
| Mar 2024 – Feb 2025 | 10.042.300 | 100.423 | 200.846 |
| Mar 2025 – Feb 2026 | 10.547.400 | 105.474 | 210.948 |
| Mar 2026 – Feb 2027 | 11.086.300 | 110.863 | 221.726 |

The cap is not an arbitrary number: it is recalculated each year as

```
new cap = previous cap x (1 + previous year's GDP growth)
```

which is why the figures look uneven.

## Other figures

| Rule | Value | Source |
|---|---|---|
| PTKP TK/0 | Rp 54.000.000 | PMK 101/PMK.010/2016 |
| PTKP K/3 | Rp 72.000.000 | PMK 101/PMK.010/2016 |
| PPh 21 brackets | 5/15/25/30/35% at 60jt / 250jt / 500jt / 5M | UU 7/2021 (HPP) |
| TER monthly withholding | from 1 Jan 2024 | PP 58/2023, PMK 168/2023 |
| BPJS Kesehatan | 5% (4% + 1%), cap Rp 12.000.000 | Perpres 59/2024 |
| JHT | 5,7% (3,7% + 2%), no cap | PP 46/2015 |
| JP | 3% (2% + 1%), capped | PP 45/2015 |

## Things that did **not** change

Worth knowing, because it is easy to assume everything moves every year:

- **PTKP** has been Rp 54.000.000 since 2016.
- **PPh 21 brackets** have been unchanged since 2022.
- **BPJS Kesehatan cap** has been Rp 12.000.000 since 2020 — the regulation behind
  it changed (Perpres 59/2024) but the number did not.

## Things that did change

- **2022** — UU HPP: first bracket moved from 50jt to 60jt; 35% layer added above
  Rp 5 miliar.
- **2024** — PP 58/2023: monthly withholding switched from annualised-divide-by-12
  to TER. This is the single largest behavioural change in the encoded range.
- **Every March** — JP wage cap.
