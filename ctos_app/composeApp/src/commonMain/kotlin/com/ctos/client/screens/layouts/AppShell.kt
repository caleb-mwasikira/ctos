package com.ctos.client.screens.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Folder
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
import com.ctos.client.theme.CtosColors
import ctosclient.composeapp.generated.resources.Res
import ctosclient.composeapp.generated.resources.ctos_logo
import org.jetbrains.compose.resources.painterResource
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
        if (maxWidth >= 760.dp) {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(current, userName, onNavigate)
                VerticalDivider(modifier = Modifier.fillMaxHeight(), color = CtosColors.Border)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    TopBar(title, canGoBack, onBack, onSearch, userName, userEmail, onLogout)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(title, canGoBack, onBack, onSearch, userName, userEmail, onLogout)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                BottomBar(current, onNavigate)
            }
        }
    }
}

@Composable
private fun Sidebar(current: Screen, userName: String, onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier.width(248.dp).fillMaxHeight().background(CtosColors.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.ctos_logo),
            contentDescription = "CTOS",
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 20.dp).height(32.dp),
        )

        navigationScreens.forEach { screen ->
            val selected = current.navigationRoot == screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) CtosColors.Primary50 else CtosColors.Surface, CtosShapes.Control)
                    .clickable { onNavigate(screen) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                screen.icon?.let {
                    Icon(it, contentDescription = null, tint = if (selected) CtosColors.Primary else CtosColors.TextMuted, modifier = Modifier.size(20.dp))
                }
                Text(screen.title, style = if (selected) CtosText.BodyStrong else CtosText.Body, color = if (selected) CtosColors.PrimaryHover else CtosColors.TextMuted)
            }
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = CtosColors.Border)
        Row(modifier = Modifier.padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(userName)
            Text(userName, style = CtosText.SmallStrong, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    userName: String,
    userEmail: String,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).background(CtosColors.Surface).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = CtosText.SectionTitle, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconAction(Icons.Default.Search, "Search", onSearch)
        ProfileMenu(userName, userEmail, onLogout)
        Spacer(Modifier.width(4.dp))
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
