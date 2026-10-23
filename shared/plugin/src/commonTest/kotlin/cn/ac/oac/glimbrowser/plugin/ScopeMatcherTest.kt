package cn.ac.oac.glimbrowser.plugin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopeMatcherTest {
    @Test fun starMatchesAll() {
        assertTrue(ScopeMatcher.matches("*", "any.site"))
    }
    @Test fun wildcardDomainMatchesRootAndSub() {
        assertTrue(ScopeMatcher.matches("*.example.com", "example.com"))
        assertTrue(ScopeMatcher.matches("*.example.com", "sub.example.com"))
        assertTrue(ScopeMatcher.matches("*.example.com", "a.b.example.com"))
        assertFalse(ScopeMatcher.matches("*.example.com", "example.org"))
        assertFalse(ScopeMatcher.matches("*.example.com", "notexample.com"))
    }
    @Test fun exactDomainWithSubPrefix() {
        assertTrue(ScopeMatcher.matches("example.com", "www.example.com"))
        assertFalse(ScopeMatcher.matches("example.com", "evil-example.com"))
    }
    @Test fun matchesAnyShortCircuits() {
        assertTrue(ScopeMatcher.matchesAny(listOf("a.io", "*.b.com"), "x.b.com"))
        assertFalse(ScopeMatcher.matchesAny(listOf("a.io", "*.b.com"), "c.org"))
    }
}
