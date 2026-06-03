import { Hono } from "hono";
import { IMPORT_SERVICES, importServicesForRegion, type Region } from "../data/import-services";

// GET /import-services            -> all companies
// GET /import-services?region=USA -> companies importing from that region
export const importServicesRouter = new Hono<{ Bindings: Env }>();

const REGIONS: readonly Region[] = ["POLAND", "EUROPE", "USA"];

importServicesRouter.get("/import-services", (c) => {
  const raw = c.req.query("region");
  const region = (REGIONS as readonly string[]).includes(raw ?? "")
    ? (raw as Region)
    : undefined;
  const services = importServicesForRegion(region);
  return c.json({ services, count: services.length });
});
