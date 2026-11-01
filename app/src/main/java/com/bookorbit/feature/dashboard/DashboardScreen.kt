package com.bookorbit.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.R
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.ReadingGoalWidget
import com.bookorbit.core.model.ReadingStreakWidget
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.components.BookCover
import com.bookorbit.ui.components.BookShelf
import com.bookorbit.ui.components.CoverBackdrop
import com.bookorbit.ui.components.EmptyState
import com.bookorbit.ui.components.ErrorState
import com.bookorbit.ui.components.IconTile
import com.bookorbit.ui.components.Panel
import com.bookorbit.ui.components.PanelShape
import com.bookorbit.ui.components.PrimaryButton
import com.bookorbit.ui.theme.BookOrbitTheme
import java.time.LocalTime

/** Home: greeting, the book to pick up next, streak and goal, then the shelves. */
@Composable
fun DashboardScreen(
    userName: String,
    onOpenProfile: () -> Unit,
    onBookClick: (Int) -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    DashboardContent(ui, userName, greeting(), onRefresh = vm::refresh, onOpenProfile = onOpenProfile, onBookClick = onBookClick)
}

/** Wider than this, the continue card and the stat tiles sit side by side. */
private val SideBySideWidth = 700.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardContent(
    ui: DashboardViewModel.UiState,
    userName: String,
    greeting: String,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
    onBookClick: (Int) -> Unit,
) {
    val isEmpty = !ui.loading && ui.continueReading.isEmpty() && ui.continueListening.isEmpty() && ui.recentlyAdded.isEmpty()

    PullToRefreshBox(isRefreshing = ui.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= SideBySideWidth
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HomeHeader(greeting = greeting, userName = userName, onOpenProfile = onOpenProfile)

                when {
                    ui.error -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        ErrorState(stringResource(R.string.couldn_t_load_your_dashboard), onRetry = onRefresh)
                    }
                    isEmpty -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
                        EmptyState(Icons.Outlined.AutoStories, stringResource(R.string.nothing_here_yet_add_books))
                    }
                    else -> {
                        // The hero is the book to pick up next: reading first, then listening.
                        val heroFromReading = ui.continueReading.firstOrNull()
                        val hero = heroFromReading ?: ui.continueListening.firstOrNull()
                        val heroCard: (@Composable (Modifier) -> Unit)? = hero?.let { book ->
                            { modifier ->
                                ContinueCard(
                                    book = book,
                                    label = stringResource(if (heroFromReading != null) R.string.home_continue_reading else R.string.continue_listening),
                                    onClick = { onBookClick(book.id) },
                                    modifier = modifier,
                                )
                            }
                        }
                        val hasStats = ui.streak != null || ui.goal != null
                        if (wide && heroCard != null && hasStats) {
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                heroCard(Modifier.weight(1.5f).fillMaxHeight())
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ui.streak?.let { StreakTile(it, Modifier.weight(1f).fillMaxWidth()) }
                                    ui.goal?.let { GoalTile(it, Modifier.weight(1f).fillMaxWidth()) }
                                }
                            }
                        } else {
                            heroCard?.invoke(Modifier.fillMaxWidth())
                            StatsStrip(streak = ui.streak, goal = ui.goal)
                        }

                        val moreReading = if (heroFromReading != null) ui.continueReading.drop(1) else ui.continueReading
                        val moreListening = if (heroFromReading != null) ui.continueListening else ui.continueListening.drop(1)
                        if (moreReading.isNotEmpty()) BookShelf(stringResource(R.string.up_next_to_read), Icons.AutoMirrored.Outlined.MenuBook, moreReading, onBookClick)
                        if (moreListening.isNotEmpty()) BookShelf(stringResource(R.string.continue_listening), Icons.Outlined.Headphones, moreListening, onBookClick)
                        if (ui.recentlyAdded.isNotEmpty()) BookShelf(stringResource(R.string.recently_added), Icons.Outlined.NewReleases, ui.recentlyAdded, onBookClick)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader(greeting: String, userName: String, onOpenProfile: () -> Unit) {
    val tokens = BookOrbitTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = tokens.mutedForeground)
            Text(userName, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(tokens.accentTint)
                .border(1.dp, tokens.border, CircleShape)
                .clickable(onClickLabel = stringResource(R.string.open_profile), onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            Text(userName.firstOrNull()?.uppercase() ?: "", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun greeting(): String = stringResource(
    when (LocalTime.now().hour) {
        in 5..11 -> R.string.home_good_morning
        in 12..17 -> R.string.home_good_afternoon
        else -> R.string.home_good_evening
    },
)

/** Streak and yearly goal side by side; each hides itself if the server doesn't provide it. */
@Composable
internal fun StatsStrip(streak: ReadingStreakWidget?, goal: ReadingGoalWidget?, modifier: Modifier = Modifier) {
    if (streak == null && goal == null) return
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (streak != null) StreakTile(streak, Modifier.weight(1f).fillMaxHeight())
        if (goal != null) GoalTile(goal, Modifier.weight(1f).fillMaxHeight())
    }
}

/** A stat in a panel: an icon tile and label, then the figure in the serif. */
@Composable
internal fun StatTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color = BookOrbitTheme.tokens.foreground,
    footer: (@Composable () -> Unit)? = null,
) {
    Panel(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint = iconTint)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = BookOrbitTheme.tokens.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f, fill = false).height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        footer?.let { Box(Modifier.padding(top = 8.dp)) { it() } }
    }
}

@Composable
private fun StreakTile(streak: ReadingStreakWidget, modifier: Modifier) {
    val tokens = BookOrbitTheme.tokens
    StatTile(
        label = stringResource(R.string.streak),
        value = pluralStringResource(R.plurals.home_streak_days, streak.currentStreak, streak.currentStreak),
        icon = Icons.Outlined.LocalFireDepartment,
        iconTint = if (streak.currentStreak > 0) tokens.warning else tokens.mutedForeground,
        modifier = modifier,
        footer = if (streak.lastSevenDays.isNotEmpty()) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    streak.lastSevenDays.takeLast(7).forEach { active ->
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) tokens.warning else tokens.muted))
                    }
                }
            }
        } else null,
    )
}

@Composable
private fun GoalTile(goal: ReadingGoalWidget, modifier: Modifier) {
    val target = goal.goalBooks
    val label = if (goal.year > 0) stringResource(R.string.home_goal_title, goal.year.toString()) else stringResource(R.string.home_goal_yearly)
    if (target != null && target > 0) {
        StatTile(
            label = label,
            value = stringResource(R.string.home_goal_progress, goal.completedBooks, target),
            icon = Icons.Outlined.Flag,
            modifier = modifier,
            footer = { ProgressLine(goal.completedBooks.toFloat() / target) },
        )
    } else {
        StatTile(
            label = label,
            value = stringResource(R.string.read_2, goal.completedBooks),
            icon = Icons.Outlined.Flag,
            modifier = modifier,
            footer = { Text(stringResource(R.string.no_goal_set), style = MaterialTheme.typography.bodySmall, color = BookOrbitTheme.tokens.mutedForeground) },
        )
    }
}

/** A thin rounded progress bar in the foreground colour, as the web draws its meters. */
@Composable
internal fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val tokens = BookOrbitTheme.tokens
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(tokens.muted)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(tokens.primary))
    }
}

/** The one book to pick up next, on its own blurred cover: cover, title, progress and a Continue button. */
@Composable
private fun ContinueCard(book: BookCard, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val imageUrls = LocalImageUrls.current
    val tokens = BookOrbitTheme.tokens
    val coverModel = if (book.hasCover) imageUrls.cover(book.id) else null
    val progress = (book.readingProgress ?: 0.0).coerceIn(0.0, 100.0)
    Box(
        modifier
            .clip(PanelShape)
            .background(tokens.card)
            .border(1.dp, tokens.accentLine, PanelShape)
            .clickable(onClick = onClick),
    ) {
        CoverBackdrop(coverModel, Modifier.matchParentSize())
        Row(Modifier.padding(18.dp).height(IntrinsicSize.Min)) {
            BookCover(
                model = coverModel,
                title = book.title,
                elevated = true,
                titleSize = MaterialTheme.typography.titleMedium.fontSize,
                modifier = Modifier.width(104.dp).height(156.dp),
            )
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = tokens.mutedForeground)
                Text(
                    book.title ?: stringResource(R.string.home_untitled),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                book.authors.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = tokens.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.weight(1f).height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine((progress / 100.0).toFloat(), Modifier.weight(1f))
                    Text("${progress.toInt()}%", style = MaterialTheme.typography.labelMedium, color = tokens.mutedForeground, modifier = Modifier.padding(start = 10.dp))
                }
                PrimaryButton(stringResource(R.string.open), onClick = onClick, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
