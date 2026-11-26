package org.omarchy.music

/** The PC's addresses to show for pairing the phone (Settings › Phone remote): its home-network addresses and its
 *  Tailscale one, each said plainly; the ones a phone can't reach (loopback, link-local, Docker and virtual-machine
 *  bridges) left out. */
object PairAddressRules {
    enum class Kind { HOME, TAILSCALE }

    data class Address(val ip: String, val kind: Kind)

    /** Interfaces that are the PC's own containers or virtual machines, not a network the phone is on. */
    private val virtual = Regex("^(docker\\d*|br-.*|veth.*|virbr\\d*|vmnet\\d*|vboxnet\\d*|lxcbr\\d*|lxdbr\\d*|cni\\d*|flannel.*|podman\\d*)$")

    /** What kind of address ip (IPv4, dotted) on the interface named iface is, or null when the phone can't use it. */
    fun kindOf(ip: String, iface: String): Kind? {
        val p = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return null
        if (p[0] == 127 || (p[0] == 169 && p[1] == 254) || p[0] == 0) return null
        if (p[0] == 100 && p[1] in 64..127) return Kind.TAILSCALE   // 100.64.0.0/10: Tailscale's (CGNAT) range
        if (iface.startsWith("tailscale")) return Kind.TAILSCALE
        if (virtual.matches(iface)) return null
        val private = p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
        return if (private) Kind.HOME else null
    }

    /** From (interface name, IPv4) pairs: the usable ones, home first, each once. */
    fun pick(found: List<Pair<String, String>>): List<Address> =
        found.mapNotNull { (iface, ip) -> kindOf(ip, iface)?.let { Address(ip, it) } }
            .distinctBy { it.ip }.sortedBy { it.kind.ordinal }

    /** How a line reads in Settings. */
    fun label(a: Address): String = when (a.kind) {
        Kind.HOME -> "At home (same Wi-Fi)"
        Kind.TAILSCALE -> "Away from home (Tailscale)"
    }
}
