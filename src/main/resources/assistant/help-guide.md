# Platform guide (what exists; answer only from this)

"Veri & Akış" is the company's in-house platform for datasets (reports), flows (automations), approvals, surveys
and mail templates. The UI is Turkish. Paths below are the only pages that exist.

## Pages

- `/` **Pano**: pending approvals, runs of the last 14 days (chart), last runs, flows that need attention.
- `/approvals` **Onaylarım**: approval requests of the groups the user is in. Filters: Bekleyen / Sonuçlanan / Tümü,
  group, search. Click a row to see details and approve or reject (with a comment). Nobody can approve their own
  request (four-eyes rule): the row then says "Sizin talebiniz"; "Grubunuz değil" = the user is not a member.
  Admins can switch to "Tüm gruplar". Mail links open a request directly.
- `/datasets` **Veri setleri**: saved datasets (name, root entity, owner). Search and paging. Click to open.
- `/datasets/new` and `/datasets/<name>` **Veri seti tasarımı**: choose the root record type, add columns (fields,
  related fields via dots like department.name, aggregates COUNT/SUM/AVG/MIN/MAX/LIST, date groups YEAR/QUARTER/
  MONTH), filters (AND/OR groups, relative dates "son N gün"/"önümüzdeki N gün", exists/not exists), group filters
  (HAVING) and sorting. The preview runs on the server with paging. "Kaydet" gives it a name so flows can use it.
  Export: "Excel" or "CSV" downloads the whole result (streamed, up to 1,000,000 rows). The **Veri seti asistanı**
  panel on this page builds or changes the dataset from a sentence.
  Visibility rules: people only see personnel (and their records) of their own department unless they have the
  all-departments permission; salary and national id need extra permissions; fields one may not see are refused.
- `/flows` **Akışlar**: list of flows with owner, step count, last update. Search, sort, paging.
- `/flows/new`, `/flows/<id>/edit` **Akış editörü**: a canvas of steps connected by arrows. Step types: dataset
  (runs a saved dataset, rows available to later steps, optional Excel/CSV attachment), filter (keeps rows matching
  a JSON Logic expression), condition (if/else branches with JSON Logic), mail (a mail template, recipients,
  attachments), approval (waits for an approval group's decision, with timeout; outcomes approved / rejected /
  timed_out), survey (mails a single-use survey link and waits for the answer), webhook call, Kafka message, wait.
  Triggers: manual, schedule (cron, "Zamanlama"), webhook (with secret), Kafka topic, survey response (event).
  The **Akış asistanı** button on the editor designs or changes a whole flow from a description; each step also
  has its own small assistant for its settings, and the schedule field can turn "her pazartesi 9'da" into cron.
  "Veri setinden akış oluştur" on /flows starts a flow from a saved dataset.
- `/flows/<id>` **Akış detayı**: the flow's steps, triggers and its runs (paged); "Çalıştır" starts it manually.
- `/runs` **Çalışmalar**: all runs: status (queued, running, completed, failed, cancelled; a run waiting for an approval or survey shows as running), trigger type,
  search, paging. A run shows each step's input/output and errors; running runs can be cancelled.
- `/templates` **Mail şablonları**: HTML mail templates with Mustache placeholders ({{name}}, tables from dataset
  steps like {{#steps.<key>.rows}}). "Yeni şablon" opens the editor with a live preview; the **Şablon asistanı**
  in the editor writes or restyles a template from a description.
- `/approval-groups` **Onay grupları**: groups whose members decide approval steps. Admins create/edit them.
- `/surveys` **Anketler**: "Anketlerim" (own surveys; admins see all) and "Açık anketler" (ones the user can answer).
- `/surveys/new`, `/surveys/<id>/edit` **Anket tasarımı**: question types (short/long text, e-mail, phone, number,
  single/multiple choice, dropdown, date, rating, NPS, yes/no, section), show-when conditions, rules that refuse a
  response with a message, settings (anonymous, one response per user, closing date, who sees results, follow-up
  tracking fields). The **Anket asistanı** tab designs or changes the survey from a description.
- `/surveys/<id>` **Anket sonuçları**: summary per question, responses table (filter, sort, paging), Excel export,
  per-response follow-up (status, priority, follow-up date, assignee, extra fields) and notes.
- `/s/<id>` the survey form respondents fill in.
- Admin only ("Yönetim → Kayıtlar" in the menu): `/admin/personnel` Personel, `/admin/departments` Departmanlar,
  `/admin/projects` Projeler (with the team tab), `/admin/trainings` Eğitimler, `/admin/training-records` Eğitim
  kayıtları (filters: overdue, due in 60 days), `/admin/skills` Beceriler. All have search, filters, sorting,
  paging (20/50/100 per page) and a side panel to add, edit or delete. Records in use cannot be deleted; the
  message says what uses them.

## General

- Every list has paging at the bottom: total count, page size 20/50/100, first/last page.
- Theme: "Tema" at the bottom of the menu switches light/dark.
- Errors: a red box or toast shows the server's message; field errors appear under the fields.
- The AI assistants never see data rows, only field names and the user's own drafts; drafts are applied to the
  editor and are saved only when the user clicks "Kaydet".
