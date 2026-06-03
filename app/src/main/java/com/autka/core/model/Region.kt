package com.autka.core.model

/** Where an offer physically is. Drives import-cost logic and filtering. */
enum class Region {
    POLAND,
    EUROPE,   // EU/EEA outside Poland — typically no customs duty into PL
    USA;      // requires import: shipping, duty, VAT, excise
}
