<x-guest-layout title="Log in" heading="Welcome back" subheading="Log in to see your departments and files.">
    <form method="POST" action="{{ route('login') }}" class="space-y-5">
        @csrf
        <x-field name="email" label="Email" type="email" required autofocus autocomplete="username" />
        <x-field name="password" label="Password" type="password" required autocomplete="current-password" />

        <div class="flex items-center justify-between">
            <label class="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                <input type="checkbox" name="remember" class="size-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500 dark:border-slate-700 dark:bg-slate-900">
                Remember me
            </label>
            <a href="{{ route('password.request') }}" class="text-sm font-medium text-brand-600 hover:text-brand-500 dark:text-brand-300">Forgot password?</a>
        </div>

        <x-button class="w-full" size="lg">Log in</x-button>
    </form>

    <p class="mt-8 text-center text-sm text-slate-600 dark:text-slate-400">
        New here?
        <a href="{{ route('register') }}" class="font-semibold text-brand-600 hover:text-brand-500 dark:text-brand-300">Create an account</a>
    </p>
</x-guest-layout>
