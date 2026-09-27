<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Notifications\Notification;
use Throwable;

abstract class Controller
{
    /**
     * Email a user, and say whether it worked. A mail failure is reported but never undoes the
     * action that caused the email (a lock must hold even when the mail server is down).
     */
    protected function notifyUser(User $user, Notification $notification): bool
    {
        try {
            $user->notify($notification);

            return true;
        } catch (Throwable $exception) {
            report($exception);

            return false;
        }
    }
}
