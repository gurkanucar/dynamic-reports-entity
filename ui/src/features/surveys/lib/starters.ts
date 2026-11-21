import type { SurveyRequest } from "../types/survey.dto"

const base = { status: "draft" as const, anonymous: false, oneResponsePerUser: true, closesAt: null }

/** Ready-made starting points; the assistant can adjust them in the chat. */
export const STARTERS: (SurveyRequest & { summary: string })[] = [
  {
    ...base,
    anonymous: true,
    title: "Çalışan memnuniyeti",
    summary: "Anonim; yıldız puanları, NPS ve düşük puana takip sorusu",
    description: "Çalışma ortamımızı iyileştirmek için görüşleriniz önemli. Anket anonimdir ve yaklaşık 3 dakika sürer.",
    definition: {
      submitLabel: "Gönder",
      successMessage: "Teşekkürler! Görüşleriniz anonim olarak kaydedildi.",
      fields: [
        { id: "general", type: "section", label: "Genel" },
        { id: "department", type: "dropdown", label: "Departmanınız", required: true,
          options: [{ id: "it", label: "Bilgi Teknolojileri" }, { id: "hr", label: "İnsan Kaynakları" },
            { id: "finance", label: "Finans" }, { id: "sales", label: "Satış" }, { id: "operations", label: "Operasyon" },
            { id: "other", label: "Diğer" }] },
        { id: "satisfaction", type: "rating", label: "Genel olarak işinizden ne kadar memnunsunuz?", required: true, max: 5 },
        { id: "whyLow", type: "longText", label: "Memnuniyetinizi düşüren en önemli şey nedir?",
          visibleWhen: { "<=": [{ var: "satisfaction" }, 2] } },
        { id: "workload", type: "singleChoice", label: "İş yükünüzü nasıl değerlendirirsiniz?",
          options: [{ id: "low", label: "Az" }, { id: "ok", label: "Dengeli" }, { id: "high", label: "Fazla" }] },
        { id: "recommend", type: "nps", label: "Şirketimizi bir arkadaşınıza çalışma yeri olarak ne kadar önerirsiniz?", required: true },
        { id: "suggestion", type: "longText", label: "Eklemek istedikleriniz", maxLength: 2000 },
      ],
      rules: [],
    },
  },
  {
    ...base,
    title: "İzin talebi",
    summary: "Tarih aralığı kuralı, “Diğer” için açıklama; akışla yönetici onayına bağlanabilir",
    description: "İzin talebinizi buradan iletin; yöneticinizin onayına gönderilir.",
    definition: {
      submitLabel: "Talebi gönder",
      successMessage: "Talebiniz alındı; onaylanınca e-posta ile bilgilendirileceksiniz.",
      fields: [
        { id: "kind", type: "singleChoice", label: "İzin türü", required: true,
          options: [{ id: "annual", label: "Yıllık izin" }, { id: "sick", label: "Hastalık" },
            { id: "excuse", label: "Mazeret" }, { id: "other", label: "Diğer" }] },
        { id: "otherReason", type: "shortText", label: "Lütfen açıklayın", required: true,
          visibleWhen: { "==": [{ var: "kind" }, "other"] } },
        { id: "startDate", type: "date", label: "Başlangıç tarihi", required: true, width: "half" },
        { id: "endDate", type: "date", label: "Bitiş tarihi", required: true, width: "half" },
        { id: "days", type: "number", label: "İş günü", required: true, min: 0.5, max: 30, width: "half" },
        { id: "backup", type: "shortText", label: "Yerinize bakacak kişi", width: "half" },
        { id: "note", type: "longText", label: "Not", maxLength: 1000 },
      ],
      rules: [
        { id: "endAfterStart", message: "Bitiş tarihi başlangıç tarihinden önce olamaz.",
          logic: { ">=": [{ var: "endDate" }, { var: "startDate" }] } },
      ],
    },
  },
  {
    ...base,
    oneResponsePerUser: true,
    title: "Eğitim / etkinlik kaydı",
    summary: "Katılım, oturum seçimi, yemek tercihi ve onay",
    description: "Etkinliğe katılım durumunuzu ve tercihlerinizi bildirin. 1 dakika sürer.",
    definition: {
      submitLabel: "Kaydol",
      successMessage: "Kaydınız alındı, görüşmek üzere!",
      fields: [
        { id: "attend", type: "yesNo", label: "Etkinliğe katılacak mısınız?" },
        { id: "sessions", type: "multipleChoice", label: "Hangi oturumlara katılmak istersiniz?", min: 1,
          visibleWhen: { "==": [{ var: "attend" }, true] },
          options: [{ id: "keynote", label: "Açılış konuşması" }, { id: "workshop", label: "Atölye" },
            { id: "panel", label: "Panel" }] },
        { id: "diet", type: "dropdown", label: "Yemek tercihi", visibleWhen: { "==": [{ var: "attend" }, true] },
          options: [{ id: "none", label: "Fark etmez" }, { id: "vegetarian", label: "Vejetaryen" },
            { id: "vegan", label: "Vegan" }, { id: "glutenFree", label: "Glutensiz" }] },
        { id: "consent", type: "yesNo", label: "Etkinlikte fotoğraf çekilmesine onay veriyorum",
          help: "Fotoğraflar yalnızca iç iletişimde kullanılır." },
      ],
      rules: [],
    },
  },
]
