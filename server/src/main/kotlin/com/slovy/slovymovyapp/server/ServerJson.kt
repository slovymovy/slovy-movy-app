package com.slovy.slovymovyapp.server

import kotlinx.serialization.json.Json

/**
 * The JSON configurations this server uses, named by what each one is for.
 *
 * `Json { … }` builds a fresh configuration and serializers module, so a route handler that
 * declares its own pays that cost on every request. The bigger problem is drift: the same
 * three settings were spelled out at nine call sites, and whether two of them agreed was a
 * matter of luck rather than intent.
 */
object ServerJson {
    /**
     * Payloads from GitHub, the AI providers, and this server's own clients. Unknown keys are
     * expected — the words repo and provider responses both carry fields the server does not
     * model — so decoding must tolerate them rather than fail.
     */
    val lenient: Json = Json { ignoreUnknownKeys = true }

    /**
     * The `/internal/update-repo` callback. An unknown key there means the caller and this
     * server disagree about the schema, and a repo write is the wrong place to find that out
     * quietly, so the payload is rejected instead.
     */
    val strict: Json = Json { ignoreUnknownKeys = false }

    /** Content written to disk or committed to the words repo, where a human reads the diff. */
    val pretty: Json = Json { prettyPrint = true }
}
