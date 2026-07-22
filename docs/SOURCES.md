# Offer data sources

This document separates technically available APIs from APIs that actually license
third-party vehicle inventory for an aggregator. An endpoint for publishing a dealer's
own ads is not automatically a market-wide search feed.

## Recommended order

1. **Direct importer/dealer feeds** — onboard one Polish US-import broker and one dealer
   with a simple JSON/CSV snapshot. This is the fastest legal path to unique inventory,
   shipping bands and lead-generation revenue.
2. **Auto.dev** — self-serve US dealer inventory for an MVP while broker/auction
   partnerships are negotiated.
3. **mobile.de Search API** — apply for an API account for a real EU source.
4. **MarketCheck** — evaluate a commercial licence for broad US/Canada dealer, private
   party and auction inventory.
5. **AutoUncle B2B** — discuss a commercial EU search/valuation partnership rather than
   reproducing their aggregation independently.
6. **Connected dealer accounts** — use OTOMOTO/OLX/AutoScout24 partner APIs only for
   inventory belonging to businesses that explicitly connect their accounts.
7. **Restricted auctions** — approach Manheim, IAA and Copart as a buyer, broker or data
   partner; keep deep-links until written data rights exist.

## Sources that can plausibly work now

### Auto.dev

- Official, authenticated vehicle listings API.
- US physical and online dealer inventory.
- Filtering, sorting, pagination and listing detail endpoints.
- A small free allowance makes it practical for an MVP; production remains usage-paid.
- Adapter fit: straightforward JSON full-snapshot ingest.

Docs: <https://docs.auto.dev/v2/products/vehicle-listings>

### eBay Browse API / eBay Motors

- Official search and item-detail API using an application OAuth token.
- Useful as a supplemental US source, especially fixed-price and classified inventory.
- Buy APIs are public beta and production access/use cases must satisfy eBay's programme
  requirements.
- Adapter fit: periodic search by Motors category plus expiry/end-date handling.

Docs: <https://developer.ebay.com/develop/api/buy/browse_api>

### Direct dealer or importer snapshot

Create a source contract owned by Autka:

```json
{
  "sourceId": "partner_slug",
  "generatedAt": "2026-07-22T10:00:00Z",
  "completeSnapshot": true,
  "offers": []
}
```

Accept HTTPS JSON, signed webhook uploads, S3/R2 drops, or CSV. Require stable native ids,
source URLs, update timestamps and explicit permission to display text/images. This route
also supports accurate shipping estimates and lead attribution, which generic listing
APIs do not provide.

## Partner APIs worth applying for

### mobile.de

- Official Search API can find ads and fetch single ads.
- Official Ad-Stream provides created/changed events.
- Requires an API account; dealer accounts may be restricted to their own inventory.
- Strongest first EU marketplace candidate because it exposes an actual search API, not
  only listing publication.

Docs: <https://services.mobile.de/manual/index.html>

### MarketCheck

- Commercial active inventory APIs for US/Canada dealers, private sellers and auctions;
  UK dealer inventory is also documented.
- Includes historical/expired listings and detailed listing records.
- Likely the broadest single paid source for the USA side of Autka.
- Licence terms and display/redistribution rights must be confirmed before implementation.

Docs: <https://docs.marketcheck.com/docs/api/cars>

### AutoUncle B2B

- Commercial API based on millions of live European listings across multiple countries.
- Best explored as a search/valuation partnership, not treated as a public feed.
- Could provide comparable-market pricing even if full listing redistribution is not
  licensed.

Product: <https://b2b.autouncle.com/en-gb/automotive-api>

### Manheim

- Official developer portal includes marketplace search, offerings, valuations and
  inventory APIs.
- Access requires approved API credentials and a Manheim/Cox Automotive relationship.
- Valuable wholesale source, but not a self-serve public catalogue.

Portal: <https://developer.manheim.com/>

## APIs useful only for connected sellers

### OTOMOTO

OTOMOTO registers API partners for business accounts. The documented workflow focuses on
creating, reading, updating and deleting adverts belonging to connected business users.
It can power an opt-in dealer channel, but should not be assumed to expose all OTOMOTO
inventory.

Info: <https://www.otomoto.pl/news/jak-uzyskac-dostep-do-api>

### OLX Partner API

OLX provides OAuth-based partner APIs for posting, managing and reading adverts in the
context of an authenticated user. Use it for dealers that connect their accounts. It is
not evidence of a licensed global OLX search feed.

Portal: <https://developer.olx.pl/api/doc>

### AutoScout24 Listing Creation API

Official API for data providers managing dealership listings. Useful for connected dealer
stock and outbound syndication, not for ingesting the whole AutoScout24 marketplace.

Docs: <https://listing-creation.api.autoscout24.com/docs>

## Auctions with restricted access

### IAA

IAA has a B2B Connect developer portal and buyer data services, but public documentation
does not establish a self-serve API for redistributing the complete buyer inventory.
Request partner access through an importer/broker or IAA relationship.

Portal: <https://b2b-portal.iaai.com/>

### Copart

Copart exposes member inventory search, alerts and downloadable sales data, but no public
developer inventory API was found. Treat the site as deep-link only until Copart or an
authorised broker grants a feed and redistribution rights.

## Avoid as core sources

- Unofficial RapidAPI wrappers whose provenance is unclear.
- Browser automation or reverse-engineered private endpoints.
- Scraping marketplace HTML without written permission.
- Historical VIN/auction databases presented as live inventory.
- A feed that cannot say whether it is a full snapshot or a delta.

## Adapter acceptance checklist

Before enabling a source, record:

- contractual right to ingest and display the data and images;
- allowed caching duration and required attribution;
- stable source ids and deletion/expiry semantics;
- whether a fetch is a full snapshot or delta stream;
- rate limits, pagination and retry behaviour;
- price currency, tax inclusion and location units;
- update timestamps and expected freshness;
- lead/affiliate tracking requirements;
- production credentials stored only as Worker secrets.
