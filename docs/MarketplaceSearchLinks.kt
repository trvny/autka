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
 * confirmed; many `search[...]`/filter query KEYS are best-guess and marked TODO(verify).
 * A wrong key lands the user on an unfiltered page (worse than no chip), so confirm
 * each against a live filtered URL before relying on the filtering.
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

    // --- Otomoto (PL) — path shape CONFIRMED; filter keys TODO(verify) -------

    private fun otomoto(f: SearchFilter, affiliateId: String?): String {
        val path = buildString {
            append("https://www.otomoto.pl/osobowe")
            f.make?.let { append("/").append(slug(it)) }
            if (f.make != null) f.model?.let { append("/").append(slug(it)) }
        }
        val q = Params()
        f.minPrice?.let { q["search[filter_float_price:from]"] = it.toLong().toString() } // TODO(verify)
        f.maxPrice?.let { q["search[filter_float_price:to]"] = it.toLong().toString() }   // TODO(verify)
        f.minYear?.let { q["search[filter_float_year:from]"] = it.toString() }            // TODO(verify)
        f.maxYear?.let { q["search[filter_float_year:to]"] = it.toString() }              // TODO(verify)
        f.maxMileageKm?.let { q["search[filter_float_mileage:to]"] = it.toString() }      // TODO(verify)
        otomotoFuel(f.fuelTypes.firstOrNull())?.let { q["search[filter_enum_fuel_type]"] = it } // TODO(verify)
        otomotoOrder(f.sort)?.let { q["search[order]"] = it }                             // TODO(verify)
        if (f.make == null) f.query.takeIf { it.isNotBlank() }?.let { q["search[filter_enum_make]"] = slug(it) }
        affiliateId?.let { q["utm_source"] = it } // TODO(verify): real affiliate param on joining the program
        return path + q.render()
    }

    private fun otomotoFuel(t: FuelType?): String? = when (t) { // TODO(verify) slugs
        FuelType.PETROL -> "petrol"
        FuelType.DIESEL -> "diesel"
        FuelType.HYBRID -> "hybrid"
        FuelType.PLUGIN_HYBRID -> "plugin-hybrid"
        FuelType.ELECTRIC -> "electric"
        FuelType.LPG -> "lpg-petrol"
        else -> null
    }

    private fun otomotoOrder(s: SortOrder): String? = when (s) { // TODO(verify) tokens
        SortOrder.NEWEST -> "created_at_first:desc"
        SortOrder.PRICE_ASC -> "filter_float_price:asc"
        SortOrder.PRICE_DESC -> "filter_float_price:desc"
        SortOrder.MILEAGE_ASC -> "filter_float_mileage:asc"
        SortOrder.YEAR_DESC -> "filter_float_year:desc"
    }

    // --- OLX (PL) — path CONFIRMED; filter keys TODO(verify) -----------------

    private fun olx(f: SearchFilter): String {
        val path = buildString {
            append("https://www.olx.pl/motoryzacja/samochody/")
            terms(f).takeIf { it.isNotEmpty() }?.let { append("q-").append(slug(it)).append("/") }
        }
        val q = Params()
        f.minPrice?.let { q["search[filter_float_price:from]"] = it.toLong().toString() } // TODO(verify)
        f.maxPrice?.let { q["search[filter_float_price:to]"] = it.toLong().toString() }   // TODO(verify)
        f.minYear?.let { q["search[filter_float_year:from]"] = it.toString() }            // TODO(verify)
        f.maxYear?.let { q["search[filter_float_year:to]"] = it.toString() }              // TODO(verify)
        f.maxMileageKm?.let { q["search[filter_float_milage:to]"] = it.toString() }       // TODO(verify): OLX spells it "milage"
        return path + q.render()
    }

    // --- Facebook Marketplace — query + price only, BY DESIGN ----------------
    // Meta's ToS forbids ingestion and there's no listings API; deep-linking into a
    // pre-filled search is the only compliant path. Location/region can't be set via
    // URL reliably (FB infers it), so we pass query + price band only.

    private fun facebook(f: SearchFilter): String {
        val q = Params()
        terms(f).takeIf { it.isNotEmpty() }?.let { q["query"] = it }
        f.minPrice?.let { q["minPrice"] = it.toLong().toString() } // TODO(verify)
        f.maxPrice?.let { q["maxPrice"] = it.toLong().toString() } // TODO(verify)
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

    // --- AutoScout24 (EU) — path + params CONFIRMED (pricefrom/priceto/fregfrom/kmto) -

    private fun autoScout24(f: SearchFilter): String {
        val path = buildString {
            append("https://www.autoscout24.com/lst")
            f.make?.let { append("/").append(slug(it)) }
            if (f.make != null) f.model?.let { append("/").append(slug(it)) }
        }
        val q = Params()
        f.minPrice?.let { q["pricefrom"] = it.toLong().toString() }
        f.maxPrice?.let { q["priceto"] = it.toLong().toString() }
        f.minYear?.let { q["fregfrom"] = it.toString() }
        f.maxYear?.let { q["fregto"] = it.toString() }
        f.maxMileageKm?.let { q["kmto"] = it.toString() }
        autoScoutFuel(f.fuelTypes.firstOrNull())?.let { q["fuel"] = it } // TODO(verify) fuel codes
        q["sort"] = autoScoutSort(f.sort)
        q["desc"] = if (f.sort == SortOrder.PRICE_DESC || f.sort == SortOrder.YEAR_DESC) "1" else "0"
        if (f.make == null) terms(f).takeIf { it.isNotEmpty() }?.let { q["search"] = it } // TODO(verify) free-text key
        return path + q.render()
    }

    private fun autoScoutFuel(t: FuelType?): String? = when (t) { // TODO(verify): AS24 fuel codes (e.g. B=petrol, D=diesel)
        FuelType.PETROL -> "B"
        FuelType.DIESEL -> "D"
        FuelType.ELECTRIC -> "E"
        FuelType.LPG -> "L"
        else -> null
    }

    private fun autoScoutSort(s: SortOrder): String = when (s) { // TODO(verify) tokens
        SortOrder.NEWEST -> "age"
        SortOrder.PRICE_ASC, SortOrder.PRICE_DESC -> "price"
        SortOrder.MILEAGE_ASC -> "mileage"
        SortOrder.YEAR_DESC -> "year"
    }

    // --- mobile.de (EU/DE) — make needs a NUMERIC id we can't derive --------
    // mobile.de keys make/model by internal numeric ids, so we can't build a
    // make-filtered URL from a name. We pass a free-text search + price/year/mileage
    // and let the user pick the make on-site. TODO(verify) every key below.

    private fun mobileDe(f: SearchFilter): String {
        val q = Params()
        terms(f).takeIf { it.isNotEmpty() }?.let { q["q"] = it }   // TODO(verify) free-text key
        f.minPrice?.let { q["minPrice"] = it.toLong().toString() } // TODO(verify)
        f.maxPrice?.let { q["maxPrice"] = it.toLong().toString() } // TODO(verify)
        f.minYear?.let { q["minFirstRegistrationDate"] = "${it}-01-01" } // TODO(verify) format
        f.maxMileageKm?.let { q["maxMileage"] = it.toString() }    // TODO(verify)
        return "https://suchen.mobile.de/fahrzeuge/search.html" + q.render()
    }

    // --- US auctions / classifieds (import to PL) — keyword search -----------
    // Where the actual import candidates live. Keyword-based; price bands optional and
    // TODO(verify). Landed cost is already computed app-side for USA-region offers.

    private fun copart(f: SearchFilter): String =
        "https://www.copart.com/lotSearchResults" +
            Params().apply { terms(f).takeIf { it.isNotEmpty() }?.let { this["free_form_search"] = it } }.render() // TODO(verify)

    private fun iaai(f: SearchFilter): String =
        "https://www.iaai.com/Search" +
            Params().apply { terms(f).takeIf { it.isNotEmpty() }?.let { this["searchKeyword"] = it } }.render() // TODO(verify)

    private fun carsCom(f: SearchFilter): String {
        val q = Params()
        f.make?.let { q["makes[]"] = slug(it) }       // TODO(verify)
        f.model?.let { q["models[]"] = slug(it) }     // TODO(verify)
        f.maxPrice?.let { q["maximum_price"] = it.toLong().toString() } // TODO(verify)
        f.minYear?.let { q["year_min"] = it.toString() }               // TODO(verify)
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
