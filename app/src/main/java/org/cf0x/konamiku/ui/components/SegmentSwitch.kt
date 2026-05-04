package org.cf0x.konamiku.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentSwitch(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier             = modifier.fillMaxWidth(),
        verticalArrangement  = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, item ->
                SegmentedButton(
                    shape    = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick  = { onSelect(index) },
                    selected = index == selectedIndex
                ) { Text(item) }
            }
        }
    }
}