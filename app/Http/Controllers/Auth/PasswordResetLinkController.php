<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Password;
use Illuminate\View\View;

class PasswordResetLinkController extends Controller
{
    public function create(): View
    {
        return view('auth.forgot-password');
    }

    /**
     * Email a password reset link.
     *
     * The answer is the same whether or not the email belongs to an account,
     * so this form cannot be used to find out who is registered.
     */
    public function store(Request $request): RedirectResponse
    {
        $request->validate(['email' => ['required', 'string', 'email']]);

        Password::sendResetLink($request->only('email'));

        return back()->with('status', 'If an account exists for that email address, we have sent a link to reset the password.');
    }
}
