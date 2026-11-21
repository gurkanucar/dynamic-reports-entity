/** Shapes of the master-data admin APIs (/api/departments, /api/personnel, /api/projects, /api/trainings ...). */

export interface Ref {
  id: number
  name: string | null
}

export interface Versioned {
  id: number
  version: number
}

export interface Department extends Versioned {
  name: string
  costCenter: string | null
  parent: Ref | null
  manager: Ref | null
  subDepartments: number
  /** e.g. { "çalışan": 12, "proje": 3 } */
  usage: Record<string, number>
}

export interface DepartmentRequest {
  name: string
  costCenter?: string | null
  parentId?: number | null
  managerId?: number | null
}

export interface Personnel extends Versioned {
  username: string | null
  fullName: string
  email: string | null
  hireDate: string | null
  active: boolean
  department: Ref | null
  manager: Ref | null
  directReports: number
  skillCount: number
  /** Only with SALARY_VIEW / NATIONAL_ID_VIEW. */
  salary: number | null
  nationalId: string | null
}

export interface Profile {
  birthDate: string | null
  bloodType: string | null
  city: string | null
  emergencyContact: string | null
}

export interface SkillRef {
  id: number
  name: string
  category: string | null
}

export interface PersonnelDetail {
  personnel: Personnel
  profile: Profile | null
  skills: SkillRef[]
  canSeeSalary: boolean
  canSeeNationalId: boolean
}

export interface PersonnelRequest {
  username?: string | null
  fullName: string
  email?: string | null
  hireDate?: string | null
  active: boolean
  departmentId?: number | null
  managerId?: number | null
  salary?: number | null
  nationalId?: string | null
  profile?: Profile | null
}

export interface Skill extends Versioned {
  name: string
  category: string | null
  holders: number
}

export interface SkillRequest {
  name: string
  category?: string | null
}

export type ProjectStatus = "PLANNED" | "ACTIVE" | "ON_HOLD" | "COMPLETED"

export interface Project extends Versioned {
  code: string
  name: string
  status: ProjectStatus
  budget: number | null
  startDate: string | null
  endDate: string | null
  department: Ref | null
  assignments: number
  /** Sum of the allocation percentages. */
  allocation: number
}

export interface ProjectRequest {
  code: string
  name: string
  status: ProjectStatus
  budget?: number | null
  startDate?: string | null
  endDate?: string | null
  departmentId?: number | null
}

export interface Assignment extends Versioned {
  projectId: number
  personnel: Ref
  role: string | null
  allocationPercent: number | null
  assignedOn: string | null
}

export interface AssignmentRequest {
  personnelId: number
  role?: string | null
  allocationPercent?: number | null
  assignedOn?: string | null
}

export interface Training extends Versioned {
  name: string
  category: string | null
  validityMonths: number | null
  mandatory: boolean
  internalNotes: string | null
  records: number
  overdue: number
}

export interface TrainingRequest {
  name: string
  category?: string | null
  validityMonths?: number | null
  mandatory: boolean
  internalNotes?: string | null
}

export type RecordStatus = "PASSED" | "FAILED" | "IN_PROGRESS"

export interface TrainingRecord extends Versioned {
  personnel: Ref
  training: Ref
  completedOn: string | null
  nextRenewalDate: string | null
  score: number | null
  status: RecordStatus
}

export interface TrainingRecordRequest {
  personnelId: number
  trainingId: number
  completedOn?: string | null
  /** Empty: completedOn + the training's validity. */
  nextRenewalDate?: string | null
  score?: number | null
  status: RecordStatus
}
