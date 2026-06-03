// The normalized offer shape served to the app. Mirrors the Android CarOffer model
// (com.carfinder.core.model.CarOffer) so the app's BackendCarOfferSource can parse it
// directly. Keep these two in sync.

export type Region = "POLAND" | "EUROPE" | "USA";

export type FuelType =
  | "PETROL" | "DIESEL" | "HYBRID" | "PLUGIN_HYBRID"
  | "ELECTRIC" | "LPG" | "OTHER" | "UNKNOWN";

export type Transmission = "MANUAL" | "AUTOMATIC" | "UNKNOWN";

export type Currency = "PLN" | "EUR" | "USD";

export interface Money {
  amount: number;
  currency: Currency;
}

export interface CarOffer {
  id: string;            // namespaced by source: "otomoto:12345"
  sourceId: string;
  title: string;
  make: string;
  model: string;
  year: number | null;
  mileageKm: number | null;
  price: Money;
  fuelType: FuelType;
  transmission: Transmission;
  powerHp: number | null;
  location: string | null;
  region: Region;
  thumbnailUrl: string | null;
  imageUrls: string[];
  listingUrl: string;
  postedAtEpochMs: number | null;
  latitude: number | null;
  longitude: number | null;
  // Populated only on deduped query results when >1 listing collapsed together:
  listingCount?: number;     // total listings in the duplicate group (>= 2)
  otherSources?: string[];   // distinct source ids across the group
}

export interface SearchFilter {
  query?: string;
  make?: string;
  model?: string;
  minPrice?: number;
  maxPrice?: number;
  minYear?: number;
  maxYear?: number;
  maxMileageKm?: number;
  fuelTypes?: FuelType[];
  regions?: Region[];
  sourceIds?: string[];
  sort?: "NEWEST" | "PRICE_ASC" | "PRICE_DESC" | "MILEAGE_ASC" | "YEAR_DESC";
  dedup?: boolean; // collapse cross-source duplicates (default true)
  limit?: number;
  offset?: number;
}
