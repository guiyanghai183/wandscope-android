package com.guiyanghai.wandscope

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUrlPolicyTest {
    private val repo = "guiyanghai183/wandscope-android"

    @Test
    fun `allows repository release and trusted github asset hosts`() {
        assertTrue(ReleaseUrlPolicy.isAllowed("https://github.com/$repo/releases/download/v1.0.1/WandScope.apk", repo))
        assertTrue(ReleaseUrlPolicy.isAllowed("https://objects.githubusercontent.com/github-production-release-asset/a/b", repo))
        assertTrue(ReleaseUrlPolicy.isAllowed("https://release-assets.githubusercontent.com/github-production-release-asset/a/b", repo))
    }

    @Test
    fun `rejects wrong repo credentials insecure schemes and deceptive hosts`() {
        assertFalse(ReleaseUrlPolicy.isAllowed("https://github.com/attacker/repo/releases/download/v1/a.apk", repo))
        assertFalse(ReleaseUrlPolicy.isAllowed("https://user:pass@github.com/$repo/releases/download/v1/a.apk", repo))
        assertFalse(ReleaseUrlPolicy.isAllowed("http://github.com/$repo/releases/download/v1/a.apk", repo))
        assertFalse(ReleaseUrlPolicy.isAllowed("https://github.com.evil.example/$repo/releases/download/v1/a.apk", repo))
    }
}
