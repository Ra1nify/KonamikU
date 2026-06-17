package org.cf0x.konamiku.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.cardemulation.NfcFCardEmulation
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.AppDataStore
import org.cf0x.konamiku.data.EmuMode
import org.cf0x.konamiku.data.JsonManager
import org.cf0x.konamiku.data.NfcCard
import org.cf0x.konamiku.nfc.EmuCard
import org.cf0x.konamiku.nfc.toCompatIdm
import org.cf0x.konamiku.notification.LiveUpdateManager
import org.cf0x.konamiku.ui.components.NfcCardItem
import org.cf0x.konamiku.ui.components.StatusIndicatorBar
import org.cf0x.konamiku.ui.viewmodels.StatusViewModel
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(dataStore: AppDataStore) {
    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()
    val jsonManager     = remember { JsonManager(context) }
    val snackbarState   = remember { SnackbarHostState() }
    val statusViewModel: StatusViewModel = viewModel()

    var cards           by remember { mutableStateOf<List<NfcCard>>(emptyList()) }
    var expandedId      by remember { mutableStateOf<String?>(null) }
    var showDialog      by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(true) }

    val activeCardId    by dataStore.activeCardId.collectAsState(initial = null)
    val emuMode         by dataStore.emuMode.collectAsState(initial = EmuMode.NORMAL)
    val devModeForceEmu by dataStore.devModeForceEmu.collectAsState(initial = false)

    val xposedState  by XposedState.activationStateFlow.collectAsState()
    val pmmActive    by XposedState.pmmActiveFlow.collectAsState()
    val modeUnlocked = ((xposedState == XposedActivationState.ACTIVE) && pmmActive) || devModeForceEmu

    LaunchedEffect(modeUnlocked) {
        if (!modeUnlocked && emuMode != EmuMode.NATIVE) {
            dataStore.saveEmuMode(EmuMode.NATIVE)
        }
    }

    val nfcAdapter       = remember { NfcAdapter.getDefaultAdapter(context) }
    val serviceComponent = remember { ComponentName(context, EmuCard::class.java) }

    /** Always obtain a fresh NfcFCardEmulation instance.
     *  The cached instance becomes a dead binder after NFC process restarts (HyperOS). */
    fun freshEmulation() = nfcAdapter?.let {
        runCatching { NfcFCardEmulation.getInstance(it) }.getOrNull()
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        LiveUpdateManager.createChannel(context)
        val loadedCards = withContext(Dispatchers.IO) { jsonManager.loadCards() }
        cards = loadedCards
        isLoading = false
    }

    LaunchedEffect(activeCardId) {
        if (activeCardId != null && expandedId == null) expandedId = activeCardId
    }

    LaunchedEffect(emuMode, activeCardId) {
        if (activeCardId != null) {
            val card = cards.find { it.id == activeCardId } ?: return@LaunchedEffect
            LiveUpdateManager.postActive(context, card.name, card.emuMode)
        }
    }

    // Auto-activate from QS tile / external intent
    fun activateCard(card: NfcCard) {
        scope.launch {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            val mode   = card.emuMode
            val realIdm   = card.idm.uppercase()
            val activeIdm = when (mode) {
                EmuMode.NORMAL           -> realIdm
                EmuMode.COMPAT, EmuMode.NATIVE -> realIdm.toCompatIdm()
            }
            android.util.Log.i("KonamikU", "Activating card: ${card.name} [IDm: $activeIdm] (mode: $mode)")
            val systemCode = "88B4"
            val activity = context as? ComponentActivity
            if (activity == null) {
                dataStore.saveActiveCardId(card.id)
                LiveUpdateManager.postActive(context, card.name, mode)
                return@launch
            }
            runCatching {
                val emulation = freshEmulation() ?: throw IllegalStateException("NFC not available")

                // Align with aicemu sequence: disable -> setIDm -> registerSys -> enable
                emulation.disableService(activity)

                val resultIdm = emulation.setNfcid2ForService(serviceComponent, activeIdm)
                val resultSys = emulation.registerSystemCodeForService(serviceComponent, systemCode)

                if (!resultIdm || !resultSys) {
                    android.util.Log.w("KonamikU", "NFC setup partial failure: IDm=$resultIdm, Sys=$resultSys. Retrying...")
                    delay(300)
                    emulation.setNfcid2ForService(serviceComponent, activeIdm)
                    emulation.registerSystemCodeForService(serviceComponent, systemCode)
                }

                emulation.enableService(activity, serviceComponent)
            }.onFailure { e ->
                android.util.Log.e("KonamikU", "NFC registration failed: ${e.message}")
                if (e is android.os.DeadObjectException || e.message?.contains("Failed to reach") == true) {
                    android.util.Log.i("KonamikU", "Attempting binder recovery...")
                    org.cf0x.konamiku.util.NfcRestart.clearNfcFCache()
                }
            }
            dataStore.saveActiveCardId(card.id)
            LiveUpdateManager.postActive(context, card.name, mode)
        }
    }

    fun deactivateCard() {
        scope.launch {
            runCatching {
                val activity = context as? Activity ?: return@launch
                freshEmulation()?.disableService(activity)
            }
            dataStore.saveActiveCardId(null)
            LiveUpdateManager.cancel(context)
        }
    }

    // Auto-activate from shortcut / external intent
    val activity = context as? ComponentActivity
    var lastIntentId by remember { mutableStateOf<String?>(null) }
    val currentIntentId = activity?.intent?.getStringExtra(EmuCard.EXTRA_AUTO_ACTIVATE)
    LaunchedEffect(currentIntentId, cards, isLoading) {
        if (currentIntentId != null && currentIntentId != lastIntentId && !isLoading) {
            lastIntentId = currentIntentId
            val card = cards.find { it.id == currentIntentId } ?: return@LaunchedEffect
            @Suppress("UNNECESSARY_SAFE_CALL")
            activity?.intent?.removeExtra(EmuCard.EXTRA_AUTO_ACTIVATE)
            activateCard(card)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector        = Icons.Default.Add,
                    contentDescription = "Add Card"
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            StatusIndicatorBar(viewModel = statusViewModel)

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    cards.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.CreditCard,
                                    contentDescription = null,
                                    modifier           = Modifier.size(64.dp),
                                    tint               = MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.cards_empty_title),
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.cards_empty_hint),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(cards, key = { it.id }) { card ->
                                val isExpanded = card.id == expandedId
                                val isActive   = card.id == activeCardId
                                NfcCardItem(
                                    card              = card,
                                    isExpanded        = isExpanded,
                                    isActive          = isActive,
                                    emuMode           = card.emuMode,
                                    onExpandClick     = {
                                        expandedId = if (isExpanded) null else card.id
                                    },
                                    onActivateClick   = {
                                        if (isActive) deactivateCard() else activateCard(card)
                                    },
                                    onEmuModeClick    = {
                                        scope.launch {
                                            if (!modeUnlocked) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.toast_mode_locked),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@launch
                                            }
                                            val next = when (card.emuMode) {
                                                EmuMode.NORMAL -> EmuMode.COMPAT
                                                EmuMode.COMPAT -> EmuMode.NATIVE
                                                EmuMode.NATIVE -> EmuMode.NORMAL
                                            }
                                            val updated = card.copy(emuMode = next)
                                            cards = cards.map { if (it.id == card.id) updated else it }
                                            withContext(Dispatchers.IO) { jsonManager.saveCards(cards) }
                                            if (isActive) activateCard(updated)
                                        }
                                    },
                                    onDeleteConfirmed = {
                                        if (isActive) deactivateCard()
                                        cards = cards.filterNot { it.id == card.id }
                                        if (expandedId == card.id) expandedId = null
                                        scope.launch(Dispatchers.IO) {
                                            jsonManager.saveCards(cards)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (showDialog) {
                    AddCardDialog(
                        onDismiss = { showDialog = false },
                        onConfirm = { name, idm ->
                            val newCard = NfcCard(UUID.randomUUID().toString(), name, idm)
                            cards += newCard
                            scope.launch(Dispatchers.IO) {
                                jsonManager.saveCards(cards)
                            }
                            // Add dynamic shortcut for long-press menu
                            showDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCardDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, idm: String) -> Unit
) {
    val context      = LocalContext.current
    rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var name       by remember { mutableStateOf("") }
    var idm        by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    val isIdmValid = idm.length == 16 &&
            idm.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    val isConfirmEnabled = name.isNotBlank() && isIdmValid && !isScanning

    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }

    DisposableEffect(isScanning) {
        val activity = context as? Activity

        if (!isScanning) {
            return@DisposableEffect onDispose { activity?.let { nfcAdapter?.disableReaderMode(it) } }
        }

        val act = activity ?: return@DisposableEffect onDispose {}

        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            val hex = tag.id
                .joinToString("") { "%02X".format(it) }
                .take(16)
                .padStart(16, '0')
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                idm = hex
                isScanning = false
            }
        }

        val options = android.os.Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000)
        }

        nfcAdapter?.enableReaderMode(
            act,
            callback,
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            options
        )

        onDispose {
            nfcAdapter?.disableReaderMode(act)
        }
    }

    AlertDialog(
        onDismissRequest = { isScanning = false; onDismiss() },
        title            = { Text(stringResource(R.string.card_add_title)) },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text(stringResource(R.string.card_add_name_label)) },
                    placeholder   = { Text(stringResource(R.string.card_add_name_placeholder)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value         = idm,
                    onValueChange = { if (!isScanning && it.length <= 16) idm = it.uppercase() },
                    label         = { Text(stringResource(R.string.card_add_idm_label)) },
                    placeholder   = {
                        Text(
                            if (isScanning) stringResource(R.string.card_add_idm_scanning)
                            else stringResource(R.string.card_add_idm_placeholder)
                        )
                    },
                    singleLine     = true,
                    enabled        = !isScanning,
                    isError        = !isScanning && idm.isNotEmpty() && !isIdmValid,
                    supportingText = {
                        when {
                            isScanning -> Text(
                                stringResource(R.string.card_add_idm_scanning_hint),
                                color = MaterialTheme.colorScheme.primary
                            )
                            idm.isNotEmpty() && !isIdmValid && idm.length == 16 ->
                                Text(stringResource(R.string.card_add_err_invalid_hex),
                                    color = MaterialTheme.colorScheme.error)
                            else -> Text("${idm.length} / 16")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scanShape = if (isScanning) CircleShape
                else MaterialTheme.shapes.medium

                val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue  = 1f,
                    targetValue   = if (isScanning) 0.3f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation  = androidx.compose.animation.core.tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Surface(
                    onClick  = {
                        isScanning = !isScanning
                        if (isScanning) { idm = ""; focusManager.clearFocus() }
                    },
                    shape  = scanShape,
                    color  = if (isScanning) MaterialTheme.colorScheme.primaryContainer
                    else androidx.compose.ui.graphics.Color.Transparent,
                    border = if (!isScanning) androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outline
                    ) else null,
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness    = Spring.StiffnessMedium
                        )
                    )
                ) {
                    Row(
                        modifier              = Modifier.padding(
                            horizontal = if (isScanning) 16.dp else 12.dp,
                            vertical   = 8.dp
                        ),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.Nfc,
                            contentDescription = "Scan",
                            modifier           = Modifier
                                .size(18.dp)
                                .graphicsLayer { alpha = if (isScanning) pulseAlpha else 1f },
                            tint = if (isScanning)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(visible = isScanning) {
                            Text(
                                text  = stringResource(R.string.card_add_scanning),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = { isScanning = false; onDismiss() }) {
                    Text(stringResource(R.string.card_add_cancel))
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick  = { onConfirm(name, idm) },
                    enabled  = isConfirmEnabled
                ) { Text(stringResource(R.string.card_add_confirm)) }
            }
        }
    )
}

