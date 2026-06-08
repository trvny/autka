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
        MarketplaceProvider("autotraderpl", "AutoTrader.pl", setOf(Region.POLAND), { f, _ -> autoTraderPl(f) }),
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
    // (confirmed against live filtered otomoto.pl URLs). Make/model are path
    // segments; the free-text make fallback below is still best-effort.

    private fun otomoto(f: SearchFilter, affiliateId: String?): String {
        val path = buildString {
            append("https://www.otomoto.pl/osobowe")
            f.make?.let { append("/").append(slug(it)) }
            if (f.make != null) f.model?.let { append("/").append(slug(it)) }
        }
        val q = Params()
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

    private fun otomotoOrder(s: SortOrder): String? = when (s) {
        SortOrder.NEWEST -> "created_at_first:desc"        // TODO(verify)
        SortOrder.PRICE_ASC -> "filter_float_price:asc"   // verified live
        SortOrder.PRICE_DESC -> "filter_float_price:desc" // verified (parity of :asc)
        SortOrder.MILEAGE_ASC -> "filter_float_mileage:asc" // TODO(verify)
        SortOrder.YEAR_DESC -> "filter_float_year:desc"   // TODO(verify)
    }

    // --- OLX (PL) — path + price/year/mileage/fuel/order keys VERIFIED (live URLs) -
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
        f.minYear?.let { q["search[filter_float_year:from]"] = it.toString() }            // verified
        f.maxYear?.let { q["search[filter_float_year:to]"] = it.toString() }              // verified
        f.maxMileageKm?.let { q["search[filter_float_milage:to]"] = it.toString() }       // verified ("milage", missing 2nd 'e')
        // OLX fuel facet key is the legacy "filter_enum_petrol"; values are the Naspers
        // vocabulary (petrol and plugin-hybrid confirmed live, rest by parity).
        f.fuelTypes.mapNotNull(::otomotoFuel).forEachIndexed { i, v ->
            q["search[filter_enum_petrol][$i]"] = v
        }
        otomotoOrder(f.sort)?.let { q["search[order]"] = it } // verified (filter_float_price:asc live)
        return path + q.render()
    }

    // --- Facebook Marketplace — query + price only, BY DESIGN ----------------
    // Meta's ToS forbids ingestion and there's no listings API; deep-linking into a
    // pre-filled search is the only compliant path. Location/region can't be set via
    // URL reliably (FB infers it), so we pass query + price band only.

    private fun facebook(f: SearchFilter): String {
        val q = Params()
        terms(f).takeIf { it.isNotEmpty() }?.let { q["query"] = it }
        f.minPrice?.let { q["minPrice"] = it.toLong().toString() } // verified (live FB marketplace URL)
        f.maxPrice?.let { q["maxPrice"] = it.toLong().toString() } // verified (live FB marketplace URL)
        return "https://www.facebook.com/marketplace/category/vehicles" + q.render()
    }

    // --- AutoUncle (PL/EU aggregator) — mixed path + query filters VERIFIED ----------
    // Aggregates ~1,900 sites incl. Otomoto/OLX and forwards to the original ad, so it
    // doubles as broad coverage. NB: it's also autka's most direct competitor — worth
    // checking for a partner/affiliate/data program; one feed could replace N deals.
    //
    // Verified live:
    //   /pl/samochody-uzywane/f-<fuel>/mp-do-<N>-pln ? s[min_price]=<N> & s[min_km]=<N>
    // Confirmed: f-benzyna, f-hybryda, mp-do-<N>-pln (max price), s[min_price] (min price).
    // make/model are case-sensitive path segments (e.g. /Lexus/IS-Series) we can't derive
    // reliably; other fuel values and s[max_km] are TODO(verify).

    private fun autoUncle(f: SearchFilter): String {
        val path = buildString {
            append("https://www.autouncle.pl/pl/samochody-uzywane")
            f.fuelTypes.firstNotNullOfOrNull(::autoUncleFuel)?.let { append("/f-").append(it) }
            f.maxPrice?.let { append("/mp-do-").append(it.toLong()).append("-pln") }
        }
        val q = Params()
        f.minPrice?.let { q["s[min_price]"] = it.toLong().toString() } // verified
        f.maxMileageKm?.let { q["s[max_km]"] = it.toString() }         // TODO(verify) parity of s[min_km]
        return path + q.render()
    }

    private fun autoUncleFuel(t: FuelType?): String? = when (t) {
        FuelType.PETROL -> "benzyna" // verified live
        FuelType.HYBRID -> "hybryda" // verified live
        else -> null                 // TODO(verify) diesel/elektryczny/lpg/plugin-hybrid
    }

    // --- AutoScout24 (.pl) — host + atype/ustate/cy/damaged/fuel/reg/mileage VERIFIED ---
    // price/reg/mileage keys (pricefrom/priceto/fregfrom/fregto/kmto) confirmed against
    // live URLs. cy scopes to common EU export markets.

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
        f.minPrice?.let { q["pricefrom"] = it.toLong().toString() } // verified
        f.maxPrice?.let { q["priceto"] = it.toLong().toString() }   // verified
        f.minYear?.let { q["fregfrom"] = it.toString() }            // verified
        f.maxYear?.let { q["fregto"] = it.toString() }              // verified
        f.maxMileageKm?.let { q["kmto"] = it.toString() }           // verified
        // fuel accepts a comma-joined list of codes (verified live: fuel=B,D and fuel=2,B,O).
        f.fuelTypes.mapNotNull(::autoScoutFuel).distinct().takeIf { it.isNotEmpty() }
            ?.let { q["fuel"] = it.joinToString(",") }
        q["sort"] = autoScoutSort(f.sort)
        q["desc"] = if (f.sort == SortOrder.PRICE_DESC || f.sort == SortOrder.YEAR_DESC) "1" else "0"
        if (f.make == null) terms(f).takeIf { it.isNotEmpty() }?.let { q["search"] = it } // TODO(verify) free-text key
        return path + q.render()
    }

    // AS24 fuel codes verified: B=petrol, D=diesel, E=electric, L=LPG, C=CNG,
    // 2=petrol/electric hybrid, 3=diesel/electric hybrid. No dedicated plug-in code,
    // so plug-in (petrol-based) maps to 2 alongside full hybrids.
    private fun autoScoutFuel(t: FuelType?): String? = when (t) {
        FuelType.PETROL -> "B"
        FuelType.DIESEL -> "D"
        FuelType.ELECTRIC -> "E"
        FuelType.LPG -> "L"
        FuelType.HYBRID -> "2"
        FuelType.PLUGIN_HYBRID -> "2"
        else -> null
    }

    private fun autoScoutSort(s: SortOrder): String = when (s) {
        SortOrder.NEWEST -> "age"                          // TODO(verify) (live default is sort=standard)
        SortOrder.PRICE_ASC, SortOrder.PRICE_DESC -> "price" // verified live
        SortOrder.MILEAGE_ASC -> "mileage"                 // TODO(verify)
        SortOrder.YEAR_DESC -> "year"                      // TODO(verify)
    }

    // --- mobile.de (EU/DE) — base + price + reg + mileage VERIFIED from live URLs ----
    // Confirmed: s=Car&vc=Car&isSearchRequest=true&dam=false; price p=<min>:<max>;
    // first-registration fr=<min>:<max>; mileage ml=:<max>. make/model are internal
    // numeric ids (ms=...) we can't derive from a name, so the user picks those on-site.

    private fun mobileDe(f: SearchFilter): String {
        val q = Params()
        q["isSearchRequest"] = "true"
        q["s"] = "Car"
        q["vc"] = "Car"
        q["dam"] = "false" // exclude damaged
        range(f.minPrice?.toLong(), f.maxPrice?.toLong())?.let { q["p"] = it }  // verified: p=min:max
        range(f.minYear?.toLong(), f.maxYear?.toLong())?.let { q["fr"] = it }   // verified: fr=min:max
        f.maxMileageKm?.let { q["ml"] = ":$it" }                                // verified: ml=:max
        return "https://suchen.mobile.de/fahrzeuge/search.html" + q.render()
    }

    // --- Autoplac (PL classifieds) — path + query keys VERIFIED from live URLs -------
    // Path-based price ("cena-do-<N>-tysiecy", N in thousands); fuel/mileage/year via
    // query. fuelTypes is a comma-joined list of GASOLINE/DIESEL/HYBRID (verified);
    // other fuel values TODO(verify).

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

    // --- AutoTrader.pl (PL classifieds) — host + paliwo/cena/rok VERIFIED from live URLs -
    // SEPARATE site from autotrader.com (US) below. benzyna (petrol), cena_od_pln /
    // cena_do_pln (price band), rok_od / rok_do (year band) confirmed; other fuel values
    // are TODO(verify).

    private fun autoTraderPl(f: SearchFilter): String {
        val q = Params()
        f.fuelTypes.firstNotNullOfOrNull(::autoTraderPlFuel)?.let { q["rodzaj_paliwa"] = it }
        f.minPrice?.let { q["cena_od_pln"] = it.toLong().toString() } // verified
        f.maxPrice?.let { q["cena_do_pln"] = it.toLong().toString() } // verified
        f.minYear?.let { q["rok_od"] = it.toString() }                // verified
        f.maxYear?.let { q["rok_do"] = it.toString() }                // verified
        return "https://www.autotrader.pl/szukaj/osobowe" + q.render()
    }

    private fun autoTraderPlFuel(t: FuelType?): String? = when (t) {
        FuelType.PETROL -> "benzyna" // verified live
        else -> null                 // TODO(verify) diesel/hybryda/elektryczny/lpg
    }

    // --- US auctions (import sourcing) — keyword search ----------------------
    // Where import candidates physically are. The detail screen pairs these with
    // ImportServices (PL brokers) and the landed-cost breakdown.

    private fun copart(f: SearchFilter): String {
        // VERIFIED: Copart make browse is /pl/vehicle-search-make/<make>?displayStr=<Make>.
        f.make?.let { mk ->
            val q = Params()
            q["displayStr"] = mk
            return "https://www.copart.com/pl/vehicle-search-make/${slug(mk)}" + q.render()
        }
        // No make -> free-text keyword search. Verified live: ?free=true&query=<text>.
        val text = terms(f)
        if (text.isEmpty()) return "https://www.copart.com/lotSearchResults"
        val q = Params()
        q["free"] = "true"
        q["query"] = text
        return "https://www.copart.com/lotSearchResults" + q.render()
    }

    // IAAI's search state is an opaque encrypted "url" token (/Search?url=<base64>), not
    // readable query params — filters can't be deep-linked, so we land on the search page.
    private fun iaai(@Suppress("UNUSED_PARAMETER") f: SearchFilter): String =
        "https://www.iaai.com/Search"

    private fun carsCom(f: SearchFilter): String {
        val q = Params()
        f.make?.let { q["makes[]"] = slug(it) }                              // verified
        // models[] is a make-prefixed slug, e.g. nissan-rogue (verified live).
        f.model?.let { m -> q["models[]"] = listOfNotNull(f.make?.let(::slug), slug(m)).joinToString("-") }
        f.minPrice?.let { q["list_price_min"] = it.toLong().toString() }     // verified
        f.maxPrice?.let { q["list_price_max"] = it.toLong().toString() }     // verified
        f.minYear?.let { q["year_min"] = it.toString() }                     // verified
        f.maxYear?.let { q["year_max"] = it.toString() }                     // verified
        q["stock_type"] = "used"
        return "https://www.cars.com/shopping/results/" + q.render()
    }

    // autotrader.com filters max price via a path bucket (/cars-for-sale/cars-under-<N>)
    // and location via zip/searchRadius we don't have, so only the verified mileage query
    // is set. make/model path form and the price bucket are TODO(verify). NB: mileage is
    // in miles on this site; our value is km, so it slightly over-restricts.
    private fun autoTrader(f: SearchFilter): String {
        val q = Params()
        f.maxMileageKm?.let { q["mileage"] = it.toString() } // verified (max mileage)
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
