<?php

use App\Http\Controllers\Auth\EmailVerificationController;
use App\Http\Controllers\Auth\EmailVerificationNoticeController;
use App\Http\Controllers\Auth\EmailVerificationNotificationController;
use App\Http\Controllers\Auth\NewPasswordController;
use App\Http\Controllers\Auth\PasswordResetLinkController;
use App\Http\Controllers\Auth\RegisteredUserController;
use App\Http\Controllers\Auth\SessionController;
use App\Http\Controllers\DashboardController;
use App\Http\Controllers\DepartmentAccessCodeController;
use App\Http\Controllers\DepartmentController;
use App\Http\Controllers\DepartmentFileController;
use App\Http\Controllers\DepartmentInvitationController;
use App\Http\Controllers\DepartmentMemberController;
use App\Http\Controllers\DepartmentMemberLockController;
use App\Http\Controllers\DepartmentMembershipController;
use App\Http\Controllers\EncryptionKeyController;
use App\Http\Controllers\OrganizationController;
use App\Http\Controllers\OrganizationMemberLockController;
use App\Http\Controllers\SecureDropController;
use Illuminate\Support\Facades\Route;

Route::view('/', 'pages.home')->name('home');
Route::view('/how-it-works', 'pages.how-it-works')->name('how-it-works');
Route::view('/downloads', 'pages.downloads')->name('downloads');

Route::middleware('guest')->group(function () {
    Route::get('/register', [RegisteredUserController::class, 'create'])->name('register');
    Route::post('/register', [RegisteredUserController::class, 'store']);

    Route::get('/login', [SessionController::class, 'create'])->name('login');
    Route::post('/login', [SessionController::class, 'store'])->middleware('throttle:login');

    Route::get('/forgot-password', [PasswordResetLinkController::class, 'create'])->name('password.request');
    Route::post('/forgot-password', [PasswordResetLinkController::class, 'store'])
        ->middleware('throttle:password-reset')
        ->name('password.email');

    Route::get('/reset-password/{token}', [NewPasswordController::class, 'create'])->name('password.reset');
    Route::post('/reset-password', [NewPasswordController::class, 'store'])->name('password.update');
});

// Opening a Secure Drop sent to someone with no account yet (docs/secure-drop.md): the
// unguessable token in the link does the job a login would, so this needs neither. Registered
// ahead of the auth group's own /secure-drop/{secureDrop} so "shared" is never mistaken for an id.
Route::get('/secure-drop/shared/{token}', [SecureDropController::class, 'showShared'])->name('secure-drop.shared.show');
Route::get('/secure-drop/shared/{token}/meta', [SecureDropController::class, 'metaShared'])->name('secure-drop.shared.meta');
Route::get('/secure-drop/shared/{token}/download', [SecureDropController::class, 'downloadShared'])->name('secure-drop.shared.download');

Route::middleware('auth')->group(function () {
    Route::post('/logout', [SessionController::class, 'destroy'])->name('logout');

    Route::get('/email/verify', EmailVerificationNoticeController::class)->name('verification.notice');
    Route::get('/email/verify/{id}/{hash}', EmailVerificationController::class)
        ->middleware(['signed', 'throttle:6,1'])
        ->name('verification.verify');
    Route::post('/email/verification-notification', [EmailVerificationNotificationController::class, 'store'])
        ->middleware('throttle:6,1')
        ->name('verification.send');

    Route::middleware('verified')->group(function () {
        Route::get('/dashboard', DashboardController::class)->name('dashboard');

        Route::get('/departments', [DepartmentController::class, 'index'])->name('departments.index');

        Route::get('/organizations', [OrganizationController::class, 'index'])->name('organizations.index');
        Route::resource('organizations', OrganizationController::class)->only(['store', 'show', 'update']);
        Route::resource('organizations.departments', DepartmentController::class)->shallow()->only(['store', 'show', 'update']);

        Route::get('/departments/{department}/explorer', [DepartmentController::class, 'explorer'])
            ->name('departments.explorer');

        Route::get('/departments/{department}/file', [DepartmentFileController::class, 'show'])
            ->name('departments.files.show');
        Route::get('/departments/{department}/download', [DepartmentFileController::class, 'download'])
            ->name('departments.files.download');
        Route::post('/departments/{department}/files', [DepartmentFileController::class, 'upload'])
            ->name('departments.files.upload');

        Route::get('/departments/{department}/access-code', [DepartmentAccessCodeController::class, 'index'])
            ->name('departments.access-code.index');
        Route::post('/departments/{department}/access-code', [DepartmentAccessCodeController::class, 'store'])
            ->name('departments.access-code.store');

        Route::get('/departments/{department}/invitations', [DepartmentInvitationController::class, 'index'])
            ->name('departments.invitations.index');
        Route::post('/departments/{department}/invitations', [DepartmentInvitationController::class, 'store'])
            ->middleware('throttle:department-share')
            ->name('departments.invitations.store');
        Route::delete('/departments/{department}/invitations/{invitation}', [DepartmentInvitationController::class, 'destroy'])
            ->scopeBindings()
            ->name('departments.invitations.destroy');

        Route::get('/departments/{department}/members', [DepartmentMemberController::class, 'index'])
            ->name('departments.members.index');
        Route::patch('/departments/{department}/members/{member}', [DepartmentMemberController::class, 'update'])
            ->scopeBindings()
            ->name('departments.members.update');
        Route::put('/departments/{department}/members/{member}/lock', [DepartmentMemberLockController::class, 'store'])
            ->scopeBindings()
            ->name('departments.members.lock.store');
        Route::delete('/departments/{department}/members/{member}/lock', [DepartmentMemberLockController::class, 'destroy'])
            ->scopeBindings()
            ->name('departments.members.lock.destroy');

        Route::put('/organizations/{organization}/members/{member}/lock', [OrganizationMemberLockController::class, 'store'])
            ->name('organizations.members.lock.store');
        Route::delete('/organizations/{organization}/members/{member}/lock', [OrganizationMemberLockController::class, 'destroy'])
            ->name('organizations.members.lock.destroy');

        Route::post('/department-memberships', [DepartmentMembershipController::class, 'store'])
            ->middleware('throttle:department-join')
            ->name('department-memberships.store');
        Route::get('/departments/{department}/leave', [DepartmentMembershipController::class, 'edit'])
            ->name('departments.membership.edit');
        Route::delete('/departments/{department}/membership', [DepartmentMembershipController::class, 'destroy'])
            ->name('departments.membership.destroy');

        // Secure Drop: end-to-end encrypted files between department members (docs/secure-drop.md).
        Route::post('/secure-drop/keys/challenge', [EncryptionKeyController::class, 'challenge'])
            ->name('secure-drop.keys.challenge');
        Route::post('/secure-drop/keys', [EncryptionKeyController::class, 'store'])
            ->name('secure-drop.keys.store');
        Route::get('/secure-drop/keys/download', [EncryptionKeyController::class, 'download'])
            ->name('secure-drop.keys.download');
        Route::delete('/secure-drop/keys', [EncryptionKeyController::class, 'destroy'])
            ->name('secure-drop.keys.destroy');

        Route::get('/secure-drop', [SecureDropController::class, 'index'])->name('secure-drop.index');
        Route::post('/secure-drop/lookup', [SecureDropController::class, 'lookup'])->name('secure-drop.lookup');
        Route::post('/secure-drop/send', [SecureDropController::class, 'send'])->name('secure-drop.send');
        Route::get('/departments/{department}/secure-drop/create', [SecureDropController::class, 'create'])
            ->name('secure-drop.create');
        Route::post('/departments/{department}/secure-drop', [SecureDropController::class, 'store'])
            ->name('secure-drop.store');
        Route::get('/secure-drop/{secureDrop}', [SecureDropController::class, 'show'])->name('secure-drop.show');
        Route::get('/secure-drop/{secureDrop}/download', [SecureDropController::class, 'download'])
            ->name('secure-drop.download');
        Route::delete('/secure-drop/{secureDrop}', [SecureDropController::class, 'destroy'])
            ->name('secure-drop.destroy');
    });
});
