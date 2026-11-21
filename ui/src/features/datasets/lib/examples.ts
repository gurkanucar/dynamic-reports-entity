import type { DatasetDefinition } from "../types/dataset.types"

/**
 * Ready-made datasets to start from: the examples of http/datasets.http (generated from it). Some use fields only
 * some users may see (e.g. salary); running one as another user says which field is not allowed.
 */
export interface DatasetExample {
  id: number
  group: string
  title: string
  definition: DatasetDefinition
}

export const DATASET_EXAMPLES: DatasetExample[] = [
  {
    "id": 2,
    "group": "Gruplama",
    "title": "Departman bazında aktif çalışan sayısı ve maaş (en az 3 kişi)",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "department.name"
        },
        {
          "aggregate": "COUNT",
          "label": "Headcount"
        },
        {
          "field": "salary",
          "aggregate": "AVG",
          "label": "Average Salary"
        },
        {
          "field": "salary",
          "aggregate": "MAX",
          "label": "Top Salary"
        }
      ],
      "where": {
        "field": "active",
        "operator": "EQ",
        "value": true
      },
      "having": {
        "aggregate": "COUNT",
        "operator": "GTE",
        "value": 3
      },
      "sort": [
        {
          "field": "salary",
          "aggregate": "AVG",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 3,
    "group": "İlişkiler",
    "title": "Organizasyon şeması: üst departman, yönetici ve üye istatistikleri",
    "definition": {
      "root": "Department",
      "columns": [
        {
          "field": "parent.name",
          "label": "Parent"
        },
        {
          "field": "name",
          "label": "Department"
        },
        {
          "field": "manager.fullName",
          "label": "Manager"
        },
        {
          "field": "members.fullName",
          "aggregate": "COUNT",
          "label": "Headcount"
        },
        {
          "field": "members.hireDate",
          "aggregate": "MIN",
          "label": "Earliest Hire"
        }
      ],
      "sort": [
        {
          "field": "parent.name"
        },
        {
          "field": "name"
        }
      ]
    }
  },
  {
    "id": 4,
    "group": "İlişkiler",
    "title": "En az 3 doğrudan bağlısı olan yöneticiler",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "fullName",
          "label": "Manager"
        },
        {
          "field": "department.name"
        },
        {
          "field": "directReports.fullName",
          "aggregate": "COUNT",
          "label": "Direct Reports"
        },
        {
          "field": "directReports.salary",
          "aggregate": "SUM",
          "label": "Team Payroll"
        }
      ],
      "having": {
        "field": "directReports.fullName",
        "aggregate": "COUNT",
        "operator": "GTE",
        "value": 3
      },
      "sort": [
        {
          "field": "directReports.fullName",
          "aggregate": "COUNT",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 5,
    "group": "İlişkiler",
    "title": "Kişi, yöneticisi ve yöneticisinin yöneticisi",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "fullName"
        },
        {
          "field": "manager.fullName"
        },
        {
          "field": "manager.manager.fullName"
        }
      ],
      "where": {
        "field": "department.parent.name",
        "operator": "EQ",
        "value": "Information Technology"
      },
      "sort": [
        {
          "field": "fullName"
        }
      ]
    }
  },
  {
    "id": 6,
    "group": "İlişkiler",
    "title": "Yöneticisi BT'ye bağlı olanların eğitim kayıtları",
    "definition": {
      "root": "TrainingRecord",
      "columns": [
        {
          "field": "personnel.fullName"
        },
        {
          "field": "training.name"
        },
        {
          "field": "personnel.manager.fullName"
        },
        {
          "field": "personnel.manager.department.parent.name",
          "label": "Manager's Division"
        }
      ],
      "where": {
        "field": "personnel.manager.department.parent.name",
        "operator": "EQ",
        "value": "Information Technology"
      },
      "sort": [
        {
          "field": "personnel.fullName"
        },
        {
          "field": "training.name"
        }
      ]
    }
  },
  {
    "id": 7,
    "group": "Gruplama",
    "title": "Zorunlu eğitim uyumu: gecikenler ve 30 gün içinde dolacaklar",
    "definition": {
      "root": "TrainingRecord",
      "columns": [
        {
          "field": "personnel.department.name"
        },
        {
          "field": "training.category"
        },
        {
          "aggregate": "COUNT",
          "label": "Records"
        },
        {
          "field": "personnel.fullName",
          "aggregate": "COUNT_DISTINCT",
          "label": "People"
        },
        {
          "field": "score",
          "aggregate": "AVG",
          "label": "Average Score"
        }
      ],
      "where": {
        "conditions": [
          {
            "field": "training.mandatory",
            "operator": "EQ",
            "value": true
          },
          {
            "field": "status",
            "operator": "NOT_IN",
            "value": [
              "FAILED"
            ]
          },
          {
            "logic": "OR",
            "conditions": [
              {
                "field": "nextRenewalDate",
                "operator": "LAST_N_DAYS",
                "value": 365
              },
              {
                "field": "nextRenewalDate",
                "operator": "NEXT_N_DAYS",
                "value": 30
              }
            ]
          }
        ]
      },
      "having": {
        "aggregate": "COUNT",
        "operator": "GTE",
        "value": 1
      },
      "sort": [
        {
          "aggregate": "COUNT",
          "direction": "DESC"
        },
        {
          "field": "personnel.department.name"
        }
      ]
    }
  },
  {
    "id": 8,
    "group": "Filtreler",
    "title": "Profili olmayan kişiler",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "fullName"
        },
        {
          "field": "hireDate"
        }
      ],
      "where": {
        "field": "profile.birthDate",
        "operator": "IS_NULL"
      },
      "sort": [
        {
          "field": "hireDate",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 9,
    "group": "Filtreler",
    "title": "Beceri filtreleri: iç içe VEYA, şunlardan biri değil, ile başlar",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "fullName"
        },
        {
          "field": "department.name"
        },
        {
          "field": "skills.skill.name"
        }
      ],
      "where": {
        "conditions": [
          {
            "logic": "OR",
            "conditions": [
              {
                "field": "skills.skill.name",
                "operator": "IN",
                "value": [
                  "Kubernetes",
                  "Java"
                ]
              },
              {
                "field": "skills.skill.category",
                "operator": "STARTS_WITH",
                "value": "Fin"
              }
            ]
          },
          {
            "field": "department.name",
            "operator": "NOT_IN",
            "value": [
              "Sales"
            ]
          },
          {
            "field": "hireDate",
            "operator": "BETWEEN",
            "value": [
              "2015-01-01",
              "2025-12-31"
            ]
          }
        ]
      },
      "sort": [
        {
          "field": "fullName"
        },
        {
          "field": "skills.skill.name"
        }
      ]
    }
  },
  {
    "id": 10,
    "group": "Gruplama",
    "title": "Beceri başına aktif sahip sayısı",
    "definition": {
      "root": "Skill",
      "columns": [
        {
          "field": "category"
        },
        {
          "field": "name"
        },
        {
          "field": "holders.personnel.fullName",
          "aggregate": "COUNT",
          "label": "Holders"
        }
      ],
      "where": {
        "field": "holders.personnel.active",
        "operator": "EQ",
        "value": true
      },
      "having": {
        "field": "holders.personnel.fullName",
        "aggregate": "COUNT",
        "operator": "GTE",
        "value": 3
      },
      "sort": [
        {
          "field": "holders.personnel.fullName",
          "aggregate": "COUNT",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 11,
    "group": "Gruplama",
    "title": "Departman bazında proje portföyü: bütçe ve takvim",
    "definition": {
      "root": "Project",
      "columns": [
        {
          "field": "department.name",
          "label": "Owner"
        },
        {
          "field": "status"
        },
        {
          "aggregate": "COUNT",
          "label": "Projects"
        },
        {
          "field": "budget",
          "aggregate": "SUM",
          "label": "Total Budget"
        },
        {
          "field": "startDate",
          "aggregate": "MIN",
          "label": "First Start"
        },
        {
          "field": "endDate",
          "aggregate": "MAX",
          "label": "Last End"
        }
      ],
      "where": {
        "field": "status",
        "operator": "IN",
        "value": [
          "ACTIVE",
          "PLANNED",
          "ON_HOLD"
        ]
      },
      "sort": [
        {
          "field": "budget",
          "aggregate": "SUM",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 12,
    "group": "Gruplama",
    "title": "Proje bazında atama oranı ve ortalama maaş",
    "definition": {
      "root": "ProjectAssignment",
      "columns": [
        {
          "field": "project.code"
        },
        {
          "field": "project.name"
        },
        {
          "field": "project.department.name",
          "label": "Owner"
        },
        {
          "aggregate": "COUNT",
          "label": "People"
        },
        {
          "field": "allocationPercent",
          "aggregate": "SUM",
          "label": "Total Allocation (%)"
        },
        {
          "field": "personnel.salary",
          "aggregate": "AVG",
          "label": "Average Salary"
        }
      ],
      "having": {
        "field": "allocationPercent",
        "aggregate": "SUM",
        "operator": "GT",
        "value": 100
      },
      "sort": [
        {
          "field": "allocationPercent",
          "aggregate": "SUM",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 13,
    "group": "Gruplama",
    "title": "Departman başına farklı proje ve rol sayısı",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "department.name"
        },
        {
          "field": "projectAssignments.project.code",
          "aggregate": "COUNT_DISTINCT",
          "label": "Projects"
        },
        {
          "field": "projectAssignments.role",
          "aggregate": "COUNT_DISTINCT",
          "label": "Distinct Roles"
        }
      ],
      "sort": [
        {
          "field": "department.name"
        }
      ]
    }
  },
  {
    "id": 14,
    "group": "Tarih",
    "title": "2025'te aylık proje atamaları",
    "definition": {
      "root": "ProjectAssignment",
      "columns": [
        {
          "field": "assignedOn",
          "dateGroup": "MONTH"
        },
        {
          "aggregate": "COUNT",
          "label": "Assignments"
        },
        {
          "field": "allocationPercent",
          "aggregate": "SUM"
        }
      ],
      "where": {
        "field": "assignedOn",
        "dateGroup": "YEAR",
        "operator": "EQ",
        "value": 2025
      },
      "sort": [
        {
          "field": "assignedOn",
          "dateGroup": "MONTH"
        }
      ]
    }
  },
  {
    "id": 15,
    "group": "Tarih",
    "title": "Yıllara göre işe alımlar: aktif ve ayrılan",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "hireDate",
          "dateGroup": "YEAR"
        },
        {
          "field": "active"
        },
        {
          "aggregate": "COUNT",
          "label": "Hires"
        }
      ],
      "sort": [
        {
          "field": "hireDate",
          "dateGroup": "YEAR"
        },
        {
          "field": "active"
        }
      ]
    }
  },
  {
    "id": 16,
    "group": "Tarih",
    "title": "Son iki yılda kategori bazında çeyreklik eğitim trendi",
    "definition": {
      "root": "TrainingRecord",
      "columns": [
        {
          "field": "completedOn",
          "dateGroup": "QUARTER"
        },
        {
          "field": "training.category"
        },
        {
          "aggregate": "COUNT",
          "label": "Completions"
        },
        {
          "field": "score",
          "aggregate": "AVG",
          "label": "Average Score"
        }
      ],
      "where": {
        "field": "completedOn",
        "operator": "LAST_N_DAYS",
        "value": 730
      },
      "sort": [
        {
          "field": "completedOn",
          "dateGroup": "QUARTER"
        },
        {
          "field": "training.category"
        }
      ]
    }
  },
  {
    "id": 17,
    "group": "Tarih",
    "title": "Aylara göre doğum günleri",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "profile.birthDate",
          "dateGroup": "MONTH_OF_YEAR",
          "label": "Month"
        },
        {
          "aggregate": "COUNT"
        }
      ],
      "where": {
        "field": "profile.birthDate",
        "operator": "IS_NOT_NULL"
      },
      "sort": [
        {
          "field": "profile.birthDate",
          "dateGroup": "MONTH_OF_YEAR"
        }
      ]
    }
  },
  {
    "id": 27,
    "group": "Vitrin",
    "title": "Departmanlar arası proje kadrosu",
    "definition": {
      "root": "ProjectAssignment",
      "columns": [
        {
          "field": "project.code"
        },
        {
          "field": "project.name"
        },
        {
          "field": "project.status"
        },
        {
          "field": "project.department.name",
          "label": "Owning Team"
        },
        {
          "field": "project.department.parent.name",
          "label": "Owning Division"
        },
        {
          "field": "personnel.fullName",
          "label": "Person"
        },
        {
          "field": "personnel.department.name",
          "label": "Person's Team"
        },
        {
          "field": "personnel.manager.fullName",
          "label": "Line Manager"
        },
        {
          "field": "personnel.profile.city",
          "label": "City"
        },
        {
          "field": "role"
        },
        {
          "field": "allocationPercent"
        },
        {
          "field": "assignedOn"
        },
        {
          "field": "personnel.hireDate"
        }
      ],
      "where": {
        "conditions": [
          {
            "field": "project.status",
            "operator": "IN",
            "value": [
              "ACTIVE",
              "ON_HOLD"
            ]
          },
          {
            "logic": "OR",
            "conditions": [
              {
                "field": "allocationPercent",
                "operator": "GTE",
                "value": 50
              },
              {
                "field": "role",
                "operator": "IN",
                "value": [
                  "Lead",
                  "Architect"
                ]
              }
            ]
          },
          {
            "field": "personnel.active",
            "operator": "EQ",
            "value": true
          },
          {
            "field": "personnel.profile.city",
            "operator": "IS_NOT_NULL"
          },
          {
            "field": "assignedOn",
            "operator": "BETWEEN",
            "value": [
              "2025-01-01",
              "2026-12-31"
            ]
          },
          {
            "field": "project.department.parent.name",
            "operator": "NOT_IN",
            "value": [
              "Sales"
            ]
          }
        ]
      },
      "sort": [
        {
          "field": "project.code"
        },
        {
          "field": "allocationPercent",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 28,
    "group": "Vitrin",
    "title": "Bölüm kadro karnesi",
    "definition": {
      "root": "ProjectAssignment",
      "columns": [
        {
          "field": "project.department.parent.name",
          "label": "Division"
        },
        {
          "field": "project.status",
          "label": "Status"
        },
        {
          "field": "assignedOn",
          "dateGroup": "YEAR",
          "label": "Year"
        },
        {
          "aggregate": "COUNT",
          "label": "Assignments"
        },
        {
          "field": "personnel.email",
          "aggregate": "COUNT_DISTINCT",
          "label": "People"
        },
        {
          "field": "project.code",
          "aggregate": "COUNT_DISTINCT",
          "label": "Projects"
        },
        {
          "field": "allocationPercent",
          "aggregate": "SUM",
          "label": "Total Allocation (%)"
        },
        {
          "field": "allocationPercent",
          "aggregate": "AVG",
          "label": "Average Allocation (%)"
        },
        {
          "field": "personnel.salary",
          "aggregate": "AVG",
          "label": "Average Salary"
        },
        {
          "field": "personnel.hireDate",
          "aggregate": "MIN",
          "label": "Earliest Hire"
        },
        {
          "field": "project.budget",
          "aggregate": "MAX",
          "label": "Largest Budget"
        }
      ],
      "where": {
        "conditions": [
          {
            "field": "project.status",
            "operator": "NOT_IN",
            "value": [
              "COMPLETED"
            ]
          },
          {
            "logic": "OR",
            "conditions": [
              {
                "field": "personnel.department.parent.name",
                "operator": "EQ",
                "value": "Information Technology"
              },
              {
                "field": "project.budget",
                "operator": "GT",
                "value": 100000
              }
            ]
          },
          {
            "field": "assignedOn",
            "dateGroup": "YEAR",
            "operator": "GTE",
            "value": 2024
          }
        ]
      },
      "having": {
        "conditions": [
          {
            "aggregate": "COUNT",
            "operator": "GTE",
            "value": 5
          },
          {
            "field": "allocationPercent",
            "aggregate": "SUM",
            "operator": "GT",
            "value": 200
          }
        ]
      },
      "sort": [
        {
          "field": "allocationPercent",
          "aggregate": "SUM",
          "direction": "DESC"
        },
        {
          "field": "project.department.parent.name"
        }
      ]
    }
  },
  {
    "id": 29,
    "group": "Vitrin",
    "title": "Bölüm ve ekip bazında zorunlu eğitim uyumu",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "department.parent.name",
          "label": "Division"
        },
        {
          "field": "department.name",
          "label": "Team"
        },
        {
          "field": "trainingRecords.training.category",
          "label": "Category"
        },
        {
          "field": "email",
          "aggregate": "COUNT_DISTINCT",
          "label": "People"
        },
        {
          "field": "manager.email",
          "aggregate": "COUNT_DISTINCT",
          "label": "Managers"
        },
        {
          "field": "trainingRecords.status",
          "aggregate": "COUNT",
          "label": "Records"
        },
        {
          "field": "trainingRecords.score",
          "aggregate": "AVG",
          "label": "Average Score"
        },
        {
          "field": "trainingRecords.nextRenewalDate",
          "aggregate": "MIN",
          "label": "Earliest Renewal"
        }
      ],
      "where": {
        "conditions": [
          {
            "field": "active",
            "operator": "EQ",
            "value": true
          },
          {
            "field": "trainingRecords.training.mandatory",
            "operator": "EQ",
            "value": true
          },
          {
            "logic": "OR",
            "conditions": [
              {
                "field": "trainingRecords.nextRenewalDate",
                "operator": "LAST_N_DAYS",
                "value": 90
              },
              {
                "field": "trainingRecords.nextRenewalDate",
                "operator": "NEXT_N_DAYS",
                "value": 60
              }
            ]
          },
          {
            "field": "profile.city",
            "operator": "IN",
            "value": [
              "Istanbul",
              "Ankara",
              "Izmir"
            ]
          }
        ]
      },
      "having": {
        "field": "email",
        "aggregate": "COUNT_DISTINCT",
        "operator": "GTE",
        "value": 3
      },
      "sort": [
        {
          "field": "trainingRecords.score",
          "aggregate": "AVG"
        }
      ]
    }
  },
  {
    "id": 30,
    "group": "Listeler",
    "title": "Kişi başına beceri, eğitim ve proje listeleri",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "fullName"
        },
        {
          "field": "email"
        },
        {
          "field": "department.name"
        },
        {
          "field": "skills.skill.name",
          "aggregate": "LIST",
          "label": "Skills"
        },
        {
          "field": "trainingRecords.training.name",
          "aggregate": "LIST",
          "label": "Trainings"
        },
        {
          "field": "projectAssignments.project.code",
          "aggregate": "LIST",
          "label": "Projects"
        }
      ],
      "where": {
        "field": "department.parent.name",
        "operator": "EQ",
        "value": "Information Technology"
      },
      "sort": [
        {
          "field": "fullName"
        },
        {
          "field": "email"
        }
      ]
    }
  },
  {
    "id": 31,
    "group": "Listeler",
    "title": "Departman başına üye listesi, alt ekipler ve proje kodları",
    "definition": {
      "root": "Department",
      "columns": [
        {
          "field": "name"
        },
        {
          "field": "manager.fullName",
          "label": "Manager"
        },
        {
          "field": "subDepartments.name",
          "aggregate": "LIST",
          "label": "Sub-teams"
        },
        {
          "field": "projects.code",
          "aggregate": "LIST",
          "label": "Projects"
        },
        {
          "field": "members.fullName",
          "aggregate": "COUNT_DISTINCT",
          "label": "Headcount"
        }
      ],
      "where": {
        "field": "parent.name",
        "operator": "IS_NULL"
      },
      "sort": [
        {
          "field": "name"
        }
      ]
    }
  },
  {
    "id": 32,
    "group": "Listeler",
    "title": "Kişi 360",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "fullName"
        },
        {
          "field": "email"
        },
        {
          "field": "hireDate"
        },
        {
          "field": "department.name",
          "label": "Team"
        },
        {
          "field": "department.parent.name",
          "label": "Division"
        },
        {
          "field": "manager.fullName",
          "label": "Manager"
        },
        {
          "field": "manager.department.parent.name",
          "label": "Manager's Division"
        },
        {
          "field": "profile.city",
          "label": "City"
        },
        {
          "field": "skills.skill.name",
          "aggregate": "LIST",
          "label": "Skills"
        },
        {
          "field": "skills.skill.category",
          "aggregate": "LIST",
          "label": "Skill Categories"
        },
        {
          "field": "trainingRecords.training.name",
          "aggregate": "LIST",
          "label": "Trainings"
        },
        {
          "field": "trainingRecords.status",
          "aggregate": "LIST",
          "label": "Training Statuses"
        },
        {
          "field": "trainingRecords.completedOn",
          "dateGroup": "YEAR",
          "aggregate": "LIST",
          "label": "Training Years"
        },
        {
          "field": "projectAssignments.project.code",
          "aggregate": "LIST",
          "label": "Projects"
        },
        {
          "field": "projectAssignments.role",
          "aggregate": "LIST",
          "label": "Roles"
        },
        {
          "field": "projectAssignments.project.department.parent.name",
          "aggregate": "LIST",
          "label": "Divisions Worked For"
        },
        {
          "field": "directReports.fullName",
          "aggregate": "LIST",
          "label": "Direct Reports"
        },
        {
          "field": "managedDepartments.name",
          "aggregate": "LIST",
          "label": "Departments Managed"
        }
      ],
      "where": {
        "conditions": [
          {
            "field": "active",
            "operator": "EQ",
            "value": true
          },
          {
            "logic": "OR",
            "conditions": [
              {
                "field": "department.parent.name",
                "operator": "EQ",
                "value": "Information Technology"
              },
              {
                "field": "manager.fullName",
                "operator": "EQ",
                "value": "Diana Prince"
              }
            ]
          },
          {
            "field": "profile.city",
            "operator": "IN",
            "value": [
              "Istanbul",
              "Ankara"
            ]
          }
        ]
      },
      "sort": [
        {
          "field": "hireDate"
        },
        {
          "field": "email"
        }
      ]
    }
  },
  {
    "id": 33,
    "group": "Listeler",
    "title": "Departman 360",
    "definition": {
      "root": "Department",
      "columns": [
        {
          "field": "name"
        },
        {
          "field": "parent.name",
          "label": "Division"
        },
        {
          "field": "manager.fullName",
          "label": "Manager"
        },
        {
          "field": "manager.profile.city",
          "label": "Manager's City"
        },
        {
          "field": "subDepartments.name",
          "aggregate": "LIST",
          "label": "Sub-teams"
        },
        {
          "field": "members.skills.skill.name",
          "aggregate": "LIST",
          "label": "Skills in Team"
        },
        {
          "field": "members.trainingRecords.training.category",
          "aggregate": "LIST",
          "label": "Training Categories"
        },
        {
          "field": "members.profile.city",
          "aggregate": "LIST",
          "label": "Cities"
        },
        {
          "field": "members.hireDate",
          "dateGroup": "YEAR",
          "aggregate": "LIST",
          "label": "Hiring Years"
        },
        {
          "field": "projects.code",
          "aggregate": "LIST",
          "label": "Projects Owned"
        },
        {
          "field": "projects.assignments.role",
          "aggregate": "LIST",
          "label": "Roles on Own Projects"
        },
        {
          "field": "members.projectAssignments.project.department.name",
          "aggregate": "LIST",
          "label": "Teams Worked For"
        }
      ],
      "where": {
        "field": "parent.name",
        "operator": "IS_NOT_NULL"
      },
      "sort": [
        {
          "field": "parent.name"
        },
        {
          "field": "name"
        }
      ]
    }
  },
  {
    "id": 34,
    "group": "Listeler",
    "title": "Eğitim 360",
    "definition": {
      "root": "Training",
      "columns": [
        {
          "field": "name"
        },
        {
          "field": "category"
        },
        {
          "field": "mandatory"
        },
        {
          "field": "validityMonths"
        },
        {
          "field": "records.personnel.department.parent.name",
          "aggregate": "LIST",
          "label": "Divisions"
        },
        {
          "field": "records.status",
          "aggregate": "LIST",
          "label": "Statuses"
        },
        {
          "field": "records.completedOn",
          "dateGroup": "YEAR",
          "aggregate": "LIST",
          "label": "Completion Years"
        },
        {
          "field": "records.personnel.skills.skill.name",
          "aggregate": "LIST",
          "label": "Skills of Attendees"
        }
      ],
      "sort": [
        {
          "field": "category"
        },
        {
          "field": "name"
        }
      ]
    }
  },
  {
    "id": 39,
    "group": "Karşılaştırma",
    "title": "Kendi departmanı dışındaki projelerde çalışanlar",
    "definition": {
      "root": "ProjectAssignment",
      "columns": [
        {
          "field": "personnel.fullName"
        },
        {
          "field": "personnel.department.name",
          "label": "Own Team"
        },
        {
          "field": "project.code"
        },
        {
          "field": "project.department.name",
          "label": "Project Owner"
        },
        {
          "field": "role"
        }
      ],
      "where": {
        "field": "personnel.department.id",
        "operator": "NOT_EQ",
        "otherField": "project.department.id"
      },
      "sort": [
        {
          "field": "project.code"
        },
        {
          "field": "personnel.fullName"
        }
      ]
    }
  },
  {
    "id": 40,
    "group": "Filtreler",
    "title": "Geçerli yangın eğitimi olmayan ama projesi olan aktif çalışanlar",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "fullName"
        },
        {
          "field": "email"
        },
        {
          "field": "department.name"
        }
      ],
      "where": {
        "conditions": [
          {
            "field": "active",
            "operator": "EQ",
            "value": true
          },
          {
            "relation": "trainingRecords",
            "operator": "NOT_EXISTS",
            "match": {
              "conditions": [
                {
                  "field": "training.name",
                  "operator": "EQ",
                  "value": "Fire Safety"
                },
                {
                  "field": "nextRenewalDate",
                  "operator": "GTE",
                  "value": "2026-01-01"
                }
              ]
            }
          },
          {
            "relation": "projectAssignments",
            "operator": "EXISTS"
          }
        ]
      },
      "sort": [
        {
          "field": "fullName"
        },
        {
          "field": "email"
        }
      ]
    }
  },
  {
    "id": 41,
    "group": "Listeler",
    "title": "Kişi başına son 3 başarılı eğitim",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "fullName"
        },
        {
          "field": "trainingRecords",
          "aggregate": "LIST",
          "label": "Recent Trainings",
          "fields": [
            "completedOn",
            "training.name",
            "score"
          ],
          "where": {
            "field": "status",
            "operator": "EQ",
            "value": "PASSED"
          },
          "order": "DESC",
          "limit": 3
        },
        {
          "field": "projectAssignments",
          "aggregate": "LIST",
          "label": "Projects",
          "fields": [
            "project.code",
            "role",
            "allocationPercent"
          ]
        }
      ],
      "where": {
        "field": "department.parent.name",
        "operator": "EQ",
        "value": "Information Technology"
      },
      "sort": [
        {
          "field": "fullName"
        },
        {
          "field": "email"
        }
      ]
    }
  },
  {
    "id": 42,
    "group": "Gruplama",
    "title": "Departman panosu: alt toplamlar, listeler ve koşullar",
    "definition": {
      "root": "Department",
      "perRoot": true,
      "columns": [
        {
          "field": "name"
        },
        {
          "field": "parent.name",
          "label": "Division"
        },
        {
          "field": "members.fullName",
          "aggregate": "COUNT",
          "label": "Headcount"
        },
        {
          "field": "members.salary",
          "aggregate": "AVG",
          "label": "Average Salary"
        },
        {
          "field": "members.trainingRecords.status",
          "aggregate": "COUNT",
          "label": "Failed Trainings",
          "where": {
            "field": "trainingRecords.status",
            "operator": "EQ",
            "value": "FAILED"
          }
        },
        {
          "field": "projects.budget",
          "aggregate": "SUM",
          "label": "Project Budget"
        },
        {
          "field": "projects.code",
          "aggregate": "LIST",
          "label": "Projects",
          "limit": 5
        },
        {
          "field": "subDepartments.name",
          "aggregate": "LIST",
          "label": "Sub-teams"
        }
      ],
      "having": {
        "field": "members.fullName",
        "aggregate": "COUNT",
        "operator": "GTE",
        "value": 3
      },
      "sort": [
        {
          "field": "members.fullName",
          "aggregate": "COUNT",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 43,
    "group": "Vitrin",
    "title": "Kişi 360 v2: karşılaştırma, var/yok ve nesne listeleri",
    "definition": {
      "root": "Personnel",
      "perRoot": true,
      "columns": [
        {
          "field": "fullName"
        },
        {
          "field": "department.name",
          "label": "Team"
        },
        {
          "field": "manager.fullName",
          "label": "Manager"
        },
        {
          "field": "manager.department.name",
          "label": "Manager's Team"
        },
        {
          "field": "trainingRecords.status",
          "aggregate": "COUNT",
          "label": "Trainings"
        },
        {
          "field": "trainingRecords.status",
          "aggregate": "COUNT",
          "label": "Failed",
          "where": {
            "field": "status",
            "operator": "EQ",
            "value": "FAILED"
          }
        },
        {
          "field": "trainingRecords.score",
          "aggregate": "AVG",
          "label": "Average Score"
        },
        {
          "field": "trainingRecords.nextRenewalDate",
          "aggregate": "MIN",
          "label": "Next Renewal"
        },
        {
          "field": "trainingRecords",
          "aggregate": "LIST",
          "label": "Due Soon",
          "fields": [
            "training.name",
            "nextRenewalDate"
          ],
          "where": {
            "field": "nextRenewalDate",
            "operator": "NEXT_N_DAYS",
            "value": 90
          }
        },
        {
          "field": "projectAssignments.allocationPercent",
          "aggregate": "SUM",
          "label": "Allocation (%)"
        },
        {
          "field": "projectAssignments",
          "aggregate": "LIST",
          "label": "Projects",
          "fields": [
            "project.code",
            "role"
          ]
        },
        {
          "field": "skills.skill.name",
          "aggregate": "LIST",
          "label": "Skills",
          "limit": 3
        }
      ],
      "where": {
        "conditions": [
          {
            "field": "active",
            "operator": "EQ",
            "value": true
          },
          {
            "field": "department.id",
            "operator": "NOT_EQ",
            "otherField": "manager.department.id"
          },
          {
            "relation": "skills",
            "operator": "EXISTS",
            "match": {
              "field": "skill.category",
              "operator": "EQ",
              "value": "Technical"
            }
          }
        ]
      },
      "having": {
        "field": "projectAssignments.allocationPercent",
        "aggregate": "SUM",
        "operator": "GTE",
        "value": 50
      },
      "sort": [
        {
          "field": "trainingRecords.score",
          "aggregate": "AVG",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 44,
    "group": "Filtreler",
    "title": "Eğitimlerin tüm alanları",
    "definition": {
      "root": "Training",
      "columns": [
        {
          "field": "id"
        },
        {
          "field": "name"
        },
        {
          "field": "category"
        },
        {
          "field": "validityMonths"
        },
        {
          "field": "mandatory"
        }
      ],
      "sort": [
        {
          "field": "category"
        },
        {
          "field": "name"
        }
      ]
    }
  },
  {
    "id": 45,
    "group": "Karşılaştırma",
    "title": "Kendi departmanının ortalamasından fazla kazananlar",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "fullName"
        },
        {
          "field": "department.name"
        },
        {
          "field": "salary"
        }
      ],
      "where": {
        "field": "salary",
        "operator": "GT",
        "otherField": "department.members.salary",
        "otherAggregate": "AVG",
        "otherWhere": {
          "field": "active",
          "operator": "EQ",
          "value": true
        }
      },
      "sort": [
        {
          "field": "salary",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 46,
    "group": "Karşılaştırma",
    "title": "Şirket ortalamasının üstünde maaş",
    "definition": {
      "root": "Personnel",
      "columns": [
        {
          "field": "department.parent.name",
          "label": "Division"
        },
        {
          "aggregate": "COUNT",
          "label": "Above Company Average"
        }
      ],
      "where": {
        "field": "salary",
        "operator": "GT",
        "otherField": "salary",
        "otherAggregate": "AVG"
      },
      "sort": [
        {
          "aggregate": "COUNT",
          "direction": "DESC"
        }
      ]
    }
  },
  {
    "id": 47,
    "group": "Karşılaştırma",
    "title": "Eğitim ortalamasının altında kalan puanlar",
    "definition": {
      "root": "TrainingRecord",
      "columns": [
        {
          "field": "personnel.fullName"
        },
        {
          "field": "training.name"
        },
        {
          "field": "score"
        }
      ],
      "where": {
        "conditions": [
          {
            "field": "status",
            "operator": "EQ",
            "value": "PASSED"
          },
          {
            "field": "score",
            "operator": "LT",
            "otherField": "training.records.score",
            "otherAggregate": "AVG"
          }
        ]
      },
      "sort": [
        {
          "field": "training.name"
        },
        {
          "field": "score"
        }
      ]
    }
  }
]
