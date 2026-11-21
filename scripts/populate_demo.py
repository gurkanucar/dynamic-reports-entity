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
        # 1. simplest: one dataset, one mail, Excel attached, no rows in the body
        (ADMIN, "Aktif personel listesi", "Elle başlatılır: aktif personel Excel olarak İK'ya gider.", [
            dataset_step("dataset", "aktif_personel", rows=0),
            mail_step("mail", templates["Kısa bildirim"], ["ik@example.com"], ["dataset"]),
        ], [], [], []),
        # 2. schedule + condition: mails only when there is something to renew
        (ALICE, "Eğitim yenileme hatırlatması", "Her pazartesi 09:00; yenilenecek eğitim yoksa mail gitmez.", [
            dataset_step("dataset", "egitim_yenileme_takvimi", rows=100),
            {"type": "condition", "key": "var_mi", "expression": {">": [{"var": "steps.dataset.rowCount"}, 0]}},
            mail_step("mail", templates["Eğitim hatırlatması"], ["ik@example.com"], ["dataset"]),
        ], [{"from": "dataset", "to": "var_mi"}, {"from": "var_mi", "to": "mail", "when": "true"}],
         [("0 9 * * MON", True)], []),
        # 3. condition → approval → two outcomes
        (ALICE, "Geciken eğitimler için toplu kayıt", "Ayın 1'i 08:00: gecikme varsa İK yöneticilerine onaya gider.", [
            dataset_step("gecikenler", "suresi_gecen_egitimler", rows=50),
            {"type": "condition", "key": "gecikme_var", "expression": {">": [{"var": "steps.gecikenler.rowCount"}, 0]}},
            {"type": "approval", "key": "ik_onayi", "groupId": groups["İK yöneticileri"],
             "title": "{{steps.gecikenler.rowCount}} geciken zorunlu eğitim için toplu kayıt açılsın mı?",
             "description": "Liste ekte; onaylanırsa eğitim birimine iletilir.", "timeoutHours": 48,
             "notifyMembers": True},
            mail_step("egitim_birimine", templates["Eğitim hatırlatması"], ["egitim@example.com"], ["gecikenler"]),
            mail_step("reddedildi", templates["Kısa bildirim"], ["alice@example.com"]),
        ], [{"from": "gecikenler", "to": "gecikme_var"}, {"from": "gecikme_var", "to": "ik_onayi", "when": "true"},
            {"from": "ik_onayi", "to": "egitim_birimine", "when": "approved"},
            {"from": "ik_onayi", "to": "reddedildi", "when": None}],
         [("0 8 1 * *", True)], []),
        # 4. two datasets into one mail with two tables and two attachments
        (BOB, "Haftalık proje raporu", "Hafta içi 08:30: portföy ve kapasite tek mailde, iki Excel eki.", [
            dataset_step("portfoy", "proje_portfoyu", rows=20),
            dataset_step("kapasite", "proje_kapasitesi", rows=50),
            mail_step("mail", templates["Proje raporu (iki tablo)"], ["pmo@example.com"], ["portfoy", "kapasite"]),
        ], [{"from": "portfoy", "to": "kapasite"}, {"from": "kapasite", "to": "mail"}],
         [("30 8 * * MON-FRI", True)], []),
        # 5. webhook in, threshold, approval, Kafka out
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
        # 6. Kafka in, filter on the message, approval, welcome mail
        (ADMIN, "Yeni işe alım karşılama", "flows.hr.new-hire mesajı gelince İK onaylar, çalışana karşılama maili gider.", [
            {"type": "approval", "key": "ik_onayi", "groupId": groups["İK yöneticileri"],
             "title": "Yeni işe alım: {{trigger.value.fullName}} ({{trigger.value.department}})",
             "description": "Başlangıç: {{trigger.value.startDate}}", "timeoutHours": 24, "notifyMembers": True},
            mail_step("karsilama", templates["Yeni çalışan karşılama"], ["yeni.calisan@example.com"]),
        ], [{"from": "ik_onayi", "to": "karsilama", "when": "approved"}],
         [], ["kafka:flows.hr.new-hire"]),
        # 7. the heaviest dataset (lists through several relations), Excel only
        (ADMIN, "Departman 360 (aylık)", "Ayın ilk iş günü 07:00: tüm departmanların 360° görünümü Excel olarak.", [
            dataset_step("dataset", "departman_360", rows=0),
            mail_step("mail", templates["Kısa bildirim"], ["yonetim@example.com"], ["dataset"]),
        ], [], [("0 7 1-7 * MON", True)], []),
        # 8. salaries: only people with SALARY_VIEW may own it
        (ADMIN, "Maaş özeti (yönetim)", "Elle: departman maaş ortalamaları, yalnızca yönetime.", [
            dataset_step("dataset", "maas_ozeti", rows=20),
            mail_step("mail", templates["Eğitim hatırlatması"], ["yonetim@example.com"], ["dataset"]),
        ], [], [], []),
        # 9. row security: carol's flow only ever sees her own department
        (CAROL, "Ekibimin eğitim durumu", "Carol'ın akışı: yalnızca kendi ekibini görür.", [
            dataset_step("dataset", "ekibimin_egitimleri", rows=20),
            mail_step("mail", templates["Kısa bildirim"], ["carol@example.com"], ["dataset"]),
        ], [], [], []),
        # 10. webhook in: high risk goes to the change board, the rest is a standard change
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
        # 11. two summaries every month for the budget committee
        (BOB, "Aylık bütçe ve eğitim özeti", "Her ayın 1'i 09:00: proje bütçeleri ve eğitim başarısı iki ekle.", [
            dataset_step("butce", "departman_butceleri", rows=20),
            dataset_step("egitim", "egitim_basari_ozeti", rows=0),
            mail_step("mail", templates["Kısa bildirim"], ["butce.komitesi@example.com"], ["butce", "egitim"]),
        ], [{"from": "butce", "to": "egitim"}, {"from": "egitim", "to": "mail"}],
         [("0 9 1 * *", True)], []),
        # 12. quarterly inventory, CSV for another system
        (ADMIN, "Beceri envanteri (çeyreklik)", "Çeyrek başlarında beceri envanteri CSV olarak İK'ya.", [
            dataset_step("dataset", "beceri_envanteri", rows=50, fmt="csv"),
            mail_step("mail", templates["Kısa bildirim"], ["ik@example.com"], ["dataset"]),
        ], [], [("0 8 1 1,4,7,10 *", True)], []),
    ]

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
    for name in ("Aktif personel listesi", "Eğitim yenileme hatırlatması", "Geciken eğitimler için toplu kayıt",
                 "Haftalık proje raporu", "Departman 360 (aylık)", "Maaş özeti (yönetim)", "Ekibimin eğitim durumu",
                 "Haftalık departman kadrosu", "Zorunlu eğitim uyumu", "Aylık bütçe ve eğitim özeti",
                 "Beceri envanteri (çeyreklik)"):
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

    # Kafka has no stdlib client: use the compose broker's console producer when Docker is there
    hires = ['h-201|{"fullName": "Ece Yıldız", "department": "Software Development", "startDate": "2026-12-01"}',
             'h-202|{"fullName": "Mert Can", "department": "Sales", "startDate": "2026-12-15"}']
    local = BASE.startswith(("http://localhost", "http://127.0.0.1"))
    try:
        if not local:
            raise OSError("not local: the local broker is not the server's")
        subprocess.run(["docker", "exec", "-i", "dynamic-reports-kafka", "/opt/kafka/bin/kafka-console-producer.sh",
                        "--bootstrap-server", "localhost:9092", "--topic", "flows.hr.new-hire",
                        "--property", "parse.key=true", "--property", "key.separator=|"],
                       input="\n".join(hires) + "\n", text=True, check=True, capture_output=True, timeout=60)
        print("  ⇢ kafka flows.hr.new-hire: 2 messages")
    except (OSError, subprocess.SubprocessError) as error:
        print(f"  · kafka skipped ({error}); send a message to flows.hr.new-hire on the server's broker by hand")

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



# ---- surveys: a satisfaction survey with answers, a request form that starts a flow, a draft ---------------

def choice(id_, label, type_="singleChoice", options=(), **extra):
    return {"id": id_, "type": type_, "label": label, "options": [{"id": o, "label": l} for o, l in options], **extra}


SURVEYS = [
    (ADMIN, {
        "title": "Çalışan memnuniyeti 2026", "status": "published", "anonymous": True, "oneResponsePerUser": False,
        "description": "Çalışma ortamımızı iyileştirmek için görüşleriniz önemli. Anonimdir, 3 dakika sürer.",
        "definition": {"submitLabel": "Gönder", "successMessage": "Teşekkürler! Görüşleriniz anonim olarak kaydedildi.",
                       "rules": [], "fields": [
            {"id": "general", "type": "section", "label": "Genel"},
            choice("department", "Departmanınız", "dropdown", [("it", "Bilgi Teknolojileri"), ("hr", "İnsan Kaynakları"),
                   ("finance", "Finans"), ("sales", "Satış")], required=True),
            {"id": "satisfaction", "type": "rating", "label": "İşinizden ne kadar memnunsunuz?", "required": True, "max": 5},
            {"id": "whyLow", "type": "longText", "label": "Memnuniyetinizi düşüren en önemli şey nedir?",
             "visibleWhen": {"<=": [{"var": "satisfaction"}, 2]}},
            choice("benefits", "En değerli bulduğunuz yan haklar", "multipleChoice", [("remote", "Uzaktan çalışma"),
                   ("health", "Özel sağlık sigortası"), ("training", "Eğitim bütçesi"), ("meal", "Yemek kartı")]),
            {"id": "recommend", "type": "nps", "label": "Şirketimizi çalışma yeri olarak önerir misiniz?", "required": True},
            {"id": "suggestion", "type": "longText", "label": "Eklemek istedikleriniz"},
        ]},
    }),
    (CAROL, {
        "title": "Ekipman talebi", "status": "published", "anonymous": False, "oneResponsePerUser": False,
        "description": "Bilgisayar, telefon ve çevre birimi taleplerinizi buradan iletin. 20.000 TL üstü talepler onaya gider.",
        "definition": {"submitLabel": "Talebi gönder", "successMessage": "Talebiniz alındı; BT ekibi sizinle iletişime geçecek.",
                       "fields": [
            choice("item", "Ne lazım?", "dropdown", [("laptop", "Dizüstü bilgisayar"), ("monitor", "Monitör"),
                   ("phone", "Telefon"), ("headset", "Kulaklık"), ("other", "Diğer")], required=True),
            {"id": "itemOther", "type": "shortText", "label": "Lütfen belirtin", "required": True,
             "visibleWhen": {"==": [{"var": "item"}, "other"]}},
            {"id": "quantity", "type": "number", "label": "Adet", "required": True, "min": 1, "max": 50, "width": "half"},
            {"id": "amount", "type": "number", "label": "Tahmini tutar (TL)", "required": True, "min": 0, "width": "half"},
            {"id": "neededBy", "type": "date", "label": "Ne zamana lazım?", "required": True},
            {"id": "reason", "type": "longText", "label": "Gerekçe",
             "help": "20.000 TL üstü taleplerde zorunlu; onaylayan kişi bunu okur."},
        ], "rules": [{"id": "reasonForBig", "message": "20.000 TL üstü taleplerde gerekçe yazılmalı.",
                      "logic": {"or": [{"<=": [{"var": "amount"}, 20000]}, {"!!": {"var": "reason"}}]}}]},
    }),
    (ALICE, {
        "title": "Ofise dönüş tercihleri", "status": "draft", "anonymous": False, "oneResponsePerUser": True,
        "description": "Hibrit çalışma düzenini planlamak için.",
        "definition": {"rules": [], "fields": [
            choice("days", "Haftada kaç gün ofiste olmak istersiniz?", "singleChoice",
                   [("1", "1 gün"), ("2", "2 gün"), ("3", "3 gün"), ("5", "Her gün")], required=True),
            choice("weekdays", "Hangi günler?", "multipleChoice", [("mon", "Pazartesi"), ("tue", "Salı"),
                   ("wed", "Çarşamba"), ("thu", "Perşembe"), ("fri", "Cuma")]),
            choice("commute", "Ulaşım", "dropdown", [("car", "Araç"), ("public", "Toplu taşıma"), ("shuttle", "Servis")]),
        ]},
    }),
]


def populate_surveys(groups):
    import random
    rng = random.Random(42)
    print("Surveys")
    ids = {}
    for owner in (ADMIN, ALICE, CAROL):
        for item in must(call("GET", "/api/surveys?scope=mine&page=1&size=100", owner), "surveys")["data"]:
            ids.setdefault(item["title"], item["id"])
    created = set()
    for owner, survey in SURVEYS:
        if survey["title"] in ids:
            print(f"  · {survey['title']} (exists)")
            continue
        ids[survey["title"]] = must(call("POST", "/api/surveys", owner, survey), survey["title"])["data"]["id"]
        created.add(survey["title"])
        print(f"  + {survey['title']} ({owner})")

    # the request form starts a flow: big amounts go to the purchasing board, then IT gets a mail
    flow_name = "Ekipman talebi onayı"
    request_id = ids["Ekipman talebi"]
    existing = {f["name"] for f in must(call("GET", "/api/flows?page=1&size=100", CAROL), "flows")["data"]}
    if flow_name not in existing:
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
            "name": flow_name, "description": "“Ekipman talebi” anketinin her yanıtında: 20.000 TL üstü kurul onayına, "
                                              "sonra BT'ye mail.",
            "steps": [
                {"type": "condition", "key": "buyuk", "expression": {">": [{"var": "trigger.answers.amount"}, 20000]}},
                {"type": "approval", "key": "onay", "groupId": groups["Satın alma kurulu"],
                 "title": "Ekipman: {{trigger.text.item}} × {{trigger.text.quantity}} — {{trigger.text.amount}} TL",
                 "description": "Talep eden: {{trigger.respondent}}\nGerekçe: {{trigger.text.reason}}", "timeoutHours": 48},
                mail_step("bt_maili", template, ["bt@example.com"]),
            ],
            "edges": [{"from": "buyuk", "to": "onay", "when": "true"}, {"from": "buyuk", "to": "bt_maili", "when": "false"},
                      {"from": "onay", "to": "bt_maili", "when": "approved"}]}), flow_name)["data"]["id"]
        must(call("POST", f"/api/flows/{flow}/triggers", CAROL,
                  {"type": "event", "enabled": True, "topic": f"survey:{request_id}"}), "survey trigger")
        print(f"  + flow {flow_name} (starts on every response)")

    if "Çalışan memnuniyeti 2026" in created:
        survey_id = ids["Çalışan memnuniyeti 2026"]
        notes = ["Toplantılar çok uzun.", "Esnek çalışma çok iyi.", "Ekipman yenilenmeli.", "", "", "Kariyer yolu belirsiz."]
        for n in range(24):
            satisfaction = rng.choice([2, 3, 4, 4, 5, 5, 3, 1])
            answers = {"department": rng.choice(["it", "it", "hr", "finance", "sales"]), "satisfaction": satisfaction,
                       "benefits": rng.sample(["remote", "health", "training", "meal"], rng.randint(1, 3)),
                       "recommend": max(0, min(10, satisfaction * 2 + rng.randint(-1, 1))),
                       "suggestion": rng.choice(notes)}
            if satisfaction <= 2:
                answers["whyLow"] = rng.choice(["İş yükü çok fazla.", "Yöneticimle iletişim zayıf.", "Maaş beklentimin altında."])
            call("POST", f"/api/surveys/{survey_id}/responses", rng.choice([ALICE, BOB, CAROL, ADMIN]), {"answers": answers})
        print("  ⇢ 24 responses to Çalışan memnuniyeti 2026")
    if "Ekipman talebi" in created:
        requests = [
            (ALICE, {"item": "monitor", "quantity": 2, "amount": 9000, "neededBy": "2026-12-05"}),
            (BOB, {"item": "laptop", "quantity": 3, "amount": 135000, "neededBy": "2026-12-20",
                   "reason": "Yeni katılan üç geliştirici için."}),
            (ADMIN, {"item": "other", "itemOther": "Toplantı odası kamerası", "quantity": 1, "amount": 28000,
                     "neededBy": "2027-01-10", "reason": "Hibrit toplantılarda ses ve görüntü sorunu."}),
        ]
        for user, answers in requests:
            status, _ = call("POST", f"/api/surveys/{request_id}/responses", user, {"answers": answers})
            print(f"  ⇢ Ekipman talebi from {user}: HTTP {status}")
        # a response the rule refuses: big amount without a reason
        status, body = call("POST", f"/api/surveys/{request_id}/responses", ALICE,
                            {"answers": {"item": "laptop", "quantity": 1, "amount": 60000, "neededBy": "2026-12-01"}})
        print(f"  ✗ refused as expected: HTTP {status} {body.get('errors', [{}])[0].get('message') if isinstance(body, dict) else ''}")


if __name__ == "__main__":
    main()
