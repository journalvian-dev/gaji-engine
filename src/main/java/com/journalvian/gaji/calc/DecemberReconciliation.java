package com.journalvian.gaji.calc;

import java.math.BigDecimal;

/**
 * The full derivation of the December PPh 21 figure.
 *
 * <p>Present only for December, where the annual progressive calculation is
 * applied and the eleven months already withheld are subtracted. Every
 * intermediate number is exposed so the result can be checked by hand — that is
 * the difference between a payroll engine and a black box.
 *
 * @param annualGross                gross income for the whole year
 * @param biayaJabatan               occupational cost: 5% of gross, capped at Rp 6.000.000/year
 * @param iuranPensiun               employee pension contribution for the year
 * @param ptkp                       annual non-taxable threshold
 * @param pkp                        Penghasilan Kena Pajak, rounded down to whole thousands
 * @param annualTax                  PPh 21 for the year, from the Pasal 17 brackets
 * @param withheldJanuaryToNovember  sum of TER withholding for the first eleven months
 * @param decemberTax                {@code annualTax - withheldJanuaryToNovember}, floored at zero
 * @param bracketSource              primary source of the bracket table
 * @param deductionSource            primary source of the occupational cost figure
 */
public record DecemberReconciliation(
        BigDecimal annualGross,
        BigDecimal biayaJabatan,
        BigDecimal iuranPensiun,
        BigDecimal ptkp,
        BigDecimal pkp,
        BigDecimal annualTax,
        BigDecimal withheldJanuaryToNovember,
        BigDecimal decemberTax,
        String bracketSource,
        String deductionSource) {
}
