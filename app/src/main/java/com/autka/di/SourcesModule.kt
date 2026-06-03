package com.autka.di

import com.autka.data.remote.CarOfferSource
import com.autka.data.remote.backend.BackendCarOfferSource
import com.autka.data.remote.mock.MockCarOfferSource
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Sources contributed into a Set<CarOfferSource>. The repository iterates the set,
 * merges, and isolates failures.
 *
 * Architecture note: aggregation now happens server-side. The backend (a Cloudflare
 * Worker, see /backend) fans out to Otomoto/OLX/US-auction/... behind one API, so the
 * app binds a single [BackendCarOfferSource] instead of per-marketplace client adapters
 * (those, with their credentials/feeds, live in the backend now). The mock source is
 * kept for offline/dev runs with zero backend needed.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SourcesModule {

    @Binds @IntoSet
    abstract fun bindBackend(source: BackendCarOfferSource): CarOfferSource

    @Binds @IntoSet
    abstract fun bindMock(source: MockCarOfferSource): CarOfferSource
}
