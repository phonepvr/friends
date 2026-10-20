package com.phonepvr.friends.data.dialer

import android.net.Uri
import android.telecom.PhoneAccount

/**
 * A `tel:` URI for [number], with `#` (and anything else URI-special) encoded.
 *
 * Building it by concatenation ("tel:$number") breaks USSD and service codes such
 * as `*123#` or `*#06#`: in a URI `#` starts the fragment, so everything from the
 * first `#` is silently dropped and the network receives `*123` (issue #46). The
 * stock dialer uses [Uri.fromParts] for the same reason. Receivers read the number
 * back with [Uri.getSchemeSpecificPart], which decodes `%23` to `#` again.
 */
fun telUri(number: String): Uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null)
