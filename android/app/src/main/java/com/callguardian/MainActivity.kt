package com.callguardian

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.shape.RoundedCornerShape
import com.callguardian.data.AppPreferences
import com.callguardian.data.CallEvent
import com.callguardian.data.CallEventStore
import com.callguardian.data.CallPatternStore
import com.callguardian.data.CallStatsSnapshot
import com.callguardian.data.CallStatsStore
import com.callguardian.data.PolicySettings
import com.callguardian.data.ScreeningDiagnostics
import com.callguardian.data.ScreeningDiagnosticsStore
import com.callguardian.engine.CoreEngineBridge
import com.callguardian.engine.PlatformCallInfo
import com.callguardian.engine.PlatformRiskAssessment
import com.callguardian.R
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallGuardianTheme {
                CallGuardianApp()
            }
        }
    }
}

private enum class AppScreen {
    Dashboard,
    Whitelist,
    Settings,
    Log,
    BlockedRules,
    Test,
    Help
}

private data class ProtectionPreset(
    val title: String,
    val description: String,
    val policy: PolicySettings
)

private data class CountryBlockOption(
    val flag: String,
    val label: String,
    val prefix: String
)

private data class TestScenario(
    val title: String,
    val description: String,
    val callInfo: PlatformCallInfo
)

private data class HelpTopic(
    val title: String,
    val body: String
)

private data class HelpPage(
    val title: String,
    val topics: List<HelpTopic>
)

private enum class ThresholdProfile {
    Warn,
    Silence,
    Block
}

private val SafeGreen = Color(0xFF27D7A2)
private val SafeGreenDark = Color(0xFF10271F)
private val SafeGreenBorder = Color(0xFF32D583)
private val AlertRed = Color(0xFFFF3D3D)
private val WarningAmber = Color(0xFFFFB23E)

@Composable
private fun uiText(source: String): String {
    val locales = LocalConfiguration.current.locales
    val locale = if (locales.isEmpty) Locale.getDefault() else locales[0]
    return localizeText(source, locale)
}

private fun localizeText(source: String, locale: Locale = Locale.getDefault()): String {
    if (locale.language.equals("it", ignoreCase = true)) {
        return source
    }
    return englishText[source] ?: source
}

private val englishText = mapOf(
    "FreyaShield Anti-spam" to "FreyaShield Anti-spam",
    "FreyaShield diagnostica privacy-safe" to "FreyaShield privacy-safe diagnostics",
    "FreyaShield diagnostica" to "FreyaShield diagnostics",
    "Generato" to "Generated",
    "Statistiche 7 giorni" to "7-day statistics",
    "Silenziate" to "Silenced",
    "Servizio" to "Service",
    "Registro mascherato" to "Masked log",
    "Esporta diagnostica FreyaShield" to "Export FreyaShield diagnostics",
    "Aiuto" to "Help",
    "Protezione" to "Protection",
    "Regole" to "Rules",
    "Config." to "Config",
    "Log" to "Log",
    "Configurazioni" to "Settings",
    "Analisi core" to "Core analysis",
    "Numero test" to "Test number",
    "Stato" to "Status",
    "Inizializzazione" to "Initializing",
    "Score" to "Score",
    "Azione" to "Action",
    "Motivo" to "Reason",
    "Segnali" to "Signals",
    "Riprova" to "Retry",
    "Ultimi 7 giorni" to "Last 7 days",
    "Bloccate" to "Blocked",
    "Silenziare" to "Silenced",
    "Motivo top" to "Top reason",
    "Registro" to "Log",
    "Solo aggregati locali" to "Local aggregates only",
    "Diagnostica servizio" to "Service diagnostics",
    "Invocazioni" to "Invocations",
    "Ultima chiamata" to "Last call",
    "Ultimo evento" to "Last event",
    "Registro recente" to "Recent log",
    "Nessuna chiamata registrata." to "No calls recorded.",
    "Nessuna" to "None",
    "Nessun evento" to "No event",
    "Protezione locale attiva" to "Local protection active",
    "Protezione pronta da abilitare" to "Protection ready to enable",
    "Fidati" to "Trust",
    "Blocca" to "Block",
    "Log chiamate filtrate" to "Filtered call log",
    "Solo numeri mascherati, senza dati completi in chiaro." to "Masked numbers only, with no full plain-text data.",
    "Aggiorna" to "Refresh",
    "Nessuna chiamata bloccata o silenziata." to "No blocked or silenced calls.",
    "Protezzione Attiva" to "Protection Active",
    "Protezione da attivare" to "Protection inactive",
    "Freya controlla!" to "Freya is watching!",
    "Abilita FreyaShield come app di filtro chiamate per proteggere le chiamate in arrivo." to "Enable FreyaShield as the call screening app to protect incoming calls.",
    "Attiva filtro chiamate" to "Enable call filter",
    "Strumenti regole" to "Rule tools",
    "Consulta blocchi salvati o prova scenari senza chiamate reali." to "Review saved blocks or test scenarios without real calls.",
    "Elenco" to "List",
    "Prove" to "Tests",
    "Pattern fidati" to "Trusted patterns",
    "Numero o prefisso" to "Number or prefix",
    "Aggiungi" to "Add",
    "Bypass locale delle euristiche" to "Local heuristic bypass",
    "Rimuovi" to "Remove",
    "Blocca prefissi" to "Block prefixes",
    "Esempio: 081 123456 blocca tutti i numeri che iniziano con quel prefisso." to "Example: 081 123456 blocks all numbers starting with that prefix.",
    "Prefisso da bloccare" to "Prefix to block",
    "Blocca aree e nazioni" to "Block areas and countries",
    "Seleziona una nazione, poi la vedrai sotto con bandierina, stato e prefisso." to "Select a country, then you will see it below with flag, name, and prefix.",
    "Area IT es. 081" to "IT area, e.g. 081",
    "Blocchi attivi" to "Active blocks",
    "Prefissi, aree e nazioni bloccate localmente." to "Prefixes, areas, and countries blocked locally.",
    "Nessun blocco salvato." to "No saved blocks.",
    "Rimuovi blocco" to "Remove block",
    "Nazione" to "Country",
    "Modalita protezione" to "Protection mode",
    "Configurazione personalizzata." to "Custom configuration.",
    "Avviso" to "Warn",
    "Silenzia" to "Silence",
    "Blocca verifica fallita" to "Block failed verification",
    "Avvisa neighbor spoofing" to "Warn on neighbor spoofing",
    "Blocca estero non salvato" to "Block unsaved foreign numbers",
    "Auto-blocca numeri simili" to "Auto-block similar numbers",
    "Quarantena temporanea prefissi" to "Temporary prefix quarantine",
    "Blocca sospetti non verificati" to "Block unverified suspicious calls",
    "Filtro sera e notte" to "Evening and night filter",
    "Solo regole fidate" to "Trusted rules only",
    "Azioni manuali nel log" to "Manual actions in log",
    "Esporta diagnostica sicura" to "Export safe diagnostics",
    "Cancella dati locali" to "Clear local data",
    "Spiegazione dettagliata della funzione selezionata." to "Detailed explanation of the selected feature.",
    "Indietro" to "Back",
    "Prove regole" to "Rule tests",
    "Simulatore locale per verificare soglie, whitelist e blocchi." to "Local simulator to check thresholds, whitelist, and blocks.",
    "Simulatore chiamata" to "Call simulator",
    "Numero da testare" to "Number to test",
    "Gia visto" to "Seen before",
    "Verifica fallita" to "Failed verification",
    "Frequenza alta" to "High frequency",
    "Analizza" to "Analyze",
    "Fidati di questo numero" to "Trust this number",
    "Blocca numeri simili" to "Block similar numbers",
    "Scenari rapidi" to "Quick scenarios",
    "Test locali senza chiamate reali e senza salvare numeri." to "Local tests without real calls and without saving numbers.",
    "Esegui test" to "Run test",
    "Meno interventi" to "Fewer interventions",
    "Piu protezione" to "More protection",
    "Spiega questa funzione" to "Explain this feature",
    "Bassa" to "Low",
    "Standard" to "Standard",
    "Alta" to "High",
    "Call center" to "Call center",
    "Avvisa spesso, blocca solo segnali molto forti." to "Warn often, block only very strong signals.",
    "Equilibrio tra protezione, silenziamento e falsi positivi." to "Balance protection, silencing, and false positives.",
    "Piu severa su verifica fallita, spoofing vicino e numeri esteri nuovi." to "Stricter on failed verification, nearby spoofing, and new foreign numbers.",
    "Protezione avanzata contro prefissi ripetuti, numeri non verificati e chiamate serali." to "Advanced protection against repeated prefixes, unverified numbers, and evening calls.",
    "Avvisa" to "Warn",
    "Consenti" to "Allow",
    "Nessun rischio" to "No risk",
    "Verifica superata" to "Verification passed",
    "Numero simile al tuo prefisso" to "Number similar to your prefix",
    "Troppe chiamate dallo stesso numero" to "Too many calls from the same number",
    "Troppe chiamate dallo stesso prefisso" to "Too many calls from the same prefix",
    "Prefisso spesso rifiutato" to "Often rejected prefix",
    "Internazionale mai visto prima" to "International number never seen before",
    "Pattern numerico sospetto" to "Suspicious numeric pattern",
    "Numero fidato" to "Trusted number",
    "Pattern bloccato" to "Blocked pattern",
    "Numeri simili ripetuti" to "Repeated similar numbers",
    "Prefisso in quarantena" to "Prefix in quarantine",
    "Sospetto non verificato" to "Unverified suspicious call",
    "Dati chiamata vuoti" to "Empty call data"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallGuardianApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedScreen by remember { mutableStateOf(AppScreen.Dashboard) }
    var helpTargetScreen by remember { mutableStateOf(AppScreen.Dashboard) }
    var activeHelpPage by remember { mutableStateOf(helpPageForScreen(AppScreen.Dashboard)) }
    var roleHeld by remember { mutableStateOf(isCallScreeningRoleHeld(context)) }
    var stats by remember { mutableStateOf(CallStatsStore.loadLastSevenDays(context)) }
    var recentEvents by remember { mutableStateOf(CallEventStore.loadRecent(context, limit = 50)) }
    var diagnostics by remember { mutableStateOf(ScreeningDiagnosticsStore.load(context)) }
    val initialPolicy = remember { AppPreferences.loadPolicy(context) }
    var assessment by remember { mutableStateOf<PlatformRiskAssessment?>(null) }
    val trustedPatterns = remember {
        mutableStateListOf<String>().apply {
            addAll(AppPreferences.loadWhitelist(context))
        }
    }
    val blockedPatterns = remember {
        mutableStateListOf<String>().apply {
            addAll(AppPreferences.loadBlocklist(context))
        }
    }
    var warnThreshold by remember { mutableFloatStateOf(initialPolicy.warnThreshold) }
    var silenceThreshold by remember { mutableFloatStateOf(initialPolicy.silenceThreshold) }
    var blockThreshold by remember { mutableFloatStateOf(initialPolicy.blockThreshold) }
    var blockFailedVerification by remember { mutableStateOf(initialPolicy.blockFailedVerification) }
    var warnNeighborSpoof by remember { mutableStateOf(initialPolicy.warnNeighborSpoof) }
    var blockFirstSeenInternational by remember { mutableStateOf(initialPolicy.blockFirstSeenInternational) }
    var autoBlockSimilarNumbers by remember { mutableStateOf(initialPolicy.autoBlockSimilarNumbers) }
    var temporaryGreylist by remember { mutableStateOf(initialPolicy.temporaryGreylist) }
    var blockUnverifiedSuspicious by remember { mutableStateOf(initialPolicy.blockUnverifiedSuspicious) }
    var quietHoursFilter by remember { mutableStateOf(initialPolicy.quietHoursFilter) }
    var trustedOnlyMode by remember { mutableStateOf(initialPolicy.trustedOnlyMode) }
    var manualFeedbackActions by remember { mutableStateOf(initialPolicy.manualFeedbackActions) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        roleHeld = isCallScreeningRoleHeld(context)
    }

    LaunchedEffect(Unit) {
        roleHeld = isCallScreeningRoleHeld(context)
        AppPreferences.applyToCore(context)
        stats = CallStatsStore.loadLastSevenDays(context)
        recentEvents = CallEventStore.loadRecent(context, limit = 50)
        diagnostics = ScreeningDiagnosticsStore.load(context)
        assessment = runSampleAnalysis()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                roleHeld = isCallScreeningRoleHeld(context)
                stats = CallStatsStore.loadLastSevenDays(context)
                recentEvents = CallEventStore.loadRecent(context, limit = 50)
                diagnostics = ScreeningDiagnosticsStore.load(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun persistPolicy() {
        saveAndApplyPolicy(
            context = context,
            warnThreshold = warnThreshold,
            silenceThreshold = silenceThreshold,
            blockThreshold = blockThreshold,
            blockFailedVerification = blockFailedVerification,
            warnNeighborSpoof = warnNeighborSpoof,
            blockFirstSeenInternational = blockFirstSeenInternational,
            autoBlockSimilarNumbers = autoBlockSimilarNumbers,
            temporaryGreylist = temporaryGreylist,
            blockUnverifiedSuspicious = blockUnverifiedSuspicious,
            quietHoursFilter = quietHoursFilter,
            trustedOnlyMode = trustedOnlyMode,
            manualFeedbackActions = manualFeedbackActions
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                selectedScreen = selectedScreen,
                onBack = {
                    selectedScreen = when (selectedScreen) {
                        AppScreen.BlockedRules, AppScreen.Test -> AppScreen.Whitelist
                        AppScreen.Help -> helpTargetScreen
                        else -> AppScreen.Dashboard
                    }
                },
                onOpenHelp = {
                    if (selectedScreen != AppScreen.Help) {
                        helpTargetScreen = selectedScreen
                        activeHelpPage = helpPageForScreen(selectedScreen)
                    }
                    selectedScreen = AppScreen.Help
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF171016), contentColor = Color(0xFFFFE8B6)) {
                NavigationBarItem(
                    selected = selectedScreen == AppScreen.Dashboard,
                    onClick = { selectedScreen = AppScreen.Dashboard },
                    modifier = Modifier.pressFeedback(),
                    icon = { Icon(Icons.Filled.Security, contentDescription = null) },
                    label = { Text(uiText("Protezione")) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedScreen == AppScreen.Whitelist,
                    onClick = { selectedScreen = AppScreen.Whitelist },
                    modifier = Modifier.pressFeedback(),
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(uiText("Regole")) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedScreen == AppScreen.Settings,
                    onClick = { selectedScreen = AppScreen.Settings },
                    modifier = Modifier.pressFeedback(),
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(uiText("Config.")) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedScreen == AppScreen.Log,
                    onClick = { selectedScreen = AppScreen.Log },
                    modifier = Modifier.pressFeedback(),
                    icon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    label = { Text(uiText("Log")) },
                    colors = navItemColors()
                )
            }
        }
    ) { padding ->
        when (selectedScreen) {
            AppScreen.Dashboard -> DashboardScreen(
                padding = padding,
                roleHeld = roleHeld,
                assessment = assessment,
                stats = stats,
                recentEvents = recentEvents,
                diagnostics = diagnostics,
                manualFeedbackActions = manualFeedbackActions,
                onEnableClick = {
                    requestCallScreeningRole(context)?.let(roleLauncher::launch)
                },
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
                },
                onTrustMaskedPrefix = { maskedNumber ->
                    val pattern = maskedNumber.toTrustedPrefixPattern()
                    if (pattern != null && trustedPatterns.none { it == pattern }) {
                        trustedPatterns.add(pattern)
                        saveAndApplyWhitelist(context, trustedPatterns)
                        assessment = runSampleAnalysis()
                    }
                },
                onBlockMaskedPrefix = { maskedNumber ->
                    val pattern = maskedNumber.toTrustedPrefixPattern()
                    if (pattern != null && blockedPatterns.none { it.blockPatternOnly() == pattern }) {
                        blockedPatterns.add(pattern)
                        saveAndApplyBlocklist(context, blockedPatterns)
                        assessment = runSampleAnalysis()
                    }
                },
                onRefresh = {
                    persistPolicy()
                    saveAndApplyWhitelist(context, trustedPatterns)
                    stats = CallStatsStore.loadLastSevenDays(context)
                    recentEvents = CallEventStore.loadRecent(context, limit = 50)
                    diagnostics = ScreeningDiagnosticsStore.load(context)
                    assessment = runSampleAnalysis()
                }
            )

            AppScreen.Whitelist -> WhitelistScreen(
                padding = padding,
                trustedPatterns = trustedPatterns,
                blockedPatterns = blockedPatterns,
                onAddPattern = { pattern ->
                    val prepared = pattern.preparePhonePattern()
                    if (prepared.isNotBlank() && trustedPatterns.none { it == prepared }) {
                        trustedPatterns.add(prepared)
                        saveAndApplyWhitelist(context, trustedPatterns)
                        assessment = runSampleAnalysis()
                    }
                },
                onRemovePattern = { pattern ->
                    trustedPatterns.remove(pattern)
                    saveAndApplyWhitelist(context, trustedPatterns)
                    assessment = runSampleAnalysis()
                },
                onAddBlockedPattern = { pattern ->
                    val prepared = pattern.preparePhonePattern(forceWildcard = true)
                    if (prepared.isNotBlank() && blockedPatterns.none { it.blockPatternOnly() == prepared }) {
                        blockedPatterns.add(prepared)
                        saveAndApplyBlocklist(context, blockedPatterns)
                        assessment = runSampleAnalysis()
                    }
                },
                onRemoveBlockedPattern = { pattern ->
                    blockedPatterns.remove(pattern)
                    saveAndApplyBlocklist(context, blockedPatterns)
                    assessment = runSampleAnalysis()
                },
                onAddCountryBlock = { option ->
                    val entry = option.toBlocklistEntry()
                    val pattern = entry.blockPatternOnly()
                    if (pattern.isNotBlank() && blockedPatterns.none { it.blockPatternOnly() == pattern }) {
                        blockedPatterns.add(entry)
                        saveAndApplyBlocklist(context, blockedPatterns)
                        assessment = runSampleAnalysis()
                    }
                },
                onAddItalianAreaBlock = { prefix ->
                    val prepared = prefix.prepareItalianAreaBlockPattern()
                    if (prepared.isNotBlank() && blockedPatterns.none { it.blockPatternOnly() == prepared }) {
                        blockedPatterns.add(prepared)
                        saveAndApplyBlocklist(context, blockedPatterns)
                        assessment = runSampleAnalysis()
                    }
                },
                onOpenTests = {
                    selectedScreen = AppScreen.Test
                },
                onOpenBlockedRules = {
                    selectedScreen = AppScreen.BlockedRules
                },
                onOpenHelp = { key ->
                    helpTargetScreen = AppScreen.Whitelist
                    activeHelpPage = helpPageForSetting(key)
                    selectedScreen = AppScreen.Help
                }
            )

            AppScreen.Settings -> SettingsScreen(
                padding = padding,
                warnThreshold = warnThreshold,
                silenceThreshold = silenceThreshold,
                blockThreshold = blockThreshold,
                blockFailedVerification = blockFailedVerification,
                warnNeighborSpoof = warnNeighborSpoof,
                blockFirstSeenInternational = blockFirstSeenInternational,
                autoBlockSimilarNumbers = autoBlockSimilarNumbers,
                temporaryGreylist = temporaryGreylist,
                blockUnverifiedSuspicious = blockUnverifiedSuspicious,
                quietHoursFilter = quietHoursFilter,
                trustedOnlyMode = trustedOnlyMode,
                manualFeedbackActions = manualFeedbackActions,
                onWarnThresholdChange = {
                    warnThreshold = it.coerceIn(0.10f, silenceThreshold)
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onSilenceThresholdChange = {
                    silenceThreshold = it.coerceIn(warnThreshold, blockThreshold)
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onBlockThresholdChange = {
                    blockThreshold = it.coerceIn(silenceThreshold, 0.95f)
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onBlockFailedVerificationChange = {
                    blockFailedVerification = it
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onWarnNeighborSpoofChange = {
                    warnNeighborSpoof = it
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onBlockFirstSeenInternationalChange = {
                    blockFirstSeenInternational = it
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onAutoBlockSimilarNumbersChange = {
                    autoBlockSimilarNumbers = it
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onTemporaryGreylistChange = {
                    temporaryGreylist = it
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onBlockUnverifiedSuspiciousChange = {
                    blockUnverifiedSuspicious = it
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onQuietHoursFilterChange = {
                    quietHoursFilter = it
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onTrustedOnlyModeChange = {
                    trustedOnlyMode = it
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onManualFeedbackActionsChange = {
                    manualFeedbackActions = it
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onClearStats = {
                    CallStatsStore.clear(context)
                    CallEventStore.clear(context)
                    ScreeningDiagnosticsStore.clear(context)
                    CallPatternStore.clear(context)
                    stats = CallStatsStore.loadLastSevenDays(context)
                    recentEvents = CallEventStore.loadRecent(context, limit = 50)
                    diagnostics = ScreeningDiagnosticsStore.load(context)
                },
                onApplyPreset = { preset ->
                    warnThreshold = preset.policy.warnThreshold
                    silenceThreshold = preset.policy.silenceThreshold
                    blockThreshold = preset.policy.blockThreshold
                    blockFailedVerification = preset.policy.blockFailedVerification
                    warnNeighborSpoof = preset.policy.warnNeighborSpoof
                    blockFirstSeenInternational = preset.policy.blockFirstSeenInternational
                    autoBlockSimilarNumbers = preset.policy.autoBlockSimilarNumbers
                    temporaryGreylist = preset.policy.temporaryGreylist
                    blockUnverifiedSuspicious = preset.policy.blockUnverifiedSuspicious
                    quietHoursFilter = preset.policy.quietHoursFilter
                    trustedOnlyMode = preset.policy.trustedOnlyMode
                    manualFeedbackActions = preset.policy.manualFeedbackActions
                    persistPolicy()
                    assessment = runSampleAnalysis()
                },
                onExportDiagnostics = {
                    sharePrivacyReport(context, stats, recentEvents, diagnostics)
                },
                onOpenHelp = { key ->
                    helpTargetScreen = AppScreen.Settings
                    activeHelpPage = helpPageForSetting(key)
                    selectedScreen = AppScreen.Help
                }
            )

            AppScreen.Log -> BlockedCallLogScreen(
                padding = padding,
                recentEvents = recentEvents,
                manualFeedbackActions = manualFeedbackActions,
                onTrustMaskedPrefix = { maskedNumber ->
                    val pattern = maskedNumber.toTrustedPrefixPattern()
                    if (pattern != null && trustedPatterns.none { it == pattern }) {
                        trustedPatterns.add(pattern)
                        saveAndApplyWhitelist(context, trustedPatterns)
                        assessment = runSampleAnalysis()
                    }
                },
                onBlockMaskedPrefix = { maskedNumber ->
                    val pattern = maskedNumber.toTrustedPrefixPattern()
                    if (pattern != null && blockedPatterns.none { it.blockPatternOnly() == pattern }) {
                        blockedPatterns.add(pattern)
                        saveAndApplyBlocklist(context, blockedPatterns)
                        assessment = runSampleAnalysis()
                    }
                },
                onRefresh = {
                    recentEvents = CallEventStore.loadRecent(context, limit = 50)
                }
            )

            AppScreen.BlockedRules -> BlockedRulesScreen(
                padding = padding,
                blockedPatterns = blockedPatterns,
                onRemoveBlockedPattern = { pattern ->
                    blockedPatterns.remove(pattern)
                    saveAndApplyBlocklist(context, blockedPatterns)
                    assessment = runSampleAnalysis()
                },
                onBackToRules = {
                    selectedScreen = AppScreen.Whitelist
                }
            )

            AppScreen.Test -> TestScreen(
                padding = padding,
                onBackToRules = {
                    selectedScreen = AppScreen.Whitelist
                },
                onTrustPattern = { pattern ->
                    val prepared = pattern.preparePhonePattern()
                    if (prepared.isNotBlank() && trustedPatterns.none { it == prepared }) {
                        trustedPatterns.add(prepared)
                        saveAndApplyWhitelist(context, trustedPatterns)
                    }
                },
                onBlockPattern = { pattern ->
                    val prepared = pattern.preparePhonePattern(forceWildcard = true)
                    if (prepared.isNotBlank() && blockedPatterns.none { it.blockPatternOnly() == prepared }) {
                        blockedPatterns.add(prepared)
                        saveAndApplyBlocklist(context, blockedPatterns)
                    }
                },
                onScenarioRun = { result ->
                    assessment = result
                }
            )

            AppScreen.Help -> HelpScreen(
                padding = padding,
                page = activeHelpPage,
                onBack = {
                    selectedScreen = helpTargetScreen
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    selectedScreen: AppScreen,
    onBack: () -> Unit,
    onOpenHelp: () -> Unit
) {
    val isMainScreen = selectedScreen == AppScreen.Dashboard
    val hasBackNavigation = selectedScreen == AppScreen.BlockedRules ||
        selectedScreen == AppScreen.Test ||
        selectedScreen == AppScreen.Help

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isMainScreen) Color(0xFF140E12) else Color(0xFF100B0F),
            titleContentColor = Color(0xFFFFE8B6),
            navigationIconContentColor = Color(0xFFFFE8B6),
            actionIconContentColor = Color(0xFFFFE8B6)
        ),
        navigationIcon = {
            if (hasBackNavigation) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.pressFeedback()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = uiText("Indietro")
                    )
                }
            }
        },
        title = {
            Text(
                if (isMainScreen) "FreyaShield" else uiText(helpTitle(selectedScreen)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            if (selectedScreen != AppScreen.Help) {
                IconButton(
                    onClick = onOpenHelp,
                    modifier = Modifier.pressFeedback()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = uiText("Aiuto"))
                }
            }
        }
    )
}

@Composable
private fun DashboardScreen(
    padding: PaddingValues,
    roleHeld: Boolean,
    assessment: PlatformRiskAssessment?,
    stats: CallStatsSnapshot,
    recentEvents: List<CallEvent>,
    diagnostics: ScreeningDiagnostics,
    manualFeedbackActions: Boolean,
    onEnableClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onTrustMaskedPrefix: (String) -> Unit,
    onBlockMaskedPrefix: (String) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            BrandPanel(roleHeld = roleHeld)
        }
        item {
            StatusPanel(roleHeld = roleHeld, onEnableClick = onEnableClick)
        }
        item {
            CardPanel {
                Text(uiText("Analisi core"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                MetricRow("Numero test", maskPhoneNumber("+393479998888"))
                if (assessment == null) {
                    MetricRow("Stato", "Inizializzazione")
                } else {
                    MetricRow("Score", "%.2f".format(assessment.score))
                    MetricRow("Azione", actionName(assessment.action))
                    MetricRow("Motivo", reasonLabel(assessment.primaryReason))
                    if (assessment.explanation.isNotBlank()) {
                        MetricRow("Segnali", signalLabels(assessment.explanation))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f).pressFeedback()) {
                        Icon(Icons.Filled.Phone, contentDescription = null)
                        Text(uiText("Riprova"), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f).pressFeedback()) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                        Text(uiText("Android"), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            CardPanel {
                Text(uiText("Ultimi 7 giorni"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                MetricRow("Bloccate", stats.blockedCount.toString())
                MetricRow("Silenziare", stats.silencedCount.toString())
                MetricRow("Motivo top", stats.topReason)
                MetricRow("Registro", "Solo aggregati locali")
            }
        }
        item {
            CardPanel {
                Text(uiText("Diagnostica servizio"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                MetricRow("Invocazioni", diagnostics.invocationCount.toString())
                MetricRow("Ultima chiamata", diagnostics.lastMaskedNumber)
                MetricRow("Ultimo evento", CallEventStore.formatTime(diagnostics.lastTimestampMillis))
            }
        }
        item {
            CardPanel {
                Text(uiText("Registro recente"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                if (recentEvents.isEmpty()) {
                    Text(uiText("Nessuna chiamata registrata."), style = MaterialTheme.typography.bodyMedium)
                } else {
                    recentEvents.take(5).forEach { event ->
                        EventRow(
                            event = event,
                            manualFeedbackActions = manualFeedbackActions,
                            onTrustMaskedPrefix = onTrustMaskedPrefix,
                            onBlockMaskedPrefix = onBlockMaskedPrefix
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandPanel(roleHeld: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D0B10))
    ) {
        Image(
            painter = painterResource(R.drawable.freyashield_brand),
            contentDescription = "FreyaShield",
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xE6171016))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                if (roleHeld) "Protezione locale attiva" else "Protezione pronta da abilitare",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFE8B6),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EventRow(
    event: CallEvent,
    manualFeedbackActions: Boolean,
    onTrustMaskedPrefix: (String) -> Unit,
    onBlockMaskedPrefix: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.maskedNumber, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${CallEventStore.formatTime(event.timestampMillis)} - ${reasonLabel(event.reason)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${actionName(event.action)} ${"%.2f".format(event.score)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        val prefixPattern = event.maskedNumber.toTrustedPrefixPattern()
        if (manualFeedbackActions && prefixPattern != null) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { onTrustMaskedPrefix(event.maskedNumber) }, modifier = Modifier.pressFeedback()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(uiText("Fidati"), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { onBlockMaskedPrefix(event.maskedNumber) }, modifier = Modifier.pressFeedback()) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("${uiText("Blocca")} $prefixPattern", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun BlockedCallLogScreen(
    padding: PaddingValues,
    recentEvents: List<CallEvent>,
    manualFeedbackActions: Boolean,
    onTrustMaskedPrefix: (String) -> Unit,
    onBlockMaskedPrefix: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val blockedEvents = recentEvents.filter { it.action == 2 || it.action == 3 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CardPanel(borderColor = Color(0xFFFF3D3D)) {
                Text(uiText("Log chiamate filtrate"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("Solo numeri mascherati, senza dati completi in chiaro."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().pressFeedback()) {
                        Icon(Icons.Filled.Phone, contentDescription = null)
                        Text(uiText("Aggiorna"), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (blockedEvents.isEmpty()) {
            item {
                CardPanel {
                    Text(uiText("Nessuna chiamata bloccata o silenziata."), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(blockedEvents) { event ->
                CardPanel(borderColor = if (event.action == 3) Color(0xFFFF3D3D) else Color(0xFFFFB23E)) {
                    EventRow(
                        event = event,
                        manualFeedbackActions = manualFeedbackActions,
                        onTrustMaskedPrefix = onTrustMaskedPrefix,
                        onBlockMaskedPrefix = onBlockMaskedPrefix
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(roleHeld: Boolean, onEnableClick: () -> Unit) {
    val title = uiText(if (roleHeld) "Protezzione Attiva" else "Protezione da attivare")
    val body = if (roleHeld) {
        uiText("Freya controlla!")
    } else {
        uiText("Abilita FreyaShield come app di filtro chiamate per proteggere le chiamate in arrivo.")
    }

    CardPanel(
        containerColor = if (roleHeld) SafeGreenDark else Color(0xFF2A2011),
        contentColor = Color(0xFFFFF2D4),
        borderColor = if (roleHeld) SafeGreenBorder else WarningAmber
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = if (roleHeld) SafeGreen else WarningAmber
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!roleHeld) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onEnableClick, modifier = Modifier.fillMaxWidth().pressFeedback()) {
                Text(uiText("Attiva filtro chiamate"))
            }
        }
    }
}

@Composable
private fun WhitelistScreen(
    padding: PaddingValues,
    trustedPatterns: List<String>,
    blockedPatterns: List<String>,
    onAddPattern: (String) -> Unit,
    onRemovePattern: (String) -> Unit,
    onAddBlockedPattern: (String) -> Unit,
    onRemoveBlockedPattern: (String) -> Unit,
    onAddCountryBlock: (CountryBlockOption) -> Unit,
    onAddItalianAreaBlock: (String) -> Unit,
    onOpenTests: () -> Unit,
    onOpenBlockedRules: () -> Unit,
    onOpenHelp: (String) -> Unit
) {
    var newPattern by remember { mutableStateOf("") }
    var newBlockedPattern by remember { mutableStateOf("") }
    var italianAreaPrefix by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CardPanel {
                HelpableTitle("Strumenti regole", onHelpClick = { onOpenHelp("rules_tools") })
                Text(
                    "Consulta blocchi salvati o prova scenari senza chiamate reali.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenBlockedRules,
                        modifier = Modifier.weight(1f).pressFeedback()
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null)
                        Text(uiText("Elenco"), modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(
                        onClick = onOpenTests,
                        modifier = Modifier.weight(1f).pressFeedback()
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = null)
                        Text(uiText("Prove"), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            CardPanel {
                HelpableTitle("Pattern fidati", onHelpClick = { onOpenHelp("trusted_patterns") })
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newPattern,
                        onValueChange = { newPattern = it },
                        label = { Text(uiText("Numero o prefisso")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onAddPattern(newPattern.trim())
                            newPattern = ""
                        },
                        modifier = Modifier.pressFeedback()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = uiText("Aggiungi"))
                    }
                }
            }
        }

        items(trustedPatterns) { pattern ->
            CardPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pattern, style = MaterialTheme.typography.titleMedium)
                        Text(uiText("Bypass locale delle euristiche"), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onRemovePattern(pattern) }, modifier = Modifier.pressFeedback()) {
                        Icon(Icons.Filled.Delete, contentDescription = uiText("Rimuovi"))
                    }
                }
            }
        }

        item {
            CardPanel(
                borderColor = Color(0xFFFF3D3D)
            ) {
                HelpableTitle("Blocca prefissi", onHelpClick = { onOpenHelp("blocked_prefixes") })
                Spacer(Modifier.height(8.dp))
                Text(
                    "Esempio: 081 123456 blocca tutti i numeri che iniziano con quel prefisso.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newBlockedPattern,
                        onValueChange = { newBlockedPattern = it },
                        label = { Text(uiText("Prefisso da bloccare")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAddBlockedPattern(newBlockedPattern.trim())
                            newBlockedPattern = ""
                        },
                        modifier = Modifier.pressFeedback()
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null)
                        Text(uiText("Blocca"), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        item {
            CardPanel(borderColor = Color(0xFFFFB23E)) {
                HelpableTitle("Blocca aree e nazioni", onHelpClick = { onOpenHelp("country_area_blocks") })
                Spacer(Modifier.height(8.dp))
                Text(
                    "Seleziona una nazione, poi la vedrai sotto con bandierina, stato e prefisso.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                CountryBlockSelect(onAddCountryBlock)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = italianAreaPrefix,
                        onValueChange = { italianAreaPrefix = it },
                        label = { Text(uiText("Area IT es. 081")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAddItalianAreaBlock(italianAreaPrefix)
                            italianAreaPrefix = ""
                        },
                        modifier = Modifier.pressFeedback()
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null)
                        Text(uiText("Blocca"), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedRulesScreen(
    padding: PaddingValues,
    blockedPatterns: List<String>,
    onRemoveBlockedPattern: (String) -> Unit,
    onBackToRules: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CardPanel(borderColor = Color(0xFFFF3D3D)) {
                Text(uiText("Blocchi attivi"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("Prefissi, aree e nazioni bloccate localmente."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onBackToRules, modifier = Modifier.fillMaxWidth().pressFeedback()) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                        Text(uiText("Regole"), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (blockedPatterns.isEmpty()) {
            item {
                CardPanel {
                    Text(uiText("Nessun blocco salvato."), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(blockedPatterns) { pattern ->
                CardPanel(borderColor = Color(0xFFFF3D3D)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pattern.blockDisplayLabel(), style = MaterialTheme.typography.titleMedium)
                            Text(pattern.blockPatternOnly(), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onRemoveBlockedPattern(pattern) }, modifier = Modifier.pressFeedback()) {
                            Icon(Icons.Filled.Delete, contentDescription = uiText("Rimuovi blocco"))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryBlockSelect(onAddCountryBlock: (CountryBlockOption) -> Unit) {
    val countryOptions = remember { countryBlockOptions() }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(countryOptions.first()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selected.displayName(),
                onValueChange = {},
                readOnly = true,
                label = { Text(uiText("Nazione")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                countryOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName()) },
                        onClick = {
                            selected = option
                            expanded = false
                        }
                    )
                }
            }
        }
        Button(onClick = { onAddCountryBlock(selected) }, modifier = Modifier.pressFeedback()) {
            Icon(Icons.Filled.Block, contentDescription = null)
            Text(uiText("Blocca"), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun SettingsScreen(
    padding: PaddingValues,
    warnThreshold: Float,
    silenceThreshold: Float,
    blockThreshold: Float,
    blockFailedVerification: Boolean,
    warnNeighborSpoof: Boolean,
    blockFirstSeenInternational: Boolean,
    autoBlockSimilarNumbers: Boolean,
    temporaryGreylist: Boolean,
    blockUnverifiedSuspicious: Boolean,
    quietHoursFilter: Boolean,
    trustedOnlyMode: Boolean,
    manualFeedbackActions: Boolean,
    onWarnThresholdChange: (Float) -> Unit,
    onSilenceThresholdChange: (Float) -> Unit,
    onBlockThresholdChange: (Float) -> Unit,
    onBlockFailedVerificationChange: (Boolean) -> Unit,
    onWarnNeighborSpoofChange: (Boolean) -> Unit,
    onBlockFirstSeenInternationalChange: (Boolean) -> Unit,
    onAutoBlockSimilarNumbersChange: (Boolean) -> Unit,
    onTemporaryGreylistChange: (Boolean) -> Unit,
    onBlockUnverifiedSuspiciousChange: (Boolean) -> Unit,
    onQuietHoursFilterChange: (Boolean) -> Unit,
    onTrustedOnlyModeChange: (Boolean) -> Unit,
    onManualFeedbackActionsChange: (Boolean) -> Unit,
    onClearStats: () -> Unit,
    onApplyPreset: (ProtectionPreset) -> Unit,
    onExportDiagnostics: () -> Unit,
    onOpenHelp: (String) -> Unit
) {
    val presets = remember { protectionPresets() }
    val activePreset = presets.firstOrNull {
        it.policy.warnThreshold == warnThreshold &&
            it.policy.silenceThreshold == silenceThreshold &&
            it.policy.blockThreshold == blockThreshold &&
            it.policy.blockFailedVerification == blockFailedVerification &&
            it.policy.warnNeighborSpoof == warnNeighborSpoof &&
            it.policy.blockFirstSeenInternational == blockFirstSeenInternational &&
            it.policy.autoBlockSimilarNumbers == autoBlockSimilarNumbers &&
            it.policy.temporaryGreylist == temporaryGreylist &&
            it.policy.blockUnverifiedSuspicious == blockUnverifiedSuspicious &&
            it.policy.quietHoursFilter == quietHoursFilter &&
            it.policy.trustedOnlyMode == trustedOnlyMode &&
            it.policy.manualFeedbackActions == manualFeedbackActions
    }?.title

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CardPanel {
                HelpableTitle("Modalita protezione", onHelpClick = { onOpenHelp("protection_mode") })
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = activePreset == preset.title,
                            onClick = { onApplyPreset(preset) },
                            modifier = Modifier.pressFeedback(),
                            label = { Text(uiText(preset.title)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    uiText(
                        presets.firstOrNull { it.title == activePreset }?.description
                            ?: "Configurazione personalizzata."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            CardPanel {
                ThresholdSlider(
                    label = "Avviso",
                    value = warnThreshold,
                    minProtection = thresholdToProtectionLevel(silenceThreshold),
                    maxProtection = 0.90f,
                    profile = ThresholdProfile.Warn,
                    onChange = onWarnThresholdChange,
                    onHelpClick = { onOpenHelp("warn_threshold") }
                )
                ThresholdSlider(
                    label = "Silenzia",
                    value = silenceThreshold,
                    minProtection = thresholdToProtectionLevel(blockThreshold),
                    maxProtection = thresholdToProtectionLevel(warnThreshold),
                    profile = ThresholdProfile.Silence,
                    onChange = onSilenceThresholdChange,
                    onHelpClick = { onOpenHelp("silence_threshold") }
                )
                ThresholdSlider(
                    label = "Blocca",
                    value = blockThreshold,
                    minProtection = 0.05f,
                    maxProtection = thresholdToProtectionLevel(silenceThreshold),
                    profile = ThresholdProfile.Block,
                    onChange = onBlockThresholdChange,
                    onHelpClick = { onOpenHelp("block_threshold") }
                )
            }
        }
        item {
            CardPanel {
                ToggleRow(
                    title = "Blocca verifica fallita",
                    checked = blockFailedVerification,
                    onCheckedChange = onBlockFailedVerificationChange,
                    onHelpClick = { onOpenHelp("block_failed_verification") }
                )
                ToggleRow(
                    title = "Avvisa neighbor spoofing",
                    checked = warnNeighborSpoof,
                    onCheckedChange = onWarnNeighborSpoofChange,
                    onHelpClick = { onOpenHelp("neighbor_spoof") }
                )
                ToggleRow(
                    title = "Blocca estero non salvato",
                    checked = blockFirstSeenInternational,
                    onCheckedChange = onBlockFirstSeenInternationalChange,
                    onHelpClick = { onOpenHelp("first_seen_international") }
                )
                ToggleRow(
                    title = "Auto-blocca numeri simili",
                    checked = autoBlockSimilarNumbers,
                    onCheckedChange = onAutoBlockSimilarNumbersChange,
                    onHelpClick = { onOpenHelp("auto_block_similar") }
                )
                ToggleRow(
                    title = "Quarantena temporanea prefissi",
                    checked = temporaryGreylist,
                    onCheckedChange = onTemporaryGreylistChange,
                    onHelpClick = { onOpenHelp("temporary_greylist") }
                )
                ToggleRow(
                    title = "Blocca sospetti non verificati",
                    checked = blockUnverifiedSuspicious,
                    onCheckedChange = onBlockUnverifiedSuspiciousChange,
                    onHelpClick = { onOpenHelp("unverified_suspicious") }
                )
                ToggleRow(
                    title = "Filtro sera e notte",
                    checked = quietHoursFilter,
                    onCheckedChange = onQuietHoursFilterChange,
                    onHelpClick = { onOpenHelp("quiet_hours") }
                )
                ToggleRow(
                    title = "Solo regole fidate",
                    checked = trustedOnlyMode,
                    onCheckedChange = onTrustedOnlyModeChange,
                    onHelpClick = { onOpenHelp("trusted_only") }
                )
                ToggleRow(
                    title = "Azioni manuali nel log",
                    checked = manualFeedbackActions,
                    onCheckedChange = onManualFeedbackActionsChange,
                    onHelpClick = { onOpenHelp("manual_feedback") }
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onExportDiagnostics, modifier = Modifier.weight(1f).pressFeedback()) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                        Text(uiText("Esporta diagnostica sicura"), modifier = Modifier.padding(start = 8.dp))
                    }
                    HelpButton(onClick = { onOpenHelp("export_diagnostics") })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onClearStats, modifier = Modifier.weight(1f).pressFeedback()) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text(uiText("Cancella dati locali"), modifier = Modifier.padding(start = 8.dp))
                    }
                    HelpButton(onClick = { onOpenHelp("clear_local_data") })
                }
            }
        }
    }
}

@Composable
private fun HelpScreen(
    padding: PaddingValues,
    page: HelpPage,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CardPanel(borderColor = Color(0xFFFFB23E)) {
                Text("${uiText("Aiuto")}: ${uiText(page.title)}", style = MaterialTheme.typography.titleMedium)
                Text(uiText("Spiegazione dettagliata della funzione selezionata."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().pressFeedback()) {
                    Text(uiText("Indietro"))
                }
            }
        }

        items(page.topics) { topic ->
            CardPanel {
                Text(uiText(topic.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(uiText(topic.body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TestScreen(
    padding: PaddingValues,
    onBackToRules: () -> Unit,
    onTrustPattern: (String) -> Unit,
    onBlockPattern: (String) -> Unit,
    onScenarioRun: (PlatformRiskAssessment) -> Unit
) {
    val scenarios = remember { testScenarios() }
    var lastResult by remember { mutableStateOf<Pair<TestScenario, PlatformRiskAssessment>?>(null) }
    var customNumber by remember { mutableStateOf("+393331234567") }
    var customSeenBefore by remember { mutableStateOf(false) }
    var customFailedVerification by remember { mutableStateOf(false) }
    var customHighFrequency by remember { mutableStateOf(false) }
    var lastCustomResult by remember { mutableStateOf<PlatformRiskAssessment?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CardPanel {
                Text(uiText("Prove regole"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("Simulatore locale per verificare soglie, whitelist e blocchi."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onBackToRules, modifier = Modifier.fillMaxWidth().pressFeedback()) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                        Text(uiText("Regole"), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item {
            CardPanel {
                Text(uiText("Simulatore chiamata"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customNumber,
                    onValueChange = { customNumber = it },
                    label = { Text(uiText("Numero da testare")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ToggleRow(
                    title = "Gia visto",
                    checked = customSeenBefore,
                    onCheckedChange = { customSeenBefore = it }
                )
                ToggleRow(
                    title = "Verifica fallita",
                    checked = customFailedVerification,
                    onCheckedChange = { customFailedVerification = it }
                )
                ToggleRow(
                    title = "Frequenza alta",
                    checked = customHighFrequency,
                    onCheckedChange = { customHighFrequency = it }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val result = CoreEngineBridge.analyzeCall(
                            customCallInfo(
                                number = customNumber,
                                seenBefore = customSeenBefore,
                                failedVerification = customFailedVerification,
                                highFrequency = customHighFrequency
                            )
                        )
                        lastCustomResult = result
                        onScenarioRun(result)
                    },
                    modifier = Modifier.fillMaxWidth().pressFeedback()
                ) {
                    Icon(Icons.Filled.BugReport, contentDescription = null)
                    Text(uiText("Analizza"), modifier = Modifier.padding(start = 8.dp))
                }
                lastCustomResult?.let { result ->
                    Spacer(Modifier.height(12.dp))
                    MetricRow("Azione", actionName(result.action))
                    MetricRow("Score", "%.2f".format(result.score))
                    MetricRow("Motivo", reasonLabel(result.primaryReason))
                    val exactPattern = customNumber.trim()
                    if (exactPattern.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onTrustPattern(exactPattern) },
                            modifier = Modifier.fillMaxWidth().pressFeedback()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(uiText("Fidati di questo numero"), modifier = Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(
                            onClick = { onBlockPattern(exactPattern) },
                            modifier = Modifier.fillMaxWidth().pressFeedback()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text(uiText("Blocca numeri simili"), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        item {
            CardPanel {
                Text(uiText("Scenari rapidi"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(uiText("Test locali senza chiamate reali e senza salvare numeri."), style = MaterialTheme.typography.bodyMedium)
            }
        }

        lastResult?.let { (scenario, result) ->
            item {
                CardPanel {
                    Text(uiText(scenario.title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    MetricRow("Azione", actionName(result.action))
                    MetricRow("Score", "%.2f".format(result.score))
                    MetricRow("Motivo", reasonLabel(result.primaryReason))
                    if (result.explanation.isNotBlank()) {
                        MetricRow("Segnali", signalLabels(result.explanation))
                    }
                }
            }
        }

        items(scenarios) { scenario ->
            CardPanel {
                Text(uiText(scenario.title), style = MaterialTheme.typography.titleMedium)
                Text(uiText(scenario.description), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val result = CoreEngineBridge.analyzeCall(scenario.callInfo)
                        lastResult = scenario to result
                        onScenarioRun(result)
                    },
                    modifier = Modifier.fillMaxWidth().pressFeedback()
                ) {
                    Icon(Icons.Filled.BugReport, contentDescription = null)
                    Text(uiText("Esegui test"), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    minProtection: Float,
    maxProtection: Float,
    profile: ThresholdProfile,
    onChange: (Float) -> Unit,
    onHelpClick: () -> Unit
) {
    val rangeStart = minOf(minProtection, maxProtection).coerceIn(0.05f, 0.90f)
    val rangeEnd = maxOf(minProtection, maxProtection).coerceIn(rangeStart + 0.01f, 0.90f)
    val protectionLevel = thresholdToProtectionLevel(value).coerceIn(rangeStart, rangeEnd)
    HelpableTitle(label, onHelpClick = onHelpClick)
    Text(
        thresholdMessage(profile, protectionLevel),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        thresholdHint(profile, protectionLevel),
        style = MaterialTheme.typography.bodySmall
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(uiText("Meno interventi"), style = MaterialTheme.typography.bodySmall)
        Text(uiText("Piu protezione"), style = MaterialTheme.typography.bodySmall)
    }
    Slider(
        value = protectionLevel,
        onValueChange = { onChange(protectionLevelToThreshold(it)) },
        valueRange = rangeStart..rangeEnd
    )
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onHelpClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(uiText(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (onHelpClick != null) {
            HelpButton(onClick = onHelpClick)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HelpableTitle(title: String, onHelpClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(uiText(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        HelpButton(onClick = onHelpClick)
    }
}

@Composable
private fun HelpButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .pressFeedback()
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Help,
            contentDescription = uiText("Spiega questa funzione"),
            modifier = Modifier.size(26.dp)
        )
    }
}

private fun thresholdToProtectionLevel(threshold: Float): Float {
    return (1.0f - threshold).coerceIn(0.05f, 0.90f)
}

private fun protectionLevelToThreshold(level: Float): Float {
    return (1.0f - level).coerceIn(0.10f, 0.95f)
}

private fun thresholdBand(level: Float): Int {
    return when {
        level < 0.25f -> 0
        level < 0.45f -> 1
        level < 0.68f -> 2
        else -> 3
    }
}

private fun thresholdMessage(profile: ThresholdProfile, level: Float): String {
    val band = thresholdBand(level)
    val source = when (profile) {
        ThresholdProfile.Warn -> when (band) {
            0 -> "Avvisa solo se il rischio e chiaro"
            1 -> "Avviso prudente"
            2 -> "Avviso equilibrato"
            else -> "Avvisa appena nota qualcosa"
        }
        ThresholdProfile.Silence -> when (band) {
            0 -> "Silenzia solo chiamate molto sospette"
            1 -> "Silenzia con cautela"
            2 -> "Silenzia con buon equilibrio"
            else -> "Silenzia molti sospetti"
        }
        ThresholdProfile.Block -> when (band) {
            0 -> "Blocca solo rischi evidenti"
            1 -> "Blocca con prudenza"
            2 -> "Blocca quando il rischio e forte"
            else -> "Blocca in modo deciso"
        }
    }
    return localizeText(source)
}

private fun thresholdHint(profile: ThresholdProfile, level: Float): String {
    val direction = if (level >= 0.68f) {
        "Protezione alta, piu possibilita di falsi positivi."
    } else if (level < 0.25f) {
        "Meno interventi automatici, piu chiamate lasciate passare."
    } else {
        "Compromesso tra protezione e chiamate legittime."
    }

    val source = when (profile) {
        ThresholdProfile.Warn -> "$direction Decide quanto presto segnalare una chiamata."
        ThresholdProfile.Silence -> "$direction Decide quanto presto togliere lo squillo."
        ThresholdProfile.Block -> "$direction Decide quanto presto respingere la chiamata."
    }
    return localizeText(source)
}

@Composable
private fun CardPanel(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = Color(0xFF3B3138),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(uiText(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.45f))
        Text(
            uiText(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.55f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CallGuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF3D3D),
            secondary = Color(0xFFFFB23E),
            tertiary = Color(0xFF27D7A2),
            surface = Color(0xFF201820),
            onSurface = Color(0xFFFFF2D4),
            background = Color(0xFF0D0B10),
            onBackground = Color(0xFFFFF2D4)
        )
    ) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color(0xFF0D0B10),
    selectedTextColor = Color(0xFFFFE8B6),
    indicatorColor = Color(0xFFFF3D3D),
    unselectedIconColor = Color(0xFF9F8E92),
    unselectedTextColor = Color(0xFF9F8E92)
)

private fun Modifier.pressFeedback(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        label = "pressFeedback"
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pressed = true
            waitForUpOrCancellation()
            pressed = false
        }
    }
}

private fun isCallScreeningRoleHeld(context: Context): Boolean {
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

private fun requestCallScreeningRole(context: Context): Intent? {
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
    return if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
        !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    ) {
        roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    } else {
        null
    }
}

private fun protectionPresets(): List<ProtectionPreset> {
    return listOf(
        ProtectionPreset(
            title = "Bassa",
            description = "Avvisa spesso, blocca solo segnali molto forti.",
            policy = PolicySettings(
                warnThreshold = 0.30f,
                silenceThreshold = 0.62f,
                blockThreshold = 0.82f,
                blockFailedVerification = false,
                warnNeighborSpoof = true,
                blockFirstSeenInternational = false
            )
        ),
        ProtectionPreset(
            title = "Standard",
            description = "Equilibrio tra protezione, silenziamento e falsi positivi.",
            policy = PolicySettings()
        ),
        ProtectionPreset(
            title = "Alta",
            description = "Piu severa su verifica fallita, spoofing vicino e numeri esteri nuovi.",
            policy = PolicySettings(
                warnThreshold = 0.25f,
                silenceThreshold = 0.42f,
                blockThreshold = 0.58f,
                blockFailedVerification = true,
                warnNeighborSpoof = true,
                blockFirstSeenInternational = true
            )
        ),
        ProtectionPreset(
            title = "Call center",
            description = "Protezione avanzata contro prefissi ripetuti, numeri non verificati e chiamate serali.",
            policy = PolicySettings(
                warnThreshold = 0.22f,
                silenceThreshold = 0.38f,
                blockThreshold = 0.55f,
                blockFailedVerification = true,
                warnNeighborSpoof = true,
                blockHighFrequencyRobocall = true,
                blockFirstSeenInternational = true,
                autoBlockSimilarNumbers = true,
                temporaryGreylist = true,
                blockUnverifiedSuspicious = true,
                quietHoursFilter = true,
                trustedOnlyMode = false,
                manualFeedbackActions = true
            )
        )
    )
}

private fun helpTitle(screen: AppScreen): String {
    return when (screen) {
        AppScreen.Dashboard -> "Protezione"
        AppScreen.Whitelist -> "Regole"
        AppScreen.Settings -> "Configurazioni"
        AppScreen.Log -> "Log"
        AppScreen.BlockedRules -> "Blocchi attivi"
        AppScreen.Test -> "Prove regole"
        AppScreen.Help -> "Aiuto"
    }
}

private fun helpPageForScreen(screen: AppScreen): HelpPage {
    return HelpPage(helpTitle(screen), helpTopics(screen))
}

private fun helpPageForSetting(key: String): HelpPage {
    return when (key) {
        "protection_mode" -> HelpPage(
            "Modalita protezione",
            listOf(
                HelpTopic("Cosa controlla", "I preset modificano piu impostazioni insieme. Bassa riduce i blocchi automatici, Standard mantiene un equilibrio, Alta aumenta la severita, Call center applica controlli piu rigorosi contro chiamate ripetute e non verificate."),
                HelpTopic("Benefici", "Ti permette di cambiare rapidamente comportamento senza regolare ogni singolo livello. Call center e utile quando ricevi molte chiamate pubblicitarie o numeri molto simili."),
                HelpTopic("Effetti collaterali", "Preset piu aggressivi possono bloccare chiamate legittime, soprattutto numeri nuovi, centralini aziendali, corrieri o servizi che usano prefissi condivisi.")
            )
        )
        "warn_threshold" -> HelpPage(
            "Livello Avviso",
            listOf(
                HelpTopic("Cosa fa", "Decide quanto presto FreyaShield deve considerare una chiamata sospetta. Spostando il cursore verso piu protezione, l'app avvisa prima anche con segnali piu leggeri."),
                HelpTopic("Benefici", "Ti aiuta a notare subito numeri mai visti, pattern strani o comportamenti debolmente sospetti senza arrivare per forza al silenzio o al blocco."),
                HelpTopic("Effetti collaterali", "Se lo rendi molto sensibile, piu chiamate legittime potranno apparire come sospette. Non vengono necessariamente bloccate, ma il registro risultera piu prudente.")
            )
        )
        "silence_threshold" -> HelpPage(
            "Livello Silenzia",
            listOf(
                HelpTopic("Cosa fa", "Decide quanto presto FreyaShield deve togliere lo squillo a una chiamata sospetta. Verso piu protezione, silenzia con segnali meno pesanti."),
                HelpTopic("Benefici", "Riduce il fastidio dei call center senza respingere subito la chiamata. E la via di mezzo piu comoda quando vuoi meno disturbo ma non vuoi bloccare troppo."),
                HelpTopic("Effetti collaterali", "Se lo rendi molto protettivo, potresti non sentire chiamate nuove ma legittime. Metti in whitelist i numeri importanti.")
            )
        )
        "block_threshold" -> HelpPage(
            "Livello Blocca",
            listOf(
                HelpTopic("Cosa fa", "Decide quanto presto FreyaShield deve respingere una chiamata. Verso piu protezione, il blocco scatta con meno tolleranza al rischio."),
                HelpTopic("Benefici", "Ferma automaticamente chiamate con segnali forti, come verifica fallita, prefissi insistenti, blocklist o combinazioni molto sospette."),
                HelpTopic("Effetti collaterali", "Se lo rendi troppo protettivo, alcuni numeri nuovi ma legittimi potrebbero essere bloccati prima che tu li veda.")
            )
        )
        "block_failed_verification" -> HelpPage(
            "Blocca verifica fallita",
            listOf(
                HelpTopic("Cosa fa", "Usa il controllo ID chiamata fornito da Android e dall'operatore. Se il sistema segnala verifica fallita, FreyaShield porta la chiamata direttamente nell'area di blocco."),
                HelpTopic("Benefici", "E una delle difese piu forti contro lo spoofing, cioe chiamate che mostrano un numero non realmente autorizzato dal chiamante."),
                HelpTopic("Effetti collaterali", "Non tutti gli operatori e centralini gestiscono la verifica allo stesso modo. In casi rari, centralini aziendali o servizi VoIP legittimi potrebbero risultare non affidabili.")
            )
        )
        "neighbor_spoof" -> HelpPage(
            "Avvisa neighbor spoofing",
            listOf(
                HelpTopic("Cosa fa", "Rileva numeri che condividono molte cifre iniziali con il tuo numero o prefisso, tecnica usata per sembrare locali o familiari."),
                HelpTopic("Benefici", "Aiuta contro chiamate truffa che imitano numeri della tua zona o del tuo stesso prefisso mobile."),
                HelpTopic("Effetti collaterali", "In aree con molti numeri simili o centralini legittimi, puo aumentare il rischio di chiamate innocue. La whitelist risolve i casi affidabili.")
            )
        )
        "first_seen_international" -> HelpPage(
            "Blocca estero non salvato",
            listOf(
                HelpTopic("Cosa fa", "Aumenta molto il rischio dei numeri internazionali mai visti prima quando l'utente e configurato su Italia."),
                HelpTopic("Benefici", "Riduce molte campagne spam o truffe che arrivano da prefissi esteri inattesi."),
                HelpTopic("Effetti collaterali", "Se ricevi chiamate legittime dall'estero, da clienti o da servizi internazionali, potresti dover aggiungere whitelist mirate.")
            )
        )
        "auto_block_similar" -> HelpPage(
            "Auto-blocca numeri simili",
            listOf(
                HelpTopic("Cosa fa", "Memorizza localmente, tramite hash, quante chiamate arrivano dallo stesso numero o prefisso in finestre brevi. Se un prefisso insiste e la chiamata e gia sospetta, forza il blocco."),
                HelpTopic("Benefici", "E efficace contro call center che ruotano le ultime cifre del numero per aggirare i blocchi manuali."),
                HelpTopic("Effetti collaterali", "Puo colpire centralini legittimi che chiamano piu volte da interni simili. In quel caso aggiungi il prefisso alla whitelist o disattiva l'opzione.")
            )
        )
        "temporary_greylist" -> HelpPage(
            "Quarantena temporanea prefissi",
            listOf(
                HelpTopic("Cosa fa", "Quando un prefisso genera piu eventi filtrati, viene messo in quarantena per 7 giorni. La memoria usa hash locali e non conserva numeri completi."),
                HelpTopic("Benefici", "Blocca automaticamente raffiche temporanee senza creare una regola permanente che potresti dimenticare."),
                HelpTopic("Effetti collaterali", "Durante la quarantena possono essere bloccate chiamate legittime dallo stesso gruppo di numeri. La cancellazione dati locali rimuove anche questa memoria.")
            )
        )
        "unverified_suspicious" -> HelpPage(
            "Blocca sospetti non verificati",
            listOf(
                HelpTopic("Cosa fa", "Se una chiamata non ha verifica positiva e ha gia raggiunto almeno il livello Avviso, viene portata direttamente al blocco."),
                HelpTopic("Benefici", "Stringe la difesa contro chiamate opache: non basta essere sconosciuti, ma sconosciuti piu un altro segnale sospetto."),
                HelpTopic("Effetti collaterali", "Alcuni operatori non forniscono sempre verifica positiva. Potrebbe quindi bloccare chiamate nuove ma lecite se hanno altri segnali deboli.")
            )
        )
        "quiet_hours" -> HelpPage(
            "Filtro sera e notte",
            listOf(
                HelpTopic("Cosa fa", "Dalle 20 alle 8 silenzia chiamate sospette e non verificate. Le chiamate fidate continuano a passare."),
                HelpTopic("Benefici", "Riduce il disturbo nelle ore piu fastidiose senza applicare per forza blocchi permanenti."),
                HelpTopic("Effetti collaterali", "Potresti non sentire chiamate legittime urgenti da numeri nuovi. Usa whitelist per familiari, lavoro, medici, corrieri o servizi importanti.")
            )
        )
        "trusted_only" -> HelpPage(
            "Solo regole fidate",
            listOf(
                HelpTopic("Cosa fa", "Consente solo numeri o prefissi presenti nelle regole fidate. Tutto il resto viene bloccato prima delle valutazioni normali."),
                HelpTopic("Benefici", "E la modalita piu restrittiva quando vuoi ridurre al minimo le chiamate non previste."),
                HelpTopic("Effetti collaterali", "Blocca quasi tutto cio che non hai previsto. Non e consigliata se aspetti chiamate da numeri nuovi, uffici pubblici, consegne, assistenza o appuntamenti.")
            )
        )
        "manual_feedback" -> HelpPage(
            "Azioni manuali nel log",
            listOf(
                HelpTopic("Cosa fa", "Mostra nel registro i pulsanti Fidati e Blocca per trasformare rapidamente un prefisso mascherato in regola."),
                HelpTopic("Benefici", "Ti permette di correggere il filtro mentre lo usi: se sbaglia, puoi fidarti; se riconosci spam, puoi bloccare simili."),
                HelpTopic("Effetti collaterali", "Le azioni partono dal prefisso visibile nel numero mascherato, quindi sono volutamente ampie. Controlla l'elenco blocchi se una regola diventa troppo severa.")
            )
        )
        "export_diagnostics" -> HelpPage(
            "Esporta diagnostica sicura",
            listOf(
                HelpTopic("Cosa fa", "Crea un testo condivisibile con statistiche, ultimo stato servizio e registro mascherato. Non include numeri completi."),
                HelpTopic("Benefici", "Aiuta a capire cosa sta decidendo l'app o a chiedere supporto senza esporre dati sensibili."),
                HelpTopic("Effetti collaterali", "Contiene comunque orari, motivi e numeri mascherati. Condividilo solo con persone o canali di cui ti fidi.")
            )
        )
        "clear_local_data" -> HelpPage(
            "Cancella dati locali",
            listOf(
                HelpTopic("Cosa fa", "Rimuove statistiche, registro mascherato, diagnostica servizio e memoria comportamentale usata da auto-blocco e quarantena."),
                HelpTopic("Benefici", "Ripulisce lo storico e azzera eventuali quarantene o conteggi che stavano influenzando le decisioni."),
                HelpTopic("Effetti collaterali", "Dopo la cancellazione l'app perde memoria dei comportamenti recenti. Auto-blocco e quarantena dovranno ricostruire i conteggi dalle chiamate future.")
            )
        )
        "rules_tools" -> HelpPage(
            "Strumenti regole",
            listOf(
                HelpTopic("Cosa fa", "Raccoglie gli accessi rapidi all'elenco dei blocchi e al simulatore. Non modifica da solo il filtro, ma ti porta agli strumenti per controllarlo."),
                HelpTopic("Benefici", "Ti permette di verificare subito quali blocchi sono attivi e provare scenari senza aspettare una chiamata reale."),
                HelpTopic("Effetti collaterali", "Le prove possono creare regole se premi Fidati o Blocca. Controlla sempre il pattern creato, soprattutto quando contiene l'asterisco.")
            )
        )
        "trusted_patterns" -> HelpPage(
            "Pattern fidati",
            listOf(
                HelpTopic("Cosa fa", "Aggiunge numeri o prefissi alla whitelist. Un pattern fidato ha priorita sulle euristiche e permette alla chiamata di passare."),
                HelpTopic("Benefici", "Riduce falsi positivi su famiglia, lavoro, medici, corrieri, clienti o centralini che il filtro potrebbe considerare sospetti."),
                HelpTopic("Effetti collaterali", "Un prefisso troppo ampio, come +39*, renderebbe il filtro quasi inutile per molti numeri. Usa pattern specifici, per esempio un numero completo o un prefisso ben riconoscibile.")
            )
        )
        "blocked_prefixes" -> HelpPage(
            "Blocca prefissi",
            listOf(
                HelpTopic("Cosa fa", "Crea una regola blocklist con asterisco finale. Se inserisci 081123, il filtro blocca i numeri che iniziano con quel prefisso normalizzato."),
                HelpTopic("Benefici", "E molto efficace contro call center che cambiano solo le ultime cifre mantenendo lo stesso inizio."),
                HelpTopic("Effetti collaterali", "Un prefisso troppo corto puo bloccare molte chiamate legittime della stessa area o dello stesso centralino. Se succede, rimuovi la regola da Blocchi attivi.")
            )
        )
        "country_area_blocks" -> HelpPage(
            "Blocca aree e nazioni",
            listOf(
                HelpTopic("Cosa fa", "Aggiunge alla blocklist un prefisso internazionale o un prefisso geografico italiano. Le chiamate che iniziano con quel codice vengono bloccate."),
                HelpTopic("Benefici", "Utile se ricevi spam sistematico da paesi o aree da cui non aspetti chiamate."),
                HelpTopic("Effetti collaterali", "Bloccare un'intera nazione o area e una scelta ampia. Puo fermare anche banche, assistenza, spedizioni o contatti legittimi che usano quel prefisso.")
            )
        )
        else -> helpPageForScreen(AppScreen.Settings)
    }
}

private fun helpTopics(screen: AppScreen): List<HelpTopic> {
    return when (screen) {
        AppScreen.Dashboard -> listOf(
            HelpTopic(
                "Stato protezione",
                "La schermata Protezione mostra se Android ha assegnato a FreyaShield il ruolo di filtro chiamate. Senza questo ruolo l'app puo salvare regole e configurazioni, ma non puo intervenire sulle chiamate in arrivo."
            ),
            HelpTopic(
                "Analisi core",
                "Il riquadro Analisi core esegue una prova locale del motore C++. Serve a verificare che JNI, policy, whitelist e blocklist siano caricate correttamente. Score indica il rischio da 0 a 1; Azione indica Consenti, Avvisa, Silenzia o Blocca."
            ),
            HelpTopic(
                "Statistiche e registro recente",
                "Gli ultimi 7 giorni mostrano solo conteggi aggregati e motivi principali. Il registro recente usa numeri mascherati, cosi puoi vedere cosa e successo senza salvare numeri completi in chiaro."
            ),
            HelpTopic(
                "Azioni rapide",
                "Se le azioni manuali sono attive, dai numeri mascherati puoi fidarti di un prefisso o bloccarne uno simile. La regola generata viene salvata localmente e applicata subito al motore."
            )
        )

        AppScreen.Whitelist -> listOf(
            HelpTopic(
                "Pattern fidati",
                "I pattern fidati sono eccezioni: un numero o prefisso in whitelist passa anche se somiglia a una chiamata sospetta. Puoi inserire un numero completo o un prefisso con asterisco, per esempio +39347*."
            ),
            HelpTopic(
                "Blocca prefissi",
                "Il blocco prefissi serve contro call center che cambiano solo le ultime cifre. Inserendo un prefisso, l'app crea una regola con asterisco e blocca tutti i numeri che iniziano allo stesso modo."
            ),
            HelpTopic(
                "Blocca aree e nazioni",
                "Puoi bloccare prefissi geografici italiani o prefissi internazionali. Questa funzione e utile quando ricevi chiamate ripetute da aree o paesi da cui non aspetti contatti legittimi."
            ),
            HelpTopic(
                "Strumenti regole",
                "Da qui puoi aprire l'elenco dei blocchi attivi o il simulatore. L'elenco serve per rimuovere regole troppo aggressive; il simulatore serve per provare una chiamata senza riceverla davvero."
            )
        )

        AppScreen.Settings -> listOf(
            HelpTopic(
                "Modalita protezione",
                "Bassa riduce i falsi positivi, Standard mantiene equilibrio, Alta aumenta la severita, Call center attiva una configurazione piu rigorosa contro numeri ripetuti, prefissi simili, chiamate non verificate e chiamate serali sospette."
            ),
            HelpTopic(
                "Livelli di intervento",
                "I tre cursori non mostrano numeri tecnici: indicano quanto presto l'app deve reagire. Verso piu protezione, FreyaShield avvisa, silenzia o blocca con meno tolleranza al rischio."
            ),
            HelpTopic(
                "Controllo ID chiamata",
                "Quando Android fornisce la verifica chiamante, FreyaShield la usa come segnale. Verifica superata riduce il rischio; verifica fallita e uno dei segnali piu forti e puo causare blocco immediato."
            ),
            HelpTopic(
                "Neighbor spoofing",
                "Rileva numeri che imitano le prime cifre del tuo numero o del tuo prefisso per sembrare familiari. L'opzione Avvisa neighbor spoofing decide se questo segnale deve pesare nella valutazione."
            ),
            HelpTopic(
                "Auto-blocco simili",
                "Conta localmente chiamate ravvicinate dallo stesso numero o dallo stesso prefisso usando hash, non numeri completi. Se un gruppo di numeri diventa insistente, puo essere bloccato automaticamente."
            ),
            HelpTopic(
                "Quarantena temporanea",
                "Se un prefisso genera piu eventi filtrati, viene messo in quarantena per 7 giorni. Durante quel periodo le chiamate successive da quel gruppo vengono bloccate, poi la quarantena scade da sola."
            ),
            HelpTopic(
                "Sospetti non verificati",
                "Blocca chiamate che non hanno verifica positiva e che hanno gia altri segnali di rischio. E utile contro call center e spoofing, ma puo essere severa con numeri legittimi non verificati dall'operatore."
            ),
            HelpTopic(
                "Filtro sera e notte",
                "Dalle 20 alle 8 silenzia chiamate sospette e non verificate. Le regole fidate continuano a passare, quindi conviene mettere in whitelist i prefissi importanti."
            ),
            HelpTopic(
                "Solo regole fidate",
                "Modalita restrittiva: lascia passare solo whitelist e pattern fidati. E pensata per periodi con molte chiamate indesiderate, non come uso quotidiano se ricevi spesso chiamate nuove ma legittime."
            ),
            HelpTopic(
                "Diagnostica e cancellazione",
                "Esporta diagnostica sicura crea un report senza numeri completi. Cancella dati locali rimuove statistiche, log mascherati, diagnostica e memoria comportamentale usata da auto-blocco e quarantena."
            )
        )

        AppScreen.Log -> listOf(
            HelpTopic(
                "Cosa mostra",
                "Il Log mostra chiamate bloccate o silenziate, con numero mascherato, azione, score e motivo. Non e pensato come registro telefonico completo, ma come traccia privacy-safe delle decisioni del filtro."
            ),
            HelpTopic(
                "Fidati",
                "Il pulsante Fidati crea una regola whitelist partendo dal prefisso mascherato disponibile. Usalo quando il filtro e stato troppo severo o quando riconosci un gruppo di numeri legittimo."
            ),
            HelpTopic(
                "Blocca simili",
                "Il pulsante Blocca crea una regola di blocco per il prefisso visibile. E utile quando un call center cambia le ultime cifre ma mantiene lo stesso inizio del numero."
            ),
            HelpTopic(
                "Aggiorna",
                "Aggiorna ricarica gli eventi salvati. Se hai appena ricevuto una chiamata filtrata e non la vedi, torna al Log e aggiorna."
            )
        )

        AppScreen.BlockedRules -> listOf(
            HelpTopic(
                "Elenco blocchi",
                "Questa pagina mostra prefissi, aree e nazioni bloccate localmente. Ogni voce indica l'etichetta leggibile e il pattern reale usato dal motore."
            ),
            HelpTopic(
                "Rimozione",
                "Se una regola blocca troppo, usa il cestino per rimuoverla. La modifica viene applicata subito e le chiamate future non useranno piu quel blocco."
            ),
            HelpTopic(
                "Priorita",
                "La whitelist ha precedenza sui segnali sospetti, mentre la blocklist forza il blocco quando un numero combacia con il pattern. Mantieni poche regole ma precise."
            )
        )

        AppScreen.Test -> listOf(
            HelpTopic(
                "Simulatore chiamata",
                "Inserisci un numero e scegli se simularlo come gia visto, con verifica fallita o ad alta frequenza. L'analisi usa lo stesso motore della chiamata reale, senza salvare la prova come evento telefonico."
            ),
            HelpTopic(
                "Scenari rapidi",
                "Gli scenari predefiniti mostrano casi tipici: chiamata normale, verifica fallita, neighbor spoofing, alta frequenza, whitelist, pattern artificiale e spam estero."
            ),
            HelpTopic(
                "Creare regole dalla prova",
                "Dopo un test puoi fidarti del numero o bloccare numeri simili. E un modo rapido per verificare una configurazione prima di usarla su chiamate reali."
            )
        )

        AppScreen.Help -> listOf(
            HelpTopic(
                "Aiuto contestuale",
                "Il pulsante ? nella barra superiore apre questa pagina con contenuti diversi in base alla schermata da cui arrivi. Usa Indietro per tornare alla pagina precedente."
            )
        )
    }
}

private fun countryBlockOptions(): List<CountryBlockOption> {
    return listOf(
        CountryBlockOption("🇦🇫", "Afghanistan", "+93"),
        CountryBlockOption("🇦🇱", "Albania", "+355"),
        CountryBlockOption("🇩🇿", "Algeria", "+213"),
        CountryBlockOption("🇦🇸", "Samoa Am.", "+1684"),
        CountryBlockOption("🇦🇩", "Andorra", "+376"),
        CountryBlockOption("🇦🇴", "Angola", "+244"),
        CountryBlockOption("🇦🇮", "Anguilla", "+1264"),
        CountryBlockOption("🇦🇬", "Antigua", "+1268"),
        CountryBlockOption("🇦🇷", "Argentina", "+54"),
        CountryBlockOption("🇦🇲", "Armenia", "+374"),
        CountryBlockOption("🇦🇼", "Aruba", "+297"),
        CountryBlockOption("🇦🇺", "Australia", "+61"),
        CountryBlockOption("🇦🇹", "Austria", "+43"),
        CountryBlockOption("🇦🇿", "Azerbaigian", "+994"),
        CountryBlockOption("🇧🇸", "Bahamas", "+1242"),
        CountryBlockOption("🇧🇭", "Bahrein", "+973"),
        CountryBlockOption("🇧🇩", "Bangladesh", "+880"),
        CountryBlockOption("🇧🇧", "Barbados", "+1246"),
        CountryBlockOption("🇧🇾", "Bielorussia", "+375"),
        CountryBlockOption("🇧🇪", "Belgio", "+32"),
        CountryBlockOption("🇧🇿", "Belize", "+501"),
        CountryBlockOption("🇧🇯", "Benin", "+229"),
        CountryBlockOption("🇧🇲", "Bermuda", "+1441"),
        CountryBlockOption("🇧🇹", "Bhutan", "+975"),
        CountryBlockOption("🇧🇴", "Bolivia", "+591"),
        CountryBlockOption("🇧🇦", "Bosnia", "+387"),
        CountryBlockOption("🇧🇼", "Botswana", "+267"),
        CountryBlockOption("🇧🇷", "Brasile", "+55"),
        CountryBlockOption("🇮🇴", "Terr. Br. IO", "+246"),
        CountryBlockOption("🇻🇬", "Vergini UK", "+1284"),
        CountryBlockOption("🇧🇳", "Brunei", "+673"),
        CountryBlockOption("🇧🇬", "Bulgaria", "+359"),
        CountryBlockOption("🇧🇫", "Burkina Faso", "+226"),
        CountryBlockOption("🇧🇮", "Burundi", "+257"),
        CountryBlockOption("🇰🇭", "Cambogia", "+855"),
        CountryBlockOption("🇨🇲", "Camerun", "+237"),
        CountryBlockOption("🇨🇦", "Canada", "+1"),
        CountryBlockOption("🇨🇻", "Capo Verde", "+238"),
        CountryBlockOption("🇰🇾", "Cayman", "+1345"),
        CountryBlockOption("🇨🇫", "Centrafrica", "+236"),
        CountryBlockOption("🇹🇩", "Ciad", "+235"),
        CountryBlockOption("🇨🇱", "Cile", "+56"),
        CountryBlockOption("🇨🇳", "Cina", "+86"),
        CountryBlockOption("🇨🇽", "Christmas", "+61"),
        CountryBlockOption("🇨🇨", "Cocos", "+61"),
        CountryBlockOption("🇨🇴", "Colombia", "+57"),
        CountryBlockOption("🇰🇲", "Comore", "+269"),
        CountryBlockOption("🇨🇬", "Congo", "+242"),
        CountryBlockOption("🇨🇩", "Congo RD", "+243"),
        CountryBlockOption("🇨🇰", "Cook", "+682"),
        CountryBlockOption("🇨🇷", "Costa Rica", "+506"),
        CountryBlockOption("🇭🇷", "Croazia", "+385"),
        CountryBlockOption("🇨🇺", "Cuba", "+53"),
        CountryBlockOption("🇨🇼", "Curacao", "+599"),
        CountryBlockOption("🇨🇾", "Cipro", "+357"),
        CountryBlockOption("🇨🇿", "Cechia", "+420"),
        CountryBlockOption("🇩🇰", "Danimarca", "+45"),
        CountryBlockOption("🇩🇯", "Gibuti", "+253"),
        CountryBlockOption("🇩🇲", "Dominica", "+1767"),
        CountryBlockOption("🇩🇴", "Rep. Dom.", "+1809"),
        CountryBlockOption("🇩🇴", "Rep. Dom.", "+1829"),
        CountryBlockOption("🇩🇴", "Rep. Dom.", "+1849"),
        CountryBlockOption("🇪🇨", "Ecuador", "+593"),
        CountryBlockOption("🇪🇬", "Egitto", "+20"),
        CountryBlockOption("🇸🇻", "El Salvador", "+503"),
        CountryBlockOption("🇬🇶", "Guinea Eq.", "+240"),
        CountryBlockOption("🇪🇷", "Eritrea", "+291"),
        CountryBlockOption("🇪🇪", "Estonia", "+372"),
        CountryBlockOption("🇸🇿", "Eswatini", "+268"),
        CountryBlockOption("🇪🇹", "Etiopia", "+251"),
        CountryBlockOption("🇫🇰", "Falkland", "+500"),
        CountryBlockOption("🇫🇴", "Faroe", "+298"),
        CountryBlockOption("🇫🇯", "Fiji", "+679"),
        CountryBlockOption("🇫🇮", "Finlandia", "+358"),
        CountryBlockOption("🇫🇷", "Francia", "+33"),
        CountryBlockOption("🇬🇫", "Guyana Fr.", "+594"),
        CountryBlockOption("🇵🇫", "Polinesia Fr.", "+689"),
        CountryBlockOption("🇬🇦", "Gabon", "+241"),
        CountryBlockOption("🇬🇲", "Gambia", "+220"),
        CountryBlockOption("🇬🇪", "Georgia", "+995"),
        CountryBlockOption("🇩🇪", "Germania", "+49"),
        CountryBlockOption("🇬🇭", "Ghana", "+233"),
        CountryBlockOption("🇬🇮", "Gibilterra", "+350"),
        CountryBlockOption("🇬🇷", "Grecia", "+30"),
        CountryBlockOption("🇬🇱", "Groenlandia", "+299"),
        CountryBlockOption("🇬🇩", "Grenada", "+1473"),
        CountryBlockOption("🇬🇵", "Guadalupa", "+590"),
        CountryBlockOption("🇬🇺", "Guam", "+1671"),
        CountryBlockOption("🇬🇹", "Guatemala", "+502"),
        CountryBlockOption("🇬🇬", "Guernsey", "+44"),
        CountryBlockOption("🇬🇳", "Guinea", "+224"),
        CountryBlockOption("🇬🇼", "Guinea-Bissau", "+245"),
        CountryBlockOption("🇬🇾", "Guyana", "+592"),
        CountryBlockOption("🇭🇹", "Haiti", "+509"),
        CountryBlockOption("🇭🇳", "Honduras", "+504"),
        CountryBlockOption("🇭🇰", "Hong Kong", "+852"),
        CountryBlockOption("🇭🇺", "Ungheria", "+36"),
        CountryBlockOption("🇮🇸", "Islanda", "+354"),
        CountryBlockOption("🇮🇳", "India", "+91"),
        CountryBlockOption("🇮🇩", "Indonesia", "+62"),
        CountryBlockOption("🇮🇷", "Iran", "+98"),
        CountryBlockOption("🇮🇶", "Iraq", "+964"),
        CountryBlockOption("🇮🇪", "Irlanda", "+353"),
        CountryBlockOption("🇮🇲", "Isola Man", "+44"),
        CountryBlockOption("🇮🇱", "Israele", "+972"),
        CountryBlockOption("🇮🇹", "Italia", "+39"),
        CountryBlockOption("🇨🇮", "Costa Avorio", "+225"),
        CountryBlockOption("🇯🇲", "Giamaica", "+1876"),
        CountryBlockOption("🇯🇵", "Giappone", "+81"),
        CountryBlockOption("🇯🇪", "Jersey", "+44"),
        CountryBlockOption("🇯🇴", "Giordania", "+962"),
        CountryBlockOption("🇰🇿", "Kazakistan", "+7"),
        CountryBlockOption("🇰🇪", "Kenya", "+254"),
        CountryBlockOption("🇰🇮", "Kiribati", "+686"),
        CountryBlockOption("🇽🇰", "Kosovo", "+383"),
        CountryBlockOption("🇰🇼", "Kuwait", "+965"),
        CountryBlockOption("🇰🇬", "Kirghizistan", "+996"),
        CountryBlockOption("🇱🇦", "Laos", "+856"),
        CountryBlockOption("🇱🇻", "Lettonia", "+371"),
        CountryBlockOption("🇱🇧", "Libano", "+961"),
        CountryBlockOption("🇱🇸", "Lesotho", "+266"),
        CountryBlockOption("🇱🇷", "Liberia", "+231"),
        CountryBlockOption("🇱🇾", "Libia", "+218"),
        CountryBlockOption("🇱🇮", "Liechtenstein", "+423"),
        CountryBlockOption("🇱🇹", "Lituania", "+370"),
        CountryBlockOption("🇱🇺", "Lussemburgo", "+352"),
        CountryBlockOption("🇲🇴", "Macao", "+853"),
        CountryBlockOption("🇲🇬", "Madagascar", "+261"),
        CountryBlockOption("🇲🇼", "Malawi", "+265"),
        CountryBlockOption("🇲🇾", "Malesia", "+60"),
        CountryBlockOption("🇲🇻", "Maldive", "+960"),
        CountryBlockOption("🇲🇱", "Mali", "+223"),
        CountryBlockOption("🇲🇹", "Malta", "+356"),
        CountryBlockOption("🇲🇭", "Marshall", "+692"),
        CountryBlockOption("🇲🇶", "Martinica", "+596"),
        CountryBlockOption("🇲🇷", "Mauritania", "+222"),
        CountryBlockOption("🇲🇺", "Mauritius", "+230"),
        CountryBlockOption("🇾🇹", "Mayotte", "+262"),
        CountryBlockOption("🇲🇽", "Messico", "+52"),
        CountryBlockOption("🇫🇲", "Micronesia", "+691"),
        CountryBlockOption("🇲🇩", "Moldavia", "+373"),
        CountryBlockOption("🇲🇨", "Monaco", "+377"),
        CountryBlockOption("🇲🇳", "Mongolia", "+976"),
        CountryBlockOption("🇲🇪", "Montenegro", "+382"),
        CountryBlockOption("🇲🇸", "Montserrat", "+1664"),
        CountryBlockOption("🇲🇦", "Marocco", "+212"),
        CountryBlockOption("🇲🇿", "Mozambico", "+258"),
        CountryBlockOption("🇲🇲", "Myanmar", "+95"),
        CountryBlockOption("🇳🇦", "Namibia", "+264"),
        CountryBlockOption("🇳🇷", "Nauru", "+674"),
        CountryBlockOption("🇳🇵", "Nepal", "+977"),
        CountryBlockOption("🇳🇱", "Paesi Bassi", "+31"),
        CountryBlockOption("🇳🇨", "Nuova Cal.", "+687"),
        CountryBlockOption("🇳🇿", "Nuova Zel.", "+64"),
        CountryBlockOption("🇳🇮", "Nicaragua", "+505"),
        CountryBlockOption("🇳🇪", "Niger", "+227"),
        CountryBlockOption("🇳🇬", "Nigeria", "+234"),
        CountryBlockOption("🇳🇺", "Niue", "+683"),
        CountryBlockOption("🇳🇫", "Norfolk", "+672"),
        CountryBlockOption("🇰🇵", "Corea Nord", "+850"),
        CountryBlockOption("🇲🇰", "Macedonia", "+389"),
        CountryBlockOption("🇲🇵", "Marianne N.", "+1670"),
        CountryBlockOption("🇳🇴", "Norvegia", "+47"),
        CountryBlockOption("🇴🇲", "Oman", "+968"),
        CountryBlockOption("🇵🇰", "Pakistan", "+92"),
        CountryBlockOption("🇵🇼", "Palau", "+680"),
        CountryBlockOption("🇵🇸", "Palestina", "+970"),
        CountryBlockOption("🇵🇦", "Panama", "+507"),
        CountryBlockOption("🇵🇬", "Papua NG", "+675"),
        CountryBlockOption("🇵🇾", "Paraguay", "+595"),
        CountryBlockOption("🇵🇪", "Peru", "+51"),
        CountryBlockOption("🇵🇭", "Filippine", "+63"),
        CountryBlockOption("🇵🇱", "Polonia", "+48"),
        CountryBlockOption("🇵🇹", "Portogallo", "+351"),
        CountryBlockOption("🇵🇷", "Porto Rico", "+1787"),
        CountryBlockOption("🇵🇷", "Porto Rico", "+1939"),
        CountryBlockOption("🇶🇦", "Qatar", "+974"),
        CountryBlockOption("🇷🇪", "Reunion", "+262"),
        CountryBlockOption("🇷🇴", "Romania", "+40"),
        CountryBlockOption("🇷🇺", "Russia", "+7"),
        CountryBlockOption("🇷🇼", "Ruanda", "+250"),
        CountryBlockOption("🇧🇱", "Saint Barth", "+590"),
        CountryBlockOption("🇸🇭", "Sant'Elena", "+290"),
        CountryBlockOption("🇰🇳", "Saint Kitts", "+1869"),
        CountryBlockOption("🇱🇨", "Saint Lucia", "+1758"),
        CountryBlockOption("🇲🇫", "Saint Martin", "+590"),
        CountryBlockOption("🇵🇲", "Saint Pierre", "+508"),
        CountryBlockOption("🇻🇨", "Saint Vinc.", "+1784"),
        CountryBlockOption("🇼🇸", "Samoa", "+685"),
        CountryBlockOption("🇸🇲", "San Marino", "+378"),
        CountryBlockOption("🇸🇹", "Sao Tome", "+239"),
        CountryBlockOption("🇸🇦", "Arabia Saud.", "+966"),
        CountryBlockOption("🇸🇳", "Senegal", "+221"),
        CountryBlockOption("🇷🇸", "Serbia", "+381"),
        CountryBlockOption("🇸🇨", "Seychelles", "+248"),
        CountryBlockOption("🇸🇱", "Sierra Leone", "+232"),
        CountryBlockOption("🇸🇬", "Singapore", "+65"),
        CountryBlockOption("🇸🇽", "Sint Maarten", "+1721"),
        CountryBlockOption("🇸🇰", "Slovacchia", "+421"),
        CountryBlockOption("🇸🇮", "Slovenia", "+386"),
        CountryBlockOption("🇸🇧", "Salomone", "+677"),
        CountryBlockOption("🇸🇴", "Somalia", "+252"),
        CountryBlockOption("🇿🇦", "Sudafrica", "+27"),
        CountryBlockOption("🇰🇷", "Corea Sud", "+82"),
        CountryBlockOption("🇸🇸", "Sud Sudan", "+211"),
        CountryBlockOption("🇪🇸", "Spagna", "+34"),
        CountryBlockOption("🇱🇰", "Sri Lanka", "+94"),
        CountryBlockOption("🇸🇩", "Sudan", "+249"),
        CountryBlockOption("🇸🇷", "Suriname", "+597"),
        CountryBlockOption("🇸🇪", "Svezia", "+46"),
        CountryBlockOption("🇨🇭", "Svizzera", "+41"),
        CountryBlockOption("🇸🇾", "Siria", "+963"),
        CountryBlockOption("🇹🇼", "Taiwan", "+886"),
        CountryBlockOption("🇹🇯", "Tagikistan", "+992"),
        CountryBlockOption("🇹🇿", "Tanzania", "+255"),
        CountryBlockOption("🇹🇭", "Thailandia", "+66"),
        CountryBlockOption("🇹🇱", "Timor Est", "+670"),
        CountryBlockOption("🇹🇬", "Togo", "+228"),
        CountryBlockOption("🇹🇰", "Tokelau", "+690"),
        CountryBlockOption("🇹🇴", "Tonga", "+676"),
        CountryBlockOption("🇹🇹", "Trinidad", "+1868"),
        CountryBlockOption("🇹🇳", "Tunisia", "+216"),
        CountryBlockOption("🇹🇷", "Turchia", "+90"),
        CountryBlockOption("🇹🇲", "Turkmenistan", "+993"),
        CountryBlockOption("🇹🇨", "Turks Caicos", "+1649"),
        CountryBlockOption("🇹🇻", "Tuvalu", "+688"),
        CountryBlockOption("🇺🇬", "Uganda", "+256"),
        CountryBlockOption("🇺🇦", "Ucraina", "+380"),
        CountryBlockOption("🇦🇪", "Emirati", "+971"),
        CountryBlockOption("🇬🇧", "Regno Unito", "+44"),
        CountryBlockOption("🇺🇸", "USA", "+1"),
        CountryBlockOption("🇺🇾", "Uruguay", "+598"),
        CountryBlockOption("🇺🇿", "Uzbekistan", "+998"),
        CountryBlockOption("🇻🇺", "Vanuatu", "+678"),
        CountryBlockOption("🇻🇦", "Vaticano", "+379"),
        CountryBlockOption("🇻🇦", "Vaticano", "+39"),
        CountryBlockOption("🇻🇪", "Venezuela", "+58"),
        CountryBlockOption("🇻🇳", "Vietnam", "+84"),
        CountryBlockOption("🇻🇮", "Vergini USA", "+1340"),
        CountryBlockOption("🇼🇫", "Wallis Fut.", "+681"),
        CountryBlockOption("🇾🇪", "Yemen", "+967"),
        CountryBlockOption("🇿🇲", "Zambia", "+260"),
        CountryBlockOption("🇿🇼", "Zimbabwe", "+263")
    )
}

private fun runSampleAnalysis(): PlatformRiskAssessment {
    return CoreEngineBridge.analyzeCall(
        PlatformCallInfo(
            rawPhoneNumber = "+393479998888",
            timestampMillis = System.currentTimeMillis(),
            verificationStatus = 3,
            direction = 0,
            userCountryCode = "IT",
            deviceNumberHint = "+393471234567",
            seenBefore = false,
            recentCallsFromSameNumber = 0,
            recentCallsFromSamePrefix = 0,
            userRejectedCallsFromSamePrefix = 0
        )
    )
}

private fun customCallInfo(
    number: String,
    seenBefore: Boolean,
    failedVerification: Boolean,
    highFrequency: Boolean
): PlatformCallInfo {
    return PlatformCallInfo(
        rawPhoneNumber = number.trim(),
        timestampMillis = System.currentTimeMillis(),
        verificationStatus = if (failedVerification) 2 else 0,
        direction = 0,
        userCountryCode = "IT",
        deviceNumberHint = "+393471234567",
        seenBefore = seenBefore,
        recentCallsFromSameNumber = if (highFrequency) 5 else 0,
        recentCallsFromSamePrefix = if (highFrequency) 10 else 0,
        userRejectedCallsFromSamePrefix = if (highFrequency) 3 else 0
    )
}

private fun testScenarios(): List<TestScenario> {
    val now = System.currentTimeMillis()
    return listOf(
        TestScenario(
            title = "Chiamata normale",
            description = "Numero italiano gia visto e senza segnali sospetti.",
            callInfo = PlatformCallInfo(
                rawPhoneNumber = "+393331234567",
                timestampMillis = now,
                verificationStatus = 1,
                direction = 0,
                userCountryCode = "IT",
                deviceNumberHint = "+393471234567",
                seenBefore = true,
                recentCallsFromSameNumber = 0,
                recentCallsFromSamePrefix = 0,
                userRejectedCallsFromSamePrefix = 0
            )
        ),
        TestScenario(
            title = "Verifica fallita",
            description = "Simula una chiamata con verifica operatore fallita.",
            callInfo = PlatformCallInfo(
                rawPhoneNumber = "+393331234567",
                timestampMillis = now,
                verificationStatus = 2,
                direction = 0,
                userCountryCode = "IT",
                deviceNumberHint = "+393471234567",
                seenBefore = false,
                recentCallsFromSameNumber = 0,
                recentCallsFromSamePrefix = 0,
                userRejectedCallsFromSamePrefix = 0
            )
        ),
        TestScenario(
            title = "Neighbor spoofing",
            description = "Numero simile al prefisso del dispositivo, non verificato.",
            callInfo = PlatformCallInfo(
                rawPhoneNumber = "+393479998888",
                timestampMillis = now,
                verificationStatus = 3,
                direction = 0,
                userCountryCode = "IT",
                deviceNumberHint = "+393471234567",
                seenBefore = false,
                recentCallsFromSameNumber = 0,
                recentCallsFromSamePrefix = 0,
                userRejectedCallsFromSamePrefix = 0
            )
        ),
        TestScenario(
            title = "Alta frequenza",
            description = "Stesso numero con troppe chiamate in una finestra breve.",
            callInfo = PlatformCallInfo(
                rawPhoneNumber = "+393331234567",
                timestampMillis = now,
                verificationStatus = 0,
                direction = 0,
                userCountryCode = "IT",
                deviceNumberHint = "+393471234567",
                seenBefore = false,
                recentCallsFromSameNumber = 5,
                recentCallsFromSamePrefix = 10,
                userRejectedCallsFromSamePrefix = 0
            )
        ),
        TestScenario(
            title = "Whitelist",
            description = "Usa il prefisso fidato predefinito +39347*.",
            callInfo = PlatformCallInfo(
                rawPhoneNumber = "+393471112222",
                timestampMillis = now,
                verificationStatus = 3,
                direction = 0,
                userCountryCode = "IT",
                deviceNumberHint = "+393471234567",
                seenBefore = false,
                recentCallsFromSameNumber = 5,
                recentCallsFromSamePrefix = 10,
                userRejectedCallsFromSamePrefix = 0
            )
        ),
        TestScenario(
            title = "Pattern artificiale",
            description = "Numero con lunga sequenza ripetuta.",
            callInfo = PlatformCallInfo(
                rawPhoneNumber = "+390211111111",
                timestampMillis = now,
                verificationStatus = 0,
                direction = 0,
                userCountryCode = "IT",
                deviceNumberHint = "+393471234567",
                seenBefore = false,
                recentCallsFromSameNumber = 0,
                recentCallsFromSamePrefix = 0,
                userRejectedCallsFromSamePrefix = 0
            )
        ),
        TestScenario(
            title = "Spam estero mai visto",
            description = "Numero internazionale nuovo: viene bloccato salvo whitelist.",
            callInfo = PlatformCallInfo(
                rawPhoneNumber = "+46738123456",
                timestampMillis = now,
                verificationStatus = 0,
                direction = 0,
                userCountryCode = "IT",
                deviceNumberHint = "+393471234567",
                seenBefore = false,
                recentCallsFromSameNumber = 0,
                recentCallsFromSamePrefix = 0,
                userRejectedCallsFromSamePrefix = 0
            )
        )
    )
}

private fun saveAndApplyPolicy(
    context: Context,
    warnThreshold: Float,
    silenceThreshold: Float,
    blockThreshold: Float,
    blockFailedVerification: Boolean,
    warnNeighborSpoof: Boolean,
    blockFirstSeenInternational: Boolean,
    autoBlockSimilarNumbers: Boolean,
    temporaryGreylist: Boolean,
    blockUnverifiedSuspicious: Boolean,
    quietHoursFilter: Boolean,
    trustedOnlyMode: Boolean,
    manualFeedbackActions: Boolean
) {
    AppPreferences.savePolicy(
        context,
        PolicySettings(
            warnThreshold = warnThreshold,
            silenceThreshold = silenceThreshold,
            blockThreshold = blockThreshold,
            blockFailedVerification = blockFailedVerification,
            warnNeighborSpoof = warnNeighborSpoof,
            blockFirstSeenInternational = blockFirstSeenInternational,
            autoBlockSimilarNumbers = autoBlockSimilarNumbers,
            temporaryGreylist = temporaryGreylist,
            blockUnverifiedSuspicious = blockUnverifiedSuspicious,
            quietHoursFilter = quietHoursFilter,
            trustedOnlyMode = trustedOnlyMode,
            manualFeedbackActions = manualFeedbackActions
        )
    )
    AppPreferences.applyToCore(context)
}

private fun saveAndApplyWhitelist(context: Context, patterns: List<String>) {
    AppPreferences.saveWhitelist(context, patterns)
    AppPreferences.applyToCore(context)
}

private fun saveAndApplyBlocklist(context: Context, patterns: List<String>) {
    AppPreferences.saveBlocklist(context, patterns)
    AppPreferences.applyToCore(context)
}

private fun sharePrivacyReport(
    context: Context,
    stats: CallStatsSnapshot,
    recentEvents: List<CallEvent>,
    diagnostics: ScreeningDiagnostics
) {
    val body = buildString {
        appendLine(localizeText("FreyaShield diagnostica privacy-safe"))
        appendLine("${localizeText("Generato")}: ${CallEventStore.formatTime(System.currentTimeMillis())}")
        appendLine()
        appendLine(localizeText("Statistiche 7 giorni"))
        appendLine("- ${localizeText("Bloccate")}: ${stats.blockedCount}")
        appendLine("- ${localizeText("Silenziate")}: ${stats.silencedCount}")
        appendLine("- ${localizeText("Motivo top")}: ${localizeText(stats.topReason)}")
        appendLine()
        appendLine(localizeText("Servizio"))
        appendLine("- ${localizeText("Invocazioni")}: ${diagnostics.invocationCount}")
        appendLine("- ${localizeText("Ultima chiamata")}: ${localizeText(diagnostics.lastMaskedNumber)}")
        appendLine("- ${localizeText("Ultimo evento")}: ${CallEventStore.formatTime(diagnostics.lastTimestampMillis)}")
        appendLine()
        appendLine(localizeText("Registro mascherato"))
        if (recentEvents.isEmpty()) {
            appendLine("- ${localizeText("Nessun evento")}")
        } else {
            recentEvents.forEach { event ->
                appendLine(
                    "- ${CallEventStore.formatTime(event.timestampMillis)} " +
                        "${event.maskedNumber} ${actionName(event.action)} " +
                        "%.2f".format(event.score) + " ${reasonLabel(event.reason)}"
                )
            }
        }
    }

    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, localizeText("FreyaShield diagnostica"))
        .putExtra(Intent.EXTRA_TEXT, body)
    context.startActivity(Intent.createChooser(intent, localizeText("Esporta diagnostica FreyaShield")))
}

private fun maskPhoneNumber(number: String): String {
    if (number.length <= 6) {
        return "******"
    }
    return number.take(6) + "******"
}

private fun String.toTrustedPrefixPattern(): String? {
    val prefix = substringBefore("*").trim()
    return if (prefix.length >= 5) "$prefix*" else null
}

private fun String.preparePhonePattern(forceWildcard: Boolean = false): String {
    var cleaned = trim()
        .replace(" ", "")
        .replace("-", "")
        .replace("(", "")
        .replace(")", "")
    if (cleaned.isBlank()) {
        return ""
    }
    if (cleaned.startsWith("00")) {
        cleaned = "+" + cleaned.drop(2)
    } else if (!cleaned.startsWith("+")) {
        cleaned = "+39$cleaned"
    }
    if (forceWildcard && !cleaned.endsWith("*")) {
        cleaned += "*"
    }
    return cleaned
}

private fun String.prepareCountryBlockPattern(): String {
    var cleaned = trim()
        .replace(" ", "")
        .replace("-", "")
        .replace("(", "")
        .replace(")", "")
        .replace("*", "")
    if (cleaned.isBlank()) {
        return ""
    }
    if (cleaned.startsWith("00")) {
        cleaned = "+" + cleaned.drop(2)
    } else if (!cleaned.startsWith("+")) {
        cleaned = "+$cleaned"
    }
    return "$cleaned*"
}

private fun String.prepareItalianAreaBlockPattern(): String {
    var cleaned = trim()
        .replace(" ", "")
        .replace("-", "")
        .replace("(", "")
        .replace(")", "")
        .replace("*", "")
    if (cleaned.isBlank()) {
        return ""
    }
    if (cleaned.startsWith("+39")) {
        return "$cleaned*"
    }
    if (cleaned.startsWith("0039")) {
        return "+${cleaned.drop(2)}*"
    }
    return if (cleaned.startsWith("0")) {
        "+39$cleaned*"
    } else {
        "+390$cleaned*"
    }
}

private fun CountryBlockOption.displayName(): String {
    return "$flag $label $prefix"
}

private fun CountryBlockOption.toBlocklistEntry(): String {
    return "${prefix.prepareCountryBlockPattern()}||${displayName()}"
}

private fun String.blockPatternOnly(): String {
    return substringBefore("||").trim()
}

private fun String.blockDisplayLabel(): String {
    val label = substringAfter("||", missingDelimiterValue = "").trim()
    return label.ifBlank { blockPatternOnly() }
}

private fun actionName(action: Int): String {
    val source = when (action) {
        1 -> "Avvisa"
        2 -> "Silenzia"
        3 -> "Blocca"
        else -> "Consenti"
    }
    return localizeText(source)
}

private fun reasonLabel(reason: String): String {
    val source = when (reason) {
        "", "NONE" -> "Nessun rischio"
        "VERIFICATION_PASSED" -> "Verifica superata"
        "VERIFICATION_FAILED" -> "Verifica fallita"
        "NEIGHBOR_SPOOF" -> "Numero simile al tuo prefisso"
        "HIGH_FREQUENCY_NUMBER" -> "Troppe chiamate dallo stesso numero"
        "HIGH_FREQUENCY_PREFIX" -> "Troppe chiamate dallo stesso prefisso"
        "REJECTED_PREFIX" -> "Prefisso spesso rifiutato"
        "INTERNATIONAL_FIRST_SEEN" -> "Internazionale mai visto prima"
        "ARTIFICIAL_PATTERN" -> "Pattern numerico sospetto"
        "WHITELIST" -> "Numero fidato"
        "BLOCKLIST" -> "Pattern bloccato"
        "AUTO_BLOCK_SIMILAR" -> "Numeri simili ripetuti"
        "TRUSTED_ONLY_MODE" -> "Solo regole fidate"
        "TEMPORARY_GREYLIST" -> "Prefisso in quarantena"
        "UNVERIFIED_SUSPICIOUS" -> "Sospetto non verificato"
        "QUIET_HOURS_FILTER" -> "Filtro sera e notte"
        "EMPTY_CALL_INFO" -> "Dati chiamata vuoti"
        else -> reason
    }
    return localizeText(source)
}

private fun signalLabels(explanation: String): String {
    return explanation
        .split(";")
        .map { reasonLabel(it.trim()) }
        .filter { it.isNotBlank() }
        .joinToString("; ")
}
