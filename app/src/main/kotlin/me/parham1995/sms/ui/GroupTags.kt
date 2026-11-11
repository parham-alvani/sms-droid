package me.parham1995.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.parham1995.sms.core.Groups
import me.parham1995.sms.ui.theme.fontFor
import kotlin.math.absoluteValue

/**
 * A contact's groups as small tags, each in the colour of its group, so a
 * group looks the same on every line and on its chip.
 */
@Composable
fun GroupTags(
    groups: List<String>,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { name ->
            val (background, foreground) = groupColors(name)
            Text(
                name,
                fontFamily = fontFor(name),
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .background(background, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** One chip per group above the conversation list; the chosen one filters it, tapped again it clears. */
@Composable
fun GroupChips(
    groups: List<Groups.Count>,
    chosen: String?,
    total: Int,
    onChoose: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = chosen == null,
                onClick = { onChoose(null) },
                label = { Text("All $total") },
            )
        }
        items(groups, key = { it.name }) { group ->
            FilterChip(
                selected = chosen == group.name,
                onClick = { onChoose(group.name) },
                label = { Text("${group.name} ${group.conversations}", fontFamily = fontFor(group.name)) },
            )
        }
    }
}

/** The colour pair of a group, from its name, so it never changes between screens. */
@Composable
private fun groupColors(name: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val pairs =
        listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
        )
    return pairs[name.lowercase().hashCode().absoluteValue % pairs.size]
}
