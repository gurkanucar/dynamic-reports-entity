#!/usr/bin/env python3
"""
Fills a running platform with realistic content through its public API, from simple to complex, so every screen
has something to show: saved datasets, styled mail templates, approval groups, flows with schedules and triggers,
and run history (manual runs, webhook calls, a Kafka message, approved / rejected / pending approvals).

Everything goes through the same checks as the UI (permissions, validation). Safe to run again: what already exists
(by name) is left alone; runs are added each time.

    python3 scripts/populate_demo.py                 # http://localhost:8080
    PLATFORM_URL=https://veri-akis.example.com DEMO_PASSWORD=... python3 scripts/populate_demo.py   # a deployment

Users are the demo users (password DEMO_PASSWORD, "password" by default): admin@mail.com (everything), alice (HR), bob (IT, salaries),
carol (Software Development only).
"""
import base64
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("PLATFORM_URL", "http://localhost:8080").rstrip("/")
# the demo users' password: DEMO_PASSWORD of the server (docker-compose.yml requires one)
PASSWORD = os.environ.get("DEMO_PASSWORD", "password")
ADMIN, ALICE, BOB, CAROL = "admin@mail.com", "alice", "bob", "carol"


def call(method, path, user=None, body=None, headers=None, raw=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Accept", "application/json")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    if user:
        token = base64.b64encode(f"{user}:{PASSWORD}".encode()).decode()
        request.add_header("Authorization", "Basic " + token)
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            text = response.read().decode()
            return response.status, (json.loads(text) if text.strip().startswith(("{", "[")) else text)
    except urllib.error.HTTPError as error:
        text = error.read().decode()
        try:
            return error.code, json.loads(text)
        except ValueError:
            return error.code, text


def must(result, what):
    status, body = result
    if status >= 300:
        print(f"  ✗ {what}: HTTP {status} {json.dumps(body, ensure_ascii=False)[:400]}")
        sys.exit(1)
    return body


# ---- saved datasets: simple → complex ------------------------------------------------------------------

DATASETS = [
    (ADMIN, "aktif_personel", "Aktif personel listesi", "Ad, departman, e-posta ve işe giriş tarihi", {
        "root": "Personnel",
        "columns": [{"field": "fullName", "label": "Ad soyad"}, {"field": "department.name", "label": "Departman"},
                    {"field": "email", "label": "E-posta"}, {"field": "hireDate", "label": "İşe giriş"}],
        "where": {"field": "active", "operator": "EQ", "value": True},
        "sort": [{"field": "department.name"}, {"field": "fullName"}]}),
    (ALICE, "son_bir_yilda_ise_girenler", "Son 1 yılda işe girenler", "Yeni çalışanlar ve yöneticileri", {
        "root": "Personnel",
        "columns": [{"field": "fullName", "label": "Ad soyad"}, {"field": "department.name", "label": "Departman"},
                    {"field": "manager.fullName", "label": "Yönetici"}, {"field": "hireDate", "label": "İşe giriş"}],
        "where": {"field": "hireDate", "operator": "LAST_N_DAYS", "value": 365},
        "sort": [{"field": "hireDate", "direction": "DESC"}]}),
    (ADMIN, "departman_kadrosu", "Departman kadrosu", "Departman başına aktif çalışan (en az 2 kişi)", {
        "root": "Personnel",
        "columns": [{"field": "department.name", "label": "Departman"},
                    {"aggregate": "COUNT", "label": "Aktif çalışan"},
                    {"field": "hireDate", "aggregate": "MIN", "label": "En kıdemli işe giriş"}],
        "where": {"field": "active", "operator": "EQ", "value": True},
        "having": {"aggregate": "COUNT", "operator": "GTE", "value": 2},
        "sort": [{"aggregate": "COUNT", "direction": "DESC"}]}),
    (ALICE, "egitim_yenileme_takvimi", "Eğitim yenileme takvimi (60 gün)",
     "Önümüzdeki 60 günde yenilenmesi gereken zorunlu eğitimler", {
        "root": "TrainingRecord",
        "columns": [{"field": "personnel.fullName", "label": "Çalışan"},
                    {"field": "personnel.department.name", "label": "Departman"},
                    {"field": "training.name", "label": "Eğitim"}, {"field": "nextRenewalDate", "label": "Son tarih"}],
        "where": {"conditions": [{"field": "training.mandatory", "operator": "EQ", "value": True},
                                 {"field": "nextRenewalDate", "operator": "NEXT_N_DAYS", "value": 60}]},
        "sort": [{"field": "nextRenewalDate"}]}),
    (ALICE, "suresi_gecen_egitimler", "Süresi geçmiş zorunlu eğitimler", "Yenileme tarihi geçmiş aktif çalışanlar", {
        "root": "TrainingRecord",
        "columns": [{"field": "personnel.fullName", "label": "Çalışan"},
                    {"field": "personnel.department.name", "label": "Departman"},
                    {"field": "training.name", "label": "Eğitim"}, {"field": "nextRenewalDate", "label": "Son tarih"}],
        "where": {"conditions": [{"field": "training.mandatory", "operator": "EQ", "value": True},
                                 {"field": "personnel.active", "operator": "EQ", "value": True},
                                 {"field": "nextRenewalDate", "operator": "LAST_N_DAYS", "value": 3650}]},
        "sort": [{"field": "nextRenewalDate"}]}),
    (BOB, "proje_portfoyu", "Proje portföyü", "Projeler, durum ve bütçe; büyükten küçüğe", {
        "root": "Project",
        "columns": [{"field": "code", "label": "Kod"}, {"field": "name", "label": "Proje"},
                    {"field": "status", "label": "Durum"}, {"field": "budget", "label": "Bütçe"},
                    {"field": "department.name", "label": "Sahip departman"}],
        "sort": [{"field": "budget", "direction": "DESC"}]}),
    (BOB, "proje_kapasitesi", "Proje kapasitesi", "Proje ve rol başına kişi sayısı ve toplam yüzde", {
        "root": "ProjectAssignment",
        "columns": [{"field": "project.name", "label": "Proje"}, {"field": "role", "label": "Rol"},
                    {"aggregate": "COUNT", "label": "Kişi"},
                    {"field": "allocationPercent", "aggregate": "SUM", "label": "Toplam %"}],
        "sort": [{"field": "project.name"}, {"field": "role"}]}),
    (ADMIN, "departman_360", "Departman 360", "Alt ekipler, beceriler, eğitim kategorileri, şehirler ve projeler", {
        "root": "Department",
        "columns": [{"field": "name", "label": "Departman"}, {"field": "parent.name", "label": "Bağlı olduğu"},
                    {"field": "manager.fullName", "label": "Yönetici"},
                    {"field": "subDepartments.name", "aggregate": "LIST", "label": "Alt ekipler"},
                    {"field": "members.skills.skill.name", "aggregate": "LIST", "label": "Beceriler"},
                    {"field": "members.trainingRecords.training.category", "aggregate": "LIST",
                     "label": "Eğitim kategorileri"},
                    {"field": "members.profile.city", "aggregate": "LIST", "label": "Şehirler"},
                    {"field": "projects.code", "aggregate": "LIST", "label": "Projeler"}],
        "sort": [{"field": "name"}]}),
    (ADMIN, "maas_ozeti", "Maaş özeti (yönetim)", "Departman başına ortalama ve en yüksek maaş; maaş yetkisi gerekir", {
        "root": "Personnel",
        "columns": [{"field": "department.name", "label": "Departman"}, {"aggregate": "COUNT", "label": "Kişi"},
                    {"field": "salary", "aggregate": "AVG", "label": "Ortalama maaş"},
                    {"field": "salary", "aggregate": "MAX", "label": "En yüksek maaş"}],
        "where": {"field": "active", "operator": "EQ", "value": True},
        "sort": [{"field": "salary", "aggregate": "AVG", "direction": "DESC"}]}),
    (CAROL, "ekibimin_egitimleri", "Ekibimin eğitimleri", "Her kişinin son 3 geçtiği eğitim (yalnızca kendi ekibi)", {
        "root": "Personnel",
        "columns": [{"field": "fullName", "label": "Ad soyad"},
                    {"field": "trainingRecords", "aggregate": "LIST", "label": "Son eğitimler",
                     "fields": ["completedOn", "training.name", "score"],
                     "where": {"field": "status", "operator": "EQ", "value": "PASSED"}, "order": "DESC", "limit": 3}],
        "sort": [{"field": "fullName"}]}),
    (ADMIN, "beceri_envanteri", "Beceri envanteri", "Her beceriye sahip kişi sayısı, kategoriye göre", {
        "root": "PersonnelSkill",
        "columns": [{"field": "skill.category", "label": "Kategori"}, {"field": "skill.name", "label": "Beceri"},
                    {"aggregate": "COUNT", "label": "Kişi"}],
        "sort": [{"field": "skill.category"}, {"aggregate": "COUNT", "direction": "DESC"}]}),
    (BOB, "departman_butceleri", "Departman proje bütçeleri", "Departman başına proje sayısı ve toplam bütçe", {
        "root": "Project",
        "columns": [{"field": "department.name", "label": "Departman"}, {"aggregate": "COUNT", "label": "Proje"},
                    {"field": "budget", "aggregate": "SUM", "label": "Toplam bütçe"},
                    {"field": "budget", "aggregate": "MAX", "label": "En büyük proje"}],
        "sort": [{"field": "budget", "aggregate": "SUM", "direction": "DESC"}]}),
    (ALICE, "egitim_basari_ozeti", "Eğitim başarı özeti", "Eğitim başına katılım, geçme ve ortalama puan", {
        "root": "TrainingRecord",
        "columns": [{"field": "training.name", "label": "Eğitim"}, {"field": "training.category", "label": "Kategori"},
                    {"aggregate": "COUNT", "label": "Katılım"},
                    {"field": "score", "aggregate": "AVG", "label": "Ortalama puan"},
                    {"field": "score", "aggregate": "MIN", "label": "En düşük puan"}],
        "sort": [{"field": "score", "aggregate": "AVG", "direction": "DESC"}]}),
]

# ---- mail templates ----------------------------------------------------------------------------------------

STYLE = """<style>
  body { margin: 0; padding: 24px 0; background: #f4f4f5; }
  .wrap { max-width: 640px; margin: 0 auto; background: #ffffff; border-top: 3px solid #dc2626; border-radius: 8px;
    padding: 24px; font-family: -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif; font-size: 14px;
    line-height: 1.5; color: #18181b; }
  h1 { margin: 0 0 4px; font-size: 20px; }
  .meta { margin: 0 0 20px; color: #71717a; font-size: 13px; }
  table.data { width: 100%; border-collapse: collapse; font-size: 13px; }
  table.data th { background: #f4f4f5; text-align: left; font-weight: 600; padding: 8px 12px; border: 1px solid #e4e4e7; }
  table.data td { padding: 8px 12px; border: 1px solid #e4e4e7; }
  .note { margin: 12px 0 0; color: #71717a; font-size: 12px; }
  .callout { padding: 12px 16px; background: #fef2f2; border-left: 3px solid #dc2626; margin: 0 0 16px; }
  .ok { padding: 12px 16px; background: #f0fdf4; border-left: 3px solid #16a34a; margin: 0 0 16px; }
  .footer { margin-top: 24px; padding-top: 12px; border-top: 1px solid #e4e4e7; color: #a1a1aa; font-size: 12px; }
</style>"""
FOOTER = '<p class="footer">Bu e-posta “{{flow.name}}” akışı tarafından {{run.date}} tarihinde otomatik gönderildi.</p>'


def table(key):
    return (f'<table class="data"><thead><tr>{{{{#steps.{key}.columns}}}}<th>{{{{.}}}}</th>{{{{/steps.{key}.columns}}}}'
            f'</tr></thead><tbody>{{{{#steps.{key}.rows}}}}<tr>{{{{#cells}}}}<td>{{{{.}}}}</td>{{{{/cells}}}}</tr>'
            f'{{{{/steps.{key}.rows}}}}</tbody></table>'
            f'{{{{#steps.{key}.previewTruncated}}}}<p class="note">İlk satırlar gösteriliyor; tamamı ekteki dosyada.'
            f'</p>{{{{/steps.{key}.previewTruncated}}}}')


TEMPLATES = [
    ("Kısa bildirim", "Dosya ekte; gövdede yalnızca kayıt sayısı", "{{flow.name}} · {{run.date}}",
     f'{STYLE}<div class="wrap"><h1>{{{{flow.name}}}}</h1><p class="meta">{{{{run.date}}}}</p><p>Merhaba,</p>'
     f'<p>Güncel liste ekteki Excel dosyasındadır ({{{{steps.dataset.rowCount}}}} kayıt).</p>{FOOTER}</div>'),
    ("Eğitim hatırlatması", "Yenilenecek eğitimler tablo hâlinde", "Yenilenmesi gereken eğitimler – {{run.date}}",
     f'{STYLE}<div class="wrap"><h1>Eğitim yenileme hatırlatması</h1>'
     f'<p class="meta">{{{{run.date}}}} · {{{{steps.dataset.rowCount}}}} kayıt</p>'
     f'<div class="callout">Aşağıdaki çalışanların zorunlu eğitimlerinin süresi yakında doluyor. '
     f'Lütfen eğitim planlamasını yapın.</div>{table("dataset")}{FOOTER}</div>'),
    ("Proje raporu (iki tablo)", "Portföy ve kapasite tabloları", "Proje raporu – {{run.date}}",
     f'{STYLE}<div class="wrap"><h1>Proje raporu</h1><p class="meta">{{{{run.date}}}}</p>'
     f'<h3>Portföy ({{{{steps.portfoy.rowCount}}}} proje)</h3>{table("portfoy")}'
     f'<h3>Kapasite</h3>{table("kapasite")}{FOOTER}</div>'),
    ("Talep sonucu", "Onay adımının kararı", "Talebiniz: {{steps.kurul_onayi.decision}}",
     f'{STYLE}<div class="wrap"><h1>Satın alma talebi</h1><p class="meta">{{{{trigger.requester}}}} · '
     f'{{{{trigger.amount}}}} TL</p><div class="ok">Karar: <b>{{{{steps.kurul_onayi.decision}}}}</b>'
     f'{{{{#steps.kurul_onayi.decidedBy}}}} ({{{{steps.kurul_onayi.decidedBy}}}}){{{{/steps.kurul_onayi.decidedBy}}}}'
     f'</div><p>{{{{#steps.kurul_onayi.comment}}}}Yorum: “{{{{steps.kurul_onayi.comment}}}}”{{{{/steps.kurul_onayi.comment}}}}'
     f'</p><p>Gerekçe: {{{{trigger.reason}}}}</p>{FOOTER}</div>'),
    ("Otomatik onay", "Eşik altı talepler", "Talebiniz otomatik onaylandı",
     f'{STYLE}<div class="wrap"><h1>Talebiniz onaylandı</h1><p class="meta">{{{{trigger.requester}}}} · '
     f'{{{{trigger.amount}}}} TL</p><div class="ok">Tutar onay eşiğinin altında olduğu için talep otomatik onaylandı.'
     f'</div>{FOOTER}</div>'),
    ("Yeni çalışan karşılama", "Kafka'dan gelen işe alım", "Aramıza hoş geldin {{trigger.value.fullName}}",
     f'{STYLE}<div class="wrap"><h1>Hoş geldin {{{{trigger.value.fullName}}}}!</h1>'
     f'<p class="meta">{{{{trigger.value.department}}}} · başlangıç {{{{trigger.value.startDate}}}}</p>'
     f'<p>İlk hafta planın ve zorunlu eğitimlerin ekipte seni bekliyor.</p>{FOOTER}</div>'),
]

TEMPLATES += [
    ("Değişiklik talebi sonucu", "IT değişiklik kurulunun kararı", "{{trigger.changeId}}: {{steps.kurul.decision}}",
     f'{STYLE}<div class="wrap"><h1>Değişiklik talebi {{{{trigger.changeId}}}}</h1>'
     f'<p class="meta">{{{{trigger.system}}}} · risk: {{{{trigger.risk}}}} · {{{{trigger.requester}}}}</p>'
     f'<div class="ok">Karar: <b>{{{{steps.kurul.decision}}}}</b>{{{{#steps.kurul.decidedBy}}}} '
     f'({{{{steps.kurul.decidedBy}}}}){{{{/steps.kurul.decidedBy}}}}</div>'
     f'<p>{{{{trigger.summary}}}}</p>{{{{#steps.kurul.comment}}}}<p>Yorum: “{{{{steps.kurul.comment}}}}”</p>'
     f'{{{{/steps.kurul.comment}}}}{FOOTER}</div>'),
    ("Standart değişiklik", "Düşük riskli değişiklik bilgisi", "{{trigger.changeId}} standart değişiklik olarak kaydedildi",
     f'{STYLE}<div class="wrap"><h1>Standart değişiklik</h1><p class="meta">{{{{trigger.system}}}} · '
     f'{{{{trigger.requester}}}}</p><div class="ok">Düşük riskli olduğu için kurul onayı gerekmedi.</div>'
     f'<p>{{{{trigger.summary}}}}</p>{FOOTER}</div>'),
]

GROUPS = [
    ("Satın alma kurulu", "50.000 TL ve üstü satın alma talepleri", [("admin@mail.com", None), ("bob", "bob@example.com")]),
    ("Proje yöneticileri", "Proje kapasite ve bütçe kararları", [("bob", "bob@example.com"), ("carol", "carol@example.com")]),
    ("IT değişiklik kurulu", "Yüksek riskli sistem değişiklikleri (CAB)", [("bob", "bob@example.com"),
                                                                          ("admin@mail.com", None)]),
    ("Bütçe komitesi", "Yıllık ve çeyreklik bütçe kararları", [("admin@mail.com", None), ("alice", "alice@example.com")]),
    ("Eğitim birimi", "Eğitim planlama ve istisnalar", [("alice", "alice@example.com"), ("carol", "carol@example.com")]),
    ("Güvenlik ekibi", "Erişim ve güvenlik istisnaları", [("bob", "bob@example.com")]),
]


def dataset_step(key, name, rows=20, fmt="xlsx"):
    return {"type": "dataset", "key": key, "datasetName": name, "format": fmt, "fileName": f"{name}.{fmt}",
            "mailRows": rows}


def mail_step(key, template, to, attachments=()):
    return {"type": "mail", "key": key, "templateId": template, "to": to, "cc": [], "attachments": list(attachments)}


def main():
    status, _ = call("GET", "/api/session", ADMIN)
    if status == 401:
        print(f"{BASE} rejected admin@mail.com: set DEMO_PASSWORD to the server's demo password.")
        sys.exit(1)
    if status != 200:
        print(f"Platform not reachable at {BASE} (HTTP {status}); start it first.")
        sys.exit(1)

    print("Saved datasets")
    existing = {d["name"] for d in must(call("GET", "/api/saved-datasets", ADMIN), "list datasets")["data"]}
    for owner, name, label, description, definition in DATASETS:
        if name in existing:
            print(f"  · {name} (exists)")
            continue
        must(call("POST", "/api/saved-datasets", owner, {"name": name, "label": label, "description": description,
                                                        "definition": definition}), f"dataset {name}")
        print(f"  + {name} ({owner})")

    print("Mail templates")
    templates = {t["name"]: t["id"] for t in must(call("GET", "/api/mail-templates?page=1&size=100", ADMIN), "list")["data"]}
    for name, description, subject, body in TEMPLATES:
        if name not in templates:
            created = must(call("POST", "/api/mail-templates", ADMIN, {"name": name, "description": description,
                                                                      "subject": subject, "bodyHtml": body}), name)
            templates[name] = created["data"]["id"]
            print(f"  + {name}")
        else:
            print(f"  · {name} (exists)")

    print("Approval groups")
    groups = {g["name"]: g["id"] for g in must(call("GET", "/api/approval-groups", ADMIN), "groups")["data"]}
    for name, description, members in GROUPS:
        if name not in groups:
            created = must(call("POST", "/api/approval-groups", ADMIN, {
                "name": name, "description": description,
                "members": [{"username": u, "email": e} for u, e in members]}), name)
            groups[name] = created["data"]["id"]
            print(f"  + {name}")

    flows_def = [
        # 1. simple: two datasets into one mail with two tables and two attachments
        (BOB, "Haftalık proje raporu", "Hafta içi 08:30: portföy ve kapasite tek mailde, iki Excel eki.", [
            dataset_step("portfoy", "proje_portfoyu", rows=20),
            dataset_step("kapasite", "proje_kapasitesi", rows=50),
            mail_step("mail", templates["Proje raporu (iki tablo)"], ["pmo@example.com"], ["portfoy", "kapasite"]),
        ], [{"from": "portfoy", "to": "kapasite"}, {"from": "kapasite", "to": "mail"}],
         [("30 8 * * MON-FRI", True)], []),
        # 2. medium: webhook in, threshold, approval, Kafka out
        (ADMIN, "Satın alma talebi", "Webhook ile gelen talep 50.000 TL ve üstüyse kurul onayına gider; onaylanan "
                                    "talep Kafka'ya yazılır.", [
            {"type": "condition", "key": "esik_ustu", "expression": {">=": [{"var": "trigger.amount"}, 50000]}},
            {"type": "approval", "key": "kurul_onayi", "groupId": groups["Satın alma kurulu"],
             "title": "Satın alma {{trigger.requestId}}: {{trigger.item}} — {{trigger.amount}} TL",
             "description": "Talep eden: {{trigger.requester}}\nGerekçe: {{trigger.reason}}", "timeoutHours": 72,
             "notifyMembers": True},
            {"type": "kafka", "key": "erp_kaydi", "topic": "flows.purchase.approved", "messageKey": "{{trigger.requestId}}",
             "payload": "{\"requestId\": \"{{trigger.requestId}}\", \"amount\": \"{{trigger.amount}}\", "
                        "\"approvedBy\": \"{{steps.kurul_onayi.decidedBy}}\"}"},
            mail_step("sonuc", templates["Talep sonucu"], ["satinalma@example.com"]),
            mail_step("otomatik", templates["Otomatik onay"], ["satinalma@example.com"]),
        ], [{"from": "esik_ustu", "to": "kurul_onayi", "when": "true"},
            {"from": "esik_ustu", "to": "otomatik", "when": "false"},
            {"from": "kurul_onayi", "to": "erp_kaydi", "when": "approved"},
            {"from": "erp_kaydi", "to": "sonuc"},
            {"from": "kurul_onayi", "to": "sonuc", "when": None}],
         [], ["webhook"]),
        # 3. medium: webhook in: high risk goes to the change board, the rest is a standard change
        (ADMIN, "IT değişiklik talebi", "Webhook ile gelen değişiklik: risk yüksekse IT değişiklik kuruluna gider, "
                                       "değilse standart değişiklik olarak kaydedilir.", [
            {"type": "condition", "key": "yuksek_risk",
             "expression": {"or": [{"==": [{"var": "trigger.risk"}, "yüksek"]},
                                   {"==": [{"var": "trigger.production"}, True]}]}},
            {"type": "approval", "key": "kurul", "groupId": groups["IT değişiklik kurulu"],
             "title": "{{trigger.changeId}}: {{trigger.system}} — {{trigger.summary}}",
             "description": "Talep eden: {{trigger.requester}}\nRisk: {{trigger.risk}}\nPlan: {{trigger.window}}",
             "timeoutHours": 24, "notifyMembers": True},
            mail_step("sonuc", templates["Değişiklik talebi sonucu"], ["degisiklik@example.com"]),
            mail_step("standart", templates["Standart değişiklik"], ["degisiklik@example.com"]),
        ], [{"from": "yuksek_risk", "to": "kurul", "when": "true"},
            {"from": "yuksek_risk", "to": "standart", "when": "false"},
            {"from": "kurul", "to": "sonuc", "when": None}],
         [], ["webhook"]),
    ]

    remove_obsolete_flows()

    print("Flows")
    flows = {}
    for owner in (ADMIN, ALICE, BOB, CAROL):
        for f in must(call("GET", "/api/flows?page=1&size=100", owner), "flows")["data"]:
            flows[f["name"]] = (f["id"], owner)
    secrets = {}
    for owner, name, description, steps, edges, schedules, triggers in flows_def:
        if name in flows:
            print(f"  · {name} (exists)")
            continue
        created = must(call("POST", "/api/flows", owner, {"name": name, "description": description,
                                                          "steps": steps, "edges": edges}), f"flow {name}")
        flow_id = created["data"]["id"]
        flows[name] = (flow_id, owner)
        for cron, enabled in schedules:
            must(call("POST", f"/api/flows/{flow_id}/schedules", owner,
                      {"cron": cron, "timeZone": "Europe/Istanbul", "enabled": enabled}), f"schedule {name}")
        for trigger in triggers:
            if trigger == "webhook":
                body = must(call("POST", f"/api/flows/{flow_id}/triggers", owner,
                                 {"type": "webhook", "enabled": True}), f"webhook {name}")["data"]
                secrets[name] = (body["trigger"]["webhookUrl"], body["secret"])
            else:
                must(call("POST", f"/api/flows/{flow_id}/triggers", owner,
                          {"type": "kafka", "enabled": True, "topic": trigger.split(":", 1)[1]}), f"kafka {name}")
        print(f"  + {name} ({owner})")

    print("Runs")
    for name in ("Haftalık proje raporu", "Haftalık departman kadrosu", "Zorunlu eğitim uyumu"):
        if name in flows:
            flow_id, owner = flows[name]
            status, _ = call("POST", f"/api/flows/{flow_id}/runs", owner if owner != CAROL else CAROL)
            print(f"  ▶ {name}: HTTP {status}")

    if "Satın alma talebi" in secrets:
        url, secret = secrets["Satın alma talebi"]
        for request in ({"requestId": "PR-1041", "item": "Toplantı odası ekranı", "amount": 18500,
                         "requester": "Deniz Kaya", "reason": "Eski ekran arızalı"},
                        {"requestId": "PR-1042", "item": "Sunucu yenileme", "amount": 240000,
                         "requester": "Bob Jones", "reason": "Garanti süresi doldu"},
                        {"requestId": "PR-1043", "item": "Eğitim platformu lisansı", "amount": 96000,
                         "requester": "Alice Smith", "reason": "Zorunlu eğitimler için"},
                        {"requestId": "PR-1044", "item": "Ofis sandalyeleri", "amount": 62000,
                         "requester": "Mia Allen", "reason": "Yeni ekip"}):
            raw = json.dumps(request).encode()
            signature = "sha256=" + hmac.new(secret.encode(), raw, hashlib.sha256).hexdigest()
            path = url if url.startswith("/") else "/" + url.split("/", 3)[3]
            status, _ = call("POST", path, raw=raw, headers={"X-Signature": signature})
            print(f"  ⇢ webhook {request['requestId']} {request['amount']} TL: HTTP {status}")

    if "IT değişiklik talebi" in secrets:
        url, secret = secrets["IT değişiklik talebi"]
        for change in ({"changeId": "CHG-3101", "system": "Bordro", "risk": "düşük", "production": False,
                        "summary": "Rapor başlıklarının düzeltilmesi", "requester": "Carol White", "window": "Salı 18:00"},
                       {"changeId": "CHG-3102", "system": "ERP", "risk": "yüksek", "production": True,
                        "summary": "Veritabanı sürüm yükseltmesi", "requester": "Bob Jones", "window": "Cumartesi 02:00"},
                       {"changeId": "CHG-3103", "system": "VPN", "risk": "yüksek", "production": True,
                        "summary": "Sertifika yenileme", "requester": "Grace Kim", "window": "Pazar 01:00"},
                       {"changeId": "CHG-3104", "system": "CRM", "risk": "orta", "production": True,
                        "summary": "Yeni alan eklenmesi", "requester": "Mia Allen", "window": "Çarşamba 20:00"}):
            raw = json.dumps(change, ensure_ascii=False).encode()
            signature = "sha256=" + hmac.new(secret.encode(), raw, hashlib.sha256).hexdigest()
            path = url if url.startswith("/") else "/" + url.split("/", 3)[3]
            status, _ = call("POST", path, raw=raw, headers={"X-Signature": signature})
            print(f"  ⇢ webhook {change['changeId']} ({change['risk']}): HTTP {status}")

    print("Approvals (waiting for runs to reach them)")
    time.sleep(8)
    decisions = {"PR-1042": ("approve", BOB, "Bütçede var, onaylıyorum."),
                 "PR-1044": ("reject", BOB, "Mevcut sandalyeler yeterli, gelecek çeyreğe kalsın."),
                 "CHG-3102": ("approve", BOB, "Geri dönüş planı yeterli, pencere uygun."),
                 "CHG-3104": ("reject", BOB, "Test ortamında denenmeden canlıya alınmasın.")}
    pending = must(call("GET", "/api/approval-requests?scope=mine&status=pending&page=1&size=50", BOB), "approvals")
    for request in pending["data"]:
        for code, (decision, user, comment) in decisions.items():
            if code in request.get("title", "") or code in json.dumps(request, ensure_ascii=False):
                status, _ = call("POST", f"/api/approval-requests/{request['id']}/{decision}", user, {"comment": comment})
                print(f"  ✓ {decision} {request['title']}: HTTP {status}")
    # alice started "Geciken eğitimler" herself: she may not approve her own request (allowSelfApproval false),
    # so it stays pending for the other members of İK yöneticileri.
    populate_surveys(groups)
    print("Done. Leftover approvals stay pending, so 'Onaylarım' has something to decide.")



# ---- surveys: from a quick poll to a help-desk form with follow-up, like survy's demo data ---------------------

def choice(id_, label, type_="singleChoice", options=(), **extra):
    return {"id": id_, "type": type_, "label": label, "options": [{"id": o, "label": l} for o, l in options], **extra}


def section(id_, label, help_=None):
    return {"id": id_, "type": "section", "label": label, **({"help": help_} if help_ else {})}


TRACK_ALL = {"status": True, "priority": True, "followUpDate": True, "assignee": True, "fields": []}
TRACK_NONE = {"status": False, "priority": False, "followUpDate": False, "assignee": False, "fields": []}
DEPARTMENTS = [("it", "Bilgi Teknolojileri"), ("hr", "İnsan Kaynakları"), ("finance", "Finans"), ("sales", "Satış"),
               ("operations", "Operasyon")]


def survey(title, description, fields, *, status="published", rules=(), anonymous=False, one=False, results="owner",
           tracking=None, submit="Gönder", success="Teşekkürler, yanıtınız alındı.", closes=None):
    return {"title": title, "description": description, "status": status, "anonymous": anonymous,
            "oneResponsePerUser": one, "closesAt": closes,
            "settings": {"results": results, "tracking": tracking or TRACK_ALL},
            "definition": {"submitLabel": submit, "successMessage": success, "fields": fields, "rules": list(rules)}}


def surveys_def():
    return [
        # 1. anonymous satisfaction survey: ratings, NPS, a follow-up for low scores, many responses
        (ADMIN, survey("Çalışan memnuniyeti 2026",
                       "Çalışma ortamımızı iyileştirmek için görüşleriniz önemli. Anonimdir, 3 dakika sürer.", [
            section("general", "Genel"),
            choice("department", "Departmanınız", "dropdown", DEPARTMENTS + [("other", "Diğer")], required=True),
            {"id": "satisfaction", "type": "rating", "label": "İşinizden ne kadar memnunsunuz?", "required": True, "max": 5},
            {"id": "whyLow", "type": "longText", "label": "Memnuniyetinizi düşüren en önemli şey nedir?",
             "visibleWhen": {"<=": [{"var": "satisfaction"}, 2]}},
            choice("benefits", "En değerli bulduğunuz yan haklar", "multipleChoice", [("remote", "Uzaktan çalışma"),
                   ("health", "Özel sağlık sigortası"), ("training", "Eğitim bütçesi"), ("meal", "Yemek kartı")]),
            {"id": "recommend", "type": "nps", "label": "Şirketimizi çalışma yeri olarak önerir misiniz?", "required": True},
            {"id": "suggestion", "type": "longText", "label": "Eklemek istedikleriniz"},
        ], anonymous=True, tracking=TRACK_NONE), 60, "satisfaction"),
        # 2. a request form that starts a flow (approval above 20.000 TL)
        (CAROL, survey("Ekipman talebi",
                       "Bilgisayar, telefon ve çevre birimi taleplerinizi buradan iletin. 20.000 TL üstü talepler onaya gider.", [
            choice("item", "Ne lazım?", "dropdown", [("laptop", "Dizüstü bilgisayar"), ("monitor", "Monitör"),
                   ("phone", "Telefon"), ("headset", "Kulaklık"), ("other", "Diğer")], required=True),
            {"id": "itemOther", "type": "shortText", "label": "Lütfen belirtin", "required": True,
             "visibleWhen": {"==": [{"var": "item"}, "other"]}},
            {"id": "quantity", "type": "number", "label": "Adet", "required": True, "min": 1, "max": 50, "width": "half"},
            {"id": "amount", "type": "number", "label": "Tahmini tutar (TL)", "required": True, "min": 0, "width": "half"},
            {"id": "neededBy", "type": "date", "label": "Ne zamana lazım?", "required": True},
            {"id": "reason", "type": "longText", "label": "Gerekçe", "help": "20.000 TL üstü taleplerde zorunlu."},
        ], rules=[{"id": "reasonForBig", "message": "20.000 TL üstü taleplerde gerekçe yazılmalı.",
                   "logic": {"or": [{"<=": [{"var": "amount"}, 20000]}, {"!!": {"var": "reason"}}]}}],
            submit="Talebi gönder", success="Talebiniz alındı; BT ekibi sizinle iletişime geçecek."), 0, None),
        # 3. a draft
        (ALICE, survey("Ofise dönüş tercihleri", "Hibrit çalışma düzenini planlamak için.", [
            choice("days", "Haftada kaç gün ofiste olmak istersiniz?", "singleChoice",
                   [("1", "1 gün"), ("2", "2 gün"), ("3", "3 gün"), ("5", "Her gün")], required=True),
            choice("weekdays", "Hangi günler?", "multipleChoice", [("mon", "Pazartesi"), ("tue", "Salı"),
                   ("wed", "Çarşamba"), ("thu", "Perşembe"), ("fri", "Cuma")]),
            choice("commute", "Ulaşım", "dropdown", [("car", "Araç"), ("public", "Toplu taşıma"), ("shuttle", "Servis")]),
        ], status="draft", one=True), 0, None),
        # 4. help desk: the owner tracks every response (status, priority, date, assignee, extra fields, notes)
        (BOB, survey("BT destek talebi", "Arıza ve erişim taleplerinizi iletin; ekip durumu buradan takip eder.", [
            choice("category", "Konu", "dropdown", [("hardware", "Donanım arızası"), ("access", "Erişim / yetki"),
                   ("software", "Yazılım kurulumu"), ("network", "Ağ / VPN"), ("other", "Diğer")], required=True),
            {"id": "summary", "type": "shortText", "label": "Kısa açıklama", "required": True, "maxLength": 120},
            {"id": "details", "type": "longText", "label": "Ayrıntılar", "help": "Hata mesajı, ne zamandan beri vb."},
            choice("impact", "Etkisi", "singleChoice", [("me", "Sadece beni"), ("team", "Ekibimi"),
                   ("company", "Tüm şirketi")], required=True, width="half"),
            {"id": "blocked", "type": "yesNo", "label": "İşimi yapamıyorum", "width": "half"},
            {"id": "phone", "type": "phone", "label": "Ulaşılacak telefon", "visibleWhen": {"==": [{"var": "blocked"}, True]}},
        ], tracking={**TRACK_ALL, "fields": [
            {"id": "ticket", "label": "Kayıt no", "type": "text"},
            {"id": "rootCause", "label": "Kök neden", "type": "choice", "options": [
                {"id": "user", "label": "Kullanıcı hatası"}, {"id": "device", "label": "Cihaz"},
                {"id": "config", "label": "Yapılandırma"}, {"id": "vendor", "label": "Tedarikçi"}]},
            {"id": "effort", "label": "Harcanan süre (saat)", "type": "number"}]},
            submit="Talebi aç", success="Talebiniz açıldı; BT ekibi en kısa sürede dönecek."), 45, "helpdesk"),
        # 5. training feedback with results shown to respondents
        (ALICE, survey("Eğitim değerlendirmesi: İleri Excel", "Katıldığınız eğitimi değerlendirin; 2 dakika.", [
            {"id": "overall", "type": "rating", "label": "Eğitimi genel olarak nasıl buldunuz?", "required": True,
             "showInResults": True},
            choice("pace", "Eğitimin hızı", "singleChoice", [("slow", "Yavaş"), ("ok", "Uygun"), ("fast", "Hızlı")],
                   required=True, showInResults=True),
            choice("useful", "En faydalı konu", "multipleChoice", [("pivot", "Pivot tablolar"), ("lookup", "XLOOKUP"),
                   ("power", "Power Query"), ("charts", "Grafikler")], showInResults=True),
            {"id": "trainer", "type": "nps", "label": "Eğitmeni bir arkadaşınıza önerir misiniz?", "required": True},
            {"id": "comment", "type": "longText", "label": "Önerileriniz"},
        ], results="respondents", tracking=TRACK_NONE), 35, "training"),
        # 6. a quick poll: everyone sees the counts
        (CAROL, survey("Cuma öğle yemeği nerede olsun?", "Ekipçe öğle yemeği; oylamaya göre rezervasyon yapacağız.", [
            choice("place", "Nerede yiyelim?", "singleChoice", [("kebab", "Kebapçı"), ("fish", "Balıkçı"),
                   ("vegan", "Vegan kafe"), ("pizza", "Pizzacı")], required=True, showInResults=True),
            {"id": "join", "type": "yesNo", "label": "Ben de geliyorum", "showInResults": True},
        ], one=True, results="everyone", tracking=TRACK_NONE, submit="Oy ver"), 4, "poll"),
        # 7. event registration with a deadline and conditional questions
        (ADMIN, survey("Yılbaşı etkinliği kaydı", "28 Aralık akşamı ofiste. Katılım ve tercihlerinizi bildirin.", [
            {"id": "attend", "type": "yesNo", "label": "Katılacağım", "showInResults": True},
            {"id": "guests", "type": "number", "label": "Yanınızda kaç kişi gelecek?", "min": 0, "max": 2,
             "visibleWhen": {"==": [{"var": "attend"}, True]}, "width": "half"},
            choice("diet", "Yemek tercihi", "dropdown", [("none", "Fark etmez"), ("vegetarian", "Vejetaryen"),
                   ("vegan", "Vegan"), ("glutenFree", "Glütensiz")], visibleWhen={"==": [{"var": "attend"}, True]},
                   width="half"),
            choice("activity", "Hangi etkinliğe katılmak istersiniz?", "multipleChoice", [("quiz", "Bilgi yarışması"),
                   ("karaoke", "Karaoke"), ("raffle", "Çekiliş")], visibleWhen={"==": [{"var": "attend"}, True]},
                   showInResults=True),
            {"id": "photo", "type": "yesNo", "label": "Fotoğraf çekilmesine onay veriyorum",
             "help": "Fotoğraflar yalnızca iç iletişimde kullanılır."},
        ], one=True, results="respondents", tracking={**TRACK_NONE, "status": True},
            closes="2026-12-20T23:59:59+03:00", submit="Kaydol"), 4, "event"),
        # 8. answered through a flow step (customer visit follow-up)
        (BOB, survey("Saha ziyareti değerlendirmesi", "Ziyaret eden ekibimizi değerlendirin.", [
            {"id": "rating", "type": "rating", "label": "Ziyaretten memnun kaldınız mı?", "required": True},
            choice("solved", "Sorununuz çözüldü mü?", "singleChoice", [("yes", "Evet"), ("partly", "Kısmen"),
                   ("no", "Hayır")], required=True),
            {"id": "note", "type": "longText", "label": "Eklemek istedikleriniz"},
        ]), 6, "visit"),
        # 9. every question type and a few rules
        (ADMIN, survey("Tüm soru tipleri", "Platformdaki bütün soru tiplerini ve kuralları gösteren örnek.", [
            section("text", "Metin soruları"),
            {"id": "name", "type": "shortText", "label": "Ad soyad", "required": True, "width": "half"},
            {"id": "email", "type": "email", "label": "E-posta", "required": True, "width": "half"},
            {"id": "phone", "type": "phone", "label": "Telefon", "width": "half"},
            {"id": "age", "type": "number", "label": "Yaş", "min": 18, "max": 70, "width": "half"},
            {"id": "bio", "type": "longText", "label": "Kendinizden bahsedin", "maxLength": 500},
            section("choices", "Seçim soruları"),
            choice("team", "Ekip", "singleChoice", DEPARTMENTS[:3], required=True),
            choice("skills", "Yetenekler", "multipleChoice", [("java", "Java"), ("react", "React"), ("sql", "SQL"),
                   ("excel", "Excel")], min=1, max=3),
            choice("city", "Şehir", "dropdown", [("ist", "İstanbul"), ("ank", "Ankara"), ("izm", "İzmir")]),
            {"id": "remote", "type": "yesNo", "label": "Uzaktan çalışıyorum"},
            section("scales", "Ölçekler ve tarih"),
            {"id": "stars", "type": "rating", "label": "Yıldız (7)", "max": 7},
            {"id": "nps", "type": "nps", "label": "0-10 puan"},
            {"id": "start", "type": "date", "label": "Başlangıç", "width": "half"},
            {"id": "end", "type": "date", "label": "Bitiş", "width": "half"},
        ], rules=[
            {"id": "companyMail", "message": "Şirket e-postası (@sirket.com) kullanın.",
             "logic": {"endsWith": [{"var": "email"}, "@sirket.com"]}},
            {"id": "dates", "message": "Bitiş başlangıçtan önce olamaz.",
             "logic": {"or": [{"!": {"var": "end"}}, {">=": [{"var": "end"}, {"var": "start"}]}]}},
        ]), 5, "allTypes"),
        # 10. closed pulse survey
        (CAROL, survey("Haftalık nabız (Kasım)", "Bu hafta nasıldı? 30 saniye.", [
            {"id": "mood", "type": "nps", "label": "Bu haftayı 0-10 arası puanlayın", "required": True},
            {"id": "blocker", "type": "shortText", "label": "Sizi en çok ne yavaşlattı?"},
        ], status="closed", anonymous=True, tracking=TRACK_NONE), 30, "pulse"),
        # 11. a project proposal that starts an approval flow
        (ALICE, survey("Yeni proje önerisi", "Bütçe gerektiren proje fikirlerinizi iletin; 100.000 TL üstü bütçe "
                       "komitesine gider.", [
            {"id": "title", "type": "shortText", "label": "Proje adı", "required": True},
            choice("area", "Alan", "dropdown", [("efficiency", "Verimlilik"), ("customer", "Müşteri deneyimi"),
                   ("compliance", "Uyum"), ("other", "Diğer")], required=True),
            {"id": "budget", "type": "number", "label": "Tahmini bütçe (TL)", "required": True, "min": 0, "width": "half"},
            {"id": "start", "type": "date", "label": "Başlangıç", "required": True, "width": "half"},
            {"id": "end", "type": "date", "label": "Bitiş", "required": True},
            {"id": "benefit", "type": "longText", "label": "Beklenen fayda", "required": True},
            {"id": "justification", "type": "longText", "label": "Bütçe gerekçesi",
             "visibleWhen": {">": [{"var": "budget"}, 100000]}},
        ], rules=[
            {"id": "dates", "message": "Bitiş tarihi başlangıçtan önce olamaz.",
             "logic": {">=": [{"var": "end"}, {"var": "start"}]}},
            {"id": "bigBudget", "message": "100.000 TL üstü bütçelerde gerekçe zorunludur.",
             "logic": {"or": [{"<=": [{"var": "budget"}, 100000]}, {"!!": {"var": "justification"}}]}},
        ], submit="Öneriyi gönder"), 0, None),
        # 12b. sent by the onboarding flow to the new hire's manager (a survey step), not answered directly
        (ADMIN, survey("Yeni çalışan ekipman listesi", "Ekibinize katılan çalışan için ekipman ve erişimleri seçin.", [
            choice("items", "Hangi ekipmanlar lazım?", "multipleChoice", [("laptop", "Dizüstü bilgisayar"),
                   ("monitor", "Monitör"), ("phone", "Telefon"), ("headset", "Kulaklık"), ("other", "Diğer")],
                   required=True, min=1),
            choice("laptopType", "Bilgisayar tipi", "dropdown", [("mac", "MacBook Pro"), ("win", "Windows (ThinkPad)"),
                   ("linux", "Linux iş istasyonu")], required=True, visibleWhen={"in": ["laptop", {"var": "items"}]}),
            {"id": "otherItem", "type": "shortText", "label": "Diğer ekipman", "required": True,
             "visibleWhen": {"in": ["other", {"var": "items"}]}},
            choice("access", "Gerekli erişimler", "multipleChoice", [("vpn", "VPN"), ("crm", "CRM"), ("erp", "ERP"),
                   ("github", "GitHub"), ("jira", "Jira")]),
            {"id": "cost", "type": "number", "label": "Tahmini toplam maliyet (TL)", "required": True, "min": 0},
            {"id": "notes", "type": "longText", "label": "Notlar / gerekçe", "help": "ERP erişimi için zorunlu."},
        ], rules=[{"id": "erpNeedsReason", "message": "ERP erişimi için gerekçe yazın.",
                   "logic": {"or": [{"!": {"in": ["erp", {"var": "access"}]}}, {"!!": {"var": "notes"}}]}}],
            tracking=TRACK_NONE, submit="Listeyi gönder", success="Teşekkürler; BT hazırlığa başlıyor."), 0, None),
        # 12. waiting for its first answer
        (BOB, survey("İlk yanıtı bekleyen anket", "Henüz kimse yanıtlamadı; boş sonuç ekranını görmek için.", [
            choice("q", "Bu anketi gördünüz mü?", "singleChoice", [("yes", "Evet"), ("no", "Hayır")], required=True),
        ]), 0, None),
    ]


def answers_for(kind, rng, n):
    pick = rng.choice
    if kind == "satisfaction":
        score = pick([1, 2, 3, 3, 4, 4, 4, 5, 5])
        a = {"department": pick(["it", "it", "hr", "finance", "sales", "operations"]), "satisfaction": score,
             "benefits": rng.sample(["remote", "health", "training", "meal"], rng.randint(1, 3)),
             "recommend": max(0, min(10, score * 2 + rng.randint(-2, 1))),
             "suggestion": pick(["", "", "Toplantılar çok uzun.", "Esnek çalışma çok iyi.", "Kariyer yolu belirsiz.",
                                 "Ekipman yenilenmeli.", "Ekip içi iletişim harika."])}
        if score <= 2:
            a["whyLow"] = pick(["İş yükü çok fazla.", "Yöneticimle iletişim zayıf.", "Maaş beklentimin altında.",
                                "Terfi süreçleri şeffaf değil."])
        return a
    if kind == "helpdesk":
        category = pick(["hardware", "access", "access", "software", "network", "other"])
        summaries = {"hardware": ["Ekran titriyor", "Klavye çalışmıyor", "Laptop ısınıyor"],
                     "access": ["CRM yetkisi lazım", "Paylaşılan klasöre erişim", "VPN hesabı kilitlendi"],
                     "software": ["Power BI kurulumu", "Office lisansı", "IntelliJ güncellemesi"],
                     "network": ["VPN kopuyor", "Toplantı odasında Wi-Fi yok"], "other": ["Yazıcı kartuşu bitti"]}
        blocked = rng.random() < 0.3
        a = {"category": category, "summary": pick(summaries[category]), "impact": pick(["me", "me", "team", "company"]),
             "blocked": blocked, "details": pick(["", "Sabahtan beri böyle.", "Dün güncellemeden sonra başladı."])}
        if blocked:
            a["phone"] = f"+90 532 {rng.randint(100, 999)} {rng.randint(10, 99)} {rng.randint(10, 99)}"
        return a
    if kind == "training":
        overall = pick([3, 4, 4, 5, 5, 5])
        return {"overall": overall, "pace": pick(["ok", "ok", "ok", "fast", "slow"]),
                "useful": rng.sample(["pivot", "lookup", "power", "charts"], rng.randint(1, 2)),
                "trainer": max(0, min(10, overall * 2 - rng.randint(0, 2))),
                "comment": pick(["", "Daha çok örnek olsun.", "Süre kısaydı.", "Power Query için ayrı eğitim olmalı."])}
    if kind == "poll":
        return {"place": ["fish", "kebab", "fish", "vegan"][n % 4], "join": n != 3}
    if kind == "event":
        attend = n != 2
        return {"attend": attend, "photo": n % 2 == 0, **({"guests": n % 2, "diet": pick(["none", "vegetarian"]),
                "activity": rng.sample(["quiz", "karaoke", "raffle"], 2)} if attend else {})}
    if kind == "visit":
        return {"rating": pick([3, 4, 5, 5]), "solved": pick(["yes", "yes", "partly", "no"]),
                "note": pick(["", "Teknisyen çok ilgiliydi.", "Randevuya geç kalındı."])}
    if kind == "allTypes":
        return {"name": pick(["Ayşe Yılmaz", "Mehmet Kaya", "Zeynep Demir", "Can Öztürk"]),
                "email": pick(["ayse", "mehmet", "zeynep", "can"]) + f"{n}@sirket.com", "age": rng.randint(22, 55),
                "team": pick(["it", "hr", "finance"]), "skills": rng.sample(["java", "react", "sql", "excel"], 2),
                "city": pick(["ist", "ank", "izm"]), "remote": rng.random() < 0.5, "stars": rng.randint(3, 7),
                "nps": rng.randint(5, 10), "start": "2026-12-01", "end": "2026-12-15", "bio": "Merhaba!"}
    if kind == "pulse":
        return {"mood": pick([4, 6, 7, 7, 8, 8, 9, 10]), "blocker": pick(["", "Toplantılar", "Belirsiz öncelikler", "VPN"])}
    return {}


def populate_surveys(groups):
    import random
    rng = random.Random(7)
    print("Surveys")
    ids = {}
    for owner in (ADMIN, ALICE, BOB, CAROL):
        for item in must(call("GET", "/api/surveys?scope=mine&page=1&size=100", owner), "surveys")["data"]:
            ids.setdefault(item["title"], item["id"])
    users = [ALICE, BOB, CAROL, ADMIN]
    for owner, body, count, kind in surveys_def():
        if body["title"] in ids:
            print(f"  · {body['title']} (exists)")
            continue
        # a closed survey takes no answers: collect them while published, close it afterwards
        status = body["status"]
        if status == "closed":
            body = {**body, "status": "published"}
        created = must(call("POST", "/api/surveys", owner, body), body["title"])["data"]
        survey_id = ids[body["title"]] = created["id"]
        accepted = []
        for n in range(count):
            user = users[n % 4] if body["oneResponsePerUser"] else rng.choice(users)
            status_code, answer = call("POST", f"/api/surveys/{survey_id}/responses", user,
                                       {"answers": answers_for(kind, rng, n)})
            if status_code == 201:
                accepted.append(answer["data"]["responseId"])
        if status == "closed":
            current = must(call("GET", f"/api/surveys/{survey_id}", owner), "survey")["data"]
            must(call("PUT", f"/api/surveys/{survey_id}", owner, {**body, "status": "closed"},
                      headers={"If-Match": f'"{current["version"]}"'}), "close")
        print(f"  + {body['title']} ({owner}, {status}): {len(accepted)} responses")
        if kind == "helpdesk":
            follow_up(survey_id, owner, accepted, rng)

    flow_for_equipment(ids["Ekipman talebi"], groups)
    complex_flows(ids, groups)
    flow_for_proposals(ids["Yeni proje önerisi"], groups)
    flow_with_survey_step(ids["Saha ziyareti değerlendirmesi"])


def follow_up(survey_id, owner, response_ids, rng):
    """The help desk at work: most tickets handled, some open, a few overdue, with notes."""
    notes = ["Kullanıcıyla görüşüldü.", "Tedarikçiye iletildi, dönüş bekleniyor.", "Uzaktan bağlanılıp çözüldü.",
             "Cihaz değişimi gerekiyor; sipariş açıldı.", "Yetki talebi yöneticisi tarafından onaylandı."]
    for index, response_id in enumerate(response_ids):
        roll = index / max(1, len(response_ids))
        status = "resolved" if roll < 0.55 else "in_progress" if roll < 0.8 else "unresolved" if roll < 0.87 else "new"
        change = {"status": status}
        if status != "new":
            change.update({"priority": rng.choice(["low", "normal", "normal", "high"]),
                           "assignee": rng.choice(["bob", "carol", "admin@mail.com"]),
                           "tracking": {"ticket": f"BT-{2400 + index}",
                                        "rootCause": rng.choice(["user", "device", "config", "vendor"]),
                                        **({"effort": rng.choice([0.5, 1, 2, 4])} if status == "resolved" else {})}})
        if status in ("in_progress", "unresolved"):
            change["followUpAt"] = rng.choice(["2026-11-18", "2026-11-25", "2026-11-28", "2026-12-03"])
        call("PATCH", f"/api/surveys/{survey_id}/responses/{response_id}", owner, change)
        if status != "new" and rng.random() < 0.6:
            for note in rng.sample(notes, rng.randint(1, 2)):
                call("POST", f"/api/surveys/{survey_id}/responses/{response_id}/notes", rng.choice([owner, CAROL]),
                     {"text": note})
    print(f"  ⇢ follow-up and notes on {len(response_ids)} help-desk tickets")


def flow_exists(name, owner):
    return name in {f["name"] for f in must(call("GET", f"/api/flows?page=1&size=100", owner), "flows")["data"]}


def flow_for_equipment(survey_id, groups):
    name = "Ekipman talebi onayı"
    if flow_exists(name, CAROL):
        return
    template = must(call("POST", "/api/mail-templates", CAROL, {
        "name": "Ekipman talebi bildirimi", "description": "Anket yanıtından gelen ekipman talebi",
        "subject": "Ekipman talebi #{{trigger.responseId}}: {{trigger.text.item}}",
        "bodyHtml": STYLE + """
<div class="wrap">
  <h1>Yeni ekipman talebi</h1>
  <p class="meta">{{trigger.respondent}} · {{trigger.submittedAt}}</p>
  <table class="data">
    <tr><th>Talep</th><td>{{trigger.text.item}} {{trigger.text.itemOther}}</td></tr>
    <tr><th>Adet</th><td>{{trigger.text.quantity}}</td></tr>
    <tr><th>Tutar</th><td>{{trigger.text.amount}} TL</td></tr>
    <tr><th>Ne zaman</th><td>{{trigger.text.neededBy}}</td></tr>
    <tr><th>Gerekçe</th><td>{{trigger.text.reason}}{{^trigger.text.reason}}—{{/trigger.text.reason}}</td></tr>
    <tr><th>Onay</th><td>{{steps.onay.decision}}{{^steps.onay.decision}}gerekmedi{{/steps.onay.decision}}</td></tr>
  </table>
  """ + FOOTER + "\n</div>"}), "template")["data"]["id"]
    flow = must(call("POST", "/api/flows", CAROL, {
        "name": name, "description": "“Ekipman talebi” anketinin her yanıtında: 20.000 TL üstü kurul onayına, sonra BT'ye mail.",
        "steps": [
            {"type": "condition", "key": "buyuk", "expression": {">": [{"var": "trigger.answers.amount"}, 20000]}},
            {"type": "approval", "key": "onay", "groupId": groups["Satın alma kurulu"],
             "title": "Ekipman: {{trigger.text.item}} × {{trigger.text.quantity}} — {{trigger.text.amount}} TL",
             "description": "Talep eden: {{trigger.respondent}}\nGerekçe: {{trigger.text.reason}}", "timeoutHours": 48},
            mail_step("bt_maili", template, ["bt@example.com"]),
        ],
        "edges": [{"from": "buyuk", "to": "onay", "when": "true"}, {"from": "buyuk", "to": "bt_maili", "when": "false"},
                  {"from": "onay", "to": "bt_maili", "when": "approved"}]}), name)["data"]["id"]
    must(call("POST", f"/api/flows/{flow}/triggers", CAROL, {"type": "event", "enabled": True, "topic": f"survey:{survey_id}"}),
         "survey trigger")
    print(f"  + flow {name}")
    for user, answers in [
        (ALICE, {"item": "monitor", "quantity": 2, "amount": 9000, "neededBy": "2026-12-05"}),
        (BOB, {"item": "laptop", "quantity": 3, "amount": 135000, "neededBy": "2026-12-20", "reason": "Yeni katılan üç geliştirici için."}),
        (ADMIN, {"item": "other", "itemOther": "Toplantı odası kamerası", "quantity": 1, "amount": 28000,
                 "neededBy": "2027-01-10", "reason": "Hibrit toplantılarda ses ve görüntü sorunu."}),
        (CAROL, {"item": "headset", "quantity": 5, "amount": 7500, "neededBy": "2026-12-01"}),
    ]:
        status, _ = call("POST", f"/api/surveys/{survey_id}/responses", user, {"answers": answers})
        print(f"  ⇢ Ekipman talebi from {user}: HTTP {status}")
    status, body = call("POST", f"/api/surveys/{survey_id}/responses", ALICE,
                        {"answers": {"item": "laptop", "quantity": 1, "amount": 60000, "neededBy": "2026-12-01"}})
    print(f"  ✗ refused as expected: HTTP {status} {body.get('errors', [{}])[0].get('message') if isinstance(body, dict) else ''}")


def flow_for_proposals(survey_id, groups):
    name = "Proje önerisi değerlendirme"
    if flow_exists(name, ALICE):
        return
    template = must(call("POST", "/api/mail-templates", ALICE, {
        "name": "Proje önerisi PMO bildirimi", "description": "Onaylanan veya küçük bütçeli proje önerisi",
        "subject": "Yeni proje: {{trigger.text.title}} ({{trigger.text.budget}} TL)",
        "bodyHtml": STYLE + """
<div class="wrap">
  <h1>{{trigger.text.title}}</h1>
  <p class="meta">{{trigger.respondent}} · {{trigger.text.area}}</p>
  <table class="data">
    <tr><th>Bütçe</th><td>{{trigger.text.budget}} TL</td></tr>
    <tr><th>Tarih</th><td>{{trigger.text.start}} – {{trigger.text.end}}</td></tr>
    <tr><th>Fayda</th><td>{{trigger.text.benefit}}</td></tr>
    <tr><th>Komite</th><td>{{steps.komite.decidedBy}}{{^steps.komite.decidedBy}}gerekmedi{{/steps.komite.decidedBy}}</td></tr>
  </table>
  """ + FOOTER + "\n</div>"}), "template")["data"]["id"]
    flow = must(call("POST", "/api/flows", ALICE, {
        "name": name, "description": "Her proje önerisinde: 100.000 TL üstü bütçe komitesine; sonuç öneri sahibine bildirilir.",
        "steps": [
            {"type": "condition", "key": "buyuk_butce", "expression": {">": [{"var": "trigger.answers.budget"}, 100000]}},
            {"type": "approval", "key": "komite", "groupId": groups["Bütçe komitesi"],
             "title": "Proje önerisi: {{trigger.text.title}} — {{trigger.text.budget}} TL",
             "description": "Öneren: {{trigger.respondent}}\nAlan: {{trigger.text.area}}\nTarih: {{trigger.text.start}} – "
                            "{{trigger.text.end}}\nFayda: {{trigger.text.benefit}}\nGerekçe: {{trigger.text.justification}}",
             "timeoutHours": 120},
            mail_step("pmo", template, ["pmo@example.com"]),
        ],
        "edges": [{"from": "buyuk_butce", "to": "komite", "when": "true"},
                  {"from": "buyuk_butce", "to": "pmo", "when": "false"},
                  {"from": "komite", "to": "pmo", "when": "approved"}]}), name)["data"]["id"]
    must(call("POST", f"/api/flows/{flow}/triggers", ALICE, {"type": "event", "enabled": True, "topic": f"survey:{survey_id}"}),
         "survey trigger")
    print(f"  + flow {name}")
    for user, answers in [
        (BOB, {"title": "Fatura onayını otomatikleştirme", "area": "efficiency", "budget": 240000, "start": "2027-01-06",
               "end": "2027-04-30", "benefit": "Aylık 120 saat elle onay işi biter.",
               "justification": "RPA lisansı ve 3 aylık danışmanlık."}),
        (CAROL, {"title": "Müşteri memnuniyet paneli", "area": "customer", "budget": 45000, "start": "2026-12-15",
                 "end": "2027-02-15", "benefit": "Şikayetler haftalık izlenir."}),
        (ADMIN, {"title": "KVKK envanter güncellemesi", "area": "compliance", "budget": 160000, "start": "2027-02-01",
                 "end": "2027-03-31", "benefit": "Denetim bulgusu kapanır.", "justification": "Dış hukuk danışmanlığı."}),
    ]:
        status, _ = call("POST", f"/api/surveys/{survey_id}/responses", user, {"answers": answers})
        print(f"  ⇢ Proje önerisi from {user}: HTTP {status}")


def flow_with_survey_step(survey_id):
    name = "Saha ziyareti sonrası değerlendirme"
    if flow_exists(name, BOB):
        return
    template = must(call("POST", "/api/mail-templates", BOB, {
        "name": "Düşük ziyaret puanı", "description": "Saha ziyareti değerlendirmesi düşük geldiğinde",
        "subject": "Dikkat: ziyaret puanı {{steps.anket.answers.rating}}/5",
        "bodyHtml": STYLE + """
<div class="wrap">
  <h1>Düşük ziyaret değerlendirmesi</h1>
  <p class="meta">{{steps.anket.respondent}} · {{steps.anket.submittedAt}}</p>
  <table class="data">
    <tr><th>Puan</th><td>{{steps.anket.text.rating}}</td></tr>
    <tr><th>Sorun çözüldü mü?</th><td>{{steps.anket.text.solved}}</td></tr>
    <tr><th>Not</th><td>{{steps.anket.text.note}}</td></tr>
  </table>
  """ + FOOTER + "\n</div>"}), "template")["data"]["id"]
    flow = must(call("POST", "/api/flows", BOB, {
        "name": name, "description": "Elle başlatılır: müşteriye anket gider, yanıt gelince düşük puanlar ekip liderine iletilir.",
        "steps": [
            {"type": "survey", "key": "anket", "surveyId": survey_id, "to": ["bob@example.com"],
             "subject": "Ziyaretimizi değerlendirir misiniz?",
             "message": "Merhaba,\nbugünkü saha ziyaretimizle ilgili kısa bir değerlendirme rica ediyoruz.", "timeoutHours": 48},
            {"type": "condition", "key": "dusuk", "expression": {"<=": [{"var": "steps.anket.answers.rating"}, 3]}},
            mail_step("lider", template, ["saha-lideri@example.com"]),
        ],
        "edges": [{"from": "anket", "to": "dusuk", "when": "answered"},
                  {"from": "dusuk", "to": "lider", "when": "true"}]}), name)["data"]["id"]
    status, _ = call("POST", f"/api/flows/{flow}/runs", BOB)
    print(f"  + flow {name}; started (waits for the survey link mailed to bob@example.com): HTTP {status}")



# ---- cleanup: flows that showed the same thing as others (one dataset, one mail) ---------------------------

OBSOLETE_FLOWS = ["Aktif personel listesi", "Eğitim yenileme hatırlatması", "Geciken eğitimler için toplu kayıt",
                  "Yeni işe alım karşılama", "Departman 360 (aylık)", "Maaş özeti (yönetim)", "Ekibimin eğitim durumu",
                  "Aylık bütçe ve eğitim özeti", "Beceri envanteri (çeyreklik)", "Bütçe talebi onayı",
                  "Satın alma talebi onay akışı"]


def remove_obsolete_flows():
    """Deletes the demo flows that only repeated others; their active runs are cancelled first."""
    flows = must(call("GET", "/api/flows?page=1&size=100", ADMIN), "flows")["data"]
    for flow in flows:
        if flow["name"] not in OBSOLETE_FLOWS:
            continue
        active = must(call("GET", f"/api/flows/{flow['id']}/runs?status=queued&status=running&size=100", ADMIN), "runs")["data"]
        for run in active:
            call("POST", f"/api/flow-runs/{run['id']}/cancel", ADMIN)
        for _ in range(20):
            status, _ = call("DELETE", f"/api/flows/{flow['id']}", ADMIN)
            if status in (204, 404):
                break
            time.sleep(1)   # cancelling waits for the engine to stop the runs
        print(f"  − {flow['name']} (removed: repeated another demo flow) HTTP {status}")


# ---- complex flows: what the platform can do when it is put together --------------------------------------

def template_id(owner, name, description, subject, body):
    """A mail template by name, created if missing."""
    existing = must(call("GET", "/api/mail-templates?page=1&size=100", owner), "templates")["data"]
    for item in existing:
        if item["name"] == name:
            return item["id"]
    return must(call("POST", "/api/mail-templates", owner, {"name": name, "description": description,
                                                            "subject": subject, "bodyHtml": STYLE + body}), name)["data"]["id"]


def create_flow(owner, name, description, steps, edges, schedules=(), triggers=()):
    """Creates the flow with its schedules and triggers; returns (id, {trigger type: created trigger})."""
    if flow_exists(name, owner):
        print(f"  · {name} (exists)")
        return None, {}
    flow_id = must(call("POST", "/api/flows", owner, {"name": name, "description": description, "steps": steps,
                                                      "edges": edges}), name)["data"]["id"]
    for cron in schedules:
        must(call("POST", f"/api/flows/{flow_id}/schedules", owner,
                  {"cron": cron, "timeZone": "Europe/Istanbul", "enabled": True}), f"schedule {name}")
    created = {}
    for trigger in triggers:
        created[trigger["type"]] = must(call("POST", f"/api/flows/{flow_id}/triggers", owner,
                                             {"enabled": True, **trigger}), f"trigger {name}")["data"]
    print(f"  + {name}")
    return flow_id, created


def send_webhook(trigger, payload, event_id):
    url, secret = trigger["trigger"]["webhookUrl"], trigger["secret"]
    raw = json.dumps(payload, ensure_ascii=False).encode()
    signature = "sha256=" + hmac.new(secret.encode(), raw, hashlib.sha256).hexdigest()
    status, body = call("POST", url, raw=raw, headers={"X-Signature": signature, "X-Event-Id": event_id})
    return body.get("data", {}).get("runId") if isinstance(body, dict) else None


def kafka_send(topic, messages):
    """Kafka has no stdlib client: use the compose broker's console producer when Docker is there (local only)."""
    if not BASE.startswith(("http://localhost", "http://127.0.0.1")):
        print(f"  · kafka {topic} skipped: not local; produce to the server's broker by hand")
        return False
    try:
        subprocess.run(["docker", "exec", "-i", "dynamic-reports-kafka", "/opt/kafka/bin/kafka-console-producer.sh",
                        "--bootstrap-server", "localhost:9092", "--topic", topic,
                        "--property", "parse.key=true", "--property", "key.separator=|"],
                       input="\n".join(f"{key}|{json.dumps(value, ensure_ascii=False)}" for key, value in messages) + "\n",
                       text=True, check=True, capture_output=True, timeout=60)
        print(f"  ⇢ kafka {topic}: {len(messages)} messages")
        return True
    except (OSError, subprocess.SubprocessError) as error:
        print(f"  · kafka {topic} skipped ({error})")
        return False


def wait_for_step(run_id, key, status="waiting", seconds=30):
    for _ in range(seconds * 2):
        run = must(call("GET", f"/api/flow-runs/{run_id}", ADMIN), "run")["data"]
        for step in run.get("steps", []):
            if step["key"] == key and step["status"] == status:
                return step
        if run["status"] in ("completed", "failed", "cancelled"):
            return None
        time.sleep(0.5)
    return None


def complex_flows(ids, groups):
    print("Complex flows")
    order_risk_flows(groups)
    onboarding_flow(ids["Yeni çalışan ekipman listesi"], groups)
    compliance_flow(groups)


def order_risk_flows(groups):
    """Kafka in (with a trigger filter) → filter over the order lines → nested condition → approval → Kafka out;
    a second flow listens to the blocked orders: two flows chained by an event."""
    blocked = template_id(ADMIN, "Engellenen sipariş", "Risk onayından geçmeyen sipariş",
                          "Sipariş {{trigger.value.orderId}} engellendi ({{trigger.value.total}} {{trigger.value.currency}})", """
<div class="wrap">
  <h1>Sipariş {{trigger.value.orderId}} engellendi</h1>
  <p class="meta">{{trigger.value.customer}} · {{trigger.value.total}} {{trigger.value.currency}}</p>
  {{#steps.buyuk.result}}<p><span class="badge">Yüksek tutar</span> Yönetim bilgilendirildi; müşteri temsilcisi aramalı.</p>{{/steps.buyuk.result}}
  <table class="data">
    <tr><th>Karar veren</th><td>{{trigger.value.decidedBy}}{{^trigger.value.decidedBy}}süre doldu{{/trigger.value.decidedBy}}</td></tr>
    <tr><th>Gerekçe</th><td>{{trigger.value.reason}}</td></tr>
  </table>
  """ + FOOTER + "\n</div>")
    chain_id, _ = create_flow(ADMIN, "Engellenen sipariş takibi",
        "Olay zinciri: “Sipariş risk taraması” bir siparişi engelleyince flows.orders.blocked mesajıyla başlar; "
        "100.000 üstü tutarlar yönetime, diğerleri satış ekibine gider.", [
            {"type": "condition", "key": "buyuk", "expression": {">=": [{"var": "trigger.value.total"}, 100000]}},
            mail_step("yonetim", blocked, ["yonetim@example.com"]),
            mail_step("satis", blocked, ["satis@example.com"]),
        ], [{"from": "buyuk", "to": "yonetim", "when": "true"}, {"from": "buyuk", "to": "satis", "when": "false"}],
        triggers=[{"type": "kafka", "topic": "flows.orders.blocked"}])

    result = template_id(ADMIN, "Sipariş risk sonucu", "Sipariş risk taramasının özeti",
                         "Sipariş {{trigger.value.orderId}}: {{steps.onay.decision}}{{^steps.onay.decision}}otomatik serbest{{/steps.onay.decision}}", """
<div class="wrap">
  <h1>Sipariş {{trigger.value.orderId}}</h1>
  <p class="meta">{{trigger.value.customer.name}} · {{trigger.value.country}} · {{trigger.value.total}} {{trigger.value.currency}}</p>
  <p>{{steps.riskli.count}} / {{steps.riskli.total}} kalem riskli bulundu.</p>
  <table class="data">
    <thead><tr><th>Ürün</th><th>Kategori</th><th>Adet</th><th>Birim fiyat</th></tr></thead>
    <tbody>{{#steps.riskli.items}}<tr><td>{{name}}</td><td>{{category}}</td><td>{{qty}}</td><td>{{price}}</td></tr>{{/steps.riskli.items}}
    {{^steps.riskli.items}}<tr><td colspan="4">Riskli kalem yok.</td></tr>{{/steps.riskli.items}}</tbody>
  </table>
  {{#steps.onay}}<p class="note">Karar: <b>{{decision}}</b> · {{decidedBy}} {{comment}}</p>{{/steps.onay}}
  {{^steps.onay}}<p class="note">İnceleme gerekmedi; sipariş otomatik serbest bırakıldı.</p>{{/steps.onay}}
  """ + FOOTER + "\n</div>")
    flow_id, _ = create_flow(ADMIN, "Sipariş risk taraması",
        "Kafka flows.orders.created → tetikleyici filtresi (≥ 1.000, TR/DE/NL) → riskli kalemleri süz (adet × fiyat > "
        "5.000 veya elektronik/mücevher) → iç içe koşul → güvenlik onayı → serbest bırak ya da engelle (Kafka) → özet mail.", [
            {"type": "filter", "key": "riskli", "source": "trigger.value.items", "expression": {"or": [
                {">": [{"*": [{"var": "item.qty"}, {"var": "item.price"}]}, 5000]},
                {"in": [{"var": "item.category"}, ["electronics", "jewelry"]]}]}},
            {"type": "condition", "key": "inceleme", "expression": {"or": [
                {">": [{"var": "steps.riskli.count"}, 0]},
                {"and": [{">=": [{"var": "trigger.value.total"}, 50000]},
                         {"==": [{"var": "trigger.value.customer.newCustomer"}, True]}]}]}},
            {"type": "approval", "key": "onay", "groupId": groups["Güvenlik ekibi"],
             "title": "Sipariş {{trigger.value.orderId}} — {{trigger.value.total}} {{trigger.value.currency}} ({{steps.riskli.count}} riskli kalem)",
             "description": "Müşteri: {{trigger.value.customer.name}}{{#trigger.value.customer.newCustomer}} (yeni müşteri){{/trigger.value.customer.newCustomer}}\n"
                            "Ülke: {{trigger.value.country}}\nRiskli kalemler:\n{{#steps.riskli.items}}• {{name}} ({{category}}) × {{qty}} = birim {{price}}\n{{/steps.riskli.items}}",
             "timeoutHours": 4},
            {"type": "kafka", "key": "serbest", "topic": "flows.orders.released", "messageKey": "{{trigger.value.orderId}}",
             "payload": '{"orderId": "{{trigger.value.orderId}}", "approvedBy": "{{steps.onay.decidedBy}}", "riskyLines": {{steps.riskli.count}}}'},
            {"type": "kafka", "key": "engelle", "topic": "flows.orders.blocked", "messageKey": "{{trigger.value.orderId}}",
             "payload": '{"orderId": "{{trigger.value.orderId}}", "customer": "{{trigger.value.customer.name}}", '
                        '"total": {{trigger.value.total}}, "currency": "{{trigger.value.currency}}", '
                        '"decidedBy": "{{steps.onay.decidedBy}}", "reason": "{{steps.onay.comment}}"}'},
            mail_step("ozet", result, ["satis@example.com"]),
        ], [{"from": "riskli", "to": "inceleme"},
            {"from": "inceleme", "to": "onay", "when": "true"}, {"from": "inceleme", "to": "serbest", "when": "false"},
            {"from": "onay", "to": "serbest", "when": "approved"}, {"from": "onay", "to": "engelle", "when": None},
            {"from": "serbest", "to": "ozet"}, {"from": "engelle", "to": "ozet"}],
        triggers=[{"type": "kafka", "topic": "flows.orders.created", "filter": {"and": [
            {">=": [{"var": "trigger.value.total"}, 1000]},
            {"in": [{"var": "trigger.value.country"}, ["TR", "DE", "NL"]]}]}}])
    if flow_id is None:
        return
    orders = [
        ("o-501", {"orderId": "o-501", "country": "TR", "currency": "TRY", "total": 3200,
                   "customer": {"name": "Anadolu Ofis", "newCustomer": False},
                   "items": [{"name": "Kablosuz kulaklık", "category": "electronics", "qty": 2, "price": 1600}]}),
        ("o-502", {"orderId": "o-502", "country": "DE", "currency": "EUR", "total": 128000,
                   "customer": {"name": "Berlin Möbel GmbH", "newCustomer": True},
                   "items": [{"name": "Yönetici masası", "category": "furniture", "qty": 4, "price": 18500},
                             {"name": "Toplantı masası", "category": "furniture", "qty": 2, "price": 27000}]}),
        ("o-503", {"orderId": "o-503", "country": "NL", "currency": "EUR", "total": 1800,
                   "customer": {"name": "Delft Print", "newCustomer": False},
                   "items": [{"name": "Kalem seti", "category": "stationery", "qty": 30, "price": 60}]}),
        ("o-504", {"orderId": "o-504", "country": "TR", "currency": "TRY", "total": 450,
                   "customer": {"name": "Küçük sipariş"}, "items": []}),
        ("o-505", {"orderId": "o-505", "country": "US", "currency": "USD", "total": 9000,
                   "customer": {"name": "Austin Labs"}, "items": []}),
    ]
    # a new trigger's consumer starts at the latest offset once the listeners reconcile (flows.kafka.reconcile-interval)
    print("    waiting 35 s for the Kafka listener to subscribe to flows.orders.created …")
    time.sleep(35)
    if kafka_send("flows.orders.created", orders):
        print("    o-501, o-502 go to the security team; o-503 is released at once; o-504 and o-505 are filtered out")
        time.sleep(8)
        pending = must(call("GET", "/api/approval-requests?scope=mine&status=pending&q=o-502&size=5", BOB), "approvals")["data"]
        for request in pending:
            call("POST", f"/api/approval-requests/{request['id']}/reject", BOB,
                 {"comment": "Yeni müşteri, ön ödeme olmadan bu tutar riskli."})
            print("  ✗ o-502 rejected by bob → flows.orders.blocked → “Engellenen sipariş takibi” starts")


def onboarding_flow(survey_id, groups):
    """Webhook from the HR system → validation (JSON Logic) → a survey to the manager, waiting for the answer →
    approval only when needed → Kafka to IT provisioning → summary mail; every other way ends in a warning mail."""
    summary = template_id(ADMIN, "İşe giriş özeti", "Yeni çalışanın ekipman ve erişim özeti",
                          "İşe giriş: {{trigger.fullName}} ({{trigger.department}}, {{trigger.startDate}})", """
<div class="wrap">
  <h1>{{trigger.fullName}} aramıza katılıyor</h1>
  <p class="meta">{{trigger.role}} · {{trigger.department}} · başlangıç {{trigger.startDate}} · sicil {{trigger.employeeId}}</p>
  <table class="data">
    <tr><th>Ekipman</th><td>{{steps.ekipman.text.items}} {{steps.ekipman.text.laptopType}} {{steps.ekipman.text.otherItem}}</td></tr>
    <tr><th>Erişimler</th><td>{{steps.ekipman.text.access}}{{^steps.ekipman.text.access}}—{{/steps.ekipman.text.access}}</td></tr>
    <tr><th>Tahmini maliyet</th><td>{{steps.ekipman.text.cost}} TL</td></tr>
    <tr><th>Listeyi dolduran</th><td>{{steps.ekipman.respondent}}</td></tr>
    <tr><th>Onay</th><td>{{#steps.bt_onayi}}{{decision}} · {{decidedBy}}{{/steps.bt_onayi}}{{^steps.bt_onayi}}gerekmedi{{/steps.bt_onayi}}</td></tr>
  </table>
  <p class="note">BT hazırlık kaydı flows.it.provisioning kuyruğuna yazıldı.</p>
  """ + FOOTER + "\n</div>")
    warning = template_id(ADMIN, "İşe giriş uyarısı", "İşe giriş akışı tamamlanamadığında",
                          "Dikkat: {{trigger.fullName}}{{^trigger.fullName}}isimsiz kayıt{{/trigger.fullName}} için işe giriş tamamlanamadı", """
<div class="wrap">
  <h1>İşe giriş tamamlanamadı</h1>
  <p class="meta">{{trigger.fullName}} · {{trigger.department}} · sicil {{trigger.employeeId}}</p>
  {{^steps.veri_tam.result}}<p>İK sisteminden gelen kayıt eksik ya da departman tanınmıyor; kaydı düzeltip yeniden gönderin.</p>{{/steps.veri_tam.result}}
  {{#steps.veri_tam.result}}{{^steps.ekipman.answers}}<p>Yönetici ({{trigger.managerEmail}}) ekipman listesini süresi içinde doldurmadı.</p>{{/steps.ekipman.answers}}{{/steps.veri_tam.result}}
  {{#steps.bt_onayi}}<p>Güvenlik ekibi kararı: <b>{{decision}}</b> · {{decidedBy}} {{comment}}</p>{{/steps.bt_onayi}}
  """ + FOOTER + "\n</div>")
    flow_id, triggers = create_flow(ADMIN, "Yeni çalışan işe giriş (uçtan uca)",
        "İK sistemi webhook'u → kayıt kontrolü → yöneticiye ekipman anketi (yanıt beklenir) → 40.000 TL üstü ya da ERP "
        "erişimi varsa güvenlik onayı → BT hazırlık kaydı (Kafka) → özet mail. Eksik kayıt, yanıtsız anket ve red "
        "uyarı maili ile biter.", [
            {"type": "condition", "key": "veri_tam", "expression": {"and": [
                {"!!": {"var": "trigger.fullName"}}, {"!!": {"var": "trigger.managerEmail"}},
                {"in": [{"var": "trigger.department"}, ["Software Development", "Sales", "Finance",
                                                       "Human Resources", "Marketing"]]}]}},
            {"type": "survey", "key": "ekipman", "surveyId": survey_id, "to": ["{{trigger.managerEmail}}"],
             "subject": "{{trigger.fullName}} için ekipman listesi",
             "message": "Merhaba,\n{{trigger.fullName}} {{trigger.startDate}} tarihinde {{trigger.role}} olarak ekibinize "
                        "katılıyor. Gerekli ekipman ve erişimleri seçer misiniz?", "timeoutHours": 48},
            {"type": "condition", "key": "onay_gerekli", "expression": {"or": [
                {">": [{"var": "steps.ekipman.answers.cost"}, 40000]},
                {"in": ["erp", {"var": "steps.ekipman.answers.access"}]}]}},
            {"type": "approval", "key": "bt_onayi", "groupId": groups["Güvenlik ekibi"],
             "title": "{{trigger.fullName}}: ekipman ve erişim — {{steps.ekipman.text.cost}} TL",
             "description": "Departman: {{trigger.department}} · Rol: {{trigger.role}}\nEkipman: {{steps.ekipman.text.items}}\n"
                            "Erişimler: {{steps.ekipman.text.access}}\nGerekçe: {{steps.ekipman.text.notes}}\n"
                            "Listeyi dolduran: {{steps.ekipman.respondent}}", "timeoutHours": 24},
            {"type": "kafka", "key": "bt_kaydi", "topic": "flows.it.provisioning", "messageKey": "{{trigger.employeeId}}",
             "payload": '{"employeeId": "{{trigger.employeeId}}", "name": "{{trigger.fullName}}", '
                        '"startDate": "{{trigger.startDate}}", "equipment": "{{steps.ekipman.text.items}}", '
                        '"access": "{{steps.ekipman.text.access}}", "approvedBy": "{{steps.bt_onayi.decidedBy}}"}'},
            mail_step("ozet", summary, ["ik@example.com", "bt@example.com"]),
            mail_step("uyari", warning, ["ik@example.com"]),
        ], [{"from": "veri_tam", "to": "ekipman", "when": "true"}, {"from": "veri_tam", "to": "uyari", "when": "false"},
            {"from": "ekipman", "to": "onay_gerekli", "when": "answered"}, {"from": "ekipman", "to": "uyari", "when": "timeout"},
            {"from": "onay_gerekli", "to": "bt_onayi", "when": "true"}, {"from": "onay_gerekli", "to": "bt_kaydi", "when": "false"},
            {"from": "bt_onayi", "to": "bt_kaydi", "when": "approved"}, {"from": "bt_onayi", "to": "uyari", "when": None},
            {"from": "bt_kaydi", "to": "ozet"}],
        triggers=[{"type": "webhook"}])
    if flow_id is None:
        return
    hook = triggers["webhook"]
    hires = [
        ({"employeeId": "E-2041", "fullName": "Ece Yıldız", "department": "Software Development", "role": "Backend geliştirici",
          "startDate": "2026-12-01", "managerEmail": "bob@example.com"},
         {"items": ["laptop", "monitor", "headset"], "laptopType": "mac", "access": ["vpn", "github", "erp"],
          "cost": 92000, "notes": "Fatura modülü üzerinde çalışacak; ERP okuma erişimi gerekli."}),
        ({"employeeId": "E-2042", "fullName": "Mert Can", "department": "Sales", "role": "Satış temsilcisi",
          "startDate": "2026-12-15", "managerEmail": "carol@example.com"},
         {"items": ["laptop", "phone"], "laptopType": "win", "access": ["vpn", "crm"], "cost": 38000}),
        ({"employeeId": "E-2043", "fullName": "Zeynep Ak", "department": "Hukuk", "role": "Avukat",
          "startDate": "2026-12-08", "managerEmail": "alice@example.com"}, None),
        ({"employeeId": "E-2044", "fullName": "Deniz Ay", "department": "Finance", "role": "Analist",
          "startDate": "2027-01-05", "managerEmail": "alice@example.com"}, None),
    ]
    for hire, answers in hires:
        run_id = send_webhook(hook, hire, hire["employeeId"])
        print(f"  ⇢ webhook {hire['employeeId']} {hire['fullName']} ({hire['department']}) → run {run_id}")
        if answers is None or run_id is None:
            continue
        step = wait_for_step(run_id, "ekipman")
        link = (step or {}).get("output", {}).get("link", "")
        token = link.split("invitation=")[-1] if "invitation=" in link else None
        if token:
            manager = BOB if hire["managerEmail"].startswith("bob") else CAROL
            status, _ = call("POST", f"/api/surveys/{survey_id}/responses", manager,
                             {"answers": answers, "invitation": token})
            print(f"    {manager} filled in the equipment list: HTTP {status}")
    print("    E-2041 waits for the security team (92.000 TL + ERP); E-2042 goes straight to IT; E-2043 ends with "
          "'unknown department'; E-2044 waits for alice's answer")


def compliance_flow(groups):
    """Two datasets → filter over the dataset rows → if/else JSON Logic with arithmetic → approval (four-eyes: the
    owner may not approve) → Kafka to the learning system → one mail with both tables and both Excel files."""
    report = template_id(ALICE, "Eğitim uyum raporu", "Aylık eğitim uyum denetiminin raporu",
                         "Eğitim uyumu {{run.date}}: {{steps.geciken.rowCount}} gecikmiş, {{steps.yaklasan.rowCount}} yaklaşan", """
<div class="wrap">
  <h1>Aylık eğitim uyum denetimi</h1>
  <p class="meta">{{run.date}} · <span class="badge">{{steps.geciken.rowCount}} gecikmiş</span>
    <span class="badge">{{steps.yaklasan.rowCount}} yaklaşan</span>
    <span class="badge">yazılım ekibinde {{steps.yazilim.count}}</span></p>
  {{#steps.toplu_kayit}}<p>Toplu kayıt kararı: <b>{{decision}}</b> · {{decidedBy}} {{comment}}</p>{{/steps.toplu_kayit}}
  <h2 style="font-size:15px;margin:20px 0 8px">Süresi geçmiş eğitimler</h2>
  {{#steps.geciken.hasRows}}
  <table class="data">
    <thead><tr>{{#steps.geciken.columns}}<th>{{.}}</th>{{/steps.geciken.columns}}</tr></thead>
    <tbody>{{#steps.geciken.rows}}<tr>{{#cells}}<td>{{.}}</td>{{/cells}}</tr>{{/steps.geciken.rows}}</tbody>
  </table>
  {{#steps.geciken.previewTruncated}}<p class="note">İlk satırlar; tamamı ekte.</p>{{/steps.geciken.previewTruncated}}
  {{/steps.geciken.hasRows}}
  {{^steps.geciken.hasRows}}<p>Gecikmiş eğitim yok. 🎉</p>{{/steps.geciken.hasRows}}
  <h2 style="font-size:15px;margin:20px 0 8px">60 gün içinde yenilenecekler</h2>
  <table class="data">
    <thead><tr>{{#steps.yaklasan.columns}}<th>{{.}}</th>{{/steps.yaklasan.columns}}</tr></thead>
    <tbody>{{#steps.yaklasan.rows}}<tr>{{#cells}}<td>{{.}}</td>{{/cells}}</tr>{{/steps.yaklasan.rows}}</tbody>
  </table>
  """ + FOOTER + "\n</div>")
    flow_id, _ = create_flow(ALICE, "Aylık eğitim uyum denetimi",
        "Ayın 1'i 08:00: gecikmiş ve yaklaşan zorunlu eğitimler → yazılım ekibinin gecikmeleri süzülür → "
        "gecikme varsa ya da toplam 3'ü aşarsa Eğitim birimi toplu kayıt onaylar (sahibi kendi talebini "
        "onaylayamaz) → LMS'e Kafka kaydı → iki tablo ve iki Excel ekli rapor.", [
            dataset_step("geciken", "suresi_gecen_egitimler", rows=15),
            dataset_step("yaklasan", "egitim_yenileme_takvimi", rows=10),
            {"type": "filter", "key": "yazilim", "source": "steps.geciken.rows",
             "expression": {"==": [{"var": "item.cells.1"}, "Software Development"]}},
            {"type": "condition", "key": "aksiyon", "expression": {"if": [
                {">": [{"var": "steps.geciken.rowCount"}, 0]}, True,
                {">": [{"+": [{"var": "steps.geciken.rowCount"}, {"var": "steps.yaklasan.rowCount"}]}, 3]}]}},
            {"type": "approval", "key": "toplu_kayit", "groupId": groups["Eğitim birimi"],
             "title": "{{steps.geciken.rowCount}} gecikmiş + {{steps.yaklasan.rowCount}} yaklaşan eğitim: toplu kayıt açılsın mı?",
             "description": "Yazılım ekibinde {{steps.yazilim.count}} gecikme var.\nListeler rapor mailinde Excel olarak ekte.",
             "timeoutHours": 72},
            {"type": "kafka", "key": "lms", "topic": "flows.lms.bulk-enroll", "messageKey": "{{run.id}}",
             "payload": '{"run": {{run.id}}, "overdue": {{steps.geciken.rowCount}}, "upcoming": {{steps.yaklasan.rowCount}}, '
                        '"approvedBy": "{{steps.toplu_kayit.decidedBy}}"}'},
            mail_step("rapor", report, ["ik@example.com"], ["geciken", "yaklasan"]),
        ], [{"from": "geciken", "to": "yaklasan"}, {"from": "yaklasan", "to": "yazilim"}, {"from": "yazilim", "to": "aksiyon"},
            {"from": "aksiyon", "to": "toplu_kayit", "when": "true"}, {"from": "aksiyon", "to": "rapor", "when": "false"},
            {"from": "toplu_kayit", "to": "lms", "when": "approved"}, {"from": "toplu_kayit", "to": "rapor", "when": None},
            {"from": "lms", "to": "rapor"}],
        schedules=["0 8 1 * *"])
    if flow_id is not None:
        status, _ = call("POST", f"/api/flows/{flow_id}/runs", ALICE)
        print(f"  ▶ Aylık eğitim uyum denetimi started by alice (carol decides: four-eyes): HTTP {status}")


if __name__ == "__main__":
    main()
