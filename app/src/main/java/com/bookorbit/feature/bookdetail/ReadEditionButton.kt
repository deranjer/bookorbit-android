package com.bookorbit.feature.bookdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R

@Composable
internal fun ReadEditionButton(onRead: () -> Unit, onChooseEdition: () -> Unit, hasAlternatives: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Button(onClick = onRead, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp),
            shape = if (hasAlternatives) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 8, bottomEndPercent = 8)
                else RoundedCornerShape(50)) {
            Icon(Icons.Filled.Book, contentDescription = null)
            Text(stringResource(R.string.read), Modifier.padding(start = 8.dp))
        }
        if (hasAlternatives) Button(onClick = onChooseEdition, modifier = Modifier.width(48.dp), contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50, topStartPercent = 8, bottomStartPercent = 8)) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.edition_read_title))
        }
    }
}
