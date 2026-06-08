package com.autka.feature.external

import com.autka.core.model.FuelType
import com.autka.core.model.Region
import com.autka.core.model.SearchFilter
import com.autka.core.model.SortOrder
import java.util.Locale

/**
 * Builds deep links into external marketplaces' OWN search pages from the current
 * [SearchFilter]. The user searches on the marketplace; we ingest nothing. This is the
 * compliant alternative to scraping for sources with no feed (Otomoto/OLX/Facebook/
 * AutoScout24/US auctions).
 *
 * NOT a CarOfferSource: it returns URLs, not CarOffers, so it lives in feature/external
 * and is surfaced as buttons in the UI — never bound into the SourcesModule multibinding.
 *
 * Region-aware: [all] returns only providers whose [MarketplaceProvider.regions]
 * intersect the filter's selected regions, so a USA-only search shows auctions, a
 * Poland search shows Otomoto/OLX/Facebook, a Europe search shows AutoScout24/mobile.de.
 *
 * ⚠️ CONFIDENCE VARIES PER PROVIDER — see the per-builder notes. Path shapes are
 * confirmed; remaining best-guess query KEYS are marked TODO(verify). A wrong key lands
 * the user on an unfiltered page (worse than no chip), so confirm against a live
 * filtered URL before relying on the filtering.
 */
data class MarketplaceLink(val sourceId: String, val displayName: String, val url: String)

private data class MarketplaceProvider(
    val sourceId: String,
    val displayName: String,
    val regions: Set<Region>,
    val build: (SearchFilter, String?) -> String,
)

object MarketplaceSearchLinks {

    private val providers = listOf(
        MarketplaceProvider("otomoto", "Otomoto", setOf(Region.POLAND), ::otomoto),
        MarketplaceProvider("olx", "OLX", setOf(Region.POLAND), { f, _ -> olx(f) }),
        MarketplaceProvider("facebook", "Facebook", setOf(Region.POLAND, Region.EUROPE), { f, _ -> facebook(f) }),
        MarketplaceProvider("autoplac", "Autoplac", setOf(Region.POLAND), { f, _ -> autoplac(f) }),
        // AutoUncle is itself an aggregator (~11M EU listings, price ratings) that
        // redirects to the original ad — so one chip covers far more than the single-
        // marketplace ones below. Listed first for that reason.
        MarketplaceProvider("autouncle", "AutoUncle", setOf(Region.POLAND, Region.EUROPE), { f, _ -> autoUncle(f) }),
        MarketplaceProvider("autoscout24", "AutoScout24", setOf(Region.EUROPE, Region.POLAND), { f, _ -> autoScout24(f) }),
        MarketplaceProvider("mobilede", "mobile.de", setOf(Region.EUROPE), { f, _ -> mobileDe(f) }),
        MarketplaceProvider("copart", "Copart (US)", setOf(Region.USA), { f, _ -> copart(f) }),
        MarketplaceProvider("iaai", "IAAI (US)", setOf(Region.USA), { f, _ -> iaai(f) }),
        MarketplaceProvider("carscom", "Cars.com (US)", setOf(Region.USA), { f, _ -> carsCom(f) }),
        MarketplaceProvider("autotrader", "AutoTrader (US)", setOf(Region.USA), { f, _ -> autoTrader(f) }),
        // Named PL import brokers (e.g. companies that buy from US auctions and land
        // the car in PL) are PARTNERSHIPS, not generic deep-link targets — adding a
        // wrong/guessed URL would just be a dead link. Add each real one here once you
        // have its actual search URL, tagging Region.USA:
        //   MarketplaceProvider("brokerX", "Broker X", setOf(Region.USA), { f, _ -> "https://...?q=${terms(f)}" }),
    )

    /** Providers whose regions intersect the filter's selected regions. */
    fun all(filter: SearchFilter, affiliateId: String? = null): List<MarketplaceLink> =
        providers
            .filter { it.regions.any { r -> r in filter.regions } }
            .map { MarketplaceLink(it.sourceId, it.displayName, it.build(filter, affiliateId)) }

    // --- Otomoto (PL) — path + price/year/mileage/fuel/order keys VERIFIED ---
    // (confirmed against a live filtered otomoto.pl URL). Make/model are path
    // segments; the free-text make fallback below is still best-effort.

    private fun otomoto(f: SearchFilter, affiliateId: String?): String {
        val path = buildString {
            append("https://www.otomoto.pl/osobowe")
            f.make?.let { append("/").append(slug(it)) }
            if (f.make != null) f.model?.let { append("/").append(slug(it)) }
        }
        val q = Params()
        // Keys VERIFIED against a live filtered Otomoto URL (price/year/mileage/fuel/order).
        f.minPrice?.let { q["search[filter_float_price:from]"] = it.toLong().toString() }
        f.maxPrice?.let { q["search[filter_float_price:to]"] = it.toLong().toString() }
        f.minYear?.let { q["search[filter_float_year:from]"] = it.toString() }
        f.maxYear?.let { q["search[filter_float_year:to]"] = it.toString() }
        f.maxMileageKm?.let { q["search[filter_float_mileage:to]"] = it.toString() }
        // Fuel is an INDEXED array param, one entry per selected type:
        // search[filter_enum_fuel_type][0]=petrol&...[1]=diesel
        f.fuelTypes.mapNotNull(::otomotoFuel).forEachIndexed { i, slug ->
            q["search[filter_enum_fuel_type][$i]"] = slug
        }
        otomotoOrder(f.sort)?.let { q["search[order]"] = it }
        if (f.make == null) f.query.takeIf { it.isNotBlank() }?.let { q["search[filter_enum_make]"] = slug(it) }
        affiliateId?.let { q["utm_source"] = it } // TODO(verify): real affiliate param on joining the program
        return path + q.render()
    }

    // Naspers (Otomoto / OLX Group) fuel vocabulary. All slugs verified against live
    // indexed otomoto.pl URLs.
    private fun otomotoFuel(t: FuelType?): String? = when (t) {
        FuelType.PETROL -> "petrol"
        FuelType.DIESEL -> "diesel"
        FuelType.LPG -> "petrol-lpg"          // verified (NB: petrol-lpg, not lpg-petrol)
        FuelType.HYBRID -> "hybrid"           // verified (live indexed otomoto.pl URL)
        FuelType.PLUGIN_HYBRID -> "plugin-hybrid" // verified (live indexed otomoto.pl URL)
        FuelType.ELECTRIC -> "electric"       // verified (live indexed otomoto.pl URL)
        else -> null
    }

    private fun otomotoOrder(s: SortOrder): String? = when (s) { // TODO(verify) tokens
        SortOrder.NEWEST -> "created_at_first:desc"
        SortOrder.PRICE_ASC -> "filter_float_price:asc"
        SortOrder.PRICE_DESC -> "filter_float_price:desc"
        SortOrder.MILEAGE_ASC -> "filter_float_mileage:asc"
        SortOrder.YEAR_DESC -> "filter_float_year:desc"
    }

    // --- OLX (PL) — path + price + mileage + fuel keys VERIFIED (seen on live URLs) -
    // Same OLX Group filter scheme as Otomoto. Location is a path segment
    // (/samochody/<city>/), which we don't set (no location field in SearchFilter).

    private fun olx(f: SearchFilter): String {
        val path = buildString {
            append("https://www.olx.pl/motoryzacja/samochody/")
            terms(f).takeIf { it.isNotEmpty() }?.let { append("q-").append(slug(it)).append("/") }
        }
        val q = Params()
        f.minPrice?.let { q["search[filter_float_price:from]"] = it.toLong().toString() } // verified
        f.maxPrice?.let { q["search[filter_float_price:to]"] = it.toLong().toString() }   // verified
        f.minYear?.let { q["search[filter_float_year:from]"] = it.toString() }            // same scheme
        f.maxYear?.let { q["search[filter_float_year:to]"] = it.toString() }              // same scheme
        f.maxMileageKm?.let { q["search[filter_float_milage:to]"] = it.toString() }       // verified (OLX param is "milage", missing 2nd 'e')
        // OLX fuel facet key is the legacy "filter_enum_petrol" (value "petrol" seen live).
        // OLX Group shares Otomoto's fuel vocabulary, so values are reused; only "petrol"
        // is confirmed live for OLX, the rest follow Naspers parity. TODO(verify) non-petrol.
        f.fuelTypes.mapNotNull(::otomotoFuel).forEachIndexed { i, v ->
            q["search[filter_enum_petrol][$i]"] = v
        }
        return path + q.render()
    }

    // --- Facebook Marketplace — query + price only, BY DESIGN ----------------
    // Meta's ToS forbids ingestion and there's no listings API; deep-linking into a
    // pre-filled search is the only compliant path. Location/region can't be set via
    // URL reliably (FB infers it), so we pass query + price band only.

    private fun facebook(f: SearchFilter): String {
        val q = Params()
        terms(f).takeIf { it.isNotEmpty() }?.let { q["query"] = it }
        f.minPrice?.let { q["minPrice"] = it.toLong().toString() } // FB minPrice/maxPrice pair (maxPrice verified live)
        f.maxPrice?.let { q["maxPrice"] = it.toLong().toString() } // verified (live FB marketplace URL)
        return "https://www.facebook.com/marketplace/category/vehicles" + q.render()
    }

    // --- AutoUncle (PL/EU aggregator) — base path CONFIRMED; query keys TODO(verify) -
    // Aggregates ~1,900 sites incl. Otomoto/OLX and forwards to the original ad, so it
    // doubles as broad coverage. NB: it's also autka's most direct competitor — see the
    // strategic note in the chat. Worth checking whether AutoUncle offers a partner/
    // affiliate or data program: one feed from them could replace N marketplace deals.

    private fun autoUncle(f: SearchFilter): String {
        val q = Params()
        f.make?.let { q["brands[]"] = slug(it) }   // TODO(verify) key + slug-vs-id
        f.model?.let { q["models[]"] = slug(it) }  // TODO(verify)
        f.minPrice?.let { q["price_from"] = it.toLong().toString() } // TODO(verify)
        f.maxPrice?.let { q["price_to"] = it.toLong().toString() }   // TODO(verify)
        f.minYear?.let { q["year_from"] = it.toString() }           // TODO(verify)
        f.maxYear?.let { q["year_to"] = it.toString() }             // TODO(verify)
        f.maxMileageKm?.let { q["odometer_to"] = it.toString() }    // TODO(verify)
        if (f.make == null) terms(f).takeIf { it.isNotEmpty() }?.let { q["q"] = it } // TODO(verify)
        return "https://www.autouncle.pl/pl/samochody-uzywane" + q.render()
    }

    // --- AutoScout24 (.pl) — host + atype/ustate/cy/damaged/fuel VERIFIED from live URLs -
    // price/reg/mileage keys (pricefrom/priceto/fregfrom/fregto/kmto) are AS24's
    // long-standing params. cy scopes to common EU export markets (from the live URL).

    private fun autoScout24(f: SearchFilter): String {
        val path = buildString {
            append("https://www.autoscout24.pl/lst")
            f.make?.let { append("/").append(slug(it)) }
            if (f.make != null) f.model?.let { append("/").append(slug(it)) }
        }
        val q = Params()
        q["atype"] = "C"                 // cars (verified)
        q["ustate"] = "N,U"              // new + used (verified)
        q["damaged_listing"] = "exclude" // hide damaged (verified)
        q["cy"] = "D,A,I,B,NL,E,L,F"     // DE/AT/IT/BE/NL/ES/LU/FR — common EU export markets (verified)
        f.minPrice?.let { q["pricefrom"] = it.toLong().toString() }
        f.maxPrice?.let { q["priceto"] = it.toLong().toString() }
        f.minYear?.let { q["fregfrom"] = it.toString() }
        f.maxYear?.let { q["fregto"] = it.toString() }
        f.maxMileageKm?.let { q["kmto"] = it.toString() }
        // fuel accepts a comma-joined list of codes (verified live: fuel=2,B,O).
        f.fuelTypes.mapNotNull(::autoScoutFuel).distinct().takeIf { it.isNotEmpty() }
            ?.let { q["fuel"] = it.joinToString(",") }
        q["sort"] = autoScoutSort(f.sort)
        q["desc"] = if (f.sort == SortOrder.PRICE_DESC || f.sort == SortOrder.YEAR_DESC) "1" else "0"
        if (f.make == null) terms(f).takeIf { it.isNotEmpty() }?.let { q["search"] = it } // TODO(verify) free-text key
        return path + q.render()
    }

    // AS24 fuel codes verified: B=petrol, D=diesel, E=electric, L=LPG, C=CNG,
    // 2=petrol/electric hybrid, 3=diesel/electric hybrid. AS24 has no dedicated
    // plug-in code, so plug-in (petrol-based) maps to 2 alongside full hybrids.
    private fun autoScoutFuel(t: FuelType?): String? = when (t) {
        FuelType.PETROL -> "B"
        FuelType.DIESEL -> "D"
        FuelType.ELECTRIC -> "E"
        FuelType.LPG -> "L"
        FuelType.HYBRID -> "2"
        FuelType.PLUGIN_HYBRID -> "2"
        else -> null
    }

    private fun autoScoutSort(s: SortOrder): String = when (s) { // TODO(verify) tokens (live default seen: sort=standard)
        SortOrder.NEWEST -> "age"
        SortOrder.PRICE_ASC, SortOrder.PRICE_DESC -> "price"
        SortOrder.MILEAGE_ASC -> "mileage"
        SortOrder.YEAR_DESC -> "year"
    }

    // --- mobile.de (EU/DE) — base params + price VERIFIED from a live URL ----
    // Confirmed: s=Car&vc=Car&isSearchRequest=true&dam=false, and price uses a single
    // range param p=<min>:<max> (e.g. p=:10000). make/model are internal numeric ids we
    // can't derive from a name, so the user picks those on-site; year/mileage ranges
    // (fr/ml) follow mobile.de's range scheme but stay TODO(verify).

    private fun mobileDe(f: SearchFilter): String {
        val q = Params()
        q["isSearchRequest"] = "true"
        q["s"] = "Car"
        q["vc"] = "Car"
        q["dam"] = "false" // exclude damaged
        range(f.minPrice?.toLong(), f.maxPrice?.toLong())?.let { q["p"] = it } // verified: p=min:max
        range(f.minYear?.toLong(), f.maxYear?.toLong())?.let { q["fr"] = it }  // TODO(verify) first-registration range
        f.maxMileageKm?.let { q["ml"] = ":$it" }                               // TODO(verify) mileage range
        return "https://suchen.mobile.de/fahrzeuge/search.html" + q.render()
    }

    // --- Autoplac (PL classifieds) — path + query keys VERIFIED from live URLs -------
    // Path-based price ("cena-do-<N>-tysiecy", N in thousands); fuel/mileage/year via
    // query. fuelTypes is a comma-joined list of GASOLINE/DIESEL/HYBRID (verified);
    // other fuel values TODO(verify). The "zagraniczne" path segment scopes to imported
    // cars — useful later for the import axis, but there's no SearchFilter flag for it yet.

    private fun autoplac(f: SearchFilter): String {
        val path = buildString {
            append("https://autoplac.pl/oferty/samochody-osobowe")
            f.maxPrice?.let { append("/cena-do-").append(it.toLong() / 1000).append("-tysiecy") }
        }
        val q = Params()
        f.fuelTypes.mapNotNull(::autoplacFuel).takeIf { it.isNotEmpty() }?.let { q["fuelTypes"] = it.joinToString(",") }
        f.minYear?.let { q["yearFrom"] = it.toString() }
        f.maxMileageKm?.let { q["mileageTo"] = it.toString() }
        return path + q.render()
    }

    private fun autoplacFuel(t: FuelType?): String? = when (t) { // GASOLINE/DIESEL/HYBRID verified; rest TODO(verify)
        FuelType.PETROL -> "GASOLINE"
        FuelType.DIESEL -> "DIESEL"
        FuelType.HYBRID -> "HYBRID"
        else -> null
    }

    // --- US auctions (import sourcing) — keyword search ----------------------
    // Where import candidates physically are. Keyword-based; price bands optional and
    // TODO(verify). The detail screen pairs these with ImportServices (PL brokers) and
    // the landed-cost breakdown.

    private fun copart(f: SearchFilter): String {
        // VERIFIED: Copart make browse is /pl/vehicle-search-make/<make>?displayStr=<Make>.
        f.make?.let { mk ->
            val q = Params()
            q["displayStr"] = mk
            return "https://www.copart.com/pl/vehicle-search-make/${slug(mk)}" + q.render()
        }
        // No make selected -> keyword search (best-effort; TODO(verify)).
        val q = Params()
        terms(f).takeIf { it.isNotEmpty() }?.let { q["free_form_search"] = it }
        return "https://www.copart.com/lotSearchResults" + q.render()
    }

    // IAAI's search state is an opaque encrypted "url" token (/Search?url=<base64>), not
    // readable query params — filters can't be deep-linked, so we land on the search page
    // and the user filters there. (Verified: live IAAI search URLs carry only ?url=<blob>.)
    private fun iaai(@Suppress("UNUSED_PARAMETER") f: SearchFilter): String =
        "https://www.iaai.com/Search"

    private fun carsCom(f: SearchFilter): String {
        val q = Params()
        f.make?.let { q["makes[]"] = slug(it) }                              // verified
        // models[] is a make-prefixed slug, e.g. nissan-rogue (verified live).
        f.model?.let { m -> q["models[]"] = listOfNotNull(f.make?.let(::slug), slug(m)).joinToString("-") }
        f.maxPrice?.let { q["maximum_price"] = it.toLong().toString() }      // TODO(verify)
        f.minYear?.let { q["year_min"] = it.toString() }                     // TODO(verify)
        q["stock_type"] = "used"
        return "https://www.cars.com/shopping/results/" + q.render()
    }

    private fun autoTrader(f: SearchFilter): String {
        val q = Params()
        terms(f).takeIf { it.isNotEmpty() }?.let { q["keywordPhrases"] = it } // TODO(verify)
        f.maxPrice?.let { q["maxPrice"] = it.toLong().toString() }            // TODO(verify)
        f.minYear?.let { q["startYear"] = it.toString() }                     // TODO(verify)
        return "https://www.autotrader.com/cars-for-sale/all-cars" + q.render()
    }

    // --- helpers -------------------------------------------------------------

    private fun terms(f: SearchFilter): String =
        listOfNotNull(f.make, f.model, f.query.ifBlank { null }).joinToString(" ").trim()

    /** mobile.de-style "min:max" range (either side may be blank); null if both null. */
    private fun range(min: Long?, max: Long?): String? =
        if (min == null && max == null) null else "${min ?: ""}:${max ?: ""}"

    /** Lowercase, spaces/odd chars -> hyphens. Good enough for path/slug params. */
    private fun slug(s: String): String = s.trim().lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Tiny ordered query-string builder with percent-encoding. */
    private class Params {
        private val pairs = mutableListOf<Pair<String, String>>()
        operator fun set(k: String, v: String) { pairs += k to v }
        fun render(): String {
            if (pairs.isEmpty()) return ""
            fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
            return "?" + pairs.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        }
    }
}
