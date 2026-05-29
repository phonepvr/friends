package com.phonepvr.friends.feature

/**
 * Compile-time feature gates for the all-in-one rollout (dialer,
 * contacts manager, in-call UI). Each phase ships behind one of these
 * so a release-blocking bug in a new surface can be hidden without
 * reverting code.
 *
 * Set to `false` to compile the app with the surface hidden — the
 * bottom-nav tab disappears, the manifest declarations stay (cheap),
 * and the feature's code paths become unreachable from the UI.
 */
object Features {
    /** Master switch for the phone + contacts + in-call rollout. */
    const val ALL_IN_ONE: Boolean = true
}
