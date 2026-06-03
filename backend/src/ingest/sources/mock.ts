import type { IngestSource } from "../source";
import type { CarOffer } from "../../lib/types";

/** Always-on sample source so the backend has data with zero configuration. */
export const mockSource: IngestSource = {
  sourceId: "mock",
  displayName: "Sample data",
  isEnabled: () => true,
  async fetch(): Promise<CarOffer[]> {
    const now = Date.now();
    const h = (n: number) => now - n * 3_600_000;
    return [
      {
        id: "mock:1", sourceId: "mock", title: "BMW 320d 2018 Touring",
        make: "BMW", model: "320d", year: 2018, mileageKm: 142_000,
        price: { amount: 78_900, currency: "PLN" },
        fuelType: "DIESEL", transmission: "AUTOMATIC", powerHp: 190,
        location: "Krakow, PL", region: "POLAND",
        thumbnailUrl: "https://picsum.photos/seed/mock1/640/480", imageUrls: ["https://picsum.photos/seed/mock1/640/480", "https://picsum.photos/seed/mock1b/640/480"],
        listingUrl: "https://example.com/listing/1", postedAtEpochMs: h(3),
        latitude: 50.0647, longitude: 19.9450,
      },
      {
        id: "mock:2", sourceId: "mock", title: "Audi A4 2.0 TFSI 2019",
        make: "Audi", model: "A4", year: 2019, mileageKm: 98_000,
        price: { amount: 19_500, currency: "EUR" },
        fuelType: "PETROL", transmission: "AUTOMATIC", powerHp: 190,
        location: "Berlin, DE", region: "EUROPE",
        thumbnailUrl: "https://picsum.photos/seed/mock2/640/480", imageUrls: ["https://picsum.photos/seed/mock2/640/480", "https://picsum.photos/seed/mock2b/640/480"],
        listingUrl: "https://example.com/listing/2", postedAtEpochMs: h(20),
        latitude: 52.5200, longitude: 13.4050,
      },
      {
        id: "mock:3", sourceId: "mock", title: "Ford Mustang GT 5.0 2020",
        make: "Ford", model: "Mustang", year: 2020, mileageKm: 35_000,
        price: { amount: 18_000, currency: "USD" },
        fuelType: "PETROL", transmission: "AUTOMATIC", powerHp: 460,
        location: "Newark, NJ, USA", region: "USA",
        thumbnailUrl: "https://picsum.photos/seed/mock3/640/480", imageUrls: ["https://picsum.photos/seed/mock3/640/480", "https://picsum.photos/seed/mock3b/640/480"],
        listingUrl: "https://example.com/listing/3", postedAtEpochMs: h(50),
        latitude: 40.7357, longitude: -74.1724,
      },
    ];
  },
};
