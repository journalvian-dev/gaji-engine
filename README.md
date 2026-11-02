# gaji-engine

Payroll calculation engine for Indonesia.

Computes take-home pay from gross salary, PTKP status, and a **date** — using the
rules that were in effect on that date, not just the latest ones.

> **Status: usable.** Rules are effective-dated, PPh 21 uses TER for
> January–November and annual reconciliation for December, and
> `POST /api/v1/calculate` returns an auditable payslip breakdown.
> 68 tests. Not yet covered: THR, bonus, overtime, gross-up, annual
> reconciliation across changing salaries, and the deductibility of the
> employee's JHT share — see `rules/SOURCES.md`.

## Why this exists

Every salary calculator in Indonesia gives you one number per year of rules: today's.
That breaks in two ways that nobody talks about.

**1. Rules change mid-year.**

BPJS Ketenagakerjaan adjusts the Jaminan Pensiun (JP) wage cap every year — but it
takes effect in **March**, not January. So for the same salary:

| Payslip period | JP wage cap | Employee JP (1%) on Rp 12.000.000 salary |
|---|---|---|
| January 2026 | Rp 10.547.400 | Rp 105.474 |
| April 2026 | Rp 11.086.300 | Rp 110.863 |

Same person, same salary, same year — **two different correct answers.** A calculator
that keeps one cap per year is wrong for one of them.

**2. Payroll has to be reproducible.**

Recompute a payslip from 2025 and the answer must match what was actually paid. If
the system quietly applies 2026 rules, the number is wrong and there is no way to
tell. Payroll disputes and audits both start here.

So rules here are stored as **effective-dated data**, not hardcoded branches:

```yaml
- from: 2025-03-01
  to:   2026-03-01      # half-open: applies up to, but not including, this date
  wageCap: 10547400
- from: 2026-03-01
  to:   null            # still in force
  wageCap: 11086300
```

Intervals are half-open, `[from, to)`. That way exactly one rule owns any given
day, and the answer never depends on iteration order. On startup the engine
refuses to run if the intervals overlap or leave a gap.

Adding a new year is a new entry in a YAML file — not a code change.

## Scope

**In scope**

- Gross monthly salary, fixed allowances
- PTKP status: TK/0 – K/3
- PPh 21 and BPJS employee deductions
- Calculation by effective date
- Rule provenance: every result points back to the rule it used

**Out of scope (for now)**

- THR, bonus, overtime, severance
- Non-fixed allowances
- Gross-up (tax borne by employer)
- NPWP surcharge
- Annual reconciliation at year end

## Rule sources

All figures are taken from primary sources, not from blog posts — secondary
articles are frequently years out of date.

| Rule | Source |
|---|---|
| PTKP | PMK 101/PMK.010/2016 (unchanged since 2016) |
| PPh 21 brackets | UU 7/2021 (HPP), effective 2022 |
| Monthly withholding (TER) | PP 58/2023, PMK 168/2023, effective 1 Jan 2024 |
| BPJS Kesehatan | Perpres 59/2024 — cap Rp 12.000.000 |
| BPJS JP wage cap | Annual BPJS Ketenagakerjaan circular, effective 1 March |
| JHT / JP rates | PP 45/2015 (JP), PP 46/2015 (JHT) |

See `src/main/resources/rules/` for the encoded data and `SOURCES.md` in the same
folder for per-year circular numbers.

## Run it

Requires Java 17. The Maven wrapper is included, so no Maven install is needed.

```bash
./mvnw test        # 68 tests
./mvnw spring-boot:run
```

The two requests below are the whole point of the project. Same salary, same
calendar year, different correct answers:

```bash
curl "localhost:8080/api/v1/rules/jp-cap?date=2026-01-15"
```

```json
{
  "date": "2026-01-15",
  "wageCap": 10547400,
  "employeeRate": 0.01,
  "maxEmployeeAmount": 105474,
  "source": "Surat Edaran BPJS Ketenagakerjaan (2025)",
  "validFrom": "2025-03-01",
  "validTo": "2026-03-01"
}
```

```bash
curl "localhost:8080/api/v1/rules/jp-cap?date=2026-04-15"
```

```json
{
  "date": "2026-04-15",
  "wageCap": 11086300,
  "employeeRate": 0.01,
  "maxEmployeeAmount": 110863,
  "source": "Surat Edaran BPJS Ketenagakerjaan (2026)",
  "validFrom": "2026-03-01",
  "validTo": null
}
```

Every response carries the rule it used and where that rule came from. A date the
engine has no rules for returns `400 NO_RULE_FOR_DATE` rather than guessing.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/rules/jp-cap?date=` | JP wage cap and employee share in force on that date |
| GET | `/api/v1/rules/ptkp?date=&status=` | Annual PTKP threshold for a status |
| GET | `/api/v1/rules/ter?date=&status=&gross=` | TER rate on a monthly gross, and the PPh 21 it withholds |
| POST | `/api/v1/calculate` | One month's payslip: BPJS deductions, PPh 21, take-home |

The TER lookup is checkable against the regulation itself. PP 58/2023 works
through an example of Rp 10.000.000 monthly gross with PTKP K/0 and states the
answer is 2%, "Tabel A baris 9":

```bash
curl "localhost:8080/api/v1/rules/ter?date=2026-06-01&status=K/0&gross=10000000"
```

```json
{"category":"A","rate":0.02,"withholding":200000,"layerCeiling":10050000}
```

December is deliberately not answered by this endpoint: the final month uses the
annual progressive calculation and subtracts what was already withheld, so the
December figure legitimately differs from the other eleven.

## Calculate a payslip

```bash
curl -X POST localhost:8080/api/v1/calculate \
  -H 'Content-Type: application/json' \
  -d '{"grossMonthly":10000000,"ptkpStatus":"K/0","period":"2026-12-01"}'
```

```json
{
  "period": "2026-12-01",
  "ptkpStatus": "K/0",
  "grossMonthly": 10000000,
  "jht": 200000,
  "jp": 100000,
  "kesehatan": 100000,
  "pph21": 515000,
  "totalDeductions": 915000,
  "takeHomePay": 9085000,
  "pph21Method": "ANNUAL_RECONCILIATION",
  "december": {
    "annualGross": 120000000,
    "biayaJabatan": 6000000,
    "iuranPensiun": 1200000,
    "ptkp": 58500000,
    "pkp": 54300000,
    "annualTax": 2715000,
    "withheldJanuaryToNovember": 2200000,
    "decemberTax": 515000,
    "bracketSource": "UU 7/2021 (HPP) — Pasal 17",
    "deductionSource": "PMK 168/2023 Pasal 2 ayat (2), restating PMK 250/PMK.03/2008"
  }
}
```

This is the worked example printed in PP 58/2023, reproduced by the engine. The
same employee in January pays **Rp 200.000** instead — the difference is the
annual reconciliation, and the `december` block shows every step of it rather
than asking you to trust the total.

Every intermediate figure is exposed on purpose. A payroll number nobody can
audit is a liability, not a feature.

## Layout

```
src/main/java/com/journalvian/gaji/
  rule/
    EffectivePeriod.java   # half-open [from, to) interval
    BpjsRules.java         # JP caps + contribution, loaded from YAML
    PtkpRules.java         # PTKP by status, effective-dated
    TerRules.java          # TER A/B/C tables + category mapping
    BracketsRules.java     # progressive Pasal 17 brackets (December reconciliation)
    Pph21DeductionRules.java # biaya jabatan ceiling and PKP rounding
    YamlRuleLoader.java    # classpath YAML → typed entries
    RuleValues.java        # typed access with clear error messages
  calc/
    PayrollCalculator.java # gross → deductions → take-home
    CalculationRequest.java
    CalculationResponse.java
    DecemberReconciliation.java
  api/
    RuleController.java    # rule lookup endpoints
    CalculationController.java
    ApiExceptionHandler.java
src/main/resources/rules/
  ptkp.yaml                # PTKP by status (single interval, 2016 → present)
  pph21-brackets.yaml      # progressive brackets (2022 → present)
  pph21-ter.yaml           # monthly effective rate tables (2024 → present)
  bpjs.yaml                # rates + effective-dated caps
  SOURCES.md               # primary source per figure, per year
```

## Disclaimer

This is a simulation and calculation tool, not tax or payroll advice. Where the
result disagrees with the prevailing regulation, the regulation is correct.

## References

Prior art, and how this differs:

- [Mekari Talenta — Kalkulator Gaji Bersih](https://www.talenta.co/tool-gratis/kalkulator-gaji-bersih/) —
  form-only; no API, no historical rules.
- [Sigma HRIS — Simulasi PPh 21 & BPJS](https://www.sigmahris.com/Tools/Simulasi-Perhitungan-Gaji-BPJS-PPh-21) —
  detailed simulation with current rules; no historical rule sets.

Both are excellent at what they do. Neither can answer "what would this payslip have
been under the rules that applied then", and neither exposes the calculation as an
API.
