<x-guest-layout title="Verify your email" heading="Check your inbox" :subheading="'We sent a verification link to '.auth()->user()->email.'. Open it to activate your account.'">
    <div class="flex items-center gap-4 rounded-2xl bg-brand-50 p-5 dark:bg-brand-500/10">
        <span class="flex size-12 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white"><x-icon name="mail" class="size-6" /></span>
        <p class="text-sm text-slate-700 dark:text-slate-300">The link is valid for 60 minutes. If you cannot find the email, check your spam folder.</p>
    </div>

    <form method="POST" action="{{ route('verification.send') }}" class="mt-6">
        @csrf
        <x-button class="w-full" size="lg">Send the link again</x-button>
    </form>

    <form method="POST" action="{{ route('logout') }}" class="mt-4 text-center">
        @csrf
        <button type="submit" class="text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white">Log out</button>
    </form>
</x-guest-layout>
