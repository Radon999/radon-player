package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The addresses shown for pairing: the home network's and Tailscale's, never ones the phone can't reach. */
class PairAddressRulesTest {
    @Test fun homeAndTailscaleAddressesAreShownHomeFirst() {
        val got = PairAddressRules.pick(listOf(
            "tailscale0" to "100.101.5.7", "lo" to "127.0.0.1", "enp5s0" to "192.168.1.20",
            "docker0" to "172.17.0.1", "wlan0" to "10.0.0.4", "enp5s0" to "192.168.1.20"))
        assertEquals(listOf("192.168.1.20", "10.0.0.4", "100.101.5.7"), got.map { it.ip })
        assertEquals(PairAddressRules.Kind.TAILSCALE, got.last().kind)
    }

    @Test fun addressesThePhoneCantReachAreLeftOut() {
        assertNull(PairAddressRules.kindOf("169.254.3.3", "enp5s0"))
        assertNull("a container bridge", PairAddressRules.kindOf("172.18.0.1", "br-3f2a"))
        assertNull("a virtual machine's", PairAddressRules.kindOf("192.168.122.1", "virbr0"))
        assertNull("a public address", PairAddressRules.kindOf("8.8.8.8", "enp5s0"))
        assertNull("not an address", PairAddressRules.kindOf("fe80::1", "enp5s0"))
    }
}
