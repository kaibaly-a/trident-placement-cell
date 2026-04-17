# Branch-Specific Drive Visibility Feature

## ✅ Feature Status: FULLY IMPLEMENTED

This document explains how the branch-specific drive filtering feature works in your Trident Placement Cell application.

---

## 📋 Overview

When an admin posts a recruitment drive, they select which branches (CSE, ECE, ETC, etc.) are eligible for that drive. Only students from those branches see the drive in their eligible drives list. Students from other branches will not see the drive at all.

**Example:**
- Admin posts a drive for "Google" and selects branches: **CSE, ECE**
- A **CSE student** sees the Google drive ✅
- An **ETC student** does NOT see the Google drive ❌
- An **ME student** does NOT see the Google drive ❌

---

## 🏗️ Architecture Overview

### Entity Structure

```
DRIVES (Main table)
├── ID (PK)
├── COMPANY_NAME
├── ROLE
├── MINIMUM_CGPA
├── STATUS
└── ... other fields

DRIVE_BRANCHES (ElementCollection)
├── DRIVE_ID (FK) 
└── BRANCH_CODE ("CSE", "ECE", "ETC", etc.)

STUDENT
├── REGDNO (PK)
├── NAME
├── BRANCH_CODE ⭐ (key for filtering)
├── COURSE
└── ... other fields

ELIGIBLE_DRIVES
├── ID (PK)
├── REGDNO (FK to STUDENT)
├── DRIVE_ID (FK to DRIVE)
└── Created when admin posts a drive
```

---

## 🔄 How It Works: Step-by-Step

### Step 1: Admin Creates a Drive
```
Admin submits DriveCreateRequest with:
- companyName: "Google"
- minimumCgpa: 8.0
- allowedBranches: ["CSE", "ECE"] ⭐
```

**Code Location:** [AdminDriveServiceImpl.createDrive()](src/main/java/com/trident/placement/service/AdminDriveServiceImpl.java)

### Step 2: Drive is Saved with Branches
```java
Drive drive = Drive.builder()
    .companyName("Google")
    .minimumCgpa(8.0)
    .branches(["CSE", "ECE"])  // ⭐ Branches stored in DRIVE_BRANCHES table
    .build();

adminDriveRepository.save(drive);
```

**Database:**
```sql
INSERT INTO DRIVES (ID, COMPANY_NAME, MINIMUM_CGPA, ...) 
VALUES (1, 'Google', 8.0, ...);

INSERT INTO DRIVE_BRANCHES (DRIVE_ID, BRANCH_CODE) VALUES (1, 'CSE');
INSERT INTO DRIVE_BRANCHES (DRIVE_ID, BRANCH_CODE) VALUES (1, 'ECE');
```

### Step 3: Background Job Assigns Eligible Students
After the drive is saved, `CgpaEligibilityService.assignEligibleStudents()` runs asynchronously:

```
For each STUDENT where:
  ✓ Branch code is IN ['CSE', 'ECE']  ⭐ BRANCH FILTERING
  ✓ CGPA >= drive.minimumCgpa (8.0)
  ✓ Final year (2021+ batch)
  
  → Create an ELIGIBLE_DRIVE record
```

**Code Location:** [CgpaEligibilityService.assignEligibleStudentsInternal()](src/main/java/com/trident/placement/service/CgpaEligibilityService.java)

**Database Query:**
```sql
-- Pseudo-code (actual JPQL in StudentRepository)
SELECT DISTINCT s.* FROM STUDENT s
  LEFT JOIN STUDENT_CGPA sc ON s.REGDNO = sc.REGDNO
WHERE s.ADMISSIONYEAR >= '2021'
  AND sc.CGPA >= 8.0
  AND s.BRANCH_CODE IN ('CSE', 'ECE')      -- ⭐ BRANCH FILTER
  AND s.DEGREE_YOP >= YEAR(CURDATE());

-- Then insert into ELIGIBLE_DRIVES
INSERT INTO ELIGIBLE_DRIVES (REGDNO, DRIVE_ID) 
SELECT s.REGDNO, ? FROM STUDENT s ...;
```

### Step 4: Student Requests Eligible Drives

**API Call:**
```
GET /api/drives/eligible/{regdno}
```

**Endpoint:** [DriveController.getEligibleDrives()](src/main/java/com/trident/placement/controller/DriveController.java)

### Step 5: Service Fetches Filtered Drives

```java
// DriveServiceImpl.getEligibleDrives()
List<Drive> eligibleDrives = cgpaEligibilityService
    .getEligibleDrivesForStudent(regdno);

// CgpaEligibilityService.getEligibleDrivesForStudent()
String studentBranchCode = studentRepository
    .findById(regdno)
    .map(Student::getBranchCode)
    .get();  // "CSE"

// ⭐ KEY QUERY with Branch Check
eligibleDriveRepository.findEligibleDrivesForStudent(
    regdno,           // "2021001"
    studentBranchCode // "CSE"
);
```

**Database Query (JPQL):**
```sql
SELECT e FROM EligibleDrive e
JOIN FETCH e.drive d
WHERE e.regdno = '2021001'
  AND d.status = 'OPEN'
  AND ('CSE' MEMBER OF d.branches OR d.branches IS EMPTY)  ⭐ BRANCH CHECK
ORDER BY d.lastDate ASC;
```

**Explanation:**
- `'CSE' MEMBER OF d.branches`: The student's branch is in the drive's allowed branches
- `OR d.branches IS EMPTY`: If drive has NO branch restrictions, all students are eligible

### Step 6: Response with Branch Information

**Response:**
```json
{
  "status": "success",
  "data": [
    {
      "id": 1,
      "companyName": "Google",
      "role": "Software Engineer",
      "type": "On-Campus",
      "lpaPackage": 15.5,
      "minimumCgpa": 8.0,
      "lastDate": "31-05-26",
      "status": "OPEN",
      "eligibleBranches": ["CSE", "ECE"]  ⭐ Branches included in response
    }
  ]
}
```

---

## 🗂️ Key Code Files

| File | Purpose |
|------|---------|
| [Drive.java](src/main/java/com/trident/placement/entity/Drive.java) | Entity with `branches` field (ElementCollection) |
| [Student.java](src/main/java/com/trident/placement/entity/Student.java) | Entity with `branchCode` field |
| [DriveDTO.java](src/main/java/com/trident/placement/dto/DriveDTO.java) | DTO includes `eligibleBranches` |
| [AdminDriveServiceImpl.java](src/main/java/com/trident/placement/service/AdminDriveServiceImpl.java) | Creates drive with branches |
| [CgpaEligibilityService.java](src/main/java/com/trident/placement/service/CgpaEligibilityService.java) | Assigns eligible students by branch + CGPA |
| [DriveServiceImpl.java](src/main/java/com/trident/placement/service/DriveServiceImpl.java) | Returns filtered eligible drives |
| [EligibleDriveRepository.java](src/main/java/com/trident/placement/repository/EligibleDriveRepository.java) | JPQL query with branch filtering |
| [StudentRepository.java](src/main/java/com/trident/placement/repository/StudentRepository.java) | Finds students by branch + CGPA |

---

## 📊 Database Schema

### DRIVES Table
```sql
CREATE TABLE DRIVES (
  ID NUMBER PRIMARY KEY,
  COMPANY_NAME VARCHAR2(200) NOT NULL,
  MINIMUM_CGPA NUMBER(4,2),
  STATUS VARCHAR2(10),
  ...
);
```

### DRIVE_BRANCHES Table (ElementCollection)
```sql
CREATE TABLE DRIVE_BRANCHES (
  DRIVE_ID NUMBER NOT NULL,
  BRANCH_CODE VARCHAR2(20),
  CONSTRAINT FK_DRIVE_BRANCHES 
    FOREIGN KEY (DRIVE_ID) REFERENCES DRIVES(ID)
);

-- Example data:
-- (1, 'CSE')
-- (1, 'ECE')
-- (2, 'CSE')
-- (2, 'ECE')
-- (2, 'ETC')
```

### STUDENT Table
```sql
CREATE TABLE STUDENT (
  REGDNO VARCHAR2(255) PRIMARY KEY,
  NAME VARCHAR2(255),
  BRANCH_CODE VARCHAR2(255),  -- ⭐ Key for branch filtering
  ADMISSIONYEAR VARCHAR2(20),
  ...
);
```

### ELIGIBLE_DRIVES Table
```sql
CREATE TABLE ELIGIBLE_DRIVES (
  ID NUMBER PRIMARY KEY,
  REGDNO VARCHAR2(255),
  DRIVE_ID NUMBER,
  CONSTRAINT FK_STUDENT FOREIGN KEY (REGDNO) REFERENCES STUDENT(REGDNO),
  CONSTRAINT FK_DRIVE FOREIGN KEY (DRIVE_ID) REFERENCES DRIVES(ID)
);

-- Example: After admin posts Google drive with [CSE, ECE]
-- (101, '2021001', 1)  -- CSE student eligible
-- (102, '2021002', 1)  -- ECE student eligible
-- (103, '2021003', 1)  -- CSE student eligible
-- NOT inserted: ETC students
```

---

## 🧪 How to Test

### Scenario 1: Admin Creates a CSE-Only Drive

**Admin Action:**
```
POST /api/admin/drives
{
  "companyName": "Microsoft",
  "role": "Cloud Engineer",
  "minimumCgpa": 7.5,
  "driveType": "Virtual",
  "lastDate": "2026-06-30",
  "allowedBranches": ["CSE"]
}
```

**What Happens:**
1. Drive is created with branches = ["CSE"]
2. `assignEligibleStudents()` runs asynchronously
3. All N CSE students with CGPA >= 7.5 get an ELIGIBLE_DRIVES record
4. ETC/ECE students are NOT eligible

### Scenario 2: CSE Student Fetches Available Drives

**Student Action (CSE, REGDNO: 2021001):**
```
GET /api/drives/eligible/2021001
```

**Expected Response:**
```json
{
  "status": "success",
  "data": [
    {
      "id": 1,
      "companyName": "Microsoft",
      "role": "Cloud Engineer",
      "minimumCgpa": 7.5,
      "eligibleBranches": ["CSE"],
      "status": "OPEN"
    }
  ]
}
```

**Why?**
- Student's branch = "CSE"
- Drive's branches = ["CSE"]
- Student's CGPA meets minimum requirement
- Query: `"CSE" MEMBER OF ["CSE"]` ✅

### Scenario 3: ETC Student Fetches Available Drives

**Student Action (ETC, REGDNO: 2021099):**
```
GET /api/drives/eligible/2021099
```

**Expected Response:**
```json
{
  "status": "success",
  "data": []  // Empty — no eligible drives
}
```

**Why?**
- Student's branch = "ETC"
- Drive's branches = ["CSE"]
- Query: `"ETC" MEMBER OF ["CSE"]` ❌

---

## 🔐 Branch Code Validation

Supported branch codes are normalized and validated in `BranchCodeUtils.java`:

```java
// Example branch codes:
"CSE", "ECE", "ETC", "ME", "CE", "LE"

// Automatic normalization:
" cse " → "CSE"
"[cse, ece]" → ["CSE", "ECE"]
"cse,ece,etc" → ["CSE", "ECE", "ETC"]
```

**Location:** [BranchCodeUtils.java](src/main/java/com/trident/placement/util/BranchCodeUtils.java)

---

## ⚙️ Configuration & Settings

### Default Branches (if needed)
Currently, all branch codes are user-defined. If you want to restrict to predefined branches, update:

```java
// BranchCodeUtils.java
public static final Set<String> VALID_BRANCHES = Set.of(
    "CSE", "ECE", "ETC", "ME", "CE", "LE"
);
```

### Empty Branches = Open to All
If admin doesn't select any branch:
```sql
AND ('CSE' MEMBER OF d.branches OR d.branches IS EMPTY)
```

If `d.branches IS EMPTY`, all students are eligible.

---

## 🚀 Production Checklist

- [x] Drive entity has `branches` field
- [x] Student entity has `branchCode` field
- [x] Eligibility assignment filters by branch
- [x] Student API returns only eligible drives
- [x] DTO includes `eligibleBranches` in response
- [x] JPQL query uses proper branch filtering
- [x] Oracle 11g compatible (no FETCH FIRST issues)

---

## 📝 Notes & Best Practices

### Note 1: Admin MUST Select Branches
When creating a drive, admin must select at least one branch. If no branches are selected, **no students will be marked eligible**.

### Note 2: CGPA Refresh Required
Before admin posts drives:
1. Go to admin dashboard
2. Click "Refresh CGPA" (runs async background job)
3. Wait for completion notification
4. THEN create drives

If CGPA is not refreshed, eligible_drives table will be empty!

### Note 3: Real-Time Branch Check
The JPQL query includes both:
1. **Database-level filtering** (eligible_drives table)
2. **Real-time branch check** (`MEMBER OF` clause)

This double filtering prevents students from seeing drives intended for other branches.

### Note 4: Performance
- First query (admin creates drive): Automatic via async job
- Subsequent queries (student fetches drives): DB query with branch filter
- Index recommendation: Add index on `ELIGIBLE_DRIVES(REGDNO, DRIVE_ID)`

---

## 🐛 Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| Student sees drives from other branches | Branch filter not applied | Verify student's `BRANCH_CODE` in DB matches drive's `DRIVE_BRANCHES` |
| No eligible drives shown despite meeting CGPA | CGPA not refreshed | Admin must click "Refresh CGPA" before creating drive |
| All branches see same drives | Admin selected "Open to All" | Verify the drive has branch restrictions in request |
| Database constraint error on DRIVE_BRANCHES insert | Invalid branch code format | Ensure branch codes match predefined set (CSE, ECE, etc.) |

---

## 📚 Related Documentation

- [CgpaEligibilityService](src/main/java/com/trident/placement/service/CgpaEligibilityService.java)
- [EligibleDriveRepository](src/main/java/com/trident/placement/repository/EligibleDriveRepository.java)
- [BranchCodeUtils](src/main/java/com/trident/placement/util/BranchCodeUtils.java)
- [StudentRepository](src/main/java/com/trident/placement/repository/StudentRepository.java)

---

## 🎯 Summary

Your application **already has fully-implemented branch-specific drive visibility**. The feature works through:

1. **Admin selects branches** when creating a drive
2. **Background job filters students** by branch + CGPA
3. **Student sees only eligible drives** via smart JPQL query
4. **Response includes branches** in DTO

No further code changes are needed! Students will automatically see only drives for their branch. ✅

