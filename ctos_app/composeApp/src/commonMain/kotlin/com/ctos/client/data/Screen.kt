package com.ctos.client.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val title: String = "",
    val icon: ImageVector? = null,
    val displayInNavigation: Boolean = false,
) {
    // (Not visible in the navigation)
    Loading,
    Login,
    Register,
    VerifyEmail,
    ForgotPassword,

    // Navigation (sidebar on a computer, bottom bar on a phone)
    Dashboard("Organizations", Icons.Default.Business, displayInNavigation = true),
    Departments("Departments", Icons.Default.Folder, displayInNavigation = true),
    SecureDrop("Secure Drop", Icons.Default.Lock, displayInNavigation = true),
    Settings("Settings", Icons.Default.Settings, displayInNavigation = true),

    // Detail screens (not in the navigation)
    OrganizationDetail,
    DepartmentDetail,

    // A department's own sections, each its own page (like on the website), reached from DepartmentDetail
    DepartmentMembers,
    DepartmentAccessCode,
    DepartmentInvitations,
    DepartmentLeave,

    // Secure Drop (docs/secure-drop.md), reached from Screen.SecureDrop
    SecureDropSend,
}

/** The department screens: [Destination.departmentSlug] means something on all of these, not just [Screen.DepartmentDetail]. */
val departmentScopedScreens = setOf(
    Screen.DepartmentDetail,
    Screen.DepartmentMembers,
    Screen.DepartmentAccessCode,
    Screen.DepartmentInvitations,
    Screen.DepartmentLeave,
    Screen.SecureDropSend,
)
