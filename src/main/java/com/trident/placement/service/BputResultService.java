package com.trident.placement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BputResultService {

    @Value("${bput.result.service.url}")
    private String bputServiceUrl;

    private final RestTemplate restTemplate;

    /**
     * Fetches student results from BputResultExtract service
     * and calculates CGPA from all semesters.
     *
     * @param regdno   student registration number e.g. "2101289370"
     * @param dob      date of birth in YYYY-MM-DD format e.g. "2001-03-13"
     * @param startSession e.g. "Odd-(2022-23)"
     * @param endSession   e.g. "Even-(2024-25)"
     * @return calculated CGPA as BigDecimal, or null if fetch fails
     */
    public BigDecimal fetchAndCalculateCgpa(String regdno,
                                             String dob,
                                             String startSession,
                                             String endSession) {
        try {
            // Build request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("startRegNo",    regdno);
            requestBody.put("endRegNo",      regdno); // same — single student
            requestBody.put("startSession",  startSession);
            requestBody.put("endSession",    endSession);
            requestBody.put("dob",           dob);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            // Call BputResultExtract service — returns CSV string
            ResponseEntity<String> response = restTemplate.exchange(
                    bputServiceUrl + "/api/results/extract",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK
                    || response.getBody() == null
                    || response.getBody().isBlank()) {
                log.warn("Empty response from BPUT service for regdno: {}", regdno);
                return null;
            }

            // Parse CSV and calculate CGPA
            return calculateCgpaFromCsv(response.getBody(), regdno);

        } catch (Exception e) {
            log.error("Failed to fetch BPUT results for regdno {}: {}", regdno, e.getMessage());
            return null;
        }
    }

    /**
     * Parses the CSV response and calculates CGPA.
     *
     * CSV format:
     * regdNo,semId,subjectCode,credits,grade,examSession
     */
    private BigDecimal calculateCgpaFromCsv(String csv, String regdno) {
        String[] lines = csv.split("\n");

        double totalWeightedPoints = 0.0;
        double totalCredits = 0.0;

        for (int i = 1; i < lines.length; i++) { // skip header line
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] cols = line.split(",");
            if (cols.length < 5) continue;

            // CSV columns: regdNo, semId, subjectCode, credits, grade, examSession
            String rowRegdno = cols[0].trim();
            if (!rowRegdno.equals(regdno)) continue;

            try {
                double credits     = Double.parseDouble(cols[3].trim());
                String grade       = cols[4].trim();
                double gradePoints = gradeToPoints(grade);

                if (gradePoints >= 0) { // -1 means unknown grade — skip
                    totalWeightedPoints += gradePoints * credits;
                    totalCredits        += credits;
                }
            } catch (NumberFormatException e) {
                log.debug("Skipping unparseable line: {}", line);
            }
        }

        if (totalCredits == 0) {
            log.warn("No valid credits found for regdno: {}", regdno);
            return null;
        }

        double cgpa = totalWeightedPoints / totalCredits;
        return BigDecimal.valueOf(cgpa).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Converts BPUT grade letter to grade points.
     * Based on BPUT grading system.
     */
    private double gradeToPoints(String grade) {
        return switch (grade.toUpperCase()) {
            case "O"       -> 10.0;
            case "A+", "E" -> 9.0;
            case "A"       -> 8.0;
            case "B+", "B" -> 7.0;
            case "C"       -> 6.0;
            case "D"       -> 5.0;
            case "F", "AB" -> 0.0;  // fail or absent
            default        -> -1.0; // unknown — skip
        };
    }
}