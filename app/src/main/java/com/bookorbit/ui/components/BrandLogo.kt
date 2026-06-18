package com.bookorbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R

/**
 * BookOrbit brand mark: the Lucide Orbit icon on a primary-gradient rounded square, mirroring the
 * web client's sidebar logo (client/src/components/AppSidebar.vue).
 */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, size: Int = 72) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size.dp)
            .background(
                brush = Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.75f))),
                shape = RoundedCornerShape(percent = 28),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_orbit),
            contentDescription = "BookOrbit",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .padding(size.dp * 0.22f)
                .size((size * 0.56f).dp),
        )
    }
}
