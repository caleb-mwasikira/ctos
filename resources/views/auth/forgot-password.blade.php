<x-guest-layout title="Forgot password" heading="Forgot your password?" subheading="Enter your email and we will send you a link to choose a new one.">
    <form method="POST" action="{{ route('password.email') }}" class="space-y-5">
        @csrf
        <x-field name="email" label="Email" type="email" required autofocus autocomplete="username" />

        <x-button class="w-full" size="lg">Email me a reset link</x-button>
    </form>

    <p class="mt-8 text-center text-sm text-slate-600 dark:text-slate-400">
        Remembered it?
        <a href="{{ route('login') }}" class="font-semibold text-brand-600 hover:text-brand-500 dark:text-brand-300">Back to log in</a>
    </p>
</x-guest-layout>
