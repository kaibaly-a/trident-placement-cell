package com.trident.placement.service;

import com.trident.placement.dto.DriveJDResponse;
import com.trident.placement.dto.admin.DriveJDRequest;
import com.trident.placement.entity.Drive;
import com.trident.placement.entity.DriveJD;
import com.trident.placement.entity.DriveJDSelectionStep;
import com.trident.placement.repository.AdminDriveRepository;
import com.trident.placement.repository.DriveJDRepository;
import com.trident.placement.repository.DriveJDSelectionStepRepository;
import com.trident.placement.repository.EligibleDriveRepository;
import com.trident.placement.util.BranchCodeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriveJDServiceImpl implements DriveJDService {

    private final DriveJDRepository driveJDRepository;
    private final DriveJDSelectionStepRepository stepRepository;
    private final AdminDriveRepository adminDriveRepository;
    private final EligibleDriveRepository eligibleDriveRepository;
    private final CgpaEligibilityService cgpaEligibilityService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DriveJDResponse createJD(Long driveId, DriveJDRequest request) {
        if (driveJDRepository.existsByDriveId(driveId)) {
            throw new IllegalStateException(
                    "JD already exists for drive id=" + driveId +
                            ". Use PUT /api/admin/drives/{id}/jd to update.");
        }
        Drive drive = findDriveOrThrow(driveId);
        DriveJD jd = buildJDFromRequest(drive, request);
        DriveJD saved = driveJDRepository.save(jd);
        syncDriveBranches(drive, request.getAllowedBranches());
        log.info("JD created for drive id={} ({})", driveId, drive.getCompanyName());
        return toResponse(drive, saved);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DriveJDResponse updateJD(Long driveId, DriveJDRequest request) {
        DriveJD existing = driveJDRepository.findByDriveIdWithSteps(driveId)
                .orElseThrow(() -> new RuntimeException(
                        "No JD found for drive id=" + driveId +
                                ". Use POST /api/admin/drives/{id}/jd to create."));

        Drive drive = existing.getDrive();

        // Delete old steps first (bulk delete — faster than orphanRemoval on large
        // lists)
        stepRepository.deleteAllByDriveJdId(existing.getId());
        existing.getSelectionSteps().clear();

        // Apply all fields from request
        applyRequestToJD(existing, request);

        // Build and attach new steps
        List<DriveJDSelectionStep> newSteps = buildSteps(request, existing);
        existing.getSelectionSteps().addAll(newSteps);

        DriveJD saved = driveJDRepository.save(existing);
        syncDriveBranches(drive, request.getAllowedBranches());
        log.info("JD updated for drive id={} ({})", driveId, drive.getCompanyName());
        return toResponse(drive, saved);
    }

    // ── Upsert ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DriveJDResponse upsertJD(Long driveId, DriveJDRequest request) {
        return driveJDRepository.existsByDriveId(driveId)
                ? updateJD(driveId, request)
                : createJD(driveId, request);
    }

    // ── Read: Admin ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DriveJDResponse getJD(Long driveId) {
        Drive drive = findDriveOrThrow(driveId);
        Optional<DriveJD> jdOpt = driveJDRepository.findByDriveIdWithSteps(driveId);

        if (jdOpt.isEmpty()) {
            log.debug("No JD found for drive id={} — returning shell response", driveId);
            return shellResponse(drive);
        }

        return toResponse(drive, jdOpt.get());
    }

    // ── Read: Student (eligibility enforced) ──────────────────────────────────

    /**
     * Returns the JD only if the student's regdno appears in eligible_drives
     * for this drive.
     *
     * A student is in eligible_drives only when:
     * • They are a 4th-year student (admissionYear ≥ 2021)
     * • Their stored CGPA ≥ drive.minimumCgpa
     *
     * Any other student — including alumni — gets a 403 error.
     */
    @Override
    @Transactional(readOnly = true)
    public DriveJDResponse getJDForStudent(Long driveId, String regdno) {

        Drive drive = findDriveOrThrow(driveId);
        Optional<DriveJD> jdOpt = driveJDRepository.findByDriveIdWithSteps(driveId);

        if (jdOpt.isEmpty()) {
            log.debug("No JD found for drive id={} — returning shell response", driveId);
            return shellResponse(drive);
        }

        log.debug("Student {} viewing JD for drive id={} ({})", regdno, driveId, drive.getCompanyName());
        return toResponse(drive, jdOpt.get());
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteJD(Long driveId) {
        DriveJD jd = driveJDRepository.findByDriveIdWithSteps(driveId)
                .orElseThrow(() -> new RuntimeException(
                        "No JD found for drive id=" + driveId));
        driveJDRepository.delete(jd); // cascade deletes steps
        log.info("JD deleted for drive id={}", driveId);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private Drive findDriveOrThrow(Long driveId) {
        return adminDriveRepository.findById(driveId)
                .orElseThrow(() -> new RuntimeException(
                        "Drive not found with id: " + driveId));
    }

    /**
     * Build a brand-new DriveJD entity from a request.
     * Steps are attached as children.
     */
    private DriveJD buildJDFromRequest(Drive drive, DriveJDRequest request) {
        DriveJD jd = DriveJD.builder()
                .drive(drive)
                .build();
        applyRequestToJD(jd, request);
        List<DriveJDSelectionStep> steps = buildSteps(request, jd);
        jd.getSelectionSteps().addAll(steps);
        return jd;
    }

    /**
     * Apply scalar fields from request onto an existing (or new) DriveJD.
     * Does NOT touch selectionSteps — managed separately.
     */
    private void applyRequestToJD(DriveJD jd, DriveJDRequest request) {
        jd.setJobLocation(request.getJobLocation());
        jd.setEmploymentType(request.getEmploymentType());
        jd.setWorkMode(request.getWorkMode());
        jd.setVacancies(request.getVacancies());
        jd.setServiceAgreement(request.getServiceAgreement());
        jd.setJoining(request.getJoining());

        jd.setCgpaCutoff(request.getCgpaCutoff());
        jd.setBacklogsAllowed(request.getBacklogsAllowed() != null
                ? request.getBacklogsAllowed()
                : false);
        jd.setAllowedBranches(listToPipe(request.getAllowedBranches()));
        jd.setAllowedCourses(listToPipe(request.getAllowedCourses()));
        jd.setBatch(request.getBatch());

        jd.setAboutCompany(request.getAboutCompany());
        jd.setWebsite(request.getWebsite());
        jd.setHeadquarters(request.getHeadquarters());
        jd.setRoleOverview(request.getRoleOverview());
        jd.setRequiredSkills(listToPipe(request.getRequiredSkills()));
        jd.setKeyResponsibilities(listToPipe(request.getKeyResponsibilities()));
        jd.setWhyJoin(listToPipe(request.getWhyJoin()));
    }

    /** Build DriveJDSelectionStep list from request steps. */
    private List<DriveJDSelectionStep> buildSteps(DriveJDRequest request, DriveJD parent) {
        if (request.getSelectionProcess() == null || request.getSelectionProcess().isEmpty()) {
            return Collections.emptyList();
        }
        return request.getSelectionProcess().stream()
                .filter(s -> s.getDescription() != null && !s.getDescription().isBlank())
                .map(s -> DriveJDSelectionStep.builder()
                        .driveJD(parent)
                        .description(s.getDescription().trim())
                        .eliminationRound(s.isEliminationRound())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Pipe helpers ──────────────────────────────────────────────────────────

    private String listToPipe(List<String> list) {
        if (list == null || list.isEmpty())
            return null;
        return list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.joining("|"));
    }

    private List<String> pipeToList(String value) {
        if (value == null || value.isBlank())
            return Collections.emptyList();
        return Arrays.stream(value.split("\\|"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private DriveJDResponse toResponse(Drive drive, DriveJD jd) {
        List<DriveJDResponse.SelectionStepResponse> steps = jd.getSelectionSteps()
                .stream()
                .map(s -> DriveJDResponse.SelectionStepResponse.builder()
                        .description(s.getDescription())
                        .eliminationRound(s.isEliminationRound())
                        .build())
                .collect(Collectors.toList());

        return DriveJDResponse.builder()
                // Drive fields
                .driveId(drive.getId())
                .companyName(drive.getCompanyName())
                .role(drive.getRole())
                .driveType(drive.getDriveType().name())
                .lpaPackage(drive.getLpaPackage())
                .minimumCgpa(drive.getMinimumCgpa())
                .lastDate(drive.getLastDate().format(DATE_FMT))
                .driveStatus(drive.getStatus().name())
                // JD fields
                .jobLocation(jd.getJobLocation())
                .employmentType(jd.getEmploymentType())
                .workMode(jd.getWorkMode())
                .vacancies(jd.getVacancies())
                .serviceAgreement(jd.getServiceAgreement())
                .joining(jd.getJoining())
                .cgpaCutoff(jd.getCgpaCutoff())
                .backlogsAllowed(jd.getBacklogsAllowed())
                .allowedBranches(pipeToList(jd.getAllowedBranches()))
                .allowedCourses(pipeToList(jd.getAllowedCourses()))
                .batch(jd.getBatch())
                .aboutCompany(jd.getAboutCompany())
                .website(jd.getWebsite())
                .headquarters(jd.getHeadquarters())
                .roleOverview(jd.getRoleOverview())
                .requiredSkills(pipeToList(jd.getRequiredSkills()))
                .keyResponsibilities(pipeToList(jd.getKeyResponsibilities()))
                .whyJoin(pipeToList(jd.getWhyJoin()))
                .selectionProcess(steps)
                .jdExists(true)
                .build();
    }

    /**
     * Shell response when a drive exists but JD hasn't been filled yet.
     * Frontend uses jdExists=false to show "No JD posted yet" state.
     */
    private DriveJDResponse shellResponse(Drive drive) {
        return DriveJDResponse.builder()
                .driveId(drive.getId())
                .companyName(drive.getCompanyName())
                .role(drive.getRole())
                .driveType(drive.getDriveType().name())
                .lpaPackage(drive.getLpaPackage())
                .minimumCgpa(drive.getMinimumCgpa())
                .lastDate(drive.getLastDate().format(DATE_FMT))
                .driveStatus(drive.getStatus().name())
                .jdExists(false)
                .build();
    }

    private void syncDriveBranches(Drive drive, List<String> newBranches) {
        List<String> normalized = BranchCodeUtils.normalizeList(newBranches);
        if (normalized == null) {
            normalized = List.of();
        }

        boolean branchesChanged = !BranchCodeUtils.sameBranchSet(drive.getBranches(), normalized);

        if (branchesChanged) {
            if (normalized.isEmpty()) {
                if (drive.getBranches() != null) {
                    drive.getBranches().clear();
                }
            } else {
                drive.setBranches(new java.util.ArrayList<>(normalized));
            }
            adminDriveRepository.save(drive);
            log.info("Syncing branches from JD → {}. Retriggering eligibility for drive {}",
                    normalized, drive.getId());
            eligibleDriveRepository.deleteByDriveId(drive.getId());
            cgpaEligibilityService.scheduleAssignEligibleStudentsAfterCommit(drive.getId());
        }
    }
}