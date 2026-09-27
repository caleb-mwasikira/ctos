<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use Illuminate\Foundation\Auth\EmailVerificationRequest;
use Illuminate\Http\RedirectResponse;

class EmailVerificationController extends Controller
{
    /**
     * Mark the email address as verified when the signed link from the email is opened.
     */
    public function __invoke(EmailVerificationRequest $request): RedirectResponse
    {
        $request->fulfill();

        return redirect()->route('dashboard')->with('status', 'Your email address is verified. Welcome aboard!');
    }
}
