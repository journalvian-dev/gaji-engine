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
| Biaya jabatan | 5% of gross, max Rp 6.000.000/year (Rp 500.000/month) | PMK 250/PMK.03/2008, restated in PMK 168/2023 Pasal 2(2) |
| PKP rounding | rounded **down** to whole thousands of rupiah | UU PPh Pasal 17 ayat (4) |

## TER tables — how they were transcribed

The three TER tables (A: 44 layers, B: 40 layers, C: 41 layers) live in the
**Lampiran of PP 58/2023**, not in PMK 168/2023. PMK 168/2023 is the implementing
regulation — it explains the method and works through examples, but the layer
tables themselves are attached to the PP.

They were transcribed from the official Lampiran as published by DJP
(`pajak.go.id`), and then verified three ways:

1. **Against the regulation's own worked example.** PP 58/2023 uses Tuan R with a
   monthly gross of Rp 10.000.000 and PTKP K/0, and states the result is
   "tarif efektif 2% (Tabel A baris 9)". In the encoded table, TER A layer 9
   covers Rp 9.650.001–Rp 10.050.000 at 2,00%, and the engine returns
   Rp 200.000 for that input.
2. **Layer counts** are asserted in tests: 44 / 40 / 41.
3. **Structural invariants** are asserted in tests: every table is exhaustive
   (no income falls between layers), the final layer is unbounded, and rates
   never decrease as income rises.

TER is **not** derivable from a formula. It is a simplification that deliberately
rounds away the exact annualised result — that is why December needs a separate
annual reconciliation. Anyone generating these tables from the bracket maths will
produce numbers that look plausible and are wrong.

## The December reconciliation chain

The annual figure is built in a fixed order, and each link has its own source:

```
annual gross                                    (12 x monthly gross)
  - biaya jabatan      5%, max 6.000.000/year   PMK 250/PMK.03/2008
  - JHT + JP           employee shares, 12 mo   PMK 168/2023 Pasal 10(1)(b)
  = neto
  - PTKP               by status                PMK 101/PMK.010/2016
  = PKP, rounded down to whole thousands        UU PPh Pasal 17(4)
  -> brackets                                    UU 7/2021
  - already withheld Jan-Nov                     PP 58/2023
  = December PPh 21, floored at zero
```

`pph21-deductions.yaml` holds the biaya jabatan and rounding figures rather than
Java constants, so a future change to them is a cited data edit rather than an
unaccountable code change.

### Why JHT is deductible and BPJS Kesehatan is not

PMK 168/2023 Pasal 10 ayat (1) huruf b allows a deduction for

> iuran terkait program **pensiun dan hari tua**, yang terkait dengan gaji, yang
> dibayar oleh Pegawai melalui pemberi kerja kepada: … 2. badan penyelenggara
> Jaminan sosial ketenagakerjaan

JHT is literally *Jaminan Hari Tua* — old-age — and is administered by BPJS
Ketenagakerjaan, which point 2 names. So the employee's 2% JHT counts. JP is the
pension half of the same phrase. Both are deducted here.

BPJS Kesehatan is a health programme, not a pension or old-age one, so the
employee's 1% does **not** reduce the tax base even though it does reduce
take-home pay. Confusing the two is a common payroll error in both directions.

An earlier version of this engine deducted only JP. That was wrong, and the
mistake is worth recording. It was justified by the fact that doing so reproduced
PP 58/2023's Rp 515.000 December figure exactly — but it reproduced it by
coincidence. That example states a pension contribution of Rp 100.000 a month as
an *input*; it never says the figure comes from BPJS, and Rp 100.000 merely
happens to be 1% of a Rp 10.000.000 salary. A worked example only validates the
arithmetic it actually exercises.

The reconciliation is now a separate unit — `reconcileAnnual` — that takes the
year's deductible contributions as a parameter. One test feeds it the
regulation's own inputs and gets Rp 515.000; another runs the engine's BPJS
derivation and gets Rp 395.000. Both are right, for different questions.

## What counts as gross income

PMK 168/2023 Pasal 5 ayat (3) settles an easily-missed asymmetry:

- **Employer-paid JKK, JKM and BPJS Kesehatan premiums are taxable income** to the
  employee (huruf d and e) and belong in gross.
- **Employer-paid JHT and JP contributions are not** — they are taxed when the
  employee eventually receives them.

This engine takes `grossMonthly` as given rather than assembling it, so the caller
owns that distinction. It is listed as a limitation in the README.

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
