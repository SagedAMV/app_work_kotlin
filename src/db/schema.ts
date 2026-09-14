/**
 * مجرة (Galaxy) — نظام إدارة مواقع الاتصالات
 * مخطط قاعدة البيانات الكامل — كل الكيانات والعلاقات
 */
import {
  pgTable,
  serial,
  text,
  integer,
  doublePrecision,
  boolean,
  timestamp,
  numeric,
} from "drizzle-orm/pg-core";

/* ــــــــــــــــــــ 3.1 المواقع ــــــــــــــــــــ */
export const sites = pgTable("sites", {
  id: serial("id").primaryKey(),
  name: text("name").notNull(),
  code: text("code").notNull().unique(),
  latitude: doublePrecision("latitude").notNull().default(0),
  longitude: doublePrecision("longitude").notNull().default(0),
  status: text("status").notNull().default("active"), // active|degraded|down|planned
  siteType: text("site_type").notNull().default("mountain"), // mountain|city|camp|mobile
  notes: text("notes").notNull().default(""),
  createdAt: timestamp("created_at").notNull().defaultNow(),
  updatedAt: timestamp("updated_at").notNull().defaultNow(),
});

/* ــــــــــــــــــــ 3.2 المعدات ــــــــــــــــــــ */
export const equipment = pgTable("equipment", {
  id: serial("id").primaryKey(),
  siteId: integer("site_id")
    .notNull()
    .references(() => sites.id, { onDelete: "cascade" }),
  category: text("category").notNull(), // antenna|radio|microwave|satellite|switch|router|solar|battery|regulator|charger|cable|other
  company: text("company").notNull().default(""),
  model: text("model").notNull().default(""),
  serialNumber: text("serial_number").notNull().default(""),
  status: text("status").notNull().default("working"), // working|faulty|maintenance|retired
  installDate: timestamp("install_date").notNull().defaultNow(),
  lifespanYears: integer("lifespan_years").notNull().default(10),
  notes: text("notes").notNull().default(""),
});

/* ــــــــــــــــــــ 3.3 المرفقات ــــــــــــــــــــ */
export const attachments = pgTable("attachments", {
  id: serial("id").primaryKey(),
  siteId: integer("site_id")
    .notNull()
    .references(() => sites.id, { onDelete: "cascade" }),
  type: text("type").notNull().default("image"), // image|pdf
  dataUrl: text("data_url").notNull().default(""),
  caption: text("caption").notNull().default(""),
  addedAt: timestamp("added_at").notNull().defaultNow(),
});

/* ــــــــــــــــــــ 3.4 سجل الموقع ــــــــــــــــــــ */
export const siteHistory = pgTable("site_history", {
  id: serial("id").primaryKey(),
  siteId: integer("site_id")
    .notNull()
    .references(() => sites.id, { onDelete: "cascade" }),
  action: text("action").notNull(),
  oldValue: text("old_value").notNull().default(""),
  newValue: text("new_value").notNull().default(""),
  at: timestamp("at").notNull().defaultNow(),
});

/* ــــــــــــــــــــ 3.5 المخزون ــــــــــــــــــــ */
export const inventoryItems = pgTable("inventory_items", {
  id: serial("id").primaryKey(),
  name: text("name").notNull(),
  category: text("category").notNull().default("مواد"),
  unit: text("unit").notNull().default("قطعة"),
  quantity: integer("quantity").notNull().default(0),
  minThreshold: integer("min_threshold").notNull().default(1),
  location: text("location").notNull().default("المخزن المركزي"),
});

/* ــــــــــــــــــــ 3.6 الاحتياج ــــــــــــــــــــ */
export const requirements = pgTable("requirements", {
  id: serial("id").primaryKey(),
  siteId: integer("site_id")
    .notNull()
    .references(() => sites.id, { onDelete: "cascade" }),
  type: text("type").notNull().default("materials"), // materials|devices|maintenance
  status: text("status").notNull().default("open"), // open|approved|fulfilled|cancelled
  createdAt: timestamp("created_at").notNull().defaultNow(),
  notes: text("notes").notNull().default(""),
});

/* ــــــــــــــــــــ 3.7 بنود الاحتياج ــــــــــــــــــــ */
export const requirementItems = pgTable("requirement_items", {
  id: serial("id").primaryKey(),
  requirementId: integer("requirement_id")
    .notNull()
    .references(() => requirements.id, { onDelete: "cascade" }),
  inventoryItemId: integer("inventory_item_id").references(
    () => inventoryItems.id,
    { onDelete: "set null" }
  ),
  description: text("description").notNull(),
  quantity: integer("quantity").notNull().default(1),
  fulfilled: boolean("fulfilled").notNull().default(false),
});

/* ــــــــــــــــــــ 3.8 BOQ ــــــــــــــــــــ */
export const boqDocuments = pgTable("boq_documents", {
  id: serial("id").primaryKey(),
  requirementId: integer("requirement_id")
    .notNull()
    .references(() => requirements.id, { onDelete: "cascade" }),
  createdAt: timestamp("created_at").notNull().defaultNow(),
  notes: text("notes").notNull().default(""),
});

export const boqLines = pgTable("boq_lines", {
  id: serial("id").primaryKey(),
  boqId: integer("boq_id")
    .notNull()
    .references(() => boqDocuments.id, { onDelete: "cascade" }),
  description: text("description").notNull(),
  quantity: integer("quantity").notNull().default(1),
  unit: text("unit").notNull().default("قطعة"),
  unitCost: numeric("unit_cost").notNull().default("0"),
});

/* ــــــــــــــــــــ 3.9 الروابط (المجرة) ــــــــــــــــــــ */
export const links = pgTable("links", {
  id: serial("id").primaryKey(),
  sourceSiteId: integer("source_site_id")
    .notNull()
    .references(() => sites.id, { onDelete: "restrict" }),
  targetSiteId: integer("target_site_id")
    .notNull()
    .references(() => sites.id, { onDelete: "restrict" }),
  type: text("type").notNull().default("microwave"), // microwave|fiber|satellite|vhf|uhf|cellular|ip
  status: text("status").notNull().default("active"), // active|degraded|down
  networkClass: text("network_class").notNull().default("operations"), // command|operations|backup|emergency
  priority: integer("priority").notNull().default(3), // 1..5
  frequencyMhz: doublePrecision("frequency_mhz").notNull().default(5800),
  txPowerDbm: doublePrecision("tx_power_dbm").notNull().default(20),
  txGainDbi: doublePrecision("tx_gain_dbi").notNull().default(24),
  rxGainDbi: doublePrecision("rx_gain_dbi").notNull().default(24),
  cableLossDb: doublePrecision("cable_loss_db").notNull().default(1),
  distanceKm: doublePrecision("distance_km").notNull().default(0),
  notes: text("notes").notNull().default(""),
  createdAt: timestamp("created_at").notNull().defaultNow(),
});

/* ــــــــــــــــــــ 3.10 التذاكر ــــــــــــــــــــ */
export const tickets = pgTable("tickets", {
  id: serial("id").primaryKey(),
  siteId: integer("site_id")
    .notNull()
    .references(() => sites.id, { onDelete: "cascade" }),
  equipmentId: integer("equipment_id").references(() => equipment.id, {
    onDelete: "set null",
  }),
  linkId: integer("link_id").references(() => links.id, {
    onDelete: "set null",
  }),
  title: text("title").notNull(),
  description: text("description").notNull().default(""),
  severity: text("severity").notNull().default("minor"), // critical|major|minor
  status: text("status").notNull().default("open"), // open|in_progress|closed
  openedAt: timestamp("opened_at").notNull().defaultNow(),
  closedAt: timestamp("closed_at"),
  resolutionTimeMinutes: integer("resolution_time_minutes"),
});

/* ــــــــــــــــــــ 3.11 أوامر الشغل ــــــــــــــــــــ */
export const workOrders = pgTable("work_orders", {
  id: serial("id").primaryKey(),
  ticketId: integer("ticket_id").references(() => tickets.id, {
    onDelete: "set null",
  }),
  siteId: integer("site_id")
    .notNull()
    .references(() => sites.id, { onDelete: "cascade" }),
  assignedTo: text("assigned_to").notNull().default(""),
  tasks: text("tasks").notNull().default(""),
  status: text("status").notNull().default("planned"), // planned|in_progress|done
  scheduledAt: timestamp("scheduled_at").notNull().defaultNow(),
  completedAt: timestamp("completed_at"),
});

/* ــــــــــــــــــــ 3.12 الصيانة الوقائية ــــــــــــــــــــ */
export const maintenanceSchedules = pgTable("maintenance_schedules", {
  id: serial("id").primaryKey(),
  siteId: integer("site_id")
    .notNull()
    .references(() => sites.id, { onDelete: "cascade" }),
  equipmentId: integer("equipment_id").references(() => equipment.id, {
    onDelete: "set null",
  }),
  type: text("type").notNull().default("فحص دوري"),
  intervalDays: integer("interval_days").notNull().default(90),
  lastDone: timestamp("last_done").notNull().defaultNow(),
  nextDue: timestamp("next_due").notNull().defaultNow(),
  notes: text("notes").notNull().default(""),
});

/* ــــــــــــــــــــ 3.13 التنبيهات ــــــــــــــــــــ */
export const alerts = pgTable("alerts", {
  id: serial("id").primaryKey(),
  type: text("type").notNull().default("maintenance"), // maintenance|lifespan|inventory|system
  refId: integer("ref_id"),
  message: text("message").notNull(),
  createdAt: timestamp("created_at").notNull().defaultNow(),
  isRead: boolean("is_read").notNull().default(false),
});

/* ــــــــــــــــــــ 3.14 سجل التدقيق ــــــــــــــــــــ */
export const auditLog = pgTable("audit_log", {
  id: serial("id").primaryKey(),
  action: text("action").notNull(),
  entityType: text("entity_type").notNull(),
  entityId: integer("entity_id"),
  at: timestamp("at").notNull().defaultNow(),
  details: text("details").notNull().default(""),
});

/* ــــــــــــــــــــ الإعدادات ــــــــــــــــــــ */
export const settings = pgTable("settings", {
  key: text("key").primaryKey(),
  value: text("value").notNull().default(""),
});
