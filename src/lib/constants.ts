/** ثوابت النظام وتسمياته العربية — مصدر الحقيقة الوحيد للتصنيفات */

export const SITE_STATUS = {
  active: "يعمل",
  degraded: "متدهور",
  down: "معطل",
  planned: "مخطط",
} as const;
export type SiteStatus = keyof typeof SITE_STATUS;

export const SITE_TYPES = {
  mountain: "موقع جبلي",
  city: "موقع حضري",
  camp: "معسكر",
  mobile: "محطة متحركة",
} as const;
export type SiteType = keyof typeof SITE_TYPES;

export const EQUIPMENT_CATEGORIES = {
  antenna: "هوائي",
  radio: "راديو",
  microwave: "ميكروويف",
  satellite: "ساتلايت",
  switch: "سويتش",
  router: "راوتر",
  solar: "لوح شمسي",
  battery: "بطارية",
  regulator: "منظم",
  charger: "شاحن",
  cable: "كابل",
  other: "أخرى",
} as const;
export type EquipmentCategory = keyof typeof EQUIPMENT_CATEGORIES;

export const EQUIPMENT_STATUS = {
  working: "يعمل",
  faulty: "معطل",
  maintenance: "قيد الصيانة",
  retired: "متقاعد",
} as const;
export type EquipmentStatus = keyof typeof EQUIPMENT_STATUS;

export const LINK_TYPES = {
  microwave: "ميكروويف",
  fiber: "ألياف ضوئية",
  satellite: "ساتلايت",
  vhf: "VHF",
  uhf: "UHF",
  cellular: "خلوي",
  ip: "IP",
} as const;
export type LinkType = keyof typeof LINK_TYPES;

export const LINK_STATUS = {
  active: "نشط",
  degraded: "متدهور",
  down: "معطل",
} as const;
export type LinkStatus = keyof typeof LINK_STATUS;

export const NETWORK_CLASSES = {
  command: "قيادة",
  operations: "عمليات",
  backup: "احتياطية",
  emergency: "طوارئ",
} as const;
export type NetworkClass = keyof typeof NETWORK_CLASSES;

export const REQUIREMENT_TYPES = {
  materials: "مواد",
  devices: "أجهزة",
  maintenance: "صيانة",
} as const;
export type RequirementType = keyof typeof REQUIREMENT_TYPES;

export const REQUIREMENT_STATUS = {
  open: "مفتوح",
  approved: "معتمد",
  fulfilled: "مصروف",
  cancelled: "ملغي",
} as const;
export type RequirementStatus = keyof typeof REQUIREMENT_STATUS;

export const TICKET_SEVERITY = {
  critical: "حرجة",
  major: "كبيرة",
  minor: "بسيطة",
} as const;
export type TicketSeverity = keyof typeof TICKET_SEVERITY;

export const TICKET_STATUS = {
  open: "مفتوح",
  in_progress: "قيد المعالجة",
  closed: "مغلق",
} as const;
export type TicketStatus = keyof typeof TICKET_STATUS;

export const WORK_ORDER_STATUS = {
  planned: "مخطط",
  in_progress: "قيد التنفيذ",
  done: "منجز",
} as const;
export type WorkOrderStatus = keyof typeof WORK_ORDER_STATUS;

export const ALERT_TYPES = {
  maintenance: "صيانة وقائية",
  lifespan: "عمر معدة",
  inventory: "نقص مخزون",
  system: "نظام",
} as const;
export type AlertType = keyof typeof ALERT_TYPES;

/* ألوان الحالات — تُستخدم في المجرة والبطاقات */
export const STATUS_COLORS: Record<string, string> = {
  active: "#3AF08F",
  degraded: "#F5B841",
  down: "#FF5470",
  planned: "#8E9BB3",
  working: "#3AF08F",
  faulty: "#FF5470",
  maintenance: "#F5B841",
  retired: "#8E9BB3",
};
