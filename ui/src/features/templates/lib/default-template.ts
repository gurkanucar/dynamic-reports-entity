import type { TemplateRequest } from "../types/template.dto"

/**
 * Shared look of the ready-made templates: written once as a <style> block, which the server copies into style=""
 * before sending (Outlook and Gmail drop <style>).
 */
const STYLE = `<style>
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
  .badge { display: inline-block; padding: 2px 10px; border-radius: 999px; background: #f4f4f5; font-weight: 600; }
  .footer { margin-top: 24px; padding-top: 12px; border-top: 1px solid #e4e4e7; color: #a1a1aa; font-size: 12px; }
</style>`

const FOOTER = `<p class="footer">Bu e-posta “{{flow.name}}” akışı tarafından {{run.date}} tarihinde otomatik gönderildi.</p>`

/** A ready-made template for scheduled datasets: title, first rows as a table, the file attached. */
export const DEFAULT_DATASET_TEMPLATE: TemplateRequest = {
  name: "Veri seti tablosu",
  description: "Akış adı, veri setinin ilk satırları tablo olarak ve Excel eki",
  subject: "{{flow.name}} - {{run.date}} ({{steps.dataset.rowCount}} satır)",
  bodyHtml: `${STYLE}
<div class="wrap">
  <h1>{{flow.name}}</h1>
  <p class="meta">{{run.date}} · {{steps.dataset.rowCount}} kayıt</p>
  {{^steps.dataset.hasRows}}<p>Bu dönem için kayıt yok.</p>{{/steps.dataset.hasRows}}
  {{#steps.dataset.hasRows}}
  <table class="data">
    <thead><tr>{{#steps.dataset.columns}}<th>{{.}}</th>{{/steps.dataset.columns}}</tr></thead>
    <tbody>{{#steps.dataset.rows}}<tr>{{#cells}}<td>{{.}}</td>{{/cells}}</tr>{{/steps.dataset.rows}}</tbody>
  </table>
  {{#steps.dataset.previewTruncated}}<p class="note">İlk satırlar gösteriliyor; veri setinin tamamı ektedir.</p>{{/steps.dataset.previewTruncated}}
  {{/steps.dataset.hasRows}}
  ${FOOTER}
</div>`,
}

/** Ready-made starting points; the assistant can then adjust them in a chat. */
export const STARTER_TEMPLATES: TemplateRequest[] = [
  DEFAULT_DATASET_TEMPLATE,
  {
    name: "Bilgilendirme",
    description: "Kısa bir duyuru: başlık, metin ve kayıt sayısı",
    subject: "{{flow.name}} · {{run.date}}",
    bodyHtml: `${STYLE}
<div class="wrap">
  <h1>{{flow.name}}</h1>
  <p class="meta">{{run.date}}</p>
  <p>Merhaba,</p>
  <p>Bu haftanın özeti hazır: <span class="badge">{{steps.dataset.rowCount}} kayıt</span>. Ayrıntılar ekteki dosyadadır.</p>
  ${FOOTER}
</div>`,
  },
  {
    name: "Onay sonucu",
    description: "Bir onay adımının kararı: onaylayan, karar ve yorum",
    subject: "{{flow.name}}: talep {{steps.approval.decision}}",
    bodyHtml: `${STYLE}
<div class="wrap">
  <h1>{{steps.approval.title}}</h1>
  <p class="meta">{{steps.approval.groupName}} · {{run.date}}</p>
  <table class="data">
    <tr><th>Karar</th><td>{{steps.approval.decision}}</td></tr>
    <tr><th>Karar veren</th><td>{{steps.approval.decidedBy}}</td></tr>
    <tr><th>Yorum</th><td>{{steps.approval.comment}}{{^steps.approval.comment}}—{{/steps.approval.comment}}</td></tr>
  </table>
  ${FOOTER}
</div>`,
  },
]
