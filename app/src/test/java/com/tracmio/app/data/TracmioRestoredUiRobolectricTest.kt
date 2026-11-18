package com.tracmio.app.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.tracmio.app.data.local.AccountEntity
import com.tracmio.app.data.local.TransactionEntity
import com.tracmio.app.ui.nativeapp.DashboardScreen
import com.tracmio.app.ui.nativeapp.LiquidGlassSettings
import com.tracmio.app.ui.nativeapp.NativeTracmioViewModel
import com.tracmio.app.ui.nativeapp.ThemePresets
import com.tracmio.app.ui.nativeapp.TracmioNativeTheme
import com.tracmio.app.ui.nativeapp.TracmioTopBar
import com.tracmio.app.ui.nativeapp.TransactionsListScreen
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp-xxhdpi")
class TracmioRestoredUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topBarHasLogoGreetingPrivacyAddAndNoSearchButton() {
        val settings = LiquidGlassSettings(userName = "Charan")
        val palette = ThemePresets.getPalette(settings.themePreset)

        composeRule.setContent {
            TracmioNativeTheme(settings = settings) {
                TracmioTopBar(
                    settings = settings,
                    palette = palette,
                    onSingleTapPrivacy = {},
                    onTripleTapStealth = {},
                    onQuickAdd = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tracmio_topbar").assertIsDisplayed()
        composeRule.onNodeWithText("Tracmio").assertIsDisplayed()
        composeRule.onNodeWithText("Charan", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_eye_button").assertIsDisplayed()
        composeRule.onNodeWithTag("topbar_quick_add_button").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Global Search").assertDoesNotExist()
    }

    @Test
    fun dashboardRendersRestoredHeroPaceQuickStatsAndV1613Widgets() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = NativeTracmioViewModel(app)
        val settings = LiquidGlassSettings(userName = "Charan", hasCompletedOnboarding = true)
        val palette = ThemePresets.getPalette(settings.themePreset)
        val now = System.currentTimeMillis()
        val state = NativeTracmioUiState(
            accounts = listOf(AccountEntity(id = 1L, name = "Main Bank", accountType = "Bank", balance = 10000.0)),
            transactions = listOf(
                TransactionEntity(
                    id = 1L,
                    accountId = 1L,
                    type = "BORROW",
                    amount = 500.0,
                    amountPaise = 50_000L,
                    category = "Other",
                    note = "Friend borrow",
                    timestamp = now
                ),
                TransactionEntity(
                    id = 2L,
                    accountId = 1L,
                    type = "LEND",
                    amount = 1200.0,
                    amountPaise = 120_000L,
                    category = "Other",
                    note = "Friend lend",
                    timestamp = now
                )
            ),
            totalBalance = 10000.0,
            totalIncome = 15000.0,
            totalExpense = 5000.0
        )

        composeRule.setContent {
            TracmioNativeTheme(settings = settings) {
                DashboardScreen(
                    state = state,
                    settings = settings,
                    palette = palette,
                    viewModel = viewModel,
                    onQuickAdd = {},
                    onOpenTransactionReceipt = {},
                    onNavigate = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Restored v2.5 Top Section
        composeRule.onNodeWithTag("dashboard_hero_balance_card").assertIsDisplayed()
        composeRule.onNodeWithText("TOTAL BALANCE").assertIsDisplayed()
        composeRule.onNodeWithText("₹10,000").assertIsDisplayed()
        composeRule.onNodeWithText("Income").assertIsDisplayed()
        composeRule.onNodeWithText("₹15,000").assertIsDisplayed()
        composeRule.onNodeWithText("Expense").assertIsDisplayed()
        composeRule.onNodeWithText("₹5,000").assertIsDisplayed()

        composeRule.onNodeWithTag("dashboard_monthly_pace_card").assertIsDisplayed()
        composeRule.onNodeWithText("MONTHLY PACE").assertIsDisplayed()

        composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Transactions"))
        composeRule.onNodeWithTag("dashboard_quick_stats_section").assertIsDisplayed()
        composeRule.onNodeWithText("QUICK STATS").assertIsDisplayed()
        composeRule.onNodeWithText("Borrowed").assertIsDisplayed()
        composeRule.onNodeWithText("₹500").assertIsDisplayed()
        composeRule.onNodeWithText("Lent").assertIsDisplayed()
        composeRule.onNodeWithText("₹1,200").assertIsDisplayed()
        composeRule.onNodeWithText("Net Worth").assertIsDisplayed()
        composeRule.onNodeWithText("₹10,700").assertIsDisplayed()
        composeRule.onNodeWithText("Transactions").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()

        // Scrollable v16.13 Intelligence Widgets below Quick Stats
        composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasText("SAFE-TO-SPEND TODAY"))
        composeRule.onNodeWithText("SAFE-TO-SPEND TODAY").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasText("1-TAP QUICK SPEND"))
        composeRule.onNodeWithText("1-TAP QUICK SPEND").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasText("PASTE UPI / BANK ALERT QUICK-LOG"))
        composeRule.onNodeWithText("PASTE UPI / BANK ALERT QUICK-LOG").assertIsDisplayed()
    }

    @Test
    fun transactionsListInlineSearchFiltersTransactions() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = NativeTracmioViewModel(app)
        val settings = LiquidGlassSettings(hasCompletedOnboarding = true)
        val palette = ThemePresets.getPalette(settings.themePreset)
        val now = System.currentTimeMillis()
        val state = NativeTracmioUiState(
            accounts = listOf(AccountEntity(id = 1L, name = "Main Bank", accountType = "Bank", balance = 5000.0)),
            transactions = listOf(
                TransactionEntity(
                    id = 1L,
                    accountId = 1L,
                    type = "EXPENSE",
                    amount = 240.0,
                    amountPaise = 24_000L,
                    category = "Food & Chai",
                    note = "Swiggy lunch #office",
                    paymentMethod = "UPI",
                    upiApp = "GPay",
                    timestamp = now
                ),
                TransactionEntity(
                    id = 2L,
                    accountId = 1L,
                    type = "EXPENSE",
                    amount = 90.0,
                    amountPaise = 9_000L,
                    category = "Transport & Travel",
                    note = "Metro ride",
                    paymentMethod = "Cash",
                    timestamp = now - 1000L
                )
            )
        )

        composeRule.setContent {
            TracmioNativeTheme(settings = settings) {
                TransactionsListScreen(
                    state = state,
                    settings = settings,
                    palette = palette,
                    viewModel = viewModel,
                    onOpenReceipt = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("transactions_search_input").assertIsDisplayed()
        composeRule.onNodeWithText("Swiggy lunch #office").assertIsDisplayed()
        composeRule.onNodeWithText("Metro ride").assertIsDisplayed()

        composeRule.onNodeWithTag("transactions_search_input").performTextInput("Swiggy")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Swiggy lunch #office").assertIsDisplayed()
        composeRule.onNodeWithText("Metro ride").assertDoesNotExist()
    }

    @Test
    fun manifestDeclaresZeroNetworkPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = info.requestedPermissions?.toSet() ?: emptySet()
        assertFalse("INTERNET must not be declared", requested.contains("android.permission.INTERNET"))
        assertFalse(
            "ACCESS_NETWORK_STATE must not be declared",
            requested.contains("android.permission.ACCESS_NETWORK_STATE")
        )
    }
}
