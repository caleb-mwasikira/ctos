package com.ctos.client.screens.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctos.client.data.Screen
import com.ctos.client.screens.widgets.IconAction
import com.ctos.client.screens.widgets.Logo
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/** The navigation item a screen belongs under: an organization is under "Organizations", a department under "Departments". */
val Screen.navigationRoot: Screen
    get() = when (this) {
        Screen.OrganizationDetail -> Screen.Dashboard
        Screen.DepartmentDetail, Screen.DepartmentMembers, Screen.DepartmentAccessCode, Screen.DepartmentInvitations, Screen.DepartmentLeave -> Screen.Departments
        Screen.SecureDropSend, Screen.SecureDropSendByEmail -> Screen.SecureDrop
        else -> this
    }

private val navigationScreens = Screen.entries.filter { it.displayInNavigation }

/**
 * The frame around every signed-in screen. On a wide window (a computer) it is a sidebar next to
 * the page; on a narrow one (a phone) the navigation moves to the bottom of the screen.
 */
@Composable
fun AppShell(
    current: Screen,
    title: String,
    userName: String,
    userEmail: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onSearch: () -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(CtosColors.Canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 760.dp
        Column(modifier = Modifier.fillMaxSize()) {
            // A full-width header above everything else, the same as the website's own <header>:
            // the logo (or a back arrow and the screen's title, in its place, while inside one),
            // search, and the account menu.
            Header(title, canGoBack, onBack, onSearch, userName, userEmail, onLogout, onLogoClick = { onNavigate(Screen.Dashboard) })
            if (wide) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Sidebar(current, onNavigate)
                    VerticalDivider(modifier = Modifier.fillMaxHeight(), color = CtosColors.Border)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                BottomBar(current, onNavigate)
            }
        }
    }
}

/** Nav links only — the logo and the account live in the header, the same split as `<x-nav-links>` in its own `<aside>`, under `app-layout.blade.php`'s header. */
@Composable
private fun Sidebar(current: Screen, onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier.width(224.dp).fillMaxHeight().background(CtosColors.Canvas).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        navigationScreens.forEach { screen ->
            val selected = current.navigationRoot == screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) CtosColors.Primary50 else CtosColors.Canvas, CtosShapes.Control)
                    .clickable { onNavigate(screen) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                screen.icon?.let {
                    Icon(it, contentDescription = null, tint = if (selected) CtosColors.Primary else CtosColors.TextMuted, modifier = Modifier.size(20.dp))
                }
                Text(screen.title, style = if (selected) CtosText.BodyStrong else CtosText.Body, color = if (selected) CtosColors.PrimaryHover else CtosColors.TextMuted)
            }
        }
    }
}

/**
 * The header, full width above the sidebar and the page, the same as `app-layout.blade.php`'s
 * own `<header>`: the logo on the left, search and the account menu on the right. Inside a
 * screen reached by pushing (a detail page, a department's own sections), a back arrow and that
 * screen's title stand in for the logo — the website's own equivalent is each page's breadcrumbs.
 */
@Composable
private fun Header(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    userName: String,
    userEmail: String,
    onLogout: () -> Unit,
    onLogoClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).background(CtosColors.Surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(4.dp))
            Text(title, style = CtosText.SectionTitle, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else {
            Logo(onClick = onLogoClick)
            Spacer(Modifier.weight(1f))
        }
        IconAction(Icons.Default.Search, "Search", onSearch)
        ProfileMenu(userName, userEmail, onLogout)
    }
    HorizontalDivider(color = CtosColors.Border)
}

/** The avatar in the top bar: clicking it opens a dropdown with the account and a log-out button, like the website's own header. */
@Composable
private fun ProfileMenu(userName: String, userEmail: String, onLogout: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.height(44.dp).width(44.dp)) {
            Avatar(userName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(240.dp).background(CtosColors.Surface)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(userName, style = CtosText.SmallStrong, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(userEmail, style = CtosText.Caption, color = CtosColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = CtosColors.Border)
            DropdownMenuItem(
                text = { Text("Log out", style = CtosText.Body, color = CtosColors.Danger) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CtosColors.Danger) },
                onClick = { expanded = false; onLogout() },
            )
        }
    }
}

@Composable
private fun BottomBar(current: Screen, onNavigate: (Screen) -> Unit) {
    NavigationBar(containerColor = CtosColors.Surface) {
        navigationScreens.forEach { screen ->
            NavigationBarItem(
                selected = current.navigationRoot == screen,
                onClick = { onNavigate(screen) },
                icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                label = { Text(screen.title, style = CtosText.Caption) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CtosColors.Primary,
                    selectedTextColor = CtosColors.Primary,
                    indicatorColor = CtosColors.Primary50,
                    unselectedIconColor = CtosColors.TextMuted,
                    unselectedTextColor = CtosColors.TextMuted,
                ),
            )
        }
    }
}

/** A circle with a person's first letter, like on the website. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(
        modifier = modifier.size(size).background(CtosColors.Primary100, CtosShapes.Pill),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, style = CtosText.SmallStrong, color = CtosColors.PrimaryHover)
    }
}
