package com.nutriai.data.remote

import com.nutriai.data.local.TokenStore
import com.nutriai.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider

/**
 * On a 401, transparently refreshes the access token using the stored refresh token and
 * retries the request. Tokens are cleared (forcing re-login) ONLY when the server explicitly
 * rejects the refresh token (401/403/400 from /auth/refresh) - this is why sessions no longer
 * break after the 15-minute access-token expiry.
 *
 * A transient failure (network timeout, no connection, or the Render free-tier backend cold-
 * starting/redeploying - which can take 30-60s) must NOT clear a perfectly valid session. This
 * used to catch every exception and wipe tokens on any of those, which logged the user out for
 * no real reason whenever a refresh happened to land during a slow network moment or a backend
 * restart - a real, reproducible bug, not a random glitch.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApi: Provider<RefreshApi>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Give up after one retry to avoid infinite loops.
        if (priorResponseCount(response) >= 2) return null

        val refreshToken = runBlocking { tokenStore.refreshToken() } ?: return null

        val tokens = try {
            runBlocking { refreshApi.get().refresh(RefreshRequest(refreshToken)).tokens }
        } catch (e: HttpException) {
            // The server looked at the refresh token and explicitly rejected it - genuinely
            // invalid/expired, so this really is a forced-logout situation.
            if (e.code() in intArrayOf(400, 401, 403)) {
                runBlocking { tokenStore.clear() }
            }
            return null
        } catch (e: IOException) {
            // Network-level failure (timeout, no connection, backend unreachable/cold-starting) -
            // transient, not proof the refresh token is invalid. Fail this one request only; the
            // session and its tokens stay intact for the next attempt.
            return null
        } catch (e: Exception) {
            // Anything else unexpected (e.g. a malformed response) - same reasoning, don't nuke a
            // session over it.
            return null
        }

        runBlocking { tokenStore.save(tokens.accessToken, tokens.refreshToken) }
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${tokens.accessToken}")
            .build()
    }

    private fun priorResponseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
