package com.example.myapplication.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.core.net.toUri
import com.example.timelineexport.R


/**
 * Handles OAuth with Google for arbitrary scopes (AppAuth + PKCE).
 * Configure OAuth client in Google Cloud Console; add the package name & SHA-1
 * fingerprint for an Android client, and enable the Data Portability API.
 */
class AuthManager(private val context: Context) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        // Google OIDC endpoints
        "https://accounts.google.com/o/oauth2/v2/auth".toUri(),
        "https://oauth2.googleapis.com/token".toUri()
    )

    private val authService by lazy { AuthorizationService(context) }

    private val _token = MutableLiveData<TokenResponse?>()
    val token: LiveData<TokenResponse?> = _token

    fun startSignIn(activity: Activity, scopes: List<String>) {
        val clientId = context.getString(
            R.string.oauth_client_id
        )

        val redirectUri =
            context.getString(R.string.redirect_uri).toUri()

        val req = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri
        )
            .setScopes(scopes)
            .build()

        val intent = authService.getAuthorizationRequestIntent(req)
        activity.startActivity(intent)
    }

    fun handleAuthResponse(
        intent: Intent,
        onResult: (Boolean, String?) -> Unit
    ) {
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        if (resp != null) {
            authService.performTokenRequest(resp.createTokenExchangeRequest()) { tr, te ->
                if (te != null) onResult(false, te.errorDescription)
                else { _token.postValue(tr); onResult(true, null) }
            }
        } else onResult(false, ex?.errorDescription)
    }

    fun getAccessToken(): String? = token.value?.accessToken}


