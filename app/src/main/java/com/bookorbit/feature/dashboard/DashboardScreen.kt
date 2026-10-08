package com.bookorbit.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.ReadingGoalWidget
import com.bookorbit.core.model.ReadingStreakWidget
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.components.HorizontalBookScroller
import java.time.LocalTime

/** Home: greeting, streak/goal strip, one "continue" card, then the usual shelves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userName: String,
    onOpenProfile: () -> Unit,
    onBookClick: (Int) -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val isEmpty = !ui.loading && ui.continueReading.isEmpty() && ui.continueListening.isEmpty() && ui.recentlyAdded.isEmpty()

    PullToRefreshBox(
        isRefreshing = ui.loading,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HomeHeader(userName = userName, onOpenProfile = onOpenProfile)

            when {
                ui.error -> Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load your dashboard", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.refresh() }, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
                    }
                }
                isEmpty -> Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) {
                    Text(
                        "Nothing here yet. Add books on the server.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    StatsStrip(streak = ui.streak, goal = ui.goal)

                    // The hero is the book to pick up next: reading first, then listening.
                    val heroFromReading = ui.continueReading.firstOrNull()
                    val hero = heroFromReading ?: ui.continueListening.firstOrNull()
                    if (hero != null) {
                        ContinueCard(
                            book = hero,
                            label = if (heroFromReading != null) "Continue reading" else "Continue listening",
                            onClick = { onBookClick(hero.id) },
                        )
                    }

                    val moreReading = if (heroFromReading != null) ui.continueReading.drop(1) else ui.continueReading
                    val moreListening = if (heroFromReading != null) ui.continueListening else ui.continueListening.drop(1)
                    if (moreReading.isNotEmpty()) HorizontalBookScroller("Up next to read", moreReading, onBookClick)
                    if (moreListening.isNotEmpty()) HorizontalBookScroller("Continue listening", moreListening, onBookClick)
                    if (ui.recentlyAdded.isNotEmpty()) HorizontalBookScroller("Recently added", ui.recentlyAdded, onBookClick)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HomeHeader(userName: String, onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(userName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClickLabel = "Open profile", onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                userName.firstOrNull()?.uppercase() ?: "",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

/** Streak and yearly goal side by side; each hides itself if the server doesn't provide it. */
@Composable
internal fun StatsStrip(streak: ReadingStreakWidget?, goal: ReadingGoalWidget?) {
    if (streak == null && goal == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (streak != null) StreakCard(streak, Modifier.weight(1f))
        if (goal != null) GoalCard(goal, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(modifier: Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) { content() }
    }
}

@Composable
private fun StreakCard(streak: ReadingStreakWidget, modifier: Modifier) {
    StatCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = if (streak.currentStreak > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
            Text("  Streak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (streak.currentStreak == 1) "1 day" else "${streak.currentStreak} days",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (streak.lastSevenDays.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                streak.lastSevenDays.takeLast(7).forEach { active ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalCard(goal: ReadingGoalWidget, modifier: Modifier) {
    StatCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("  ${if (goal.year > 0) goal.year else "Yearly"} goal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val target = goal.goalBooks
        if (target != null && target > 0) {
            Text(
                "${goal.completedBooks} / $target",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            LinearProgressIndicator(
                progress = { (goal.completedBooks.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(CircleShape),
            )
        } else {
            Text(
                "${goal.completedBooks} read",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text("No goal set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** The one book to pick up next: cover, title, progress, and an open button. */
@Composable
private fun ContinueCard(book: BookCard, label: String, onClick: () -> Unit) {
    val imageUrls = LocalImageUrls.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.padding(top = 10.dp)) {
                Box(
                    modifier = Modifier
                        .width(84.dp)
                        .height(124.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (book.hasCover) {
                        AsyncImage(
                            model = imageUrls.cover(book.id),
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(book.title ?: "Untitled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    book.authors.firstOrNull()?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.weight(1f))
                    val progress = (book.readingProgress ?: 0.0).coerceIn(0.0, 100.0)
                    LinearProgressIndicator(
                        progress = { (progress / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${progress.toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
