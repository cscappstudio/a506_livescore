package com.football.livescores.matchlive.data.local

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent
import com.football.livescores.matchlive.data.remote.adjust.AppAdjustTokens
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val limitManager: RequestLimitManager,
    private val gsmManager: com.football.livescores.matchlive.data.remote.gsm.GsmManager
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    private val _productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetailsList: StateFlow<List<ProductDetails>> = _productDetailsList.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_WEEKLY = "com.livescorevip.weekly"
        const val PRODUCT_MONTHLY = "com.flivescorevip.monthly"
    }

    init {
        startConnection()
    }

    fun startConnection() {
        try {
            billingClient.startConnection(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting billing client connection", e)
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.d(TAG, "Billing service disconnected. Reconnecting...")
        startConnection()
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        Log.d(TAG, "Billing setup finished: Code = ${billingResult.responseCode}, Msg = ${billingResult.debugMessage}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            queryProductDetails()
            queryPurchases()
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_WEEKLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            Log.d(TAG, "Query product details finished: Code = ${billingResult.responseCode}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetailsList.value = queryProductDetailsResult.productDetailsList ?: emptyList()
            }
        }
    }

    fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            Log.d(TAG, "Query purchases finished: Code = ${billingResult.responseCode}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchasesList) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handlePurchase(purchase)
                    }
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val offerDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
        val offerToken = offerDetails?.offerToken ?: ""
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        Log.d(TAG, "Purchases updated: Code = ${billingResult.responseCode}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User cancelled purchase flow.")
        } else {
            Log.e(TAG, "Error in purchase flow: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                Log.d(TAG, "Purchase acknowledgement finished: Code = ${billingResult.responseCode}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    verifyTransactionRemote(purchase)
                }
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            verifyTransactionRemote(purchase)
        }
    }

    private fun verifyTransactionRemote(purchase: Purchase) {
        coroutineScope.launch {
            val productId = purchase.products.firstOrNull() ?: ""
            if (productId.isEmpty()) return@launch

            Log.d(TAG, "Starting remote transaction verification for product: $productId")

            // Method A:  API Verification (Recommended)
            // For livescore packages (subscriptions), productType is "subscription"
            val productType = "subscription"
            val Success = gsmManager.verify(productId, productType)
            Log.d(TAG, "GSM  Verify result: $Success")

            // Method B: Legacy GSM Check API Verification (Fallback)
            val productName = if (productId == PRODUCT_WEEKLY) "1 Week VIP" else "1 Month VIP"
            val legacySuccess = gsmManager.verifyLegacy(
                packageName = context.packageName,
                purchaseToken = purchase.purchaseToken,
                productId = productId,
                productName = productName
            )
            Log.d(TAG, "GSM Legacy Verify result: $legacySuccess")

            // Update UI/State if either verification method succeeded
            if (Success || legacySuccess) {
                Log.d(TAG, "Transaction verified successfully via remote servers. Activating VIP.")
                limitManager.setPremium(true)

                // Adjust Billing Event Tracking Integration
                try {
                    val products = _productDetailsList.value
                    val matchedProduct = products.find { it.productId == productId }
                    val priceDetails = matchedProduct?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    
                    val rawPriceMicros = priceDetails?.priceAmountMicros ?: 0L
                    val price = if (rawPriceMicros > 0L) rawPriceMicros / 1000000.0 else (if (productId == PRODUCT_WEEKLY) 1.99 else 5.99)
                    val currency = priceDetails?.priceCurrencyCode ?: "USD"
                    // 1. General purchase tracking event
                    val iapEvent = AdjustEvent(AppAdjustTokens.EVENT_IAP_COMMON).apply {
                        setRevenue(price, currency)
                        if (orderId.isNotEmpty()) {
                            setOrderId(orderId)
                        }
                    }
                    Adjust.trackEvent(iapEvent)

                    // 2. Specific billing token event
                    val eventToken = if (productId == PRODUCT_WEEKLY) {
                        AppAdjustTokens.EVENT_BUY_WEEKLY
                    } else {
                        AppAdjustTokens.EVENT_BUY_MONTHLY
                    }

                    val specificEvent = AdjustEvent(eventToken).apply {
                        setRevenue(price, currency)
                        if (orderId.isNotEmpty()) {
                            setOrderId(orderId)
                        }
                    }
                    Adjust.trackEvent(specificEvent)
                    Log.d(TAG, "Adjust successfully tracked purchase for product: $productId ($price $currency)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to track purchase via Adjust", e)
                }
            } else {
                Log.e(TAG, "Remote verification failed for both  and Legacy GSM API. Activating via local billing verification fallback.")
                limitManager.setPremium(true)
            }
        }
    }
}
