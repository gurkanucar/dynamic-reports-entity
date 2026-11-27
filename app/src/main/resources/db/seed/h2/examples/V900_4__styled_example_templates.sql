-- Example templates get the shared look: CSS in one <style> block, inlined by the server before sending.
UPDATE notification_mail_template SET body_html = '<style>
  body { margin: 0; padding: 24px 0; background: #f4f4f5; }
  .wrap { max-width: 640px; margin: 0 auto; background: #ffffff; border-top: 3px solid #dc2626; border-radius: 8px; padding: 24px; font-family: -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif; font-size: 14px; line-height: 1.5; color: #18181b; }
  h1 { margin: 0 0 4px; font-size: 20px; }
  .meta { margin: 0 0 20px; color: #71717a; font-size: 13px; }
  table.data { width: 100%; border-collapse: collapse; font-size: 13px; }
  table.data th { background: #f4f4f5; text-align: left; font-weight: 600; padding: 8px 12px; border: 1px solid #e4e4e7; }
  table.data td { padding: 8px 12px; border: 1px solid #e4e4e7; }
  .note { margin: 12px 0 0; color: #71717a; font-size: 12px; }
  .badge { display: inline-block; padding: 2px 10px; border-radius: 999px; background: #f4f4f5; font-weight: 600; }
  .footer { margin-top: 24px; padding-top: 12px; border-top: 1px solid #e4e4e7; color: #a1a1aa; font-size: 12px; }
</style>
<div class="wrap">
  <h1>{{flow.name}}</h1>
  <p class="meta">{{run.date}} · {{steps.dataset.rowCount}} kayıt</p>
  {{^steps.dataset.hasRows}}<p>Bu sefer kayıt yok.</p>{{/steps.dataset.hasRows}}
  {{#steps.dataset.hasRows}}
  <table class="data">
    <thead><tr>{{#steps.dataset.columns}}<th>{{.}}</th>{{/steps.dataset.columns}}</tr></thead>
    <tbody>{{#steps.dataset.rows}}<tr>{{#cells}}<td>{{.}}</td>{{/cells}}</tr>{{/steps.dataset.rows}}</tbody>
  </table>
  {{#steps.dataset.previewTruncated}}<p class="note">İlk satırlar gösteriliyor; tamamı ektedir.</p>{{/steps.dataset.previewTruncated}}
  {{/steps.dataset.hasRows}}
  <p class="footer">Bu e-posta “{{flow.name}}” akışı tarafından {{run.date}} tarihinde otomatik gönderildi.</p>
</div>' WHERE name = 'Veri seti tablosu (örnek)';
UPDATE notification_mail_template SET body_html = '<style>
  body { margin: 0; padding: 24px 0; background: #f4f4f5; }
  .wrap { max-width: 640px; margin: 0 auto; background: #ffffff; border-top: 3px solid #dc2626; border-radius: 8px; padding: 24px; font-family: -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif; font-size: 14px; line-height: 1.5; color: #18181b; }
  h1 { margin: 0 0 4px; font-size: 20px; }
  .meta { margin: 0 0 20px; color: #71717a; font-size: 13px; }
  table.data { width: 100%; border-collapse: collapse; font-size: 13px; }
  table.data th { background: #f4f4f5; text-align: left; font-weight: 600; padding: 8px 12px; border: 1px solid #e4e4e7; }
  table.data td { padding: 8px 12px; border: 1px solid #e4e4e7; }
  .note { margin: 12px 0 0; color: #71717a; font-size: 12px; }
  .badge { display: inline-block; padding: 2px 10px; border-radius: 999px; background: #f4f4f5; font-weight: 600; }
  .footer { margin-top: 24px; padding-top: 12px; border-top: 1px solid #e4e4e7; color: #a1a1aa; font-size: 12px; }
</style>
<div class="wrap">
  <h1>{{flow.name}}</h1>
  <p class="meta">Çalışma #{{run.id}} · {{run.date}}</p>
  <p>Merhaba,</p>
  <p>“{{flow.name}}” akışı çalıştı.{{#steps.finans_onayi.decision}} Finans kararı: <span class="badge">{{steps.finans_onayi.decision}}</span>{{#steps.finans_onayi.comment}} — “{{steps.finans_onayi.comment}}”{{/steps.finans_onayi.comment}}{{/steps.finans_onayi.decision}}</p>
  <p class="footer">Bu e-posta “{{flow.name}}” akışı tarafından {{run.date}} tarihinde otomatik gönderildi.</p>
</div>' WHERE name = 'Bilgilendirme (örnek)';
