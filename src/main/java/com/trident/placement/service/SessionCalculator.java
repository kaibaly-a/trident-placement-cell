package com.trident.placement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Calculates BPUT academic session strings from a student's admission year.
 *
 * BPUT session format:
 *   Odd-(YYYY-YY)   → first semester of academic year (Jul-Dec)
 *   Even-(YYYY-YY)  → second semester of academic year (Jan-Jun)
 *
 * Example for admissionYear = "2021":
 *   startSession = "Odd-(2021-22)"   ← first semester of first year
 *   endSession   = "Even-(2024-25)"  ← last semester of final year (4 years later)
 *
 * A 4-year B.Tech has 8 semesters:
 *   Year 1: Odd-(2021-22), Even-(2021-22)
 *   Year 2: Odd-(2022-23), Even-(2022-23)
 *   Year 3: Odd-(2023-24), Even-(2023-24)
 *   Year 4: Odd-(2024-25), Even-(2024-25)
 */
@Component
@Slf4j
public class SessionCalculator {

    /**
     * Calculates the start session (first semester) from admission year.
     *
     * @param admissionYear e.g. "2021"
     * @return e.g. "Odd-(2021-22)"
     */
    public String calculateStartSession(String admissionYear) {
        if (admissionYear == null || admissionYear.isBlank()) {
            log.warn("Admission year is null/blank — using fallback session");
            return "Odd-(2021-22)";
        }

        try {
            int year = Integer.parseInt(admissionYear.trim());
            int nextYear = year + 1;
            // Format: Odd-(YYYY-YY) where YY = last 2 digits of next year
            return String.format("Odd-(%d-%02d)", year, nextYear % 100);
        } catch (NumberFormatException e) {
            log.warn("Could not parse admission year '{}' — using fallback", admissionYear);
            return "Odd-(2021-22)";
        }
    }

    /**
     * Calculates the end session (last semester of final year) from admission year.
     * Assumes 4-year B.Tech program (8 semesters).
     *
     * @param admissionYear e.g. "2021"
     * @return e.g. "Even-(2024-25)"
     */
    public String calculateEndSession(String admissionYear) {
        if (admissionYear == null || admissionYear.isBlank()) {
            log.warn("Admission year is null/blank — using fallback session");
            return "Even-(2024-25)";
        }

        try {
            int year = Integer.parseInt(admissionYear.trim());
            // Final year = admissionYear + 3 (0-indexed: year 1, 2, 3, 4)
            int finalYear     = year + 3;
            int finalYearNext = finalYear + 1;
            // Format: Even-(YYYY-YY)
            return String.format("Even-(%d-%02d)", finalYear, finalYearNext % 100);
        } catch (NumberFormatException e) {
            log.warn("Could not parse admission year '{}' — using fallback", admissionYear);
            return "Even-(2024-25)";
        }
    }

    /**
     * Converts DOB from Oracle format (DD-MON-YY or DD/MM/YYYY) to YYYY-MM-DD.
     * BPUT API requires YYYY-MM-DD format.
     *
     * Handles common Oracle date string formats.
     */
    public String normalizeDob(String dob) {
        if (dob == null || dob.isBlank()) return null;

        dob = dob.trim();

        // Already in YYYY-MM-DD format
        if (dob.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return dob;
        }

        // DD/MM/YYYY → YYYY-MM-DD
        if (dob.matches("\\d{2}/\\d{2}/\\d{4}")) {
            String[] parts = dob.split("/");
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }

        // DD-MM-YYYY → YYYY-MM-DD
        if (dob.matches("\\d{2}-\\d{2}-\\d{4}")) {
            String[] parts = dob.split("-");
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }

        // DD-MON-YY (Oracle default: 13-MAR-01) → YYYY-MM-DD
        if (dob.matches("\\d{2}-[A-Z]{3}-\\d{2}")) {
            String[] parts = dob.split("-");
            String day   = parts[0];
            String month = monthToNumber(parts[1]);
            int    yr    = Integer.parseInt(parts[2]);
            // 2-digit year: assume 00-30 = 2000s, 31-99 = 1900s
            String year = yr <= 30
                    ? String.format("20%02d", yr)
                    : String.format("19%02d", yr);
            return year + "-" + month + "-" + day;
        }

        log.warn("Unknown DOB format '{}' — returning as-is", dob);
        return dob;
    }

    private String monthToNumber(String mon) {
        return switch (mon.toUpperCase()) {
            case "JAN" -> "01"; case "FEB" -> "02"; case "MAR" -> "03";
            case "APR" -> "04"; case "MAY" -> "05"; case "JUN" -> "06";
            case "JUL" -> "07"; case "AUG" -> "08"; case "SEP" -> "09";
            case "OCT" -> "10"; case "NOV" -> "11"; case "DEC" -> "12";
            default    -> "01";
        };
    }
}