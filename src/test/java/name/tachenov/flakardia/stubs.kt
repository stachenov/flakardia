package name.tachenov.flakardia

import name.tachenov.flakardia.storage.StatsFileRecoveryOptions

object StatsFileRecoveryOptionsStub : StatsFileRecoveryOptions {
    override fun requestRecovery(message: String): Boolean = true

    override fun notifyRecoveryImpossible(message: String) { }
}
