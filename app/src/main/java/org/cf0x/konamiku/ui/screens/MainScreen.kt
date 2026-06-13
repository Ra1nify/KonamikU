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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val devModeForceEmu by dataStore.devModeForceEmu.collectAsState(initial = dataStore.devModeForceEmuSync)

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
            LiveUpdateManager.postActive(context, card.name, emuMode)
        }
    }

    fun activateCard(card: NfcCard) {
        scope.launch {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            val realIdm   = card.idm.uppercase()
            val activeIdm = when (emuMode) {
                EmuMode.NORMAL           -> realIdm
                EmuMode.COMPAT, EmuMode.NATIVE -> realIdm.toCompatIdm()
            }
            android.util.Log.i("KonamikU", "Activating card: ${card.name} [IDm: $activeIdm] (mode: $emuMode)")
            val systemCode = when (emuMode) {
                EmuMode.NATIVE -> "4000"
                else           -> "88B4"
            }
            val activity = context as? ComponentActivity
            if (activity == null) {
                dataStore.saveActiveCardId(card.id)
                LiveUpdateManager.postActive(context, card.name, emuMode)
                return@launch
            }
            runCatching {
                val emulation = freshEmulation() ?: throw IllegalStateException("NFC not available")
                emulation.disableService(activity)
                emulation.setNfcid2ForService(serviceComponent, activeIdm)
                emulation.registerSystemCodeForService(serviceComponent, systemCode)
                
                if (emulation.setNfcid2ForService(serviceComponent, activeIdm).not() || 
                    emulation.registerSystemCodeForService(serviceComponent, systemCode).not()) {
                    delay(300)
                    emulation.setNfcid2ForService(serviceComponent, activeIdm)
                    emulation.registerSystemCodeForService(serviceComponent, systemCode)
                }
                emulation.enableService(activity, serviceComponent)
            }.onFailure { e ->
                android.util.Log.e("KonamikU", "NFC registration fail: ${e.message}")
                if (e is android.os.DeadObjectException || e.message?.contains("Failed to reach") == true) {
                    org.cf0x.konamiku.util.NfcRestart.clearNfcFCache()
                }
            }
            dataStore.saveActiveCardId(card.id)
            LiveUpdateManager.postActive(context, card.name, emuMode)
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
                Icon(Icons.Default.Add, contentDescription = "Add Card")
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
                                Icon(Icons.Outlined.CreditCard, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.cards_empty_title), color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.cards_empty_hint), color = MaterialTheme.colorScheme.outlineVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(cards, key = { it.id }) { card ->
                                val isActive = card.id == activeCardId
                                NfcCardItem(
                                    card              = card,
                                    isExpanded        = card.id == expandedId,
                                    isActive          = isActive,
                                    emuMode           = emuMode,
                                    onExpandClick     = { expandedId = if (card.id == expandedId) null else card.id },
                                    onActivateClick   = { if (isActive) deactivateCard() else activateCard(card) },
                                    onEmuModeClick    = {
                                        scope.launch {
                                            if (!modeUnlocked) {
                                                Toast.makeText(context, context.getString(R.string.toast_mode_locked), Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                            val next = when (emuMode) {
                                                EmuMode.NORMAL -> EmuMode.COMPAT
                                                EmuMode.COMPAT -> EmuMode.NATIVE
                                                EmuMode.NATIVE -> EmuMode.NORMAL
                                            }
                                            dataStore.saveEmuMode(next)
                                            if (isActive) activateCard(card)
                                        }
                                    },
                                    onDeleteConfirmed = {
                                        if (isActive) deactivateCard()
                                        cards = cards.filterNot { it.id == card.id }
                                        if (expandedId == card.id) expandedId = null
                                        scope.launch(Dispatchers.IO) { jsonManager.saveCards(cards) }
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
                            scope.launch(Dispatchers.IO) { jsonManager.saveCards(cards) }
                            showDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCardDialog(onDismiss: () -> Unit, onConfirm: (name: String, idm: String) -> Unit) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current
    var name       by remember { mutableStateOf("") }
    var idm        by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    val isIdmValid = idm.length == 16 && idm.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    val isConfirmEnabled = name.isNotBlank() && isIdmValid && !isScanning
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }

    DisposableEffect(isScanning) {
        val activity = context as? Activity
        if (!isScanning) {
            return@DisposableEffect onDispose {
                activity?.let {
                    nfcAdapter?.disableReaderMode(it)
                    (it as? org.cf0x.konamiku.MainActivity)?.enableDefaultReaderMode()
                }
            }
        }

        val act = activity ?: return@DisposableEffect onDispose {}
        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            val hex = tag.id.joinToString("") { "%02X".format(it) }.take(16).padStart(16, '0')
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                idm = hex
                isScanning = false
            }
        }

        nfcAdapter?.enableReaderMode(act, callback, NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, android.os.Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000)
        })

        onDispose {
            nfcAdapter?.disableReaderMode(act)
            (act as? org.cf0x.konamiku.MainActivity)?.enableDefaultReaderMode()
        }
    }

    AlertDialog(
        onDismissRequest = { isScanning = false; onDismiss() },
        title            = { Text(stringResource(R.string.card_add_title)) },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.card_add_name_label)) }, placeholder = { Text(stringResource(R.string.card_add_name_placeholder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = idm, 
                    onValueChange = { if (!isScanning && it.length <= 16) idm = it.uppercase() }, 
                    label = { Text(stringResource(R.string.card_add_idm_label)) }, 
                    placeholder = { Text(if (isScanning) stringResource(R.string.card_add_idm_scanning) else stringResource(R.string.card_add_idm_placeholder)) },
                    singleLine = true, 
                    enabled = !isScanning, 
                    isError = !isScanning && idm.isNotEmpty() && !isIdmValid,
                    supportingText = {
                        if (isScanning) Text(stringResource(R.string.card_add_idm_scanning_hint), color = MaterialTheme.colorScheme.primary)
                        else if (idm.isNotEmpty() && !isIdmValid && idm.length == 16) Text(stringResource(R.string.card_add_err_invalid_hex), color = MaterialTheme.colorScheme.error)
                        else Text("${idm.length} / 16")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(1f, if (isScanning) 0.3f else 1f, infiniteRepeatable(androidx.compose.animation.core.tween(800), RepeatMode.Reverse), label = "alpha")

                Surface(
                    onClick = { isScanning = !isScanning; if (isScanning) { idm = ""; focusManager.clearFocus() } },
                    shape = if (isScanning) CircleShape else MaterialTheme.shapes.medium,
                    color = if (isScanning) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    border = if (!isScanning) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                    modifier = Modifier.animateContentSize()
                ) {
                    Row(modifier = Modifier.padding(horizontal = if (isScanning) 16.dp else 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Nfc, null, modifier = Modifier.size(18.dp).graphicsLayer { alpha = pulseAlpha }, tint = if (isScanning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        AnimatedVisibility(isScanning) { Text(stringResource(R.string.card_add_scanning), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { isScanning = false; onDismiss() }) { Text(stringResource(R.string.card_add_cancel)) }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { onConfirm(name, idm) }, enabled = isConfirmEnabled) { Text(stringResource(R.string.card_add_confirm)) }
            }
        }
    )
}
