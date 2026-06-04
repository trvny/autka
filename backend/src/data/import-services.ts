// Authoritative directory of import/sourcing companies, served at GET /import-services.
// These are services that bring a car INTO Poland, not searchable marketplaces — so they
// live here as static reference data (no D1 needed) and are surfaced on the app's offer
// DETAIL screen next to the landed-cost breakdown.
//
// Mirrors the app's compiled-in seed (core/model/ImportService.kt +
// data/imports/DefaultImportServices.kt). Keep them in sync; the app fetches this to
// override its seed so brokers can be added without an app release.
//
// `origin` is the region the company imports FROM (matched to an offer's region).
// ⚠️ Only usaimport.pl's calculatorUrl is verified; others are homepages — confirm real
// landing/calculator paths before relying on the deep target.

export type Region = "POLAND" | "EUROPE" | "USA";

export interface ImportService {
  id: string;
  displayName: string;
  origin: Region;
  url: string;
  calculatorUrl?: string;
  note?: string;
}

export const IMPORT_SERVICES: ImportService[] = [
  {
    id: "usaimport",
    displayName: "USA Import",
    origin: "USA",
    url: "https://usaimport.pl/",
    calculatorUrl: "https://usaimport.pl/koszty-sprowadzenia-auta-z-usa-kalkulator/",
    note: "Import z USA i Kanady, aukcje Copart/IAAI, dostawa pod dom",
  },
  {
    id: "usacars",
    displayName: "USACARS",
    origin: "USA",
    url: "https://usacars.net.pl/pl",
    note: "Sprowadzanie aut z USA", // TODO(verify): landing/calculator path
  },
  {
    id: "mattyusa",
    displayName: "MattyUSA",
    origin: "USA",
    url: "https://mattyusa.pl/",
    note: "Import samochodów z USA", // TODO(verify)
  },
  {
    id: "autopan",
    displayName: "AutoPan",
    origin: "EUROPE",
    url: "https://autopan.pl/",
    note: "Sprowadzanie i import aut z Europy", // TODO(verify)
  },
];

export function importServicesForRegion(region?: Region): ImportService[] {
  if (!region) return IMPORT_SERVICES;
  return IMPORT_SERVICES.filter((s) => s.origin === region);
}
