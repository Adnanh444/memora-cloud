package com.adnan.memora.data

import org.junit.Assert.*
import org.junit.Test

class AppLockPolicyTest {
    @Test fun disabledNeverTriggersBackgroundLock() { assertFalse(AppLockPolicy.afterBackground(false, 99_000)) }
    @Test fun shortSystemConsentReturnUsesGracePeriod() { assertFalse(AppLockPolicy.afterBackground(true, 5_000)) }
    @Test fun locksAtThirtySeconds() { assertTrue(AppLockPolicy.afterBackground(true, 30_000)) }
    @Test fun longBackgroundStayLocks() { assertTrue(AppLockPolicy.afterBackground(true, 99_000)) }
}
