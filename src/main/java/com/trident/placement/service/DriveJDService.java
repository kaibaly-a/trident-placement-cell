package com.trident.placement.service;

import com.trident.placement.dto.DriveJDResponse;
import com.trident.placement.dto.admin.DriveJDRequest;

public interface DriveJDService {

    /**
     * Admin creates a new JD for a drive.
     * Throws if a JD already exists for that drive (use update instead).
     */
    DriveJDResponse createJD(Long driveId, DriveJDRequest request);

    /**
     * Admin updates an existing JD.
     * Replaces selection steps completely (delete + re-insert).
     * Throws if no JD exists for that drive (use create instead).
     */
    DriveJDResponse updateJD(Long driveId, DriveJDRequest request);

    /**
     * Create if not exists, update if exists.
     * Convenience method used by the frontend "Save & Publish" button.
     */
    DriveJDResponse upsertJD(Long driveId, DriveJDRequest request);

    /**
     * Get full JD for a drive — ADMIN version (no eligibility check).
     * Used by admin preview endpoint.
     * Returns response with jdExists=false if no JD has been created yet.
     */
    DriveJDResponse getJD(Long driveId);

    /**
     * Get full JD for a drive — STUDENT version (eligibility enforced).
     *
     * Before returning the JD, checks that the student's regdno appears
     * in the eligible_drives table for this drive. If not eligible,
     * throws an exception (results in 403 response).
     *
     * This ensures:
     *  - Only 4th-year students (admissionYear >= 2021) see JDs, because
     *    only they appear in eligible_drives.
     *  - Only students whose CGPA meets the drive's minimum see the JD.
     *
     * @param driveId  The drive whose JD is being requested
     * @param regdno   The requesting student's registration number
     */
    DriveJDResponse getJDForStudent(Long driveId, String regdno);

    /**
     * Admin deletes a JD (and all its selection steps via cascade).
     */
    void deleteJD(Long driveId);
}