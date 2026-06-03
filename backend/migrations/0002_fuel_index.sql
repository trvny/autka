-- Index supporting the fuel_type filter added to GET /offers (queryOffers).
CREATE INDEX IF NOT EXISTS idx_offers_fuel_type ON offers (fuel_type);
