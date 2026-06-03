-- Optional geo-coordinates so offers can be placed on a map. Real source adapters
-- would geocode the location string server-side; the mock provides coords directly.
ALTER TABLE offers ADD COLUMN latitude REAL;
ALTER TABLE offers ADD COLUMN longitude REAL;
